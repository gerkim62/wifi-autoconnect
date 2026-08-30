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
     * Directly opens the Application Details screen in Android Settings for this app.
     * This is where users on modern Android (12+) can manually toggle between
     * 'Unrestricted', 'Optimized', and 'Restricted' battery usage modes.
     */
    fun openAppDetailsSettings(context: Context): Boolean {
        val packageName = context.packageName
        return try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetailsIntent)
            AppLogger.i("OEM_BATTERY", "Launched ACTION_APPLICATION_DETAILS_SETTINGS for $packageName")
            true
        } catch (e: Exception) {
            AppLogger.e("OEM_BATTERY", "Failed to launch ACTION_APPLICATION_DETAILS_SETTINGS: ${e.localizedMessage}", e)
            false
        }
    }

    /**
     * Executes safe, prioritized navigation to configure background battery optimization:
     *
     * 1. If already exempt: Navigates to App Details Settings where the user can inspect or change restrictions.
     * 2. If not exempt:
     *    - Stage 1: Standard AOSP Direct Request dialog (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
     *    - Stage 2: Vendor-specific Autostart / Battery Management activities
     *    - Stage 3: Standard AOSP Battery Optimization List (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
     *    - Stage 4: App Details Settings fallback
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val packageName = context.packageName
        val isAlreadyExempt = isIgnoringBatteryOptimizations(context)

        // Case 1: App is ALREADY exempt.
        // Android's ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is a no-op when already whitelisted.
        // Direct the user to App Details Settings where battery usage profiles can be adjusted.
        if (isAlreadyExempt) {
            AppLogger.i("OEM_BATTERY", "App is already exempt from battery optimization. Directing to App Details Settings to allow adjustments.")
            return openAppDetailsSettings(context)
        }

        AppLogger.i("OEM_BATTERY", "App is not exempt. Initiating battery optimization exemption flow for package: $packageName (Manufacturer: $manufacturer)")

        // Stage 1: Standard AOSP Direct Request Dialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val directRequestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(directRequestIntent)
                AppLogger.i("OEM_BATTERY", "Successfully launched direct ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS for $packageName")
                return true
            } catch (e: Exception) {
                AppLogger.w("OEM_BATTERY", "Direct ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed: ${e.localizedMessage}", e)
            }
        }

        // Stage 2: OEM-Specific Autostart / Battery Management Activities
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
            } catch (e: Exception) {
                AppLogger.d("OEM_BATTERY", "OEM intent candidate not available ($intent): ${e.localizedMessage}")
            }
        }

        // Stage 3: General AOSP Battery Optimization Settings List Fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val fallbackList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackList)
                AppLogger.i("OEM_BATTERY", "Launched ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS fallback list")
                return true
            } catch (e: Exception) {
                AppLogger.w("OEM_BATTERY", "ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS fallback failed: ${e.localizedMessage}", e)
            }
        }

        // Stage 4: App Details Settings Fallback
        return openAppDetailsSettings(context)
    }
}
