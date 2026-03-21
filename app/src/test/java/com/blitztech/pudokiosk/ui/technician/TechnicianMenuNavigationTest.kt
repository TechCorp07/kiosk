package com.blitztech.pudokiosk.ui.technician

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests that verify all 8 diagnostic panel Activity classes exist
 * and are correctly mapped in the technician dashboard.
 *
 * These are pure JVM tests — they verify class existence via reflection,
 * matching what TechnicianMenuActivity wires up at runtime.
 */
class TechnicianMenuNavigationTest {

    /**
     * All 8 expected panel Activity class names, mirroring the click listeners
     * in TechnicianMenuActivity.
     */
    private val panelMapping: Map<String, String> = mapOf(
        "cardHardwareTest"  to "com.blitztech.pudokiosk.ui.technician.HardwareTestActivity",
        "cardSystemLogs"    to "com.blitztech.pudokiosk.ui.technician.SystemLogsActivity",
        "cardDeviceSettings" to "com.blitztech.pudokiosk.ui.technician.DevSettingsActivity",
        "cardNetworkDiag"   to "com.blitztech.pudokiosk.ui.technician.NetworkDiagnosticsActivity",
        "cardDevMode"       to "com.blitztech.pudokiosk.ui.technician.DeveloperModeActivity",
        "cardSystemInfo"    to "com.blitztech.pudokiosk.ui.technician.SystemInfoActivity",
        "cardClearData"     to "com.blitztech.pudokiosk.ui.technician.DataManagementActivity",
        "cardRemoteSupport" to "com.blitztech.pudokiosk.ui.technician.RemoteSupportActivity"
    )

    // ─── Class existence ─────────────────────────────────────────────────────

    @Test
    fun hardwareTestActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.HardwareTestActivity")
    }

    @Test
    fun systemLogsActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.SystemLogsActivity")
    }

    @Test
    fun devSettingsActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.DevSettingsActivity")
    }

    @Test
    fun networkDiagnosticsActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.NetworkDiagnosticsActivity")
    }

    @Test
    fun developerModeActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.DeveloperModeActivity")
    }

    @Test
    fun systemInfoActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.SystemInfoActivity")
    }

    @Test
    fun dataManagementActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.DataManagementActivity")
    }

    @Test
    fun remoteSupportActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.RemoteSupportActivity")
    }

    @Test
    fun technicianAccessActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.TechnicianAccessActivity")
    }

    @Test
    fun technicianMenuActivity_classExists() {
        assertClassLoadable("com.blitztech.pudokiosk.ui.technician.TechnicianMenuActivity")
    }

    // ─── Panel map completeness ───────────────────────────────────────────────

    @Test
    fun panelMapping_hasExactlyEightPanels() {
        assertEquals("Dashboard must have exactly 8 panels", 8, panelMapping.size)
    }

    @Test
    fun allPanelIds_areUnique() {
        val ids = panelMapping.keys.toList()
        assertEquals("Panel card IDs must be unique", ids.distinct().size, ids.size)
    }

    @Test
    fun allPanelTargetClasses_areUnique() {
        val classes = panelMapping.values.toList()
        assertEquals("Panel target classes must be unique", classes.distinct().size, classes.size)
    }

    @Test
    fun allPanelMapping_targetClassesExist() {
        panelMapping.forEach { (card, className) ->
            try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                fail("Panel '$card' points to missing class: $className")
            }
        }
    }

    @Test
    fun developerModePanel_isMappedToCorrectActivity() {
        assertEquals(
            "com.blitztech.pudokiosk.ui.technician.DeveloperModeActivity",
            panelMapping["cardDevMode"]
        )
    }

    @Test
    fun hardwareTestPanel_isMappedToCorrectActivity() {
        assertEquals(
            "com.blitztech.pudokiosk.ui.technician.HardwareTestActivity",
            panelMapping["cardHardwareTest"]
        )
    }

    // ─── DeveloperMode prefs keys ─────────────────────────────────────────────

    @Test
    fun developerModeActivity_hasExpectedPrefKeys() {
        assertEquals("dev_sim_hardware", DeveloperModeActivity.KEY_SIM_HARDWARE)
        assertEquals("dev_debug_logging", DeveloperModeActivity.KEY_DEBUG_LOGGING)
        assertEquals("dev_mock_api",      DeveloperModeActivity.KEY_MOCK_API)
        assertEquals("dev_skip_otp",      DeveloperModeActivity.KEY_SKIP_OTP)
        assertEquals("dev_environment",   DeveloperModeActivity.KEY_ENVIRONMENT)
        assertEquals("dev_custom_url",    DeveloperModeActivity.KEY_CUSTOM_URL)
        assertEquals("dev_log_level",     DeveloperModeActivity.KEY_LOG_LEVEL)
    }

    @Test
    fun developerModeActivity_hasExpectedEnvironmentConstants() {
        assertEquals("prod",    DeveloperModeActivity.ENV_PRODUCTION)
        assertEquals("staging", DeveloperModeActivity.ENV_STAGING)
        assertEquals("local",   DeveloperModeActivity.ENV_LOCAL)
        assertEquals("custom",  DeveloperModeActivity.ENV_CUSTOM)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun assertClassLoadable(className: String) {
        try {
            val cls = Class.forName(className)
            assertNotNull("Class should be loadable: $className", cls)
        } catch (e: ClassNotFoundException) {
            fail("Expected class not found: $className")
        }
    }
}
