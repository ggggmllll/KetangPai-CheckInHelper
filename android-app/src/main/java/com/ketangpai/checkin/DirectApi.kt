package com.ketangpai.checkin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KTP_BASE = "https://openapiv5.ketangpai.com"

/** 本机最多保留的签到记录条数，内存与落盘使用同一个上限。 */
internal const val HISTORY_LIMIT = 200

/** 课程列表多久重新同步一次：只有跨学期或课程变动后才需要，不必每轮都拉。 */
internal const val COURSE_SYNC_INTERVAL_MS = 3 * 60 * 60 * 1000L

data class LocalCourse(val id: String, val name: String, val semester: String = "", val enabled: Boolean = true)
data class LocalAccount(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    val password: String,
    val token: String = "",
    val displayName: String = "",
    val enabled: Boolean = true,
    val courses: List<LocalCourse> = emptyList(),
    val courseSemester: String = "",
    val courseSyncedAt: Long = 0L,
)
data class Attendance(
    val id: String,
    val courseId: String,
    val courseName: String,
    val type: String,
    val title: String,
    val createdAt: Long,
    val accountIds: Set<String>,
)

/** 一次签到请求：目标考勤、参与签到的账号，以及本次填写的内容。 */
data class SignRequest(
    val item: Attendance,
    val targetIds: Set<String>,
    val code: String = "",
    val latitude: String = "",
    val longitude: String = "",
)
data class DirectResult(val account: String, val success: Boolean, val message: String)
data class DirectHistory(val time: Long, val title: String, val type: String, val results: List<DirectResult>)
data class GpsPlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: String,
    val longitude: String,
)
data class PollSnapshot(val accounts: List<LocalAccount>, val attendances: List<Attendance>, val errors: List<String>, val failedAccountIds: Set<String>)

class KtpError(message: String, val apiCode: Int = 0) : Exception(message)

