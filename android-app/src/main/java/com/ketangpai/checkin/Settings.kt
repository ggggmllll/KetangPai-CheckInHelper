package com.ketangpai.checkin

import android.content.Context

/**
 * 应用级行为开关。和主题偏好分开存，
 * 这样切换外观或清空账号都不会把这些设置一并重置。
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("checkin_settings", Context.MODE_PRIVATE)

    /**
     * 扫到二维码后是否立刻用已启用账号提交签到。
     * 默认打开：扫码本来就是为了签到，少一次点击。
     */
    var autoSignOnScan: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SIGN_ON_SCAN, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_SIGN_ON_SCAN, value).apply()
        }

    private companion object {
        const val KEY_AUTO_SIGN_ON_SCAN = "auto_sign_on_scan"
    }
}
