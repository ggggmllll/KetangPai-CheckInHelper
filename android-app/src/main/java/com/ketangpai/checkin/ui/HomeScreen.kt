package com.ketangpai.checkin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("一个人到课，多账号一起签到", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("账号和密码只保存在本机，App 直接请求课堂派。", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = { onOpen(Page.PENDING) }, Modifier.padding(top = 14.dp)) {
                        Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("查看待签到")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("账号", accounts.count { it.enabled }.toString(), Modifier.weight(1f))
                Stat("待签到", pending.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onOpen(Page.ACCOUNTS) }, Modifier.weight(1f)) {
                    Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(4.dp)); Text("管理账号")
                }
                OutlinedButton(onClick = onRefresh, Modifier.weight(1f)) {
                    Icon(Icons.Default.Sync, null); Spacer(Modifier.width(4.dp)); Text("立即全量同步")
                }
            }
        }
        if (errors.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("部分账号刷新失败", fontWeight = FontWeight.SemiBold)
                    errors.take(3).forEach { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Text("待签到概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (accounts.isEmpty()) item { Empty("先在“账号”页添加课堂派账号，App 才能查询待签到考勤。") }
        else if (pending.isEmpty()) item { Empty("暂无待签到。App 会自动查询课堂派，没有待签到时放慢到 1 分钟一次。") }
        items(pending.take(5)) { item -> AttendanceCard(item) { onOpen(Page.PENDING) } }
    }
}
