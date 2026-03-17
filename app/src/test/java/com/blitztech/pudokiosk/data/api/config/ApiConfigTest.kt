package com.blitztech.pudokiosk.data.api.config

import org.junit.Assert.*
import org.junit.Test

class ApiConfigTest {

    @Test
    fun baseUrl_pointsToGateway() {
        assertEquals("https://api.zimpudo.com/", ApiConfig.BASE_URL)
    }

    @Test
    fun baseUrl_endsWithSlash() {
        assertTrue("BASE_URL must end with / for Retrofit", ApiConfig.BASE_URL.endsWith("/"))
    }

    @Test
    fun baseUrl_startsWithHttps() {
        assertTrue(ApiConfig.BASE_URL.startsWith("https://"))
    }

    @Test
    fun baseUrl_doesNotContainApiV1() {
        // api/v1/ is in individual endpoint paths, not in base URL
        assertFalse(ApiConfig.BASE_URL.contains("api/v1"))
    }

    @Test
    fun timeouts_arePositive() {
        assertTrue(ApiConfig.CONNECT_TIMEOUT > 0)
        assertTrue(ApiConfig.READ_TIMEOUT > 0)
        assertTrue(ApiConfig.WRITE_TIMEOUT > 0)
    }

    @Test
    fun otpMethod_isValidEnum() {
        // Backend accepts TOTP or SMS_EMAIL
        assertTrue(
            ApiConfig.OTP_METHOD == "SMS_EMAIL" || ApiConfig.OTP_METHOD == "TOTP"
        )
    }

    @Test
    fun userRole_isValid() {
        assertEquals("USER", ApiConfig.USER_ROLE)
    }

    @Test
    fun kycType_isValid() {
        // Backend accepts: NATIONAL_ID, PASSPORT, DRIVERS_LICENSE
        assertEquals("NATIONAL_ID", ApiConfig.KYC_TYPE)
    }

    @Test
    fun phoneCountryCode_isZimbabwe() {
        assertEquals("+263", ApiConfig.PHONE_COUNTRY_CODE)
    }

    @Test
    fun phonePlaceholder_startsWithCountryCode() {
        assertTrue(ApiConfig.PHONE_PLACEHOLDER.startsWith(ApiConfig.PHONE_COUNTRY_CODE))
    }

    @Test
    fun contentTypes_areValid() {
        assertEquals("application/json", ApiConfig.CONTENT_TYPE_JSON)
        assertEquals("multipart/form-data", ApiConfig.CONTENT_TYPE_MULTIPART)
    }
}
