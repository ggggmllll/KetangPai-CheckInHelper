package com.ketangpai.checkin

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalAccountStore(this)
        val repository = DirectRepository()
        setContent { CheckInTheme { CheckInApp(store, repository) } }
    }
}

private enum class Page(val label: String) { HOME("首页"), ACCOUNTS("账号"), PENDING("待签到"), PLACES("地点"), HISTORY("记录") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInApp(store: LocalAccountStore, repository: DirectRepository) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val cachedSnapshot = remember { store.loadSnapshot() }
    val accounts = remember { mutableStateListOf<LocalAccount>().apply { addAll(store.accounts()) } }
    val pending = remember { mutableStateListOf<Attendance>().apply { addAll(cachedSnapshot.first) } }
    val history = remember { mutableStateListOf<DirectHistory>().apply { addAll(store.history()) } }
    val places = remember { mutableStateListOf<GpsPlace>().apply { addAll(store.gpsPlaces()) } }
    var page by remember { mutableStateOf(Page.HOME) }
    var busy by remember { mutableStateOf(false) }
    var addAccount by remember { mutableStateOf(false) }
    var addPlace by remember { mutableStateOf(false) }
    var scanner by remember { mutableStateOf(false) }
    var qrValue by remember { mutableStateOf("") }
    var lastRefresh by remember { mutableStateOf(cachedSnapshot.second) }
    var pollErrors by remember { mutableStateOf(emptyList<String>()) }

