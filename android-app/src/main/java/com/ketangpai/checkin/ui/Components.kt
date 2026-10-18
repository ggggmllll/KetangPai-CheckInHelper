package com.ketangpai.checkin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ketangpai.checkin.Attendance
import com.ketangpai.checkin.LocationSampler

/** 首页顶部的数字统计卡片。 */
@Composable
internal fun Stat(title: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 列表为空时的居中提示。 */
@Composable
internal fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 定位采样的进度、来源与错误统一展示。 */
@Composable
internal fun LocationSamplingStatus(location: LocationSampler) {
    if (location.locating) Text("正在同时获取网络和卫星位置…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (location.info.isNotBlank()) Text(location.info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (location.error.isNotBlank()) Text(location.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** 一条待签到考勤。首页与待签到页共用。 */
@Composable
internal fun AttendanceCard(item: Attendance, onSelect: () -> Unit) {
    val type = when (item.type) { "1" -> "数字"; "2" -> "GPS"; else -> "扫码" }
    Card(onClick = onSelect, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item.type == "2") Icons.Default.LocationOn else Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(item.courseName, fontWeight = FontWeight.SemiBold)
                Text("$type · ${item.title}", style = MaterialTheme.typography.bodySmall)
                Text("${item.accountIds.size} 个账号可签到", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSelect) { Text(if (item.type == "3") "查看" else "签到") }
        }
    }
}
