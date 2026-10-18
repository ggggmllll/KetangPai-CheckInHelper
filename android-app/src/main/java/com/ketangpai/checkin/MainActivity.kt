package com.ketangpai.checkin

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ketangpai.checkin.ui.AccountsScreen
import com.ketangpai.checkin.ui.AddAccountDialog
import com.ketangpai.checkin.ui.AddPlaceDialog
import com.ketangpai.checkin.ui.HistoryScreen
import com.ketangpai.checkin.ui.HomeScreen
import com.ketangpai.checkin.ui.PendingScreen
import com.ketangpai.checkin.ui.PlacesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalAccountStore(this)
        val repository = DirectRepository()
        val themePreference = ThemePreference(this)
        setContent {
            var themeMode by remember { mutableStateOf(themePreference.mode) }
            CheckInTheme(themeMode) {
                CheckInApp(
                    store = store,
                    repository = repository,
                    themeMode = themeMode,
                    onThemeModeChange = { mode -> themePreference.mode = mode; themeMode = mode },
                )
            }
        }
    }
}

internal enum class Page(val label: String) {
    HOME("首页"), ACCOUNTS("账号"), PENDING("待签到"), PLACES("地点"), HISTORY("记录")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInApp(
    store: LocalAccountStore,
    repository: DirectRepository,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val accounts = remember { mutableStateListOf<LocalAccount>().apply { addAll(store.accounts()) } }
    // 读完账号池立刻取本次读取的结果，避免被后面的读取覆盖。
    val storeCorrupted = remember { store.readCorrupted }
    val cachedSnapshot = remember { store.loadSnapshot() }
    val pending = remember { mutableStateListOf<Attendance>().apply { addAll(cachedSnapshot.first) } }
    val history = remember { mutableStateListOf<DirectHistory>().apply { addAll(store.history()) } }
    val places = remember { mutableStateListOf<GpsPlace>().apply { addAll(store.gpsPlaces()) } }
    var page by rememberSaveable(stateSaver = PageSaver) { mutableStateOf(Page.HOME) }
    var busy by remember { mutableStateOf(false) }
    var addAccount by rememberSaveable { mutableStateOf(false) }
    var addPlace by rememberSaveable { mutableStateOf(false) }
    // 扫码态不跨重建恢复：相机随 Activity 重建会失效，重新打开更稳妥。
    var scanner by remember { mutableStateOf(false) }
    var qrValue by rememberSaveable { mutableStateOf("") }
    var lastRefresh by remember { mutableStateOf(cachedSnapshot.second) }
    var pollErrors by remember { mutableStateOf(emptyList<String>()) }
    var themeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (storeCorrupted) snack.showSnackbar("本机账号数据无法解密，可能是系统密钥已重置，请重新添加账号")
    }

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
            // 没有待签到、也没有出错时放慢节奏；一旦有待签到就回到 15 秒，
            // 避免空转时每 15 秒把每个账号的每门课都问一遍。
            while (true) {
                try { refresh() } catch (e: Exception) { pollErrors = listOf(e.message ?: "刷新失败") }
                val idle = pending.isEmpty() && pollErrors.isEmpty()
                delay(if (idle) 60_000 else 15_000)
            }
        }
    }

    fun recordHistory(item: DirectHistory) {
        history.add(0, item)
        while (history.size > HISTORY_LIMIT) history.removeAt(history.lastIndex)
        store.saveHistory(history)
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
                    Box {
                        IconButton(onClick = { themeMenu = true }) {
                            Icon(themeMode.icon(), contentDescription = "外观主题")
                        }
                        DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                            ThemeMode.entries.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.label) },
                                    leadingIcon = { Icon(candidate.icon(), null) },
                                    trailingIcon = { if (candidate == themeMode) Icon(Icons.Default.Check, "已选择") },
                                    onClick = { themeMenu = false; onThemeModeChange(candidate) },
                                )
                            }
                        }
                    }
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                Page.HOME -> HomeScreen(accounts, pending, pollErrors, lastRefresh, onOpen = { page = it }, onRefresh = { runTask { refresh(true) } })
                Page.ACCOUNTS -> AccountsScreen(
                    accounts,
                    onAdd = { addAccount = true },
                    onUpdate = ::updateAccount,
                    onDelete = { id ->
                        accounts.removeAll { it.id == id }; store.save(accounts)
                        val filtered = pending.mapNotNull { item -> item.copy(accountIds = item.accountIds - id).takeIf { it.accountIds.isNotEmpty() } }
                        pending.clear(); pending.addAll(filtered); store.saveSnapshot(pending, lastRefresh)
                    },
                    onSync = { runTask { refresh(true) } },
                )
                Page.PENDING -> PendingScreen(
                    accounts, pending, places, pollErrors, lastRefresh, qrValue,
                    onScan = { scanner = true },
                    onQr = { value -> qrValue = value },
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
                        accounts.clear(); accounts.addAll(updated); recordHistory(result); store.save(accounts)
                        snack.showSnackbar(resultMessage(result)); refresh(false)
                    } },
                    onQrSign = { value -> runTask {
                        val uri = Uri.parse(value.trim())
                        val courseId = uri.getQueryParameter("courseid").orEmpty()
                        val ticket = uri.getQueryParameter("ticketid").orEmpty()
                        val sign = uri.getQueryParameter("sign").orEmpty()
                        val expire = uri.getQueryParameter("expire").orEmpty()
                        if (courseId.isBlank() || ticket.isBlank() || sign.isBlank() || expire.isBlank()) throw KtpError("二维码缺少 courseid、ticketid、expire 或 sign 参数")
                        val (updated, result) = repository.signQr(accounts.toList(), courseId, ticket, expire, sign)
                        accounts.clear(); accounts.addAll(updated); recordHistory(result); store.save(accounts)
                        qrValue = ""; snack.showSnackbar(resultMessage(result)); refresh(false)
                    } },
                )
                Page.PLACES -> PlacesScreen(places, onAdd = { addPlace = true }, onDelete = { id ->
                    places.removeAll { it.id == id }
                    store.saveGpsPlaces(places.toList())
                })
                Page.HISTORY -> HistoryScreen(history)
            }
        }
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
    val success = history.results.count { it.success }
    return "${history.title}：$success/${history.results.size} 个账号签到成功"
}

/** 主题菜单里每个模式对应的图标。 */
private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Default.Contrast
    ThemeMode.LIGHT -> Icons.Default.LightMode
    ThemeMode.DARK -> Icons.Default.DarkMode
}

/** 考勤的稳定标识，用于在重组与 Activity 重建后仍然定位到同一条考勤。 */
internal fun Attendance.selectionKey(): String = "$courseId:$id:$type"

/** 让页面选择能跨 Activity 重建保留；枚举改名时回退到首页而不是崩溃。 */
private val PageSaver = Saver<Page, String>(
    save = { it.name },
    restore = { name -> Page.entries.firstOrNull { it.name == name } ?: Page.HOME },
)
