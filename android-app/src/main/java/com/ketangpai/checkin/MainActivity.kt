package com.ketangpai.checkin

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ketangpai.checkin.ui.AccountsScreen
import com.ketangpai.checkin.ui.AddAccountDialog
import com.ketangpai.checkin.ui.AddPlaceDialog
import com.ketangpai.checkin.ui.HistoryScreen
import com.ketangpai.checkin.ui.HomeScreen
import com.ketangpai.checkin.ui.IosBarButton
import com.ketangpai.checkin.ui.IosTabBar
import com.ketangpai.checkin.ui.IosToast
import com.ketangpai.checkin.ui.IosToastState
import com.ketangpai.checkin.ui.IosToolbar
import com.ketangpai.checkin.ui.PendingScreen
import com.ketangpai.checkin.ui.PlacesScreen
import com.ketangpai.checkin.ui.SettingsSheet
import com.ketangpai.checkin.ui.UpdatePasswordDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 内容要铺到系统栏和输入法后面，insets 交给 Compose 消费，
        // 底部表单才能跟着键盘一起上移。必须在 super.onCreate 之前调用。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = LocalAccountStore(this)
        val repository = DirectRepository()
        val themePreference = ThemePreference(this)
        val appSettings = AppSettings(this)
        setContent {
            var themeMode by remember { mutableStateOf(themePreference.mode) }
            CheckInTheme(themeMode) {
                CheckInApp(
                    store = store,
                    repository = repository,
                    appSettings = appSettings,
                    themeMode = themeMode,
                    onThemeModeChange = { mode -> themePreference.mode = mode; themeMode = mode },
                )
            }
        }
    }
}

internal enum class Page(val label: String) {
    HOME("首页"), PENDING("待签到"), ACCOUNTS("账号"), PLACES("地点"), HISTORY("记录")
}

