package com.ketangpai.checkin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ketangpai.checkin.Attendance
import com.ketangpai.checkin.LocalAccount
import com.ketangpai.checkin.Page
import com.ketangpai.checkin.iosColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun clockTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))

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
    val recent = pending.take(3)

    IosPage("签到助手", "账号与密码只保存在本机") {
        item {
            IosGroup {
                StatusLine("待签到", if (pending.isEmpty()) "无" else "${pending.size} 条")
                StatusLine("启用账号", "${enabled} 个")
                StatusLine("最近同步", if (lastRefresh == 0L) "正在刷新…" else clockTime(lastRefresh), divider = false)
                ActionLine(if (pending.isEmpty()) "立即同步" else "查看待签到考勤") {
                    if (pending.isEmpty()) onRefresh() else onOpen(Page.PENDING)
                }
            }
        }

        if (errors.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 10.dp)) {
                    errors.take(2).forEach {
                        Text(
                            it,
                            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = iosColors.red,
                        )
                    }
                }
            }
        }

        if (accounts.isEmpty()) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    IosGroup {
                        Column(Modifier.padding(16.dp)) {
                            Text("还没有账号", fontSize = 17.sp, color = iosColors.label)
                            Text(
                                "先在「账号」页添加课堂派账号，App 才能查询考勤。",
                                Modifier.padding(top = 4.dp),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = iosColors.secondaryLabel,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    IosButton("去添加账号", onClick = { onOpen(Page.ACCOUNTS) })
                }
            }
            return@IosPage
        }

        item {
            IosSectionHeader(
                "最近考勤",
                action = if (pending.isEmpty()) null else "全部",
                onAction = { onOpen(Page.PENDING) },
            )
        }
        if (recent.isEmpty()) {
            item {
                IosGroup {
                    IosEmpty(if (lastRefresh == 0L) "正在刷新课堂派…" else "暂无待签到考勤，App 会自动轮询")
                }
            }
        } else {
            item {
                IosGroup {
                    recent.forEachIndexed { index, item ->
                        AttendanceLine(item, showDivider = index < recent.lastIndex) { onOpen(Page.PENDING) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

/** 概览里的一行：左边标签，右边值。 */
@Composable
private fun StatusLine(label: String, value: String, divider: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 17.sp, color = iosColors.label)
        Text(value, fontSize = 17.sp, color = iosColors.secondaryLabel)
    }
    if (divider) IosDivider()
}

/** 卡片里的蓝色操作行，替代原来占一整行的大按钮。 */
@Composable
private fun ActionLine(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        fontSize = 17.sp,
        color = iosColors.blue,
    )
}

/** 一条待签到考勤，类型用颜色点区分。 */
@Composable
internal fun AttendanceLine(item: Attendance, showDivider: Boolean = true, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点与两行文字的第一行视觉居中对齐，靠上留 6dp 补正。
            Box(Modifier.padding(top = 6.dp, end = 11.dp)) { IosDot(typeColor(item.type)) }
            Column(Modifier.weight(1f)) {
                Text(item.courseName, fontSize = 17.sp, lineHeight = 22.sp, color = iosColors.label, maxLines = 1)
                Text(
                    "${typeLabel(item.type)} · ${item.title} · ${item.accountIds.size} 个账号",
                    Modifier.padding(top = 2.dp),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = iosColors.secondaryLabel,
                    maxLines = 1,
                )
            }
            IosChevron()
        }
        if (showDivider) IosDivider(startIndent = 37.dp)
    }
}

internal fun typeLabel(type: String): String = when (type) {
    "1" -> "数字"
    "2" -> "GPS"
    else -> "扫码"
}

/** 三种签到类型各自的标识色。 */
@Composable
internal fun typeColor(type: String): Color = when (type) {
    "1" -> iosColors.blue
    "2" -> iosColors.orange
    else -> iosColors.green
}
