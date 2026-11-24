package com.ketangpai.checkin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ketangpai.checkin.LocalAccount
import com.ketangpai.checkin.iosColors

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
    var expandedId by rememberSaveable { mutableStateOf("") }

    val enabledCount = accounts.count { it.enabled }
    val caption = if (accounts.isEmpty()) {
        "账号与密码用系统密钥加密后只存在本机"
    } else {
        "${accounts.size} 个账号 · 启用 $enabledCount · 共 ${accounts.sumOf { it.courses.size }} 门课程"
    }

    IosPage("账号", caption) {
        item {
            IosGroup {
                if (accounts.isEmpty()) {
                    IosEmpty("还没有账号")
                } else {
                    accounts.forEachIndexed { index, account ->
                        AccountBlock(
                            account = account,
                            expanded = account.id == expandedId,
                            onToggle = { expandedId = if (expandedId == account.id) "" else account.id },
                            onUpdate = onUpdate,
                            onPassword = { passwordTarget = account },
                            onRemove = { deleteTarget = account },
                        )
                        if (index < accounts.lastIndex) IosDivider()
                    }
                }
                Text(
                    "添加账号",
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    fontSize = 17.sp,
                    color = iosColors.blue,
                )
            }
        }

        if (accounts.isNotEmpty()) {
            item {
                Text(
                    "点开账号可以逐门关闭课程。签到本身不要求账号选了那门课，关闭课程只影响待签到列表里能不能发现它。",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = iosColors.secondaryLabel,
                )
            }
            item {
                IosButton("重新同步课程", onSync, Modifier.fillMaxWidth(), style = IosButtonStyle.TINTED)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }

    deleteTarget?.let { target ->
        IosAlert(
            title = "移除账号？",
            message = "将从本机删除 ${target.email} 的登录信息与课程列表。",
            confirmText = "移除",
            destructive = true,
            onConfirm = { onDelete(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
    passwordTarget?.let { target ->
        UpdatePasswordDialog(target.email, onDismiss = { passwordTarget = null }, onSave = { password ->
            onUpdate(target.id, target.copy(password = password, token = "")); passwordTarget = null; onSync()
        })
    }
}

/** 一个账号：信息头 + 可展开的课程列表与操作。 */
@Composable
private fun AccountBlock(
    account: LocalAccount,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (String, LocalAccount) -> Unit,
    onPassword: () -> Unit,
    onRemove: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IosAvatar(account.displayName.ifBlank { account.email }, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account.displayName.ifBlank { account.email },
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    color = iosColors.label,
                    maxLines = 1,
                )
                if (account.displayName.isNotBlank()) {
                    Text(
                        account.email,
                        Modifier.padding(top = 1.dp),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = iosColors.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
            Switch(
                checked = account.enabled,
                onCheckedChange = { onUpdate(account.id, account.copy(enabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = iosColors.green,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = iosColors.fill.copy(alpha = 0.45f),
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildString {
                    append("${account.courses.size} 门课程 · 启用 ${account.courses.count { it.enabled }}")
                    if (account.courseSemester.isNotBlank()) append(" · ").append(account.courseSemester)
                },
                Modifier.weight(1f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = iosColors.secondaryLabel,
                maxLines = 1,
            )
            IosChevron(Modifier.rotate(rotation))
        }

        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp)) {
                if (account.courses.isEmpty()) {
                    Text("这个学期还没有课程", fontSize = 13.sp, color = iosColors.secondaryLabel)
                }
                account.courses.forEach { course ->
                    val checked = course.enabled
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdate(
                                    account.id,
                                    account.copy(courses = account.courses.map { if (it.id == course.id) it.copy(enabled = !it.enabled) else it }),
                                )
                            }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(course.name, Modifier.weight(1f), fontSize = 15.sp, color = iosColors.label, maxLines = 1)
                        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                            if (checked) Icon(Icons.Default.Check, null, tint = iosColors.blue, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        "更新密码",
                        Modifier.clickable(onClick = onPassword).padding(vertical = 2.dp),
                        fontSize = 15.sp,
                        color = iosColors.blue,
                    )
                    Text(
                        "移除账号",
                        Modifier.clickable(onClick = onRemove).padding(vertical = 2.dp),
                        fontSize = 15.sp,
                        color = iosColors.red,
                    )
                }
            }
        }
    }
}
