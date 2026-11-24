package com.ketangpai.checkin.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ketangpai.checkin.Attendance
import com.ketangpai.checkin.GpsPlace
import com.ketangpai.checkin.LocalAccount
import com.ketangpai.checkin.SignRequest
import com.ketangpai.checkin.iosColors
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

    val caption = buildString {
        append(if (pending.isEmpty()) "暂无待办" else "${pending.size} 条待办")
        append(" · 启用 ").append(enabledAccounts.size).append(" 个账号")
        if (lastRefresh > 0) append(" · ").append(SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(lastRefresh)))
    }

    IosPage("待签到", caption) {
        if (errors.isNotEmpty()) {
            item {
                Column {
                    errors.take(3).forEach {
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

        // 二维码签到单独一组：扫码、粘贴链接都在这里。
        item {
            IosGroup {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("二维码签到", Modifier.weight(1f), fontSize = 17.sp, color = iosColors.label)
                    Text(
                        if (showQrInput) "收起" else "粘贴链接",
                        Modifier.clickable { showQrInput = !showQrInput }.padding(vertical = 2.dp),
                        fontSize = 15.sp,
                        color = iosColors.blue,
                    )
                }
                AnimatedVisibility(showQrInput) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        IosTextField(qrValue, onQr, "签到链接", singleLine = false)
                    }
                }
                IosDivider()
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IosButton("扫码", onScan, Modifier.weight(1f), style = IosButtonStyle.TINTED)
                    IosButton(
                        "全部签到",
                        onClick = { onQrSign(qrValue, enabledAccounts.map { it.id }.toSet()) },
                        modifier = Modifier.weight(1f),
                        enabled = qrValue.isNotBlank() && enabledAccounts.isNotEmpty(),
                    )
                }
                Text(
                    if (enabledAccounts.isEmpty()) "还没有启用的账号"
                    else "将把二维码提交给 ${enabledAccounts.size} 个启用账号，不受课程绑定限制",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = iosColors.secondaryLabel,
                )
            }
        }

        item { IosSectionHeader("考勤") }

        if (pending.isEmpty()) {
            item {
                IosGroup {
                    IosEmpty(if (accounts.isEmpty()) "先在「账号」页添加课堂派账号" else "暂无待签到考勤")
                }
            }
            return@IosPage
        }

        item {
            IosGroup {
                pending.forEachIndexed { index, item ->
                    val expanded = item.selectionKey() == selectedKey
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (expanded) selectedKey = "" else select(item) }
                                .padding(start = 16.dp, end = 14.dp, top = 13.dp, bottom = if (expanded) 4.dp else 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                            Text(
                                if (expanded) "收起" else "签到",
                                Modifier.padding(start = 8.dp),
                                fontSize = 15.sp,
                                color = iosColors.blue,
                            )
                        }

                        AnimatedVisibility(expanded) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                                Spacer(Modifier.height(2.dp))
                                if (item.type == "3") {
                                    Text(
                                        "这类考勤需要用上方二维码区域签到。",
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = iosColors.secondaryLabel,
                                    )
                                } else {
                                    SignControls(
                                        item = item,
                                        enabledAccounts = enabledAccounts,
                                        targetIds = targetIds,
                                        onTargetIds = { targetIds = it },
                                        showTargets = showTargets,
                                        onShowTargets = { showTargets = !showTargets },
                                        code = code,
                                        onCode = { code = it },
                                        coords = coords,
                                        onCoords = { value -> location.cancel(); coords = value },
                                        location = location,
                                        places = places,
                                        placeMenu = placeMenu,
                                        onPlaceMenu = { placeMenu = it },
                                        onRequestLocation = {
                                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            if (fine || coarse) location.sample()
                                            else requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                        },
                                        onSign = onSign,
                                    )
                                }
                            }
                        }
                    }
                    if (index < pending.lastIndex) IosDivider(startIndent = 37.dp)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

