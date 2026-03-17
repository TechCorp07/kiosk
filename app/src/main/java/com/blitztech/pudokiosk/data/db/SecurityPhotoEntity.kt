package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing metadata for security photos captured at the kiosk.
 *
 * The actual JPEG file is stored on disk at [filePath]; this entity tracks
 * the metadata, upload status, and links the photo to a specific transaction.
 */
@Entity(tableName = "security_photos")
data class SecurityPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Absolute path to the JPEG on internal storage. */
    val filePath: String,

    /** Why this photo was captured — maps to [PhotoReason] enum. */
    val reason: String,

    /** Transaction reference: order ID, parcel ID, collection ID, etc. */
    val referenceId: String,

    /** Who was photographed: customer mobile number or courier ID. */
    val userId: String,

    /** Which kiosk captured the photo. */
    val kioskId: String,

    /** Epoch millis when the photo was taken. */
    val capturedAt: Long,

    /** True once the photo has been successfully uploaded to the backend. */
    val uploaded: Boolean = false
)
