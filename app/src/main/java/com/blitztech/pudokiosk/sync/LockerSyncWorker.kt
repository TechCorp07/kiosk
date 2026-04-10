package com.blitztech.pudokiosk.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.db.CellEntity
import com.blitztech.pudokiosk.util.NetworkUtils

/**
 * LockerSyncWorker — syncs cell inventory and sends heartbeat to the backend.
 *
 * Runs every 5 minutes (WorkManager minimum is 15 min for periodic, we use
 * a chained one-time request looped from the app for the 5-min cadence on API 25).
 *
 * Responsibilities:
 *   1. Fetch cell list from GET /api/v1/lockers/{id}/cells for each configured locker
 *   2. Upsert into local Room cells table (enables offline cell assignment)
 *   3. PATCH /api/v1/lockers/{id}/status?status=ONLINE (heartbeat)
 *   4. Purge stale offline collection entries (collected + older than 7 days)
 *
 * TODO [POST-LAUNCH BACKEND P0]: Add GET /api/v1/orders/pending-collections?lockerId=
 * and call syncPendingCollections() here once available.
 */
class LockerSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "LockerSyncWorker"
        const val WORK_NAME = "pudo_locker_sync"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "▶ LockerSyncWorker starting…")

        if (!NetworkUtils.isOnline(applicationContext)) {
            Log.d(TAG, "Offline — skipping locker sync (will retry next schedule)")
            return Result.retry()
        }

        val prefs = ZimpudoApp.prefs
        // Use a dummy string since the API Key architecture ignores the Bearer token for cell syncs.
        // We keep the signature the same for backward compatibility in the repository method.
        val token = prefs.getAccessToken().orEmpty().ifBlank { "background-sync" }

        val primaryLockerUuid = prefs.getPrimaryLockerUuid()
        if (primaryLockerUuid.isBlank()) {
            Log.w(TAG, "No primary locker UUID configured — kiosk not provisioned")
            return Result.failure() // Don't retry — provisioning needed
        }

        var overallSuccess = true

        // ── 1. Sync cells for each configured locker ──────────────────────
        // Currently supports primary locker; extend when multi-locker is needed
        val lockerUuids = listOf(primaryLockerUuid)
            .filter { it.isNotBlank() }

        for (lockerUuid in lockerUuids) {
            val cellResult = ZimpudoApp.apiRepository.getLockerCells(lockerUuid, token)
            when (cellResult) {
                is NetworkResult.Success -> {
                    val cells = cellResult.data
                    val entities = cells.map { dto ->
                        CellEntity(
                            cellUuid = dto.id,
                            lockerUuid = lockerUuid,
                            physicalDoorNumber = dto.cellNumber,
                            cellSize = dto.size,
                            status = dto.status,
                            cabinetId = dto.cabinetId,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    }
                    ZimpudoApp.database.cells().upsertAll(entities)
                    Log.d(TAG, "✅ Synced ${entities.size} cells for locker $lockerUuid")
                }
                is NetworkResult.Error -> {
                    Log.w(TAG, "Failed to sync cells for $lockerUuid: ${cellResult.message}")
                    overallSuccess = false
                }
                is NetworkResult.Loading<*> -> { }
            }
        }

        // ── 2. Send heartbeat ─────────────────────────────────────────────
        for (lockerUuid in lockerUuids) {
            try {
                val heartbeatResult = ZimpudoApp.apiRepository.patchLockerStatus(
                    lockerId = lockerUuid,
                    status = "ONLINE",
                    token = token
                )
                when (heartbeatResult) {
                    is NetworkResult.Success ->
                        Log.d(TAG, "💓 Heartbeat sent for locker $lockerUuid")
                    is NetworkResult.Error ->
                        Log.w(TAG, "Heartbeat failed for $lockerUuid: ${heartbeatResult.message}")
                    is NetworkResult.Loading<*> -> { }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat exception for $lockerUuid: ${e.message}")
            }
        }

        // ── 3. Purge old collected offline entries ────────────────────────
        try {
            ZimpudoApp.offlineCollectionManager.purgeOldCollected(retentionDays = 7)
            Log.d(TAG, "🧹 Purged stale offline collection entries")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to purge offline entries: ${e.message}")
        }

        // ── 4. Sync pending collections (DEFERRED) ────────────────────────
        // TODO [POST-LAUNCH BACKEND]: Pre-load hashed OTPs for offline validation
        // Endpoint: GET /api/v1/orders/pending-collections?lockerId=
        // Action: Fetch list of pending collections for this locker, hash the OTPs,
        //         and store them via ZimpudoApp.offlineCollectionManager.upsertAll()

        return if (overallSuccess) Result.success() else Result.retry()
    }
}