/** Encrypted local storage for the app's account pool and saved places. */
class LocalAccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("ketangpai_accounts", Context.MODE_PRIVATE)
    private val alias = "ketangpai_accounts_aes"

    /**
     * 读取时发现既有数据无法解密或无法解析：本机密钥失效（换机、恢复出厂、系统重置）
     * 或数据损坏。调用方据此提示用户重新登录，而不是把账号池当成「空」处理。
     */
    var readCorrupted = false
        private set

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }

    private fun decrypt(name: String): String? {
        // 从未写入过，属于正常空数据，不算损坏。
        val encoded = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            readCorrupted = true
            null
        }
    }

    private fun encrypt(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    fun accounts(): List<LocalAccount> = runCatching {
        val array = JSONArray(decrypt("data") ?: "[]")
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::accountFromJson) }
    }.getOrElse { readCorrupted = true; emptyList() }

    fun save(accounts: List<LocalAccount>) {
        val array = JSONArray(); accounts.forEach { array.put(accountToJson(it)) }; encrypt("data", array.toString())
    }

    fun history(): List<DirectHistory> = runCatching {
        val array = JSONArray(decrypt("history") ?: "[]")
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { h ->
            val results = h.optJSONArray("results") ?: JSONArray()
            DirectHistory(h.optLong("time"), h.optString("title"), h.optString("type"),
                (0 until results.length()).mapNotNull { j -> results.optJSONObject(j)?.let { r -> DirectResult(r.optString("account"), r.optBoolean("success"), r.optString("message")) } })
        } }
    }.getOrDefault(emptyList())

    fun saveHistory(history: List<DirectHistory>) {
        val array = JSONArray(); history.take(HISTORY_LIMIT).forEach { h ->
            val results = JSONArray(); h.results.forEach { r -> results.put(JSONObject().put("account", r.account).put("success", r.success).put("message", r.message)) }
            array.put(JSONObject().put("time", h.time).put("title", h.title).put("type", h.type).put("results", results))
        }; encrypt("history", array.toString())
    }

    fun gpsPlaces(): List<GpsPlace> = runCatching {
        val array = JSONArray(decrypt("gps_places") ?: "[]")
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { place ->
            GpsPlace(place.optString("id").ifBlank { UUID.randomUUID().toString() }, place.optString("name"), place.optString("latitude"), place.optString("longitude"))
        } }.filter { it.name.isNotBlank() && it.latitude.isNotBlank() && it.longitude.isNotBlank() }
    }.getOrDefault(emptyList())

    fun saveGpsPlaces(places: List<GpsPlace>) {
        val array = JSONArray()
        places.forEach { place ->
            array.put(JSONObject().put("id", place.id).put("name", place.name).put("latitude", place.latitude).put("longitude", place.longitude))
        }
        encrypt("gps_places", array.toString())
    }

    fun loadSnapshot(): Pair<List<Attendance>, Long> = runCatching {
        val saved = JSONObject(decrypt("snapshot") ?: return emptyList<Attendance>() to 0L)
        val array = saved.optJSONArray("pending") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { obj ->
            val ids = obj.optJSONArray("account_ids") ?: JSONArray()
            Attendance(obj.optString("id"), obj.optString("course_id"), obj.optString("course_name"), obj.optString("type"), obj.optString("title"), obj.optLong("created_at"),
                (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotBlank) }.toSet())
        } }
        items to saved.optLong("refreshed_at")
    }.getOrDefault(emptyList<Attendance>() to 0L)

    fun saveSnapshot(items: List<Attendance>, refreshedAt: Long) {
        val array = JSONArray(); items.forEach { item ->
            val ids = JSONArray(); item.accountIds.forEach(ids::put)
            array.put(JSONObject().put("id", item.id).put("course_id", item.courseId).put("course_name", item.courseName)
                .put("type", item.type).put("title", item.title).put("created_at", item.createdAt).put("account_ids", ids))
        }
        encrypt("snapshot", JSONObject().put("pending", array).put("refreshed_at", refreshedAt).toString())
    }

    companion object {
        private fun accountFromJson(o: JSONObject): LocalAccount {
            val courses = o.optJSONArray("courses") ?: JSONArray()
            return LocalAccount(o.optString("id"), o.optString("email"), o.optString("password"), o.optString("token"), o.optString("displayName"), o.optBoolean("enabled", true),
                (0 until courses.length()).mapNotNull { i -> courses.optJSONObject(i)?.let { c -> LocalCourse(c.optString("id"), c.optString("name"), c.optString("semester"), c.optBoolean("enabled", true)) } }, o.optString("courseSemester"),
                o.optLong("courseSyncedAt"))
        }
        private fun accountToJson(a: LocalAccount): JSONObject {
            val courses = JSONArray(); a.courses.forEach { c -> courses.put(JSONObject().put("id", c.id).put("name", c.name).put("semester", c.semester).put("enabled", c.enabled)) }
            return JSONObject().put("id", a.id).put("email", a.email).put("password", a.password).put("token", a.token).put("displayName", a.displayName).put("enabled", a.enabled).put("courses", courses).put("courseSemester", a.courseSemester).put("courseSyncedAt", a.courseSyncedAt)
        }
    }
}