    suspend fun refresh(syncCourses: Boolean = false) {
        if (accounts.isEmpty() || busy) return
        busy = true
        try {
            val snapshot = repository.poll(accounts.toList(), syncCourses)
            accounts.clear(); accounts.addAll(snapshot.accounts)
            if (snapshot.errors.isEmpty()) {
                pending.clear(); pending.addAll(snapshot.attendances)
            } else {
                val carried = pending.mapNotNull { item ->
                    val failed = item.accountIds intersect snapshot.failedAccountIds
                    item.copy(accountIds = failed).takeIf { failed.isNotEmpty() }
                }
                val merged = linkedMapOf<String, Attendance>()
                (carried + snapshot.attendances).forEach { item ->
                    val key = "${item.courseId}:${item.id}:${item.type}"
                    merged[key] = merged[key]?.let { old -> old.copy(accountIds = old.accountIds + item.accountIds) } ?: item
                }
                pending.clear(); pending.addAll(merged.values.sortedByDescending { it.createdAt })
            }
            pollErrors = snapshot.errors
            lastRefresh = System.currentTimeMillis()
            store.save(accounts)
            store.saveSnapshot(pending.toList(), lastRefresh)
        } finally { busy = false }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) active = true
            if (event == Lifecycle.Event.ON_PAUSE) active = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active) {
        if (active) {
            var round = 0
            while (true) {
                try { refresh(round % 20 == 0) } catch (e: Exception) { pollErrors = listOf(e.message ?: "刷新失败") }
                round += 1
                delay(15_000)
            }
        }
    }

    fun updateAccount(id: String, value: LocalAccount) {
        val index = accounts.indexOfFirst { it.id == id }
        if (index >= 0) { accounts[index] = value; store.save(accounts) }
    }

    fun runTask(block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (e: Exception) { snack.showSnackbar(e.message ?: "操作失败") }
        }
    }

    val title = when (page) {
        Page.HOME -> "课堂派签到助手"
        Page.ACCOUNTS -> "本机账号池"
        Page.PENDING -> "待签到考勤"
        Page.PLACES -> "GPS 地点"
        Page.HISTORY -> "本机签到记录"
    }
    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (busy) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { runTask { refresh(true) } }) { Icon(Icons.Default.Refresh, "立即刷新") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Page.entries.forEach { destination ->
                    val icon = when (destination) {
                        Page.HOME -> Icons.Default.Home
                        Page.ACCOUNTS -> Icons.Default.AccountCircle
                        Page.PENDING -> Icons.Default.CheckCircle
                        Page.PLACES -> Icons.Default.LocationOn
                        Page.HISTORY -> Icons.Default.History
                    }
                    NavigationBarItem(page == destination, { page = destination }, icon = { Icon(icon, null) }, label = { Text(destination.label) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) { when (page) {
            Page.HOME -> HomeScreen(accounts, pending, pollErrors, lastRefresh, onOpen = { page = it }, onRefresh = { runTask { refresh(true) } })
            Page.ACCOUNTS -> AccountsScreen(accounts, onAdd = { addAccount = true }, onUpdate = ::updateAccount, onDelete = { id ->
                accounts.removeAll { it.id == id }; store.save(accounts)
                val filtered = pending.mapNotNull { item -> item.copy(accountIds = item.accountIds - id).takeIf { it.accountIds.isNotEmpty() } }
                pending.clear(); pending.addAll(filtered); store.saveSnapshot(pending, lastRefresh)
            }, onSync = { runTask { refresh(true) } })
            Page.PENDING -> PendingScreen(
                accounts, pending, places, pollErrors, lastRefresh, qrValue, onScan = { scanner = true }, onQr = { value -> qrValue = value },
                onSign = { item, code, coords -> runTask {
                    var actualCode = code
                    var latestAccounts = accounts.toList()
                    if (item.type == "1" && actualCode.isBlank()) {
                        val account = latestAccounts.firstOrNull { it.id in item.accountIds } ?: throw KtpError("没有可签到的账号")
                        val (renewed, fetched) = repository.getDigitCode(account, item.id)
                        latestAccounts = latestAccounts.map { if (it.id == renewed.id) renewed else it }
                        accounts.clear(); accounts.addAll(latestAccounts); store.save(accounts)
                        actualCode = fetched
                        if (actualCode.isBlank()) throw KtpError("未获取到数字签到码，请手动填写")
                    }
                    val (updated, result) = repository.signAttendance(latestAccounts, item, actualCode, coords.first, coords.second)
                    accounts.clear(); accounts.addAll(updated); history.add(0, result); store.save(accounts); store.saveHistory(history); snack.showSnackbar(resultMessage(result)); refresh(false)
                } }, onQrSign = { value -> runTask {
                    val uri = Uri.parse(value.trim())
                    val courseId = uri.getQueryParameter("courseid").orEmpty(); val ticket = uri.getQueryParameter("ticketid").orEmpty(); val sign = uri.getQueryParameter("sign").orEmpty(); val expire = uri.getQueryParameter("expire").orEmpty()
                    if (courseId.isBlank() || ticket.isBlank() || sign.isBlank() || expire.isBlank()) throw KtpError("二维码缺少 courseid、ticketid、expire 或 sign 参数")
                    val (updated, result) = repository.signQr(accounts.toList(), courseId, ticket, expire, sign)
                    accounts.clear(); accounts.addAll(updated); history.add(0, result); store.save(accounts); store.saveHistory(history); qrValue = ""; snack.showSnackbar(resultMessage(result)); refresh(false)
                } },
            )
            Page.PLACES -> PlacesScreen(places, onAdd = { addPlace = true }, onDelete = { id ->
                places.removeAll { it.id == id }
                store.saveGpsPlaces(places.toList())
            })
            Page.HISTORY -> HistoryScreen(history)
        } }
    }
    if (addAccount) AddAccountDialog(onDismiss = { addAccount = false }, onSave = { email, password ->
        addAccount = false
        runTask {
            if (accounts.any { it.email.equals(email.trim(), ignoreCase = true) }) throw KtpError("该账号已在本机")
            val account = repository.addAccount(email, password)
            accounts.add(account); store.save(accounts)
            snack.showSnackbar("${account.email} 已添加并同步 ${account.courses.size} 门课程")
            refresh(false)
        }
    })
    if (addPlace) AddPlaceDialog(onDismiss = { addPlace = false }, onSave = { name, latitude, longitude ->
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        when {
            name.isBlank() -> scope.launch { snack.showSnackbar("请填写地点名称") }
            lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0 -> scope.launch { snack.showSnackbar("请输入有效的经纬度") }
            else -> {
                places.add(GpsPlace(name = name.trim(), latitude = latitude.trim(), longitude = longitude.trim()))
                store.saveGpsPlaces(places.toList())
                addPlace = false
                scope.launch { snack.showSnackbar("已保存地点：${name.trim()}") }
            }
        }
    })
    if (scanner) QrScanner(onClose = { scanner = false }, onResult = { value -> scanner = false; qrValue = value; page = Page.PENDING })
}

private fun resultMessage(history: DirectHistory): String {
    val success = history.results.count { it.success }; return "${history.title}：$success/${history.results.size} 个账号签到成功"
}

@Composable
private fun HomeScreen(accounts: List<LocalAccount>, pending: List<Attendance>, errors: List<String>, lastRefresh: Long, onOpen: (Page) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("一个人到课，多账号一起签到", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("账号和密码只保存在本机，App 直接请求课堂派。", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = { onOpen(Page.PENDING) }, Modifier.padding(top = 14.dp)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("查看待签到") }
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("账号", accounts.count { it.enabled }.toString(), Modifier.weight(1f)); Stat("待签到", pending.size.toString(), Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = { onOpen(Page.ACCOUNTS) }, Modifier.weight(1f)) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(4.dp)); Text("管理账号") }; OutlinedButton(onClick = onRefresh, Modifier.weight(1f)) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(4.dp)); Text("立即全量同步") } } }
        if (errors.isNotEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(14.dp)) { Text("部分账号刷新失败", fontWeight = FontWeight.SemiBold); errors.take(3).forEach { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall) } } } }
        item { Text("待签到概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (accounts.isEmpty()) item { Empty("先在“账号”页添加课堂派账号，App 才能查询待签到考勤。") }
        else if (pending.isEmpty()) item { Empty("暂无待签到。App 会每 15 秒自动查询课堂派。") }
        items(pending.take(5)) { item -> AttendanceCard(item) { onOpen(Page.PENDING) } }
    }
}

