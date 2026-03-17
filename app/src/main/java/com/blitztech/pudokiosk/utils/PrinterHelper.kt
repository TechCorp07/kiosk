package com.blitztech.pudokiosk.utils

import com.blitztech.pudokiosk.data.api.dto.order.Currency
import com.blitztech.pudokiosk.data.api.dto.order.PackageSize
import com.blitztech.pudokiosk.data.api.dto.order.PaymentMethod
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class for printer formatting and receipt generation
 */
object PrinterHelper {

    private const val RECEIPT_WIDTH = 32 // Characters per line for thermal printer
    private const val SEPARATOR = "================================"

    /**
     * Generate customer receipt text
     */
    fun generateCustomerReceipt(
        orderId: String,
        transactionId: String,
        senderName: String,
        senderMobile: String,
        recipientName: String,
        recipientSurname: String,
        recipientMobile: String,
        houseNumber: String,
        street: String,
        suburb: String,
        city: String,
        packageSize: PackageSize,
        length: Double,
        width: Double,
        height: Double,
        contents: String,
        paymentMethod: PaymentMethod,
        amount: Double,
        currency: Currency,
        distance: String,
        lockerNumber: Int
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        return buildString {
            // Header
            appendLine(centerText("ZIMPUDO KIOSK"))
            appendLine(centerText("SEND PACKAGE RECEIPT"))
            appendLine(SEPARATOR)
            appendLine()

            // Transaction details
            appendLine("Date: $currentDate")
            appendLine("Order ID: $orderId")
            appendLine("Transaction: $transactionId")
            appendLine()

            // Sender information
            appendLine("SENDER INFORMATION")
            appendLine("Name: $senderName")
            appendLine("Mobile: $senderMobile")
            appendLine()

            // Recipient information
            appendLine("RECIPIENT INFORMATION")
            appendLine("Name: $recipientName $recipientSurname")
            appendLine("Mobile: $recipientMobile")
            appendLine("Address:")
            appendLine("  $houseNumber $street")
            appendLine("  $suburb")
            appendLine("  $city")
            appendLine()

            // Package information
            appendLine("PACKAGE INFORMATION")
            appendLine("Size: ${packageSize.displayName}")
            appendLine("Dimensions:")
            appendLine("  ${formatDimension(length)}m x ${formatDimension(width)}m x ${formatDimension(height)}m")
            appendLine("Contents: $contents")
            appendLine()

            // Payment information
            appendLine("PAYMENT INFORMATION")
            appendLine("Method: ${paymentMethod.displayName}")
            appendLine("Amount: ${currency.symbol}${formatAmount(amount)} ${currency.code}")
            appendLine("Distance: $distance")
            appendLine()

            // Locker information
            appendLine("LOCKER INFORMATION")
            appendLine("Locker Number: $lockerNumber")
            appendLine()

            // Instructions
            appendLine("Please place your package in")
            appendLine("Locker $lockerNumber and close the door.")
            appendLine()

            // Footer
            appendLine(SEPARATOR)
            appendLine(centerText("Thank you for using ZIMPUDO!"))
            appendLine(centerText("www.zimpudo.com"))
            appendLine()
        }
    }

    /**
     * Generate barcode label text
     */
    fun generateBarcodeLabel(
        orderId: String,
        lockerNumber: Int
    ): String {
        return buildString {
            appendLine(centerText("ZIMPUDO PACKAGE"))
            appendLine(centerText("Order ID:"))
        }
    }

    /**
     * Generate test receipt
     */
    fun generateTestReceipt(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        return buildString {
            appendLine(centerText("ZIMPUDO KIOSK"))
            appendLine(centerText("TEST RECEIPT"))
            appendLine(SEPARATOR)
            appendLine()
            appendLine("Date: $currentDate")
            appendLine("Test ID: TEST-${System.currentTimeMillis()}")
            appendLine()
            appendLine("This is a test receipt to verify")
            appendLine("printer functionality.")
            appendLine()
            appendLine("Hardware Status:")
            appendLine("  Printer: OK")
            appendLine("  Scanner: OK")
            appendLine("  Locker: OK")
            appendLine()
            appendLine(SEPARATOR)
            appendLine(centerText("Test Complete"))
            appendLine()
        }
    }

    /**
     * Center text for thermal printer
     */
    private fun centerText(text: String): String {
        if (text.length >= RECEIPT_WIDTH) return text
        val padding = (RECEIPT_WIDTH - text.length) / 2
        return " ".repeat(padding) + text
    }

    /**
     * Right-align text
     */
    private fun rightAlignText(text: String): String {
        if (text.length >= RECEIPT_WIDTH) return text
        val padding = RECEIPT_WIDTH - text.length
        return " ".repeat(padding) + text
    }

    /**
     * Create two-column layout
     */
    private fun twoColumnText(left: String, right: String): String {
        val availableSpace = RECEIPT_WIDTH - left.length
        return left + " ".repeat(maxOf(1, availableSpace - right.length)) + right
    }

    /**
     * Format currency amount
     */
    private fun formatAmount(amount: Double): String {
        return String.format("%.2f", amount)
    }

    /**
     * Format dimension
     */
    private fun formatDimension(dimension: Double): String {
        return String.format("%.2f", dimension)
    }

    /**
     * Create separator line
     */
    fun createSeparator(char: Char = '='): String {
        return char.toString().repeat(RECEIPT_WIDTH)
    }

    /**
     * Create dashed line
     */
    fun createDashedLine(): String {
        return "-".repeat(RECEIPT_WIDTH)
    }

    /**
     * Wrap long text to fit printer width
     */
    fun wrapText(text: String, width: Int = RECEIPT_WIDTH): List<String> {
        if (text.length <= width) return listOf(text)

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine = word
            } else if (currentLine.length + word.length + 1 <= width) {
                currentLine += " $word"
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }

    /**
     * Format barcode data for printing
     */
    fun formatBarcodeData(data: String): String {
        // Remove spaces and special characters that might cause issues
        return data.replace(Regex("[^A-Za-z0-9-]"), "")
    }

    /**
     * Validate barcode data
     */
    fun isValidBarcodeData(data: String, minLength: Int = 3, maxLength: Int = 50): Boolean {
        return data.length in minLength..maxLength &&
                data.matches(Regex("[A-Za-z0-9-]+"))
    }
}