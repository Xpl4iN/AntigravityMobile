package de.xyourp.antigravitymobile.data

import android.content.Context
import de.xyourp.antigravitymobile.net.ApiClient
import de.xyourp.antigravitymobile.net.RemoteSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheStore(private val context: Context) {
    private val cacheFile: File
        get() = File(context.filesDir, "snapshot_cache.json")

    suspend fun saveSnapshot(snapshot: RemoteSnapshot) = withContext(Dispatchers.IO) {
        try {
            val jsonStr = ApiClient.json.encodeToString(RemoteSnapshot.serializer(), snapshot)
            val tempFile = File(context.filesDir, "snapshot_cache.json.tmp")
            tempFile.writeText(jsonStr)
            if (tempFile.exists()) {
                tempFile.renameTo(cacheFile)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun loadSnapshot(): RemoteSnapshot? = withContext(Dispatchers.IO) {
        try {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                val jsonStr = cacheFile.readText()
                ApiClient.json.decodeFromString(RemoteSnapshot.serializer(), jsonStr)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        } catch (_: Exception) {
        }
    }
}