class KetangpaiDirectApi {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun post(path: String, token: String = "", body: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject(body.toString()).put("reqtimestamp", System.currentTimeMillis())
        val builder = Request.Builder().url(KTP_BASE + path).header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Origin", "https://w.ketangpai.com").header("Referer", "https://w.ketangpai.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Mobile Safari/537.36")
            .post(payload.toString().toRequestBody(json))
        if (token.isNotBlank()) builder.header("token", token)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw KtpError("课堂派网络请求失败（HTTP ${response.code}）", response.code)
            runCatching { JSONObject(text) }.getOrElse { throw KtpError("课堂派返回数据无法解析") }
        }
    }

    private fun data(response: JSONObject): Any? {
        if (response.optInt("status") != 1) throw KtpError(response.optString("message").ifBlank { "课堂派请求失败" }, response.optInt("code"))
        return response.opt("data")
    }

    suspend fun login(account: LocalAccount): LocalAccount {
        val response = post("/UserApi/login", body = JSONObject().put("email", account.email).put("password", account.password).put("remember", "1").put("source_type", 1))
        val token = (data(response) as? JSONObject)?.optString("token").orEmpty()
        if (token.isBlank()) throw KtpError("${account.email} 登录未返回 token")
        val name = runCatching { (data(post("/UserApi/getUserInfo", token, JSONObject().put("secondDomain", ""))) as? JSONObject)?.optString("username").orEmpty() }.getOrDefault("")
        return account.copy(token = token, displayName = name.ifBlank { account.displayName })
    }

    suspend fun courses(token: String): CourseSync {
        val metadata = data(post("/courseApi/semesterList", token, JSONObject().put("isstudy", "1").put("search", ""))) as? JSONObject
            ?: throw KtpError("课堂派学期列表格式异常")
        val semesters = metadata.optJSONArray("semester") ?: throw KtpError("课堂派未返回学期列表")
        val selected = selectCourseSemester((0 until semesters.length()).mapNotNull { i ->
            semesters.optJSONObject(i)?.let { item ->
                val year = item.optString("semester").takeUnless { it == "null" }.orEmpty()
                val term = item.optString("term").takeUnless { it == "null" }.orEmpty()
                if (year.isBlank() || term.isBlank()) null else CourseSemester(year, term, item.optString("termTxt").takeUnless { it == "null" }.orEmpty())
            }
        }) ?: return CourseSync(emptyList(), "暂无可用学期")
        val array = data(post("/CourseApi/semesterCourseList", token, JSONObject().put("isstudy", "1").put("search", "").put("semester", selected.year).put("term", selected.term))) as? JSONArray
            ?: throw KtpError("课堂派课程列表格式异常")
        val courses = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { c ->
            LocalCourse(c.optString("id"), c.optString("coursename"), selected.label)
        } }.filter { it.id.isNotBlank() }.distinctBy { it.id }
        return CourseSync(courses, selected.label)
    }

    suspend fun unfinished(token: String, courseId: String): List<JSONObject> {
        val obj = data(post("/AttenceApi/getNotFinishAttenceStudent", token, JSONObject().put("courseid", courseId))) as? JSONObject ?: return emptyList()
        val list = obj.optJSONArray("lists") ?: JSONArray(); return (0 until list.length()).mapNotNull(list::optJSONObject)
    }

    suspend fun digitCode(token: String, id: String): String {
        val inner = data(post("/AttenceApi/getDigitAttence", token, JSONObject().put("id", id))) as? JSONObject ?: return ""
        return inner.optJSONObject("data")?.optString("code").orEmpty()
    }

    suspend fun submit(token: String, type: String, body: JSONObject): DirectResult {
        val response = post(if (type == "3") "/AttenceApi/AttenceResult" else "/AttenceApi/checkin", token, body)
        val code = response.optInt("code"); val expected = if (type == "3") 8 else 1
        val success = code == 30324 || (response.optInt("status") == 1 && response.optJSONObject("data")?.optInt("state") == expected)
        return DirectResult("", success, if (code == 30324) "重复签到（已成功）" else if (success) "签到成功" else response.optString("message").ifBlank { "签到失败（code=$code）" })
    }
}

class DirectRepository(private val api: KetangpaiDirectApi = KetangpaiDirectApi()) {
    suspend fun addAccount(email: String, password: String): LocalAccount {
        val account = api.login(LocalAccount(email = email.trim(), password = password))
        val synced = api.courses(account.token)
        return account.copy(courses = synced.courses, courseSemester = synced.semesterLabel, courseSyncedAt = System.currentTimeMillis())
    }

    private suspend fun <T> withLogin(account: LocalAccount, action: suspend (String) -> T): Pair<LocalAccount, T> {
        var current = if (account.token.isBlank()) api.login(account) else account
        return try { current to action(current.token) } catch (e: KtpError) {
            if (e.apiCode !in setOf(20003, 401)) throw e
            current = api.login(current); current to action(current.token)
        }
    }

