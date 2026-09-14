package com.omniconvert.app.util

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "omni_convert_settings"
    private const val DYNAMIC_COLORS = "dynamic_colors"
    private const val HARDWARE_ACCELERATION = "hardware_acceleration"
    private const val STRIP_EXIF = "strip_exif"

    fun useDynamicColors(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(DYNAMIC_COLORS, true)

    fun setUseDynamicColors(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DYNAMIC_COLORS, enabled)
            .apply()
    }

    fun useHardwareAcceleration(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(HARDWARE_ACCELERATION, true)

    fun setUseHardwareAcceleration(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(HARDWARE_ACCELERATION, enabled)
            .apply()
    }

    fun stripExifByDefault(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(STRIP_EXIF, true)

    fun setStripExifByDefault(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(STRIP_EXIF, enabled)
            .apply()
    }
}
