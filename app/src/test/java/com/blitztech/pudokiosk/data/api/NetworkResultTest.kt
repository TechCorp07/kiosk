package com.blitztech.pudokiosk.data.api

import org.junit.Assert.*
import org.junit.Test

class NetworkResultTest {

    @Test
    fun success_holdsData() {
        val result: NetworkResult<String> = NetworkResult.Success("hello")
        assertTrue(result is NetworkResult.Success)
        assertEquals("hello", (result as NetworkResult.Success).data)
    }

    @Test
    fun error_holdsMessageAndCode() {
        val result: NetworkResult<String> = NetworkResult.Error("fail", 404)
        assertTrue(result is NetworkResult.Error)
        assertEquals("fail", (result as NetworkResult.Error).message)
        assertEquals(404, result.code)
    }

    @Test
    fun error_codeDefaultsToNull() {
        val result: NetworkResult<String> = NetworkResult.Error("fail")
        assertNull((result as NetworkResult.Error).code)
    }

    @Test
    fun loading_defaultIsTrue() {
        val result: NetworkResult<String> = NetworkResult.Loading()
        assertTrue((result as NetworkResult.Loading).isLoading)
    }

    @Test
    fun loading_canBeFalse() {
        val result: NetworkResult<String> = NetworkResult.Loading(false)
        assertFalse((result as NetworkResult.Loading).isLoading)
    }

    @Test
    fun success_typeSafety() {
        val result: NetworkResult<Int> = NetworkResult.Success(42)
        assertTrue(result is NetworkResult.Success)
        assertFalse(result is NetworkResult.Error)
        assertFalse(result is NetworkResult.Loading)
    }

    @Test
    fun error_typeSafety() {
        val result: NetworkResult<Int> = NetworkResult.Error("err")
        assertFalse(result is NetworkResult.Success)
        assertTrue(result is NetworkResult.Error)
        assertFalse(result is NetworkResult.Loading)
    }

    @Test
    fun success_withNullData() {
        val result: NetworkResult<String?> = NetworkResult.Success(null)
        assertTrue(result is NetworkResult.Success)
        assertNull((result as NetworkResult.Success).data)
    }

    @Test
    fun success_withComplexType() {
        val data = listOf("a", "b", "c")
        val result: NetworkResult<List<String>> = NetworkResult.Success(data)
        assertEquals(3, (result as NetworkResult.Success).data.size)
    }
}
