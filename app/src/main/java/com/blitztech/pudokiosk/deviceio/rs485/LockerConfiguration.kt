package com.blitztech.pudokiosk.deviceio.rs485

/**
 * Configuration class for Locker Controller System
 * Provides centralized configuration management and TypeScript interface compatibility
 */
data class LockerConfiguration(
    val maxStations: Int = 4,
    val locksPerBoard: Int = 16,
    val baudRate: Int = 9600,
    val communicationTimeoutMs: Int = 800,
    val maxRetries: Int = 2,
    val simulationMode: Boolean = false,
    val customMapping: Map<String, StationLockPair>? = null,
    val dipSwitchSettings: List<String> = listOf("00", "01", "10", "11")
) {

    /**
     * Validate configuration parameters
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (maxStations !in 1..4) {
            errors.add("maxStations must be 1-4, got $maxStations")
        }

        if (locksPerBoard !in 1..24) {
            errors.add("locksPerBoard must be 1-24, got $locksPerBoard")
        }

        if (baudRate !in listOf(9600, 19200, 38400, 57600, 115200)) {
            errors.add("Unsupported baud rate: $baudRate")
        }

        if (communicationTimeoutMs < 100) {
            errors.add("communicationTimeoutMs must be at least 100ms, got $communicationTimeoutMs")
        }

        if (maxRetries < 0) {
            errors.add("maxRetries must be non-negative, got $maxRetries")
        }

        if (dipSwitchSettings.size != maxStations) {
            errors.add("dipSwitchSettings size (${dipSwitchSettings.size}) must match maxStations ($maxStations)")
        }

        // Validate custom mapping if provided
        customMapping?.forEach { (lockerId, mapping) ->
            if (mapping.station !in 0 until maxStations) {
                errors.add("Custom mapping for $lockerId: station ${mapping.station} out of range (0-${maxStations-1})")
            }
            if (mapping.lockNumber !in 1..locksPerBoard) {
                errors.add("Custom mapping for $lockerId: lock ${mapping.lockNumber} out of range (1-$locksPerBoard)")
            }
        }

        return errors
    }

    /**
     * Get total system capacity
     */
    fun getTotalCapacity(): Int = maxStations * locksPerBoard

    /**
     * Get station for a given lock number (using default mapping)
     */
    fun getStationForLock(lockNumber: Int): Int {
        require(lockNumber in 1..getTotalCapacity()) {
            "Lock number must be 1-${getTotalCapacity()}, got $lockNumber"
        }
        return (lockNumber - 1) / locksPerBoard
    }

    /**
     * Get lock number within station for a given global lock number
     */
    fun getLocalLockNumber(lockNumber: Int): Int {
        require(lockNumber in 1..getTotalCapacity()) {
            "Lock number must be 1-${getTotalCapacity()}, got $lockNumber"
        }
        return ((lockNumber - 1) % locksPerBoard) + 1
    }

    /**
     * Convert to map for TypeScript/JSON serialization
     */
    fun toMap(): Map<String, Any> = mapOf(
        "maxStations" to maxStations,
        "locksPerBoard" to locksPerBoard,
        "baudRate" to baudRate,
        "communicationTimeoutMs" to communicationTimeoutMs,
        "maxRetries" to maxRetries,
        "simulationMode" to simulationMode,
        "totalCapacity" to getTotalCapacity(),
        "dipSwitchSettings" to dipSwitchSettings,
        "customMapping" to (customMapping?.mapValues {
            mapOf("station" to it.value.station, "lockNumber" to it.value.lockNumber)
        } ?: emptyMap<String, Any>())
    )

    companion object {
        /**
         * Create configuration from map (for TypeScript integration)
         */
        fun fromMap(map: Map<String, Any>): LockerConfiguration {
            val customMappingMap = map["customMapping"] as? Map<String, Map<String, Any>>
            val customMapping = customMappingMap?.mapValues { entry ->
                val stationLockMap = entry.value
                StationLockPair(
                    station = (stationLockMap["station"] as Number).toInt(),
                    lockNumber = (stationLockMap["lockNumber"] as Number).toInt()
                )
            }

            return LockerConfiguration(
                maxStations = (map["maxStations"] as? Number)?.toInt() ?: 4,
                locksPerBoard = (map["locksPerBoard"] as? Number)?.toInt() ?: 16,
                baudRate = (map["baudRate"] as? Number)?.toInt() ?: 9600,
                communicationTimeoutMs = (map["communicationTimeoutMs"] as? Number)?.toInt() ?: 800,
                maxRetries = (map["maxRetries"] as? Number)?.toInt() ?: 2,
                simulationMode = map["simulationMode"] as? Boolean ?: false,
                customMapping = customMapping,
                dipSwitchSettings = (map["dipSwitchSettings"] as? List<String>) ?: listOf("00", "01", "10", "11")
            )
        }

        /**
         * Default configuration for standard setup
         */
        fun standard(): LockerConfiguration = LockerConfiguration()

        /**
         * Configuration for testing/development
         */
        fun testing(): LockerConfiguration = LockerConfiguration(
            simulationMode = true,
            maxRetries = 1,
            communicationTimeoutMs = 200
        )

        /**
         * Configuration for single board setup
         */
        fun singleBoard(): LockerConfiguration = LockerConfiguration(
            maxStations = 1,
            dipSwitchSettings = listOf("00")
        )
    }
}

/**
 * Station and lock number pair for mapping
 */
data class StationLockPair(
    val station: Int,
    val lockNumber: Int
)

/**
 * System status information
 */
data class LockerSystemStatus(
    val totalStations: Int,
    val onlineStations: Int,
    val stationStatus: Map<Int, Boolean>,
    val totalCapacity: Int,
    val configuration: LockerConfiguration,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun isFullyOperational(): Boolean = onlineStations == totalStations
    fun getOfflineStations(): List<Int> = stationStatus.filter { !it.value }.keys.toList()
    fun getOnlineStations(): List<Int> = stationStatus.filter { it.value }.keys.toList()
}