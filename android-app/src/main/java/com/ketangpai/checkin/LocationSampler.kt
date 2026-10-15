package com.ketangpai.checkin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class LocationSampler(private val context: Context, private val scope: CoroutineScope, private val onFix: (LocationFix) -> Unit) {
    var locating by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
    var info by mutableStateOf("")
        private set
    private var job: Job? = null
    private var generation = 0

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        locating = false
        error = ""
        info = ""
    }

    fun sample() {
        if (locating) return
        val request = ++generation
        error = ""
        info = ""
        locating = true
        job = scope.launch {
            try {
                val result = currentDeviceLocation(context)
                if (generation != request) return@launch
                if (result.fix == null) error = result.error
                else {
                    onFix(result.fix)
                    info = result.fix.description(result.cached)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == request) error = "定位失败，请检查系统定位设置后重试"
            } finally {
                if (generation == request) locating = false
            }
        }
    }
}

@Composable
internal fun rememberLocationSampler(onFix: (LocationFix) -> Unit): LocationSampler {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestCallback = rememberUpdatedState(onFix)
    val sampler = remember(context, scope) { LocationSampler(context, scope) { latestCallback.value(it) } }
    DisposableEffect(sampler) { onDispose { sampler.cancel() } }
    return sampler
}
