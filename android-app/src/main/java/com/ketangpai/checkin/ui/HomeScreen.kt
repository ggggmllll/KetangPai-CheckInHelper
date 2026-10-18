package com.ketangpai.checkin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ketangpai.checkin.Attendance
import com.ketangpai.checkin.LocalAccount
import com.ketangpai.checkin.Page

@Composable
internal fun HomeScreen(
    accounts: List<LocalAccount>,
    pending: List<Attendance>,
    errors: List<String>,
    lastRefresh: Long,
    onOpen: (Page) -> Unit,
    onRefresh: () -> Unit,
) {
    val enabled = accounts.count { it.enabled }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("待签到", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                pending.size.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("启用账号", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                enabled.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Text(
                        "账号与密码只保存在本机。",
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(Page.PENDING) }, Modifier.weight(1f)) { Text("查看待签到") }
                        OutlinedButton(onClick = onRefresh, Modifier.weight(1f)) {
                            Icon(Icons.Default.Sync, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("同步")
                        }
                    }
                }
            }
        }

        if (errors.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("部分账号刷新失败", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                    errors.take(2).forEach {
                        Text(it, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("最近考勤", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (pending.isNotEmpty()) TextButton(onClick = { onOpen(Page.PENDING) }) { Text("全部") }
            }
        }

        if (accounts.isEmpty()) item { Empty("先在「账号」页添加课堂派账号。") }
        else if (pending.isEmpty()) item { Empty(if (lastRefresh == 0L) "正在刷新课堂派…" else "暂无待签到考勤，App 会自动轮询。") }

        items(pending.take(3)) { item -> AttendanceCard(item) { onOpen(Page.PENDING) } }
    }
}