    suspend fun poll(source: List<LocalAccount>, syncCourses: Boolean = false): PollSnapshot = coroutineScope {
        val limiter = Semaphore(3)
        val results = source.map { original -> async(Dispatchers.IO) {
            if (!original.enabled) return@async Triple(original, emptyList<Attendance>(), emptyList<String>())
            limiter.withPermit {
                var account = original; val pending = mutableListOf<Attendance>(); val errors = mutableListOf<String>()
                try {
                    // 课程列表只在首次使用或超过同步周期后重新拉取。跨学期时会被这里的
                    // 时间判断自然带到新学期，不必靠每轮全量同步去撞。
                    val coursesStale = account.courseSyncedAt == 0L ||
                        System.currentTimeMillis() - account.courseSyncedAt > COURSE_SYNC_INTERVAL_MS
                    if (syncCourses || coursesStale) {
                        val renewed = withLogin(account) { api.courses(it) }; account = renewed.first
                        val enabled = account.courses.associate { it.id to it.enabled }
                        account = account.copy(
                            courses = renewed.second.courses.map { it.copy(enabled = enabled[it.id] ?: true) },
                            courseSemester = renewed.second.semesterLabel,
                            courseSyncedAt = System.currentTimeMillis(),
                        )
                    }
                    for (course in account.courses.filter { it.enabled }) {
                        try {
                            val result = withLogin(account) { api.unfinished(it, course.id) }; account = result.first
                            result.second.forEach { item ->
                                val type = item.optString("type"); val id = item.optString("id")
                                if (type in setOf("1", "2", "3") && id.isNotBlank() && item.optInt("checkinover") != 1) pending += Attendance(id, course.id, course.name, type, item.optString("title").ifBlank { "待签到考勤" }, item.optLong("createtime"), setOf(account.id))
                            }
                        } catch (e: Exception) { errors += "${account.email} · ${course.name}：${e.message ?: "查询失败"}" }
                    }
                } catch (e: Exception) { errors += "${account.email}：${e.message ?: "同步失败"}" }
                Triple(account, pending, errors)
            }
        } }.awaitAll()
        val merged = linkedMapOf<String, Attendance>(); results.forEach { (_, items, _) -> items.forEach { item ->
            val key = "${item.courseId}:${item.id}:${item.type}"; merged[key] = merged[key]?.let { old -> old.copy(accountIds = old.accountIds + item.accountIds) } ?: item
        } }
        PollSnapshot(results.map { it.first }, merged.values.sortedByDescending { it.createdAt }, results.flatMap { it.third },
            results.filter { it.third.isNotEmpty() }.map { it.first.id }.toSet())
    }

    suspend fun getDigitCode(account: LocalAccount, id: String): Pair<LocalAccount, String> = withLogin(account) { api.digitCode(it, id) }

    /**
     * 数字与 GPS 签到。参与账号由调用方指定，不再要求账号事先绑定这门课程：
     * 只要账号可用，就可以为任意课程的考勤提交。
     */
    suspend fun signAttendance(accounts: List<LocalAccount>, request: SignRequest): Pair<List<LocalAccount>, DirectHistory> {
        val targets = accounts.filter { it.id in request.targetIds }
        if (targets.isEmpty()) throw KtpError("请至少选择一个要签到的账号")
        val item = request.item
        val body = JSONObject()
            .put("id", item.id).put("code", request.code).put("unusual", "")
            .put("latitude", request.latitude).put("longitude", request.longitude)
            .put("accuracy", "").put("clienttype", 1)
        return submitTargets(accounts, targets, item.courseName, item.type, body)
    }

    /** 二维码签到：同样对指定的任意账号提交，课程名仅用于结果展示。 */
    suspend fun signQr(
        accounts: List<LocalAccount>,
        targetIds: Set<String>,
        courseId: String,
        ticket: String,
        expire: String,
        sign: String,
    ): Pair<List<LocalAccount>, DirectHistory> {
        val targets = accounts.filter { it.id in targetIds }
        if (targets.isEmpty()) throw KtpError("请至少选择一个要签到的账号")
        val title = accounts.firstNotNullOfOrNull { account ->
            account.courses.firstOrNull { it.id == courseId }?.name
        } ?: "二维码签到"
        return submitTargets(accounts, targets, title, "3", JSONObject().put("ticketid", ticket).put("expire", expire).put("sign", sign))
    }

    private suspend fun submitTargets(all: List<LocalAccount>, targets: List<LocalAccount>, title: String, type: String, body: JSONObject): Pair<List<LocalAccount>, DirectHistory> {
        val map = all.associateBy { it.id }.toMutableMap(); val results = mutableListOf<DirectResult>(); var ended = false
        for (target in targets) {
            if (ended) { results += DirectResult(target.email, false, "二维码或考勤已结束，已跳过"); continue }
            try {
                val renewed = withLogin(map[target.id] ?: target) { api.submit(it, type, body) }; map[target.id] = renewed.first
                val result = renewed.second.copy(account = target.email); results += result
                if (!result.success && (result.message.contains("过期") || result.message.contains("结束"))) ended = true
            } catch (e: Exception) { results += DirectResult(target.email, false, e.message ?: "请求失败") }
        }
        return all.map { map[it.id] ?: it } to DirectHistory(System.currentTimeMillis(), title, type, results)
    }
}
