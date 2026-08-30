package com.mgeni.autologin.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Robust, zero-dependency helper to manage Android OEM-specific battery optimization,
 * autostart permissions, and background power management settings across fragmented vendors
 * (Samsung One UI, Xiaomi HyperOS/MIUI, Huawei EMUI, OnePlus/Oppo/Realme, Vivo/iQOO, Asus).
 */
object OemBatteryHelper {

    enum class OemVendor(val displayName: String, val dkmSlug: String) {
        XIAOMI("Xiaomi / HyperOS", "xiaomi"),
        SAMSUNG("Samsung One UI", "samsung"),
        HUAWEI("Huawei / Honor", "huawei"),
        OPPO_ONEPLUS("OnePlus / Oppo / Realme", "oneplus"),
        VIVO("Vivo / iQOO", "vivo"),
        ASUS("Asus", "asus"),
        GENERIC("Standard Android", "")
    }

    /**
     * Identifies the current device's OEM vendor category.
     */
    fun getOemVendor(manufacturer: String = Build.MANUFACTURER): OemVendor {
        val lower = manufacturer.lowercase()
        return when {
            lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco") -> OemVendor.XIAOMI
            lower.contains("samsung") -> OemVendor.SAMSUNG
            lower.contains("huawei") || lower.contains("honor") -> OemVendor.HUAWEI
            lower.contains("oneplus") || lower.contains("oppo") || lower.contains("realme") -> OemVendor.OPPO_ONEPLUS
            lower.contains("vivo") || lower.contains("iqoo") -> OemVendor.VIVO
            lower.contains("asus") -> OemVendor.ASUS
            else -> OemVendor.GENERIC
        }
    }

    /**
     * Checks if this app is currently exempted from Android's battery optimizations (Doze mode allowlist).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * Returns a tailored, actionable tip for the user's specific OEM to prevent
     * background network callbacks and workers from being killed.
     */
    fun getOemSpecificTip(manufacturer: String = Build.MANUFACTURER): String? {
        return when (getOemVendor(manufacturer)) {
            OemVendor.XIAOMI ->
                "Xiaomi/HyperOS: Enable 'Autostart' in Security settings and set Battery Saver to 'No restrictions'."
            OemVendor.SAMSUNG ->
                "Samsung: Add Mgeni to 'Never sleeping apps' in Device Care > Battery > Background usage limits."
            OemVendor.HUAWEI ->
                "Huawei: In Battery > App Launch, set Mgeni to 'Manage manually' with Auto-launch and Run in background enabled."
            OemVendor.OPPO_ONEPLUS ->
                "OnePlus/Oppo: Enable 'Allow background activity' and 'Allow auto-launch' in App Battery Usage."
            OemVendor.VIVO ->
                "Vivo/iQOO: Enable 'High background power consumption' and allow Autostart in Permission Management."
            OemVendor.ASUS ->
                "Asus: Allow Autostart in Mobile Manager to ensure background network callbacks stay active."
            OemVendor.GENERIC -> null
        }
    }

    /**
     * Returns the DontKillMyApp guide URL for this device's manufacturer.
     */
    fun getDontKillMyAppUrl(manufacturer: String = Build.MANUFACTURER): String {
        val vendor = getOemVendor(manufacturer)
        return if (vendor.dkmSlug.isNotBlank()) {
            "https://dontkillmyapp.com/${vendor.dkmSlug}?app=WifiAuto"
        } else {
            "https://dontkillmyapp.com?app=WifiAuto"
        }
    }

    /**
     * Launches a browser / custom tab to the DontKillMyApp troubleshooting guide.
     */
    fun openDontKillMyAppGuide(context: Context) {
        try {
            val url = getDontKillMyAppUrl()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.w("OEM_BATTERY", "Failed to open DontKillMyApp guide: ${e.localizedMessage}")
        }
    }

    /**
     * Executes a safe, 3-stage fallback navigation to open the device's battery optimization
     * or autostart management screen:
     *
     * 1. Stage 1: Vendor-specific Autostart / Battery Management activities
     * 2. Stage 2: Standard AOSP Battery Optimization Request (Direct dialog or general list)
     * 3. Stage 3: App Details Settings fallback
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val packageName = context.packageName

        // Stage 1: OEM-Specific Autostart / Battery Management Activities
        val oemIntents = when {
            // Xiaomi / Redmi / POCO
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
            )

            // Samsung One UI
            manufacturer.contains("samsung") -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.AppSleepListActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            )

            // Huawei / Honor
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            )

            // OnePlus / Oppo / Realme (ColorOS / OxygenOS)
            manufacturer.contains("oneplus") || manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.BatteryActivity")),
                Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
            )

            // Vivo / iQOO
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
            )

            // Asus
            manufacturer.contains("asus") -> listOf(
                Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
                Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")).setData(Uri.parse("mobilemanager://function/autostart"))
            )

            else -> emptyList()
        }

        for (intent in oemIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                AppLogger.i("OEM_BATTERY", "Launched OEM-specific battery/autostart settings: $intent")
                return true
            } catch (_: Exception) {
                // Try next OEM intent candidate
            }
        }

        // Stage 2: Standard AOSP Direct Request or Settings List
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val directRequestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(directRequestIntent)
                AppLogger.i("OEM_BATTERY", "Launched ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS for $packageName")
                return true
            } catch (_: Exception) {
                try {
                    val fallbackList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackList)
                    AppLogger.i("OEM_BATTERY", "Launched ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS fallback")
                    return true
                } catch (_: Exception) {}
            }
        }

        // Stage 3: App Details Settings Fallback
        return try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetailsIntent)
            AppLogger.i("OEM_BATTERY", "Launched ACTION_APPLICATION_DETAILS_SETTINGS fallback")
            true
        } catch (e: Exception) {
            AppLogger.e("OEM_BATTERY", "Failed all battery settings launch stages: ${e.localizedMessage}", e)
            false
        }
    }
}
