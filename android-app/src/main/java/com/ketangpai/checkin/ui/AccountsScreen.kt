package com.ketangpai.checkin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ketangpai.checkin.LocalAccount

@Composable
internal fun AccountsScreen(
    accounts: List<LocalAccount>,
    onAdd: () -> Unit,
    onUpdate: (String, LocalAccount) -> Unit,
    onDelete: (String) -> Unit,
    onSync: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<LocalAccount?>(null) }
    var passwordTarget by remember { mutableStateOf<LocalAccount?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("添加账号")
            }
            OutlinedButton(onClick = onSync) {
                Icon(Icons.Default.CloudSync, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("同步")
            }
        }
        if (accounts.isEmpty()) Empty("把课堂派账号添加到这台手机。")
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accounts, key = { it.id }) { account ->
                var expanded by remember(account.id) { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.displayName.ifBlank { account.email }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(account.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(account.enabled, { onUpdate(account.id, account.copy(enabled = it)) })
                        }

                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${account.courses.size} 门课程 · 启用 ${account.courses.count { it.enabled }}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (account.courseSemester.isNotBlank()) {
                                Text(
                                    account.courseSemester,
                                    Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "收起课程" else "展开课程",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        AnimatedVisibility(expanded) {
                            Column {
                                if (account.courses.isEmpty()) {
                                    Text("该学期暂无课程", Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                account.courses.forEach { course ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(course.enabled, { enabled ->
                                            onUpdate(account.id, account.copy(courses = account.courses.map { if (it.id == course.id) it.copy(enabled = enabled) else it }))
                                        })
                                        Text(course.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { passwordTarget = account }) { Text("更新密码", style = MaterialTheme.typography.labelMedium) }
                            TextButton(onClick = { deleteTarget = account }) {
                                Text("移除", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("移除账号？") },
            text = { Text("将从本机删除 ${target.email} 的登录信息与课程绑定。") },
            confirmButton = {
                TextButton(onClick = { onDelete(target.id); deleteTarget = null }) { Text("确认移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    passwordTarget?.let { target ->
        UpdatePasswordDialog(target.email, onDismiss = { passwordTarget = null }, onSave = { password ->
            onUpdate(target.id, target.copy(password = password, token = "")); passwordTarget = null; onSync()
        })
    }
}
