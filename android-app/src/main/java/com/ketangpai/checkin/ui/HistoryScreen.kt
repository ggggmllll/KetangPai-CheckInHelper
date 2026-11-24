package com.ketangpai.checkin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ketangpai.checkin.DirectHistory
import com.ketangpai.checkin.iosColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HistoryScreen(history: List<DirectHistory>) {
    val caption = if (history.isEmpty()) "本机最多保留 200 条" else "本机最近 ${history.size} 条"

    IosPage("签到记录", caption) {
        if (history.isEmpty()) {
            item { IosGroup { IosEmpty("还没有签到记录。") } }
            return@IosPage
        }
        items(history) { record ->
            IosGroup {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.title,
                            Modifier.weight(1f),
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            color = iosColors.label,
                            maxLines = 1,
                        )
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(record.time)),
                            Modifier.padding(start = 10.dp),
                            fontSize = 13.sp,
                            color = iosColors.secondaryLabel,
                        )
                    }
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        record.results.forEach { result ->
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.padding(top = 6.dp, end = 8.dp)) {
                                    IosDot(if (result.success) iosColors.green else iosColors.red)
                                }
                                Text(
                                    "${result.account}：${result.message}",
                                    Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = if (result.success) iosColors.green else iosColors.red,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
