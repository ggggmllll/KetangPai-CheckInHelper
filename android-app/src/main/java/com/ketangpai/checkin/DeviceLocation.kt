package com.ketangpai.checkin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val provider: String,
    val elapsedNanos: Long,
) {
    fun ageMillis(nowNanos: Long): Long =
        if (elapsedNanos <= 0 || elapsedNanos > nowNanos) Long.MAX_VALUE
        else (nowNanos - elapsedNanos) / 1_000_000

    fun usable(nowNanos: Long): Boolean = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && ageMillis(nowNanos) <= 60_000

    fun precise(nowNanos: Long): Boolean = usable(nowNanos) && ageMillis(nowNanos) <= 30_000 &&
        accuracyMeters?.let { it.isFinite() && it > 0 && it <= 100 } == true

    val coordinates: String get() = "$latitude,$longitude"

    fun description(cached: Boolean): String {
        val source = when (provider) { "gps" -> "GPS"; "network" -> "网络"; "fused" -> "系统融合"; else -> "系统" }
        val accuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0 }
        val age = ageMillis(SystemClock.elapsedRealtimeNanos()) / 1000
        return buildString {
            append("${source}定位")
            append(if (accuracy != null) " · 精度约 ${accuracy.roundToInt()} 米" else " · 精度未知")
            if (cached) append(" · ${age} 秒前的位置")
            if (accuracy == null || accuracy > 100) append("，请核对位置，必要时到室外重新采样")
        }
    }
}

/** Prefer a recent accurate fix, without letting an old GPS fix block a fresh network fix. */
internal fun bestLocation(fixes: Collection<LocationFix>, nowNanos: Long): LocationFix? =
    fixes.filter { it.usable(nowNanos) }.minWithOrNull(
        compareBy<LocationFix> { if (it.precise(nowNanos)) 0 else 1 }
            .thenBy { it.accuracyMeters?.takeIf { value -> value.isFinite() && value > 0 } ?: Float.MAX_VALUE }
            .thenBy { it.ageMillis(nowNanos) },
    )

internal data class DeviceLocationResult(val fix: LocationFix? = null, val cached: Boolean = false, val error: String = "")

/** No Play Services required: domestic devices can race their system network, fused and GPS providers. */
@SuppressLint("MissingPermission")
internal suspend fun currentDeviceLocation(context: Context): DeviceLocationResult = withContext(Dispatchers.Main.immediate) {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return@withContext DeviceLocationResult(error = "需要定位权限才能采样当前位置")
    val manager = context.getSystemService(LocationManager::class.java)
        ?: return@withContext DeviceLocationResult(error = "此设备不支持定位")
    if (!androidx.core.location.LocationManagerCompat.isLocationEnabled(manager)) {
        return@withContext DeviceLocationResult(error = "系统定位已关闭，请先开启定位")
    }
    val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { (fine || it != LocationManager.GPS_PROVIDER) && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return@withContext DeviceLocationResult(error = "没有可用的定位来源，请检查系统定位设置")
    fun Location.toFix() = LocationFix(latitude, longitude, if (hasAccuracy()) accuracy else null, provider.orEmpty(), elapsedRealtimeNanos)
    val started = SystemClock.elapsedRealtime()
    val fixes = linkedMapOf<String, LocationFix>()
    providers.forEach { provider ->
        runCatching { manager.getLastKnownLocation(provider)?.toFix() }.getOrNull()?.let { fixes[provider] = it }
    }
    val cachedFixes = fixes.values.toSet()
    fun result(fix: LocationFix?): DeviceLocationResult {
        val cached = fix in cachedFixes
        // Deliberately omit coordinates from diagnostics.
        Log.i("CheckInLocation", "elapsed=${SystemClock.elapsedRealtime() - started}ms provider=${fix?.provider} accuracy=${fix?.accuracyMeters} cached=$cached")
        return DeviceLocationResult(fix, cached, if (fix == null) "暂时取不到近期位置，请检查网络，或到窗边、室外重试" else "")
    }
    bestLocation(fixes.values, SystemClock.elapsedRealtimeNanos())?.takeIf { it.precise(SystemClock.elapsedRealtimeNanos()) }?.let {
        return@withContext result(it)
    }
    suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val listeners = mutableMapOf<String, LocationListener>()
        val callbacks = mutableListOf<Runnable>()
        var finished = false
        var quickWindowOver = false
        fun cleanup() {
            callbacks.forEach(handler::removeCallbacks)
            listeners.values.forEach { runCatching { manager.removeUpdates(it) } }
            listeners.clear()
        }
        fun finish(fix: LocationFix?) {
            if (finished) return
            finished = true
            cleanup()
            if (continuation.isActive) continuation.resume(result(fix))
        }
        fun best() = bestLocation(fixes.values, SystemClock.elapsedRealtimeNanos())
        fun consider() {
            val candidate = best() ?: return
            if (candidate.precise(SystemClock.elapsedRealtimeNanos()) ||
                (quickWindowOver && candidate.accuracyMeters?.let { it > 0 && it <= 1000 } == true)) finish(candidate)
        }
        fun schedule(delay: Long, action: () -> Unit) {
            val callback = Runnable { if (!finished) action() }
            callbacks += callback
            handler.postDelayed(callback, delay)
        }
        continuation.invokeOnCancellation {
            // Location callbacks and removal are serialized on the main looper.
            handler.post { if (!finished) { finished = true; cleanup() } }
        }
        for (provider in providers) {
            if (finished || !continuation.isActive) break
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (finished || !continuation.isActive) return
                    val fix = location.toFix()
                    if (fix.usable(SystemClock.elapsedRealtimeNanos())) {
                        fixes[provider] = fix
                        consider()
                    }
                }
                override fun onProviderDisabled(provider: String) {
                    listeners.remove(provider)?.let { runCatching { manager.removeUpdates(it) } }
                    if (listeners.isEmpty()) finish(best())
                }
                override fun onProviderEnabled(provider: String) = Unit
                @Deprecated("Legacy Android callback")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            listeners[provider] = listener
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (_: Exception) {
                listeners.remove(provider)
                runCatching { manager.removeUpdates(listener) }
            }
        }
        if (listeners.isEmpty()) finish(best())
        if (!finished) {
            // Give GPS a short chance to refine coarse network/cached fixes, rather than always waiting 12 seconds.
            schedule(2500) { quickWindowOver = true; consider() }
            schedule(12_000) { finish(best()) }
        }
    }
}
