package com.blitztech.pudokiosk.data.api.dto.order

import org.junit.Assert.*
import org.junit.Test

class PackageSizeTest {

    // ─────────────────────────────────────────────────────────────
    //  PackageSize.fromDimensions() — exact fits
    // ─────────────────────────────────────────────────────────────
    @Test
    fun fromDimensions_xsExactFit() {
        assertEquals(PackageSize.XS, PackageSize.fromDimensions(0.15, 0.15, 0.10))
    }

    @Test
    fun fromDimensions_sExactFit() {
        assertEquals(PackageSize.S, PackageSize.fromDimensions(0.30, 0.25, 0.20))
    }

    @Test
    fun fromDimensions_mExactFit() {
        assertEquals(PackageSize.M, PackageSize.fromDimensions(0.50, 0.40, 0.30))
    }

    @Test
    fun fromDimensions_lExactFit() {
        assertEquals(PackageSize.L, PackageSize.fromDimensions(0.70, 0.60, 0.50))
    }

    @Test
    fun fromDimensions_xlExactFit() {
        assertEquals(PackageSize.XL, PackageSize.fromDimensions(1.00, 0.80, 0.70))
    }

    // ─────────────────────────────────────────────────────────────
    //  Tiny package → XS
    // ─────────────────────────────────────────────────────────────
    @Test
    fun fromDimensions_tiny_returnsXS() {
        assertEquals(PackageSize.XS, PackageSize.fromDimensions(0.05, 0.05, 0.05))
    }

    // ─────────────────────────────────────────────────────────────
    //  Boundary: just above XS → S
    // ─────────────────────────────────────────────────────────────
    @Test
    fun fromDimensions_slightlyAboveXS_returnsS() {
        assertEquals(PackageSize.S, PackageSize.fromDimensions(0.16, 0.15, 0.10))
    }

    // ─────────────────────────────────────────────────────────────
    //  Boundary: just above S → M
    // ─────────────────────────────────────────────────────────────
    @Test
    fun fromDimensions_slightlyAboveS_returnsM() {
        assertEquals(PackageSize.M, PackageSize.fromDimensions(0.31, 0.25, 0.20))
    }

    // ─────────────────────────────────────────────────────────────
    //  Oversized → XL
    // ─────────────────────────────────────────────────────────────
    @Test
    fun fromDimensions_oversized_returnsXL() {
        assertEquals(PackageSize.XL, PackageSize.fromDimensions(2.0, 2.0, 2.0))
    }

    // ─────────────────────────────────────────────────────────────
    //  PackageSize enum properties
    // ─────────────────────────────────────────────────────────────
    @Test
    fun packageSize_displayNames() {
        assertEquals("Extra Small", PackageSize.XS.displayName)
        assertEquals("Small", PackageSize.S.displayName)
        assertEquals("Medium", PackageSize.M.displayName)
        assertEquals("Large", PackageSize.L.displayName)
        assertEquals("Extra Large", PackageSize.XL.displayName)
    }

    @Test
    fun packageSize_count() {
        assertEquals(5, PackageSize.values().size)
    }

    @Test
    fun packageSize_maxDimensions_increase() {
        val sizes = PackageSize.values()
        for (i in 0 until sizes.size - 1) {
            assertTrue(sizes[i].maxLength <= sizes[i + 1].maxLength)
            assertTrue(sizes[i].maxWidth <= sizes[i + 1].maxWidth)
            assertTrue(sizes[i].maxHeight <= sizes[i + 1].maxHeight)
        }
    }
}
