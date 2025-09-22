/**
 * TypeScript interfaces for STM32L412 Locker Controller System
 * Compatible with Kotlin implementation using Winnsen Protocol
 *
 * For Android API Level 25 (Android 7.1.2)
 * Communication: RS485 via MAX485 IC at 9600 baud
 */

/**
 * Station and lock number mapping
 */
export interface StationLockPair {
  station: number;        // 0-3 (DIP switch settings: 00, 01, 10, 11)
  lockNumber: number;     // 1-16 (lock position on board)
}

/**
 * Locker system configuration
 */
export interface LockerConfiguration {
  maxStations: number;              // Maximum number of stations (default: 4)
  locksPerBoard: number;            // Locks per board (default: 16)
  baudRate: number;                 // Communication baud rate (default: 9600)
  communicationTimeoutMs: number;   // Timeout for RS485 communication (default: 800)
  maxRetries: number;               // Maximum retry attempts (default: 2)
  simulationMode: boolean;          // Enable simulation mode for testing (default: false)
  totalCapacity: number;            // Total system capacity (calculated)
  dipSwitchSettings: string[];      // DIP switch configurations ["00", "01", "10", "11"]
  customMapping?: { [lockerId: string]: StationLockPair }; // Optional custom ID mapping
}

/**
 * Unlock operation result
 */
export interface UnlockResult {
  station: number;
  lockNumber: number;
  success: boolean;
  isOpen: boolean;
}

/**
 * Status check result
 */
export interface StatusResult {
  station: number;
  lockNumber: number;
  isOpen: boolean;
}

/**
 * System status information
 */
export interface LockerSystemStatus {
  totalStations: number;
  onlineStations: number;
  stationStatus: { [station: number]: boolean };
  totalCapacity: number;
  configuration: LockerConfiguration;
  lastUpdated: number;
}

/**
 * Locker operation response
 */
export interface LockerOperationResponse {
  success: boolean;
  lockerId: string;
  station: number;
  lockNumber: number;
  message?: string;
  timestamp: number;
}

/**
 * Communication protocol constants
 */
export const WinnsenProtocol = {
  // Frame constants
  FRAME_HEADER: 0x90,
  FRAME_END: 0x03,
  CMD_LENGTH: 0x06,
  RESP_LENGTH: 0x07,

  // Function codes
  FUNC_UNLOCK: 0x05,
  FUNC_UNLOCK_RESP: 0x85,
  FUNC_STATUS: 0x12,
  FUNC_STATUS_RESP: 0x92,

  // Status codes
  STATUS_SUCCESS: 0x01,
  STATUS_FAILURE: 0x00,
  STATUS_OPEN: 0x01,
  STATUS_CLOSED: 0x00
} as const;

/**
 * Main Locker Controller Interface
 */
export interface ILockerController {
  /**
   * Open a specific locker
   * @param lockerId Locker identifier (e.g., "M12", "M25")
   * @param retries Number of retry attempts (optional)
   * @returns Promise<boolean> true if unlock successful
   */
  openLocker(lockerId: string, retries?: number): Promise<boolean>;

  /**
   * Check if locker door is closed
   * @param lockerId Locker identifier
   * @returns Promise<boolean> true if door is closed
   */
  isClosed(lockerId: string): Promise<boolean>;

  /**
   * Test communication with specific station
   * @param station Station number (0-3)
   * @returns Promise<boolean> true if station responds
   */
  testStation(station: number): Promise<boolean>;

  /**
   * Get system status for all stations
   * @returns Promise<LockerSystemStatus> Complete system status
   */
  getSystemStatus(): Promise<LockerSystemStatus>;

  /**
   * Get mapping information for a locker ID
   * @param lockerId Locker identifier
   * @returns string Human-readable mapping information
   */
  getLockerMapping(lockerId: string): string;

  /**
   * Close controller and cleanup resources
   */
  close(): Promise<void>;
}

/**
 * Factory functions for creating configurations
 */
export const LockerConfigurationFactory = {
  /**
   * Standard production configuration
   */
  standard(): LockerConfiguration {
    return {
      maxStations: 4,
      locksPerBoard: 16,
      baudRate: 9600,
      communicationTimeoutMs: 800,
      maxRetries: 2,
      simulationMode: false,
      totalCapacity: 64,
      dipSwitchSettings: ["00", "01", "10", "11"]
    };
  },

  /**
   * Testing/development configuration
   */
  testing(): LockerConfiguration {
    return {
      maxStations: 4,
      locksPerBoard: 16,
      baudRate: 9600,
      communicationTimeoutMs: 200,
      maxRetries: 1,
      simulationMode: true,
      totalCapacity: 64,
      dipSwitchSettings: ["00", "01", "10", "11"]
    };
  },

  /**
   * Single board configuration
   */
  singleBoard(): LockerConfiguration {
    return {
      maxStations: 1,
      locksPerBoard: 16,
      baudRate: 9600,
      communicationTimeoutMs: 800,
      maxRetries: 2,
      simulationMode: false,
      totalCapacity: 16,
      dipSwitchSettings: ["00"]
    };
  },

  /**
   * Custom configuration
   */
  custom(overrides: Partial<LockerConfiguration>): LockerConfiguration {
    const standard = LockerConfigurationFactory.standard();
    return { ...standard, ...overrides };
  }
};

/**
 * Default locker ID mapping functions
 */
export const LockerMapping = {
  /**
   * Map locker ID to station and lock number
   * Default mapping: M1-M16 -> Station 0, M17-M32 -> Station 1, etc.
   */
  getStationLock(lockerId: string, config: LockerConfiguration): StationLockPair {
    // Check custom mapping first
    if (config.customMapping && config.customMapping[lockerId]) {
      return config.customMapping[lockerId];
    }

    // Extract numeric part from locker ID
    const numericPart = parseInt(lockerId.replace(/\D/g, ''), 10);
    if (isNaN(numericPart) || numericPart < 1 || numericPart > config.totalCapacity) {
      throw new Error(`Invalid locker ID: ${lockerId}`);
    }

    // Calculate station and lock number
    const station = Math.floor((numericPart - 1) / config.locksPerBoard);
    const lockNumber = ((numericPart - 1) % config.locksPerBoard) + 1;

    return { station, lockNumber };
  },

  /**
   * Get DIP switch setting for station
   */
  getDipSetting(station: number): string {
    const settings = ["00", "01", "10", "11"];
    return settings[station] || "??";
  },

  /**
   * Validate locker ID format
   */
  isValidLockerId(lockerId: string): boolean {
    return /^[A-Z]\d+$/.test(lockerId);
  }
};

/**
 * Error types for locker operations
 */
export enum LockerError {
  INVALID_LOCKER_ID = "INVALID_LOCKER_ID",
  COMMUNICATION_TIMEOUT = "COMMUNICATION_TIMEOUT",
  STATION_OFFLINE = "STATION_OFFLINE",
  UNLOCK_FAILED = "UNLOCK_FAILED",
  STATUS_CHECK_FAILED = "STATUS_CHECK_FAILED",
  SERIAL_PORT_ERROR = "SERIAL_PORT_ERROR",
  CONFIGURATION_ERROR = "CONFIGURATION_ERROR"
}

/**
 * Locker operation exception
 */
export class LockerException extends Error {
  constructor(
    public readonly errorType: LockerError,
    message: string,
    public readonly lockerId?: string,
    public readonly station?: number
  ) {
    super(message);
    this.name = "LockerException";
  }
}