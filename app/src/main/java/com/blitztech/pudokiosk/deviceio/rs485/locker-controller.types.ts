/**
 * TypeScript interfaces for STM32L412 Locker Controller System - Production Version
 * Single Board Configuration (Station 0, Locks 1-16)
 *
 * For Android API Level 25 (Android 7.1.2)
 * Communication: RS485 via MAX485 IC at 9600 baud
 * Hardware: VID:04E2 PID:1414 CDC device, Port 2
 */

/**
 * Fixed hardware configuration constants
 */
export const HardwareConfig = {
  // USB Device identifiers (fixed)
  TARGET_VID: 0x04E2,
  TARGET_PID: 0x1414,
  TARGET_PORT_INDEX: 2,

  // Communication settings (fixed)
  BAUD_RATE: 9600,
  DATA_BITS: 8,
  STOP_BITS: 1,
  PARITY: 'none' as const,

  // System configuration (single board)
  STATION_ADDRESS: 0,
  MIN_LOCK: 1,
  MAX_LOCK: 16,
  TOTAL_LOCKS: 16,

  // Timing settings
  COMMUNICATION_TIMEOUT_MS: 800,
  READ_TIMEOUT_MS: 500,
  WRITE_TIMEOUT_MS: 500,
  MAX_RETRIES: 3
} as const;

/**
 * Locker system configuration for single board
 */
export interface LockerConfiguration {
  stationAddress: number;           // Always 0 for single board
  totalLocks: number;               // Always 16 for single board
  baudRate: number;                 // Always 9600
  communicationTimeoutMs: number;   // Default: 800
  maxRetries: number;               // Default: 3
  hardwareConnected: boolean;       // Connection status
}

/**
 * Unlock operation result
 */
export interface UnlockResult {
  station: number;        // Always 0 for single board
  lockNumber: number;     // 1-16
  success: boolean;       // Operation success
  isOpen: boolean;        // Final lock state
}

/**
 * Status check result
 */
export interface StatusResult {
  station: number;        // Always 0 for single board
  lockNumber: number;     // 1-16
  isOpen: boolean;        // true = open, false = closed/locked
}

/**
 * System status information
 */
export interface SystemStatus {
  boardOnline: boolean;               // STM32L412 board connectivity
  stationAddress: number;             // Always 0
  totalLocks: number;                 // Always 16
  communicationOk: boolean;           // RS485 communication status
  serialConnected: boolean;           // USB serial connection status
  lastUpdated: number;                // Timestamp of last update
  error?: string;                     // Error message if any
}

/**
 * Locker operation response
 */
export interface LockerOperationResponse {
  success: boolean;       // Operation success
  lockNumber: number;     // Lock that was operated on (1-16)
  station: number;        // Always 0 for single board
  message?: string;       // Optional status message
  timestamp: number;      // Operation timestamp
}

/**
 * Communication protocol constants (Winnsen Protocol)
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
 * Main Locker Controller Interface for Single Board
 */
export interface ILockerController {
  /**
   * Open a specific locker
   * @param lockNumber Lock number (1-16)
   * @param retries Number of retry attempts (optional, default: 3)
   * @returns Promise<boolean> true if unlock successful
   */
  openLocker(lockNumber: number, retries?: number): Promise<boolean>;

  /**
   * Check the status of a specific locker
   * @param lockNumber Lock number (1-16)
   * @param retries Number of retry attempts (optional, default: 3)
   * @returns Promise<boolean> true if locker is closed/locked
   */
  checkLockerStatus(lockNumber: number, retries?: number): Promise<boolean>;

  /**
   * Test communication with the locker board
   * @returns Promise<boolean> true if board responds correctly
   */
  testCommunication(): Promise<boolean>;

  /**
   * Get current system status
   * @returns Promise<SystemStatus> Current system information
   */
  getSystemStatus(): Promise<SystemStatus>;

  /**
   * Close the serial connection
   */
  close(): void;
}

/**
 * Hardware test result
 */
export interface HardwareTestResult {
  testName: string;           // Test identifier
  success: boolean;           // Test result
  duration: number;           // Test duration in ms
  message: string;            // Result message
  timestamp: number;          // Test timestamp
  details?: any;              // Additional test details
}

/**
 * System diagnostics result
 */
export interface SystemDiagnostics {
  lockerController: HardwareTestResult;     // Locker board test
  barcodeScanner: HardwareTestResult;       // Scanner test
  printer: HardwareTestResult;              // Printer test
  overallStatus: 'operational' | 'degraded' | 'offline';
  timestamp: number;                        // Diagnostics timestamp
  recommendations?: string[];               // Service recommendations
}

/**
 * Lock validation utilities
 */
export const LockValidation = {
  /**
   * Validate lock number is within valid range
   */
  isValidLockNumber: (lockNumber: number): boolean => {
    return Number.isInteger(lockNumber) &&
           lockNumber >= HardwareConfig.MIN_LOCK &&
           lockNumber <= HardwareConfig.MAX_LOCK;
  },

  /**
   * Get all valid lock numbers
   */
  getAllValidLockNumbers: (): number[] => {
    return Array.from(
      { length: HardwareConfig.MAX_LOCK },
      (_, i) => i + HardwareConfig.MIN_LOCK
    );
  },

  /**
   * Format lock number for display
   */
  formatLockNumber: (lockNumber: number): string => {
    return LockValidation.isValidLockNumber(lockNumber)
      ? `Lock ${lockNumber.toString().padStart(2, '0')}`
      : 'Invalid Lock';
  }
} as const;

/**
 * Protocol utilities
 */
export const ProtocolUtils = {
  /**
   * Convert byte array to hex string
   */
  toHexString: (bytes: number[]): string => {
    return bytes.map(b => b.toString(16).toUpperCase().padStart(2, '0')).join(' ');
  },

  /**
   * Create unlock command bytes
   */
  createUnlockCommand: (lockNumber: number): number[] => {
    if (!LockValidation.isValidLockNumber(lockNumber)) {
      throw new Error(`Invalid lock number: ${lockNumber}`);
    }

    return [
      WinnsenProtocol.FRAME_HEADER,
      WinnsenProtocol.CMD_LENGTH,
      WinnsenProtocol.FUNC_UNLOCK,
      HardwareConfig.STATION_ADDRESS,
      lockNumber,
      WinnsenProtocol.FRAME_END
    ];
  },

  /**
   * Create status command bytes
   */
  createStatusCommand: (lockNumber: number): number[] => {
    if (!LockValidation.isValidLockNumber(lockNumber)) {
      throw new Error(`Invalid lock number: ${lockNumber}`);
    }

    return [
      WinnsenProtocol.FRAME_HEADER,
      WinnsenProtocol.CMD_LENGTH,
      WinnsenProtocol.FUNC_STATUS,
      HardwareConfig.STATION_ADDRESS,
      lockNumber,
      WinnsenProtocol.FRAME_END
    ];
  }
} as const;

/**
 * Export all types and utilities
 */
export type {
  LockerConfiguration,
  UnlockResult,
  StatusResult,
  SystemStatus,
  LockerOperationResponse,
  ILockerController,
  HardwareTestResult,
  SystemDiagnostics
};

export {
  HardwareConfig,
  WinnsenProtocol,
  LockValidation,
  ProtocolUtils
};