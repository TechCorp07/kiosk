package com.blitztech.pudokiosk.deviceio.camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager as SystemCameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.graphics.ImageFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.blitztech.pudokiosk.data.db.SecurityPhotoEntity
import com.blitztech.pudokiosk.prefs.Prefs
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * SecurityCameraManager — singleton for capturing security photos via a USB webcam.
 *
 * Design principles:
 *  - **Non-blocking**: [captureSecurityPhoto] is a fire-and-forget coroutine.
 *    If the camera is absent, disabled, or fails, it logs and returns silently.
 *  - **No flow impact**: camera state never blocks any kiosk workflow.
 *  - **Camera2 API**: USB webcams are enumerated as EXTERNAL Camera2 devices.
 *
 * Usage:
 * ```kotlin
 * lifecycleScope.launch {
 *     SecurityCameraManager.getInstance(ctx).captureSecurityPhoto(
 *         reason = PhotoReason.CLIENT_DEPOSIT,
 *         referenceId = orderId,
 *         userId = mobile
 *     )
 * }
 * ```
 */
class SecurityCameraManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SecurityCamera"

        /** Target photo resolution width. USB webcams typically support 640×480 or 1280×720. */
        private const val TARGET_WIDTH = 1280
        private const val TARGET_HEIGHT = 720

        /** Maximum time (ms) we wait for the camera to open + capture before giving up. */
        private const val CAPTURE_TIMEOUT_MS = 5_000L

        @Volatile
        private var instance: SecurityCameraManager? = null

        fun getInstance(context: Context): SecurityCameraManager {
            return instance ?: synchronized(this) {
                instance ?: SecurityCameraManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: Prefs by lazy { Prefs(context) }
    private val photoDir: File by lazy {
        File(context.filesDir, "security_photos").also { it.mkdirs() }
    }

    // Background thread for Camera2 callbacks
    private val cameraThread = HandlerThread("SecurityCameraThread").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)

    // -------------------------------------------------------------------------
    //  Public API
    // -------------------------------------------------------------------------

    /**
     * Captures a single security photo. This is **fire-and-forget** —
     * it never throws and never blocks the calling flow.
     *
     * @param reason   Why the photo is being taken (links to flow step).
     * @param referenceId  Transaction ID (order, parcel, collection, etc.).
     * @param userId   The person being photographed (mobile number or courier ID).
     * @return The [SecurityPhotoEntity] if capture succeeded, or `null`.
     */
    suspend fun captureSecurityPhoto(
        reason: PhotoReason,
        referenceId: String,
        userId: String
    ): SecurityPhotoEntity? {
        // Gate 1: is the feature enabled?
        if (!prefs.isCameraEnabled()) {
            Log.d(TAG, "Camera disabled in settings — skipping capture")
            return null
        }

        return try {
            withTimeout(CAPTURE_TIMEOUT_MS) {
                doCapture(reason, referenceId, userId)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Camera capture timed out after ${CAPTURE_TIMEOUT_MS}ms")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Camera capture failed (non-fatal): ${e.message}")
            null
        }
    }

    /**
     * Returns true if at least one camera (preferably external/USB) is available.
     */
    fun isCameraAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as SystemCameraManager
            val ids = cm.cameraIdList
            ids.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes photos older than [days] from disk and marks them for DB cleanup.
     * Should be called periodically (e.g. from SyncWorker).
     */
    fun cleanupOldPhotos(days: Int = 30) {
        val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val files = photoDir.listFiles() ?: return
        var deleted = 0
        for (f in files) {
            if (f.lastModified() < cutoff) {
                f.delete()
                deleted++
            }
        }
        if (deleted > 0) Log.d(TAG, "Cleaned up $deleted old security photo(s)")
    }

    /** Releases background thread resources. Call on app shutdown. */
    fun release() {
        try {
            cameraThread.quitSafely()
        } catch (_: Exception) { }
    }

    // -------------------------------------------------------------------------
    //  Internal capture logic
    // -------------------------------------------------------------------------

    private suspend fun doCapture(
        reason: PhotoReason,
        referenceId: String,
        userId: String
    ): SecurityPhotoEntity? = withContext(Dispatchers.IO) {

        val cm = context.getSystemService(Context.CAMERA_SERVICE) as SystemCameraManager
        val cameraId = findBestCamera(cm) ?: run {
            Log.w(TAG, "No camera available — skipping capture")
            return@withContext null
        }

        Log.d(TAG, "▶ Capturing security photo: reason=$reason ref=$referenceId camera=$cameraId")

        // 1. Open camera
        val device = openCamera(cm, cameraId)

        try {
            // 2. Set up ImageReader to receive the JPEG
            val quality = prefs.getCameraJpegQuality()
            val reader = ImageReader.newInstance(TARGET_WIDTH, TARGET_HEIGHT, ImageFormat.JPEG, 1)

            // 3. Create capture session and take the photo
            val jpegBytes = captureImage(device, reader)

            // 4. Save to disk — adaptive JPEG compression targeting 50–100 KB
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${timestamp}_${reason.name}.jpg"
            val file = File(photoDir, fileName)

            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: throw RuntimeException("Failed to decode camera JPEG to Bitmap")

            val finalBytes = compressToTargetSize(
                bitmap = bitmap,
                targetMaxKb = 100,
                targetMinKb = 50
            )
            bitmap.recycle()

            FileOutputStream(file).use { it.write(finalBytes) }
            Log.d(TAG, "✅ Photo saved: ${file.absolutePath} (${finalBytes.size / 1024} KB @ ${finalBytes.size} bytes)")

            // 5. Create entity (caller is responsible for DB insert)
            val entity = SecurityPhotoEntity(
                filePath = file.absolutePath,
                reason = reason.name,
                referenceId = referenceId,
                userId = userId,
                kioskId = prefs.getLocationId().ifBlank { "UNKNOWN" },
                capturedAt = System.currentTimeMillis()
            )

            entity
        } finally {
            device.close()
        }
    }

    /**
     * Adaptively compresses a [Bitmap] to a JPEG byte array targeting [targetMinKb]–[targetMaxKb].
     *
     * Strategy:
     *  - Start at quality 80 (good sharpness at token file sizes for faces).
     *  - Step down by 5 each iteration until the output is ≤ [targetMaxKb] KB.
     *  - Stop at quality 10 to avoid blocking the thread too long.
     *  - If the compressed result is already < [targetMinKb] KB, keep it as-is
     *    (the image was already very compressible — e.g. a dark/simple scene).
     *
     * Resolution is **never changed** — only JPEG encoding quality is reduced.
     */
    private fun compressToTargetSize(
        bitmap: Bitmap,
        targetMaxKb: Int,
        targetMinKb: Int
    ): ByteArray {
        var quality = 80
        val targetMaxBytes = targetMaxKb * 1024
        val targetMinBytes = targetMinKb * 1024

        var result: ByteArray
        do {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            result = stream.toByteArray()
            Log.d(TAG, "  Compress q=$quality → ${result.size / 1024} KB")

            if (result.size <= targetMaxBytes) break   // within target
            quality -= 5
        } while (quality >= 10)

        // If still above max after quality=10, log a warning but keep the result
        if (result.size > targetMaxBytes) {
            Log.w(TAG, "Photo still ${result.size / 1024} KB after max compression — accepting")
        }

        return result
    }

    /**
     * Finds the best camera ID — prefers EXTERNAL (USB) over FRONT/BACK.
     */
    private fun findBestCamera(cm: SystemCameraManager): String? {
        val ids = try { cm.cameraIdList } catch (_: Exception) { return null }
        if (ids.isEmpty()) return null

        // Prefer external (USB) cameras
        for (id in ids) {
            try {
                val chars = cm.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    return id
                }
            } catch (_: Exception) { /* skip */ }
        }

        // Fallback: prefer front-facing (user is facing the kiosk)
        for (id in ids) {
            try {
                val chars = cm.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id
                }
            } catch (_: Exception) { /* skip */ }
        }

        // Last resort: any camera
        return ids.firstOrNull()
    }

    /**
     * Opens a Camera2 device, suspending until the callback fires.
     */
    @Suppress("MissingPermission") // Permission is checked at call site via isCameraAvailable
    private suspend fun openCamera(
        cm: SystemCameraManager,
        cameraId: String
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (cont.isActive) cont.resume(camera)
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                if (cont.isActive) cont.resumeWithException(
                    RuntimeException("Camera disconnected")
                )
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                if (cont.isActive) cont.resumeWithException(
                    RuntimeException("Camera error: $error")
                )
            }
        }, cameraHandler)
    }

    /**
     * Creates a capture session, sends a still-capture request, and returns the JPEG bytes.
     */
    private suspend fun captureImage(
        device: CameraDevice,
        reader: ImageReader
    ): ByteArray = suspendCancellableCoroutine { cont ->

        // Wait for the image to arrive
        reader.setOnImageAvailableListener({ r ->
            try {
                val image = r.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    if (cont.isActive) cont.resume(bytes)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }, cameraHandler)

        // Create capture session
        try {
            device.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            val request = device.createCaptureRequest(
                                CameraDevice.TEMPLATE_STILL_CAPTURE
                            ).apply {
                                addTarget(reader.surface)
                                set(CaptureRequest.JPEG_QUALITY, prefs.getCameraJpegQuality().toByte())
                                // Auto-focus and auto-exposure
                                set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON)
                            }
                            session.capture(request.build(), null, cameraHandler)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        if (cont.isActive) cont.resumeWithException(
                            RuntimeException("Camera session configuration failed")
                        )
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }
}