@Composable
private fun CheckInApp(
    store: LocalAccountStore,
    repository: DirectRepository,
    appSettings: AppSettings,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toast = remember { IosToastState() }
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
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var autoSignOnScan by remember { mutableStateOf(appSettings.autoSignOnScan) }
    // 只记 id，账号信息变化后仍能取到最新的一份。
    var passwordTargetId by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (storeCorrupted) toast.show("本机账号数据无法解密，可能是系统密钥已重置，请重新添加账号")
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
            try { block() } catch (e: Exception) { toast.show(e.message ?: "操作失败") }
        }
    }

    /** 解析二维码链接并提交给指定账号；扫码自动签到与手动提交共用这条路。 */
    suspend fun submitQr(value: String, targetIds: Set<String>) {
        val uri = Uri.parse(value.trim())
        val courseId = uri.getQueryParameter("courseid").orEmpty()
        val ticket = uri.getQueryParameter("ticketid").orEmpty()
        val sign = uri.getQueryParameter("sign").orEmpty()
        val expire = uri.getQueryParameter("expire").orEmpty()
        if (courseId.isBlank() || ticket.isBlank() || sign.isBlank() || expire.isBlank()) {
            throw KtpError("二维码缺少 courseid、ticketid、expire 或 sign 参数")
        }
        val (updated, result) = repository.signQr(accounts.toList(), targetIds, courseId, ticket, expire, sign)
        accounts.clear(); accounts.addAll(updated); recordHistory(result); store.save(accounts)
        qrValue = ""; toast.show(resultMessage(result)); refresh(false)
    }

    Scaffold(
        containerColor = iosColors.groupedBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            IosToolbar {
                if (busy) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = iosColors.secondaryLabel)
                    }
                } else {
                    IosBarButton(Icons.Outlined.Refresh, "立即刷新", onClick = { runTask { refresh(true) } })
                }
                IosBarButton(Icons.Outlined.Settings, "设置", onClick = { settingsOpen = true })
            }
        },
        bottomBar = { IosTabBar(page) { page = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
                    onChangePassword = { account -> passwordTargetId = account.id },
                )
                Page.PENDING -> PendingScreen(
                    accounts, pending, places, pollErrors, lastRefresh, qrValue,
                    onScan = { scanner = true },
                    onQr = { value -> qrValue = value },
                    onSign = { request -> runTask {
                        var latestAccounts = accounts.toList()
                        var code = request.code
                        if (request.item.type == "1" && code.isBlank()) {
                            // 取数字码优先用本来就选了这门课的账号，其次才退到任意已选账号。
                            val account = latestAccounts.firstOrNull { it.id in request.targetIds && it.id in request.item.accountIds }
                                ?: latestAccounts.firstOrNull { it.id in request.targetIds }
                                ?: throw KtpError("请至少选择一个要签到的账号")
                            val (renewed, fetched) = repository.getDigitCode(account, request.item.id)
                            latestAccounts = latestAccounts.map { if (it.id == renewed.id) renewed else it }
                            accounts.clear(); accounts.addAll(latestAccounts); store.save(accounts)
                            code = fetched
                            if (code.isBlank()) throw KtpError("未获取到数字签到码，请手动填写")
                        }
                        val (updated, result) = repository.signAttendance(latestAccounts, request.copy(code = code))
                        accounts.clear(); accounts.addAll(updated); recordHistory(result); store.save(accounts)
                        toast.show(resultMessage(result)); refresh(false)
                    } },
                    onQrSign = { value, targetIds -> runTask { submitQr(value, targetIds) } },
                )
                Page.PLACES -> PlacesScreen(places, onAdd = { addPlace = true }, onDelete = { id ->
                    places.removeAll { it.id == id }
                    store.saveGpsPlaces(places.toList())
                })
                Page.HISTORY -> HistoryScreen(history)
            }
            IosToast(toast, Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 24.dp, end = 24.dp))
        }
    }

    if (addAccount) AddAccountDialog(onDismiss = { addAccount = false }, onSave = { email, password ->
        addAccount = false
        runTask {
            if (accounts.any { it.email.equals(email.trim(), ignoreCase = true) }) throw KtpError("该账号已在本机")
            val account = repository.addAccount(email, password)
            accounts.add(account); store.save(accounts)
            toast.show("${account.email} 已添加并同步 ${account.courses.size} 门课程")
            refresh(false)
        }
    })
    if (addPlace) AddPlaceDialog(onDismiss = { addPlace = false }, onSave = { name, latitude, longitude ->
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        when {
            name.isBlank() -> scope.launch { toast.show("请填写地点名称") }
            lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0 -> scope.launch { toast.show("请输入有效的经纬度") }
            else -> {
                places.add(GpsPlace(name = name.trim(), latitude = latitude.trim(), longitude = longitude.trim()))
                store.saveGpsPlaces(places.toList())
                addPlace = false
                scope.launch { toast.show("已保存地点：${name.trim()}") }
            }
        }
    })
    accounts.firstOrNull { it.id == passwordTargetId }?.let { target ->
        UpdatePasswordDialog(
            target.email,
            onDismiss = { passwordTargetId = "" },
            onSave = { password ->
                passwordTargetId = ""
                updateAccount(target.id, target.copy(password = password, token = ""))
                runTask { refresh(true) }
            },
        )
    }
    if (scanner) QrScanner(
        onClose = { scanner = false },
        onResult = { value ->
            scanner = false
            // 先把链接落进输入框：自动签到失败时，用户还能在待签到页手动重试。
            qrValue = value
            page = Page.PENDING
            if (autoSignOnScan) {
                val targets = accounts.filter { it.enabled }.map { it.id }.toSet()
                if (targets.isEmpty()) scope.launch { toast.show("没有启用的账号，先到「账号」页添加") }
                else runTask { submitQr(value, targets) }
            }
        },
    )
    if (settingsOpen) SettingsSheet(
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        autoSignOnScan = autoSignOnScan,
        onAutoSignOnScanChange = { autoSignOnScan = it; appSettings.autoSignOnScan = it },
        onDismiss = { settingsOpen = false },
    )
}

private fun resultMessage(history: DirectHistory): String {
    val success = history.results.count { it.success }
    return "${history.title}：$success/${history.results.size} 个账号签到成功"
}

/** 考勤的稳定标识，用于在重组与 Activity 重建后仍然定位到同一条考勤。 */
internal fun Attendance.selectionKey(): String = "$courseId:$id:$type"

/** 让页面选择能跨 Activity 重建保留；枚举改名时回退到首页而不是崩溃。 */
private val PageSaver = Saver<Page, String>(
    save = { it.name },
    restore = { name -> Page.entries.firstOrNull { it.name == name } ?: Page.HOME },
)
