package com.ketangpai.checkin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ketangpai.checkin.DirectHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HistoryScreen(history: List<DirectHistory>) {
    // 提前返回，避免空提示和铺满的列表在同一个 Column 里互相挤压。
    if (history.isEmpty()) {
        Empty("本机还没有签到记录。")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(history) { record ->
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(record.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(record.time)),
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    record.results.forEach { result ->
                        Text(
                            "${if (result.success) "✓" else "✕"} ${result.account}：${result.message}",
                            Modifier.padding(top = 4.dp),
                            color = if (result.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