/** 展开后的签到控件：账号勾选、签到码或经纬度、提交按钮。 */
@Composable
private fun SignControls(
    item: Attendance,
    enabledAccounts: List<LocalAccount>,
    targetIds: Set<String>,
    onTargetIds: (Set<String>) -> Unit,
    showTargets: Boolean,
    onShowTargets: () -> Unit,
    code: String,
    onCode: (String) -> Unit,
    coords: String,
    onCoords: (String) -> Unit,
    location: com.ketangpai.checkin.LocationSampler,
    places: List<GpsPlace>,
    placeMenu: Boolean,
    onPlaceMenu: (Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    onSign: (SignRequest) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "签到账号 ${targetIds.size}/${enabledAccounts.size}",
            Modifier.weight(1f),
            fontSize = 15.sp,
            color = iosColors.secondaryLabel,
        )
        Text(
            if (showTargets) "收起" else "调整",
            Modifier.clickable(onClick = onShowTargets).padding(vertical = 4.dp),
            fontSize = 15.sp,
            color = iosColors.blue,
        )
    }

    if (showTargets) {
        Column(Modifier.padding(top = 2.dp)) {
            enabledAccounts.forEach { account ->
                val checked = account.id in targetIds
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTargetIds(if (checked) targetIds - account.id else targetIds + account.id) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        account.displayName.ifBlank { account.email },
                        Modifier.weight(1f),
                        fontSize = 15.sp,
                        color = iosColors.label,
                        maxLines = 1,
                    )
                    if (account.id in item.accountIds) {
                        Text("已选此课", Modifier.padding(end = 8.dp), fontSize = 13.sp, color = iosColors.secondaryLabel)
                    }
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                        if (checked) Icon(Icons.Default.Check, null, tint = iosColors.blue, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    "全选",
                    Modifier.clickable { onTargetIds(enabledAccounts.map { it.id }.toSet()) }.padding(vertical = 4.dp),
                    fontSize = 15.sp,
                    color = iosColors.blue,
                )
                Text(
                    "清空",
                    Modifier.clickable { onTargetIds(emptySet()) }.padding(vertical = 4.dp),
                    fontSize = 15.sp,
                    color = iosColors.blue,
                )
            }
        }
    }

    if (item.type == "1") {
        Spacer(Modifier.height(10.dp))
        IosTextField(code, onCode, "数字签到码（留空自动获取）")
    }

    if (item.type == "2") {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { IosTextField(coords, onCoords, "纬度, 经度") }
            Spacer(Modifier.width(10.dp))
            Box {
                Text(
                    "地点",
                    Modifier.clickable { onPlaceMenu(true) }.padding(vertical = 6.dp, horizontal = 2.dp),
                    fontSize = 15.sp,
                    color = iosColors.blue,
                )
                DropdownMenu(expanded = placeMenu, onDismissRequest = { onPlaceMenu(false) }) {
                    if (places.isEmpty()) {
                        DropdownMenuItem(text = { Text("暂无保存地点") }, onClick = { onPlaceMenu(false) }, enabled = false)
                    } else {
                        places.forEach { place ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(place.name, fontSize = 15.sp)
                                        Text("${place.latitude}, ${place.longitude}", fontSize = 13.sp, color = iosColors.secondaryLabel)
                                    }
                                },
                                onClick = { location.cancel(); onCoords("${place.latitude},${place.longitude}"); onPlaceMenu(false) },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(if (location.locating) "正在定位…" else "采样当前位置", fontSize = 15.sp) },
                        enabled = !location.locating,
                        onClick = { onPlaceMenu(false); onRequestLocation() },
                    )
                }
            }
        }
        LocationStatus(location)
    }

    val parts = coords.split(",", limit = 2).map(String::trim)
    Spacer(Modifier.height(14.dp))
    IosButton(
        if (item.type == "1") "数字签到（${targetIds.size} 个账号）" else "GPS 签到（${targetIds.size} 个账号）",
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
        modifier = Modifier.fillMaxWidth(),
        enabled = targetIds.isNotEmpty() && (item.type != "2" || (!location.locating && parts.size == 2)),
    )
}

/** 定位过程中的状态与错误。 */
@Composable
private fun LocationStatus(location: com.ketangpai.checkin.LocationSampler) {
    Column(Modifier.padding(top = 6.dp)) {
        if (location.locating) {
            Text("正在同时获取网络与卫星位置…", fontSize = 13.sp, color = iosColors.secondaryLabel)
        }
        if (location.info.isNotBlank()) {
            Text(location.info, fontSize = 13.sp, lineHeight = 18.sp, color = iosColors.secondaryLabel)
        }
        if (location.error.isNotBlank()) {
            Text(location.error, fontSize = 13.sp, lineHeight = 18.sp, color = iosColors.red)
        }
        if (location.locating) {
            Text(
                "取消定位",
                Modifier.clickable { location.cancel() }.padding(top = 6.dp, bottom = 2.dp),
                fontSize = 15.sp,
                color = iosColors.blue,
            )
        }
    }
}
