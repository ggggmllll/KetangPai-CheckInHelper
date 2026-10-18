package com.ketangpai.checkin.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ketangpai.checkin.Attendance
import com.ketangpai.checkin.GpsPlace
import com.ketangpai.checkin.LocalAccount
import com.ketangpai.checkin.rememberLocationSampler
import com.ketangpai.checkin.selectionKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onSign: (Attendance, String, Pair<String, String>) -> Unit,
    onQrSign: (String) -> Unit,
) {
    val context = LocalContext.current
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
    val selected = pending.firstOrNull { it.selectionKey() == selectedKey }
    var code by rememberSaveable { mutableStateOf("") }
    var placeMenu by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                if (lastRefresh == 0L) "正在刷新课堂派考勤…"
                else "上次刷新 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(lastRefresh))} · 有待签到时每 15 秒刷新",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (errors.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp)) {
                    Text("部分考勤读取失败，显示的是本次成功查询到的结果", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    errors.take(5).forEach { Text(it, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("扫码签到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(qrValue, onQr, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("签到链接（可直接粘贴）") }, minLines = 2)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onScan, Modifier.weight(1f)) {
                            Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("扫码")
                        }
                        Button(onClick = { onQrSign(qrValue) }, Modifier.weight(1f), enabled = qrValue.isNotBlank()) { Text("为匹配课程签到") }
                    }
                }
            }
        }
        if (accounts.isEmpty()) item { Empty("先在“账号”页添加课堂派账号，App 才能查询待签到考勤。") }
        else if (pending.isEmpty()) item {
            Empty(if (errors.isEmpty()) "暂无待签到考勤。没有待签到时 App 每分钟查询一次课堂派。" else "本次没有成功读取到待签到考勤，请检查上方错误或手动刷新。")
        }
        items(pending) { item ->
            AttendanceCard(item) { location.cancel(); selectedKey = item.selectionKey(); code = ""; coords = "" }
            if (selected?.id == item.id && selected?.courseId == item.courseId) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "待签账号：${accounts.filter { it.id in item.accountIds }.joinToString("、") { it.displayName.ifBlank { it.email } }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (item.type == "1") {
                            OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("数字签到码（可自动获取）") }, singleLine = true)
                        }
                        if (item.type == "2") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        if (item.type != "3") {
                            Button(onClick = {
                                val parts = coords.split(",", limit = 2).map(String::trim)
                                onSign(item, code, if (parts.size == 2) parts[0] to parts[1] else "" to "")
                            }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = item.type != "2" || (!location.locating && coords.split(",").size == 2)) {
                                Text(if (item.type == "1") "执行数字签到" else "执行 GPS 签到")
                            }
                        }
                        if (item.type == "3") Text("请使用上方二维码区域签到。", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
