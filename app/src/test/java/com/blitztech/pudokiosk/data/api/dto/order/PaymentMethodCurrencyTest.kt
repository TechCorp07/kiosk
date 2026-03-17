package com.blitztech.pudokiosk.data.api.dto.order

import org.junit.Assert.*
import org.junit.Test

class PaymentMethodCurrencyTest {

    // ─────────────────────────────────────────────────────────────
    //  PaymentMethod — Paynow backend integration
    // ─────────────────────────────────────────────────────────────
    @Test
    fun paymentMethod_singlePaynowEntry() {
        assertEquals(1, PaymentMethod.values().size)
    }

    @Test
    fun paymentMethod_fromApiValue_paynow() {
        assertEquals(PaymentMethod.PAYNOW, PaymentMethod.fromApiValue("PAYNOW"))
    }

    @Test
    fun paymentMethod_fromApiValue_unknown_returnsNull() {
        assertNull(PaymentMethod.fromApiValue("ECOCASH"))
        assertNull(PaymentMethod.fromApiValue("TELECASH"))
        assertNull(PaymentMethod.fromApiValue("VISA"))
        assertNull(PaymentMethod.fromApiValue(""))
    }

    @Test
    fun paymentMethod_displayName() {
        assertEquals("Paynow", PaymentMethod.PAYNOW.displayName)
    }

    @Test
    fun paymentMethod_apiValue() {
        assertEquals("PAYNOW", PaymentMethod.PAYNOW.apiValue)
    }

    // ─────────────────────────────────────────────────────────────
    //  Currency — USD and ZWG per backend BankingDetailsDto
    // ─────────────────────────────────────────────────────────────
    @Test
    fun currency_fromCode_usd() {
        assertEquals(Currency.USD, Currency.fromCode("USD"))
    }

    @Test
    fun currency_fromCode_zwg() {
        assertEquals(Currency.ZWG, Currency.fromCode("ZWG"))
    }

    @Test
    fun currency_fromCode_oldZwl_returnsNull() {
        // ZWL is deprecated, should no longer be valid
        assertNull(Currency.fromCode("ZWL"))
    }

    @Test
    fun currency_fromCode_unknown_returnsNull() {
        assertNull(Currency.fromCode("EUR"))
        assertNull(Currency.fromCode(""))
    }

    @Test
    fun currency_displayNames() {
        assertEquals("US Dollar", Currency.USD.displayName)
        assertEquals("Zimbabwe Gold", Currency.ZWG.displayName)
    }

    @Test
    fun currency_symbols() {
        assertEquals("$", Currency.USD.symbol)
        assertEquals("ZiG", Currency.ZWG.symbol)
    }

    @Test
    fun currency_count() {
        assertEquals(2, Currency.values().size)
    }
}
