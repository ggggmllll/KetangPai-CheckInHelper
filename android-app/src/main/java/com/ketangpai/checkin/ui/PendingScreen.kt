package com.ketangpai.checkin.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ketangpai.checkin.Attendance
import com.ketangpai.checkin.GpsPlace
import com.ketangpai.checkin.LocalAccount
import com.ketangpai.checkin.SignRequest
import com.ketangpai.checkin.rememberLocationSampler
import com.ketangpai.checkin.selectionKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 选中账号集合需要能跨 Activity 重建保存，用逗号拼接即可。 */
private val TargetIdsSaver = Saver<Set<String>, String>(
    save = { it.joinToString(",") },
    restore = { raw -> raw.split(",").map(String::trim).filter(String::isNotBlank).toSet() },
)

private fun typeLabel(type: String): String = when (type) {
    "1" -> "数字"
    "2" -> "GPS"
    else -> "扫码"
}

@Composable
internal fun PendingScreen(
    accounts: List<LocalAccount>,
    pending: List<Attendance>,
    places: List<GpsPlace>,
    errors: List<String>,
    lastRefresh: Long,
    qrValue: String,
    onScan: () -> Unit,
    onQr: (String) -> Unit,
    onSign: (SignRequest) -> Unit,
    onQrSign: (String, Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val enabledAccounts = remember(accounts) { accounts.filter { it.enabled } }
    var coords by rememberSaveable { mutableStateOf("") }
    val location = rememberLocationSampler { coords = it.coordinates }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            location.sample()
        } else {
            location.error = "需要定位权限才能采样当前位置"
        }
    }
    // 只记录选中的考勤标识，重建后从最新列表里重新取，避免持有过期对象。
    var selectedKey by rememberSaveable { mutableStateOf("") }
    var targetIds by rememberSaveable(stateSaver = TargetIdsSaver) { mutableStateOf(emptySet<String>()) }
    var showTargets by rememberSaveable { mutableStateOf(false) }
    var showQrInput by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var placeMenu by remember { mutableStateOf(false) }

    // 默认勾选原本就选了这门课的账号；没有匹配账号时退回全部启用账号，
    // 这样任何账号都能给任何课程签到。
    fun select(item: Attendance) {
        selectedKey = item.selectionKey()
        code = ""
        coords = ""
        showTargets = false
        val matched = item.accountIds.filter { id -> enabledAccounts.any { it.id == id } }.toSet()
        targetIds = matched.ifEmpty { enabledAccounts.map { it.id }.toSet() }
    }

    val selected = pending.firstOrNull { it.selectionKey() == selectedKey }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                buildString {
                    if (lastRefresh == 0L) append("正在刷新…")
                    else append(SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(lastRefresh)))
                    append(" · 待签到 ").append(pending.size)
                    if (enabledAccounts.isNotEmpty()) append(" · 启用账号 ").append(enabledAccounts.size)
                },
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (errors.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("部分考勤读取失败", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                    errors.take(3).forEach {
                        Text(it, Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("二维码签到", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showQrInput = !showQrInput }) { Text(if (showQrInput) "收起" else "粘贴链接") }
                    }
                    AnimatedVisibility(showQrInput) {
                        OutlinedTextField(qrValue, onQr, Modifier.fillMaxWidth().padding(bottom = 6.dp), label = { Text("签到链接") }, minLines = 2)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onScan, Modifier.weight(1f)) {
                            Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("扫码")
                        }
                        Button(
                            onClick = { onQrSign(qrValue, enabledAccounts.map { it.id }.toSet()) },
                            Modifier.weight(1f),
                            enabled = qrValue.isNotBlank() && enabledAccounts.isNotEmpty(),
                        ) { Text("为所有账号签到") }
                    }
                    Text(
                        if (enabledAccounts.isEmpty()) "还没有启用的账号"
                        else "将对 ${enabledAccounts.size} 个启用账号提交，不受课程绑定限制",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (accounts.isEmpty()) item { Empty("先在「账号」页添加课堂派账号。") }
        else if (pending.isEmpty()) item { Empty(if (errors.isEmpty()) "暂无待签到考勤。" else "本次没有读到考勤，请检查上方错误或稍后重试。") }

        items(pending, key = { it.selectionKey() }) { item ->
            val expanded = selected?.selectionKey() == item.selectionKey()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.courseName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${typeLabel(item.type)} · ${item.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { if (expanded) selectedKey = "" else select(item) }) {
                            Text(if (expanded) "收起" else "去签到")
                        }
                    }

                    AnimatedVisibility(expanded) {
                        Column {
                            // 账号选择：默认带上选了这门课的账号，也可以任意增减。
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "签到账号 ${targetIds.size}/${enabledAccounts.size}",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                TextButton(onClick = { showTargets = !showTargets }) { Text(if (showTargets) "收起" else "调整") }
                            }
                            if (showTargets) {
                                enabledAccounts.forEach { account ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(account.id in targetIds, { checked ->
                                            targetIds = if (checked) targetIds + account.id else targetIds - account.id
                                        })
                                        Text(
                                            account.displayName.ifBlank { account.email },
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (account.id in item.accountIds) {
                                            Text("已选此课", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { targetIds = enabledAccounts.map { it.id }.toSet() }) { Text("全选") }
                                    TextButton(onClick = { targetIds = emptySet() }) { Text("清空") }
                                }
                            }

                            if (item.type == "1") {
                                OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("数字签到码（留空自动获取）") }, singleLine = true)
                            }

                            if (item.type == "2") {
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(coords, { location.cancel(); coords = it }, Modifier.weight(1f), label = { Text("纬度, 经度") }, singleLine = true)
                                    Box {
                                        TextButton(onClick = { placeMenu = true }) { Text("地点") }
                                        DropdownMenu(expanded = placeMenu, onDismissRequest = { placeMenu = false }) {
                                            if (places.isEmpty()) {
                                                DropdownMenuItem(text = { Text("暂无保存地点") }, onClick = { placeMenu = false }, enabled = false)
                                            } else {
                                                places.forEach { place ->
                                                    DropdownMenuItem(
                                                        text = { Column { Text(place.name); Text("${place.latitude}, ${place.longitude}", style = MaterialTheme.typography.labelSmall) } },
                                                        onClick = { location.cancel(); coords = "${place.latitude},${place.longitude}"; placeMenu = false },
                                                    )
                                                }
                                            }
                                            DropdownMenuItem(text = { Text(if (location.locating) "正在定位…" else "采样当前位置") }, enabled = !location.locating, onClick = {
                                                placeMenu = false
                                                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                                if (fine || coarse) location.sample()
                                                else requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                            })
                                        }
                                    }
                                }
                                LocationSamplingStatus(location)
                                if (location.locating) TextButton(onClick = location::cancel) { Text("取消定位") }
                            }

                            if (item.type == "3") {
                                Text("这类考勤需要用上方二维码区域签到。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val parts = coords.split(",", limit = 2).map(String::trim)
                                Button(
                                    onClick = {
                                        onSign(
                                            SignRequest(
                                                item = item,
                                                targetIds = targetIds,
                                                code = code,
                                                latitude = if (parts.size == 2) parts[0] else "",
                                                longitude = if (parts.size == 2) parts[1] else "",
                                            ),
                                        )
                                    },
                                    Modifier.fillMaxWidth().padding(top = 8.dp),
                                    enabled = targetIds.isNotEmpty() && (item.type != "2" || (!location.locating && parts.size == 2)),
                                ) {
                                    Text(if (item.type == "1") "数字签到（${targetIds.size} 个账号）" else "GPS 签到（${targetIds.size} 个账号）")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