@Composable
private fun AccountsScreen(accounts: List<LocalAccount>, onAdd: () -> Unit, onUpdate: (String, LocalAccount) -> Unit, onDelete: (String) -> Unit, onSync: () -> Unit) {
    var deleteTarget by remember { mutableStateOf<LocalAccount?>(null) }
    var passwordTarget by remember { mutableStateOf<LocalAccount?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("添加课堂派账号") }
            OutlinedButton(onClick = onSync) { Icon(Icons.Default.CloudSync, null); Spacer(Modifier.width(5.dp)); Text("同步") }
        }
        if (accounts.isEmpty()) Empty("把需要代签的课堂派账号添加到这台手机。")
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accounts, key = { it.id }) { account ->
                var coursesExpanded by remember(account.id) { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(account.displayName.ifBlank { account.email }, fontWeight = FontWeight.SemiBold); Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(account.enabled, { onUpdate(account.id, account.copy(enabled = it)) })
                        }
                        TextButton(onClick = { coursesExpanded = !coursesExpanded }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("课程 ${account.courses.size} 门 · 已启用 ${account.courses.count { it.enabled }} 门", style = MaterialTheme.typography.bodySmall)
                                Text(account.courseSemester.ifBlank { "待同步学期信息" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (coursesExpanded) "收起" else "展开")
                            Icon(if (coursesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                        AnimatedVisibility(coursesExpanded) {
                            Column {
                                if (account.courses.isEmpty()) Text("该学期暂无课程", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                account.courses.forEach { course ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(course.enabled, { enabled -> onUpdate(account.id, account.copy(courses = account.courses.map { if (it.id == course.id) it.copy(enabled = enabled) else it })) }); Text(course.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Row {
                            TextButton(onClick = { passwordTarget = account }) { Text("更新密码") }
                            TextButton(onClick = { deleteTarget = account }) { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp)); Text("移除账号", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { target -> AlertDialog(
        onDismissRequest = { deleteTarget = null }, title = { Text("移除账号？") },
        text = { Text("将从本机删除 ${target.email} 的登录信息与课程绑定。") },
        confirmButton = { TextButton(onClick = { onDelete(target.id); deleteTarget = null }) { Text("确认移除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
    ) }
    passwordTarget?.let { target -> UpdatePasswordDialog(target.email, onDismiss = { passwordTarget = null }, onSave = { password ->
        onUpdate(target.id, target.copy(password = password, token = "")); passwordTarget = null; onSync()
    }) }
}

@Composable
private fun PendingScreen(accounts: List<LocalAccount>, pending: List<Attendance>, places: List<GpsPlace>, errors: List<String>, lastRefresh: Long, qrValue: String, onScan: () -> Unit, onQr: (String) -> Unit, onSign: (Attendance, String, Pair<String, String>) -> Unit, onQrSign: (String) -> Unit) {
    val context = LocalContext.current
    var coords by remember { mutableStateOf("") }
    val location = rememberLocationSampler { coords = it.coordinates }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            location.sample()
        } else location.error = "需要定位权限才能采样当前位置"
    }
    var selected by remember { mutableStateOf<Attendance?>(null) }
    var code by remember { mutableStateOf("") }
    var placeMenu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(if (lastRefresh == 0L) "正在刷新课堂派考勤…" else "上次刷新 ${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(lastRefresh))} · 每 15 秒自动刷新", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (errors.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp)) {
                    Text("部分考勤读取失败，显示的是本次成功查询到的结果", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    errors.take(5).forEach { Text(it, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("扫码签到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(qrValue, onQr, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("签到链接（可直接粘贴）") }, minLines = 2)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = onScan, Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("扫码") }; Button(onClick = { onQrSign(qrValue) }, Modifier.weight(1f), enabled = qrValue.isNotBlank()) { Text("为匹配课程签到") } }
            } }
        }
        if (accounts.isEmpty()) item { Empty("先在“账号”页添加课堂派账号，App 才能查询待签到考勤。") }
        else if (pending.isEmpty()) item { Empty(if (errors.isEmpty()) "暂无待签到考勤。App 每 15 秒自动刷新课堂派。" else "本次没有成功读取到待签到考勤，请检查上方错误或手动刷新。") }
        items(pending) { item ->
            AttendanceCard(item) { location.cancel(); selected = item; code = ""; coords = "" }
            if (selected?.id == item.id && selected?.courseId == item.courseId) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("待签账号：${accounts.filter { it.id in item.accountIds }.joinToString("、") { it.displayName.ifBlank { it.email } }}", style = MaterialTheme.typography.bodySmall)
                        if (item.type == "1") OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("数字签到码（可自动获取）") }, singleLine = true)
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
                        if (item.type != "3") Button(onClick = {
                            val parts = coords.split(",", limit = 2).map(String::trim); onSign(item, code, if (parts.size == 2) parts[0] to parts[1] else "" to "")
                        }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = item.type != "2" || (!location.locating && coords.split(",").size == 2)) { Text(if (item.type == "1") "执行数字签到" else "执行 GPS 签到") }
                        if (item.type == "3") Text("请使用上方二维码区域签到。", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceCard(item: Attendance, onSelect: () -> Unit) {
    val type = when (item.type) { "1" -> "数字"; "2" -> "GPS"; else -> "扫码" }
    Card(onClick = onSelect, shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (item.type == "2") Icons.Default.LocationOn else Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) { Text(item.courseName, fontWeight = FontWeight.SemiBold); Text("$type · ${item.title}", style = MaterialTheme.typography.bodySmall); Text("${item.accountIds.size} 个账号可签到", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = onSelect) { Text(if (item.type == "3") "查看" else "签到") }
    } }
}

@Composable
private fun HistoryScreen(history: List<DirectHistory>) {
    if (history.isEmpty()) Empty("本机还没有签到记录。")
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(history) { h ->
        Card(shape = RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(15.dp)) { Text(h.title, fontWeight = FontWeight.SemiBold); Text(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(h.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); h.results.forEach { result -> Text("${if (result.success) "✓" else "✕"} ${result.account}：${result.message}", Modifier.padding(top = 5.dp), color = if (result.success) Color(0xFF16704D) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } } }
    } }
}

@Composable
private fun PlacesScreen(places: List<GpsPlace>, onAdd: () -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GPS 地点列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("保存常用位置，GPS 签到时可直接选择。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("添加地点") }
        }
        if (places.isEmpty()) {
            Empty("还没有保存地点。可以手动输入经纬度，或采样当前手机位置。")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(places, key = { it.id }) { place ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(place.name, fontWeight = FontWeight.SemiBold)
                                Text("纬度 ${place.latitude}", style = MaterialTheme.typography.bodySmall)
                                Text("经度 ${place.longitude}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onDelete(place.id) }) { Icon(Icons.Default.DeleteOutline, "删除地点", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSamplingStatus(location: LocationSampler) {
    if (location.locating) Text("正在同时获取网络和卫星位置…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (location.info.isNotBlank()) Text(location.info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (location.error.isNotBlank()) Text(location.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun AddPlaceDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    val location = rememberLocationSampler {
        latitude = it.latitude.toString()
        longitude = it.longitude.toString()
    }
    val requestLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) location.sample()
        else location.error = "需要定位权限才能采样当前位置"
    }
    fun sampleLocation() {
        location.error = ""
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) location.sample()
        else requestLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 GPS 地点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("地点名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latitude, { location.cancel(); latitude = it }, Modifier.weight(1f), label = { Text("纬度") }, singleLine = true)
                    OutlinedTextField(longitude, { location.cancel(); longitude = it }, Modifier.weight(1f), label = { Text("经度") }, singleLine = true)
                }
                OutlinedButton(onClick = ::sampleLocation, enabled = !location.locating, modifier = Modifier.fillMaxWidth()) {
                    if (location.locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.LocationOn, null)
                    Spacer(Modifier.width(6.dp)); Text(if (location.locating) "正在获取位置…" else "采样当前位置")
                }
                LocationSamplingStatus(location)
                if (location.locating) TextButton(onClick = location::cancel) { Text("取消定位，手动输入") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, latitude, longitude) }, enabled = !location.locating && name.isNotBlank() && latitude.isNotBlank() && longitude.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加课堂派账号") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(email, { email = it }, label = { Text("邮箱或手机号") }, singleLine = true); OutlinedTextField(password, { password = it }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); Text("添加后会立即登录课堂派并同步课程。", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { onSave(email, password) }, enabled = email.isNotBlank() && password.isNotBlank()) { Text("添加") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun UpdatePasswordDialog(email: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("更新 $email 的课堂派密码") },
        text = { OutlinedTextField(password, { password = it }, label = { Text("新密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(password) }, enabled = password.isNotBlank()) { Text("保存并重新登录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable private fun Stat(title: String, value: String, modifier: Modifier) { Card(modifier, shape = RoundedCornerShape(17.dp)) { Column(Modifier.padding(13.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun Empty(text: String) { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) } }
