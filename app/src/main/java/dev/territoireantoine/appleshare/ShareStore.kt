package dev.territoireantoine.appleshare

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

data class SharedItem(val id: String, val uri: Uri, val name: String, val size: Long, val mime: String)

object ShareStore {
    var items by mutableStateOf<List<SharedItem>>(emptyList())
        private set

    fun add(context: Context, uris: List<Uri>) {
        val newItems = uris.mapNotNull { uri ->
            try {
                var name = "Fichier"
                var length = -1L
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                            name = cursor.getString(it) ?: name
                        }
                        cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let {
                            if (!cursor.isNull(it)) length = cursor.getLong(it)
                        }
                    }
                }
                SharedItem(UUID.randomUUID().toString(), uri, name,
                    length, context.contentResolver.getType(uri) ?: "application/octet-stream")
            } catch (_: Exception) { null }
        }
        items = (items + newItems).takeLast(30)
    }

    fun remove(id: String) { items = items.filterNot { it.id == id } }
    fun clear() { items = emptyList() }
}

data class IncomingRequest(
    val name: String,
    val bytes: Long,
    val decision: java.util.concurrent.CompletableFuture<Boolean> = java.util.concurrent.CompletableFuture()
)

object TransferState {
    var active by mutableStateOf(false)
    var address by mutableStateOf("")
    var status by mutableStateOf("Serveur arrêté")
    var pending by mutableStateOf<IncomingRequest?>(null)
        private set

    @Synchronized fun requestApproval(name: String, length: Long): IncomingRequest? {
        if (pending != null) return null
        return IncomingRequest(name, length).also { pending = it }
    }

    @Synchronized fun answer(approved: Boolean) {
        pending?.decision?.complete(approved)
        pending = null
    }

    @Synchronized fun finish(request: IncomingRequest) {
        if (pending === request) pending = null
    }
}
