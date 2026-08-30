package com.mgeni.autologin

import com.mgeni.autologin.data.OemBatteryHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemBatteryHelperTest {

    @Test
    fun `OemBatteryHelper accurately categorizes OEM manufacturers`() {
        assertEquals(OemBatteryHelper.OemVendor.XIAOMI, OemBatteryHelper.getOemVendor("Xiaomi"))
        assertEquals(OemBatteryHelper.OemVendor.XIAOMI, OemBatteryHelper.getOemVendor("Redmi"))
        assertEquals(OemBatteryHelper.OemVendor.XIAOMI, OemBatteryHelper.getOemVendor("POCO"))

        assertEquals(OemBatteryHelper.OemVendor.SAMSUNG, OemBatteryHelper.getOemVendor("samsung"))
        assertEquals(OemBatteryHelper.OemVendor.SAMSUNG, OemBatteryHelper.getOemVendor("SAMSUNG"))

        assertEquals(OemBatteryHelper.OemVendor.HUAWEI, OemBatteryHelper.getOemVendor("Huawei"))
        assertEquals(OemBatteryHelper.OemVendor.HUAWEI, OemBatteryHelper.getOemVendor("Honor"))

        assertEquals(OemBatteryHelper.OemVendor.OPPO_ONEPLUS, OemBatteryHelper.getOemVendor("OnePlus"))
        assertEquals(OemBatteryHelper.OemVendor.OPPO_ONEPLUS, OemBatteryHelper.getOemVendor("OPPO"))
        assertEquals(OemBatteryHelper.OemVendor.OPPO_ONEPLUS, OemBatteryHelper.getOemVendor("Realme"))

        assertEquals(OemBatteryHelper.OemVendor.VIVO, OemBatteryHelper.getOemVendor("vivo"))
        assertEquals(OemBatteryHelper.OemVendor.VIVO, OemBatteryHelper.getOemVendor("iQOO"))

        assertEquals(OemBatteryHelper.OemVendor.ASUS, OemBatteryHelper.getOemVendor("asus"))

        assertEquals(OemBatteryHelper.OemVendor.GENERIC, OemBatteryHelper.getOemVendor("Google"))
        assertEquals(OemBatteryHelper.OemVendor.GENERIC, OemBatteryHelper.getOemVendor("Motorola"))
    }

    @Test
    fun `OemBatteryHelper provides tailored tips for aggressive OEMs and null for generic`() {
        val xiaomiTip = OemBatteryHelper.getOemSpecificTip("Xiaomi")
        assertNotNull(xiaomiTip)
        assertTrue(xiaomiTip!!.contains("Autostart"))

        val samsungTip = OemBatteryHelper.getOemSpecificTip("Samsung")
        assertNotNull(samsungTip)
        assertTrue(samsungTip!!.contains("Never sleeping apps"))

        val huaweiTip = OemBatteryHelper.getOemSpecificTip("Huawei")
        assertNotNull(huaweiTip)
        assertTrue(huaweiTip!!.contains("Manage manually"))

        val pixelTip = OemBatteryHelper.getOemSpecificTip("Google")
        assertNull(pixelTip)
    }

    @Test
    fun `OemBatteryHelper generates correct DontKillMyApp URLs`() {
        assertEquals("https://dontkillmyapp.com/xiaomi?app=WifiAuto", OemBatteryHelper.getDontKillMyAppUrl("Xiaomi"))
        assertEquals("https://dontkillmyapp.com/samsung?app=WifiAuto", OemBatteryHelper.getDontKillMyAppUrl("samsung"))
        assertEquals("https://dontkillmyapp.com/huawei?app=WifiAuto", OemBatteryHelper.getDontKillMyAppUrl("honor"))
        assertEquals("https://dontkillmyapp.com/oneplus?app=WifiAuto", OemBatteryHelper.getDontKillMyAppUrl("OnePlus"))
        assertEquals("https://dontkillmyapp.com?app=WifiAuto", OemBatteryHelper.getDontKillMyAppUrl("Google"))
    }

    @Test
    fun `OemBatteryHelper vendor enums have valid display names and slugs`() {
        for (vendor in OemBatteryHelper.OemVendor.values()) {
            assertTrue(vendor.displayName.isNotBlank())
            if (vendor != OemBatteryHelper.OemVendor.GENERIC) {
                assertTrue(vendor.dkmSlug.isNotBlank())
            }
        }
    }
}
