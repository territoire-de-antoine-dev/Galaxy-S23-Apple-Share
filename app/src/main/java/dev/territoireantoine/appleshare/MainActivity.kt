package dev.territoireantoine.appleshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import dev.territoireantoine.appleshare.experimental.HardwareDiagnostic

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ingestShare(intent)
        setContent {
            MaterialTheme { ShareScreen() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ingestShare(intent)
    }

    @Suppress("DEPRECATION")
    private fun ingestShare(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) ShareStore.add(this, uris)
    }
}

@Composable
private fun ShareScreen() {
    val context = LocalContext.current
    var showDiagnostic by remember { mutableStateOf(false) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        ShareStore.add(context, uris)
    }

    val pending = TransferState.pending
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { TransferState.answer(false) },
            title = { Text("Transfert entrant") },
            text = {
                Text("Autoriser la réception de « ${pending.name} » (${formatSize(pending.bytes)}) " +
                    "dans Téléchargements / OpenDrop S23 ?")
            },
            confirmButton = { Button(onClick = { TransferState.answer(true) }) { Text("Recevoir") } },
            dismissButton = { TextButton(onClick = { TransferState.answer(false) }) { Text("Refuser") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text("◉ OpenDrop S23", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Partage bidirectionnel avec Safari sur iPhone, iPad ou Mac",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Serveur local", style = MaterialTheme.typography.titleLarge)
                Text(TransferState.status)
                if (TransferState.active) {
                    Button(onClick = {
                        context.stopService(Intent(context, TransferService::class.java))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Arrêter le partage") }
                } else {
                    Button(onClick = {
                        context.startForegroundService(Intent(context, TransferService::class.java)
                            .setAction(TransferService.ACTION_START))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Activer le partage") }
                }
                if (TransferState.active && TransferState.address.isNotEmpty()) {
                    val link = TransferState.address
                    QrPreview(link)
                    Text("Avec l'iPhone : connectez-vous au même Wi-Fi, puis scannez ce code " +
                        "avec l'appareil photo.", textAlign = TextAlign.Center)
                    Text(link.substringBefore("#"), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Adresse OpenDrop", link))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Copier le lien d'association") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Fichiers proposés à l'iPhone", style = MaterialTheme.typography.titleMedium)
                Text("La personne sur l'appareil Apple pourra télécharger ces fichiers.")
                OutlinedButton(onClick = { pickFiles.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()) { Text("Choisir des fichiers") }
                if (ShareStore.items.isEmpty()) Text("Aucun fichier sélectionné.")
                ShareStore.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, maxLines = 1)
                            Text(formatSize(item.size), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { ShareStore.remove(item.id) }) { Text("Retirer") }
                    }
                }
                if (ShareStore.items.isNotEmpty()) {
                    TextButton(onClick = { ShareStore.clear() }) { Text("Vider la liste") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AirDrop natif / KernelSU", style = MaterialTheme.typography.titleMedium)
                Text("Module AWDL isolé et désactivé. Cette version ne se présente pas comme un iPhone.")
                OutlinedButton(onClick = { showDiagnostic = !showDiagnostic }) {
                    Text(if (showDiagnostic) "Masquer le diagnostic" else "Diagnostic matériel en lecture seule")
                }
                if (showDiagnostic) {
                    val diagnostic = remember { HardwareDiagnostic.collect() }
                    Text(diagnostic, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Sécurité : liaison HTTP non chiffrée. Utilisez uniquement un Wi-Fi privé " +
            "de confiance. Toute réception nécessite votre accord.",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QrPreview(address: String) {
    val image = remember(address) {
        runCatching {
            val matrix = MultiFormatWriter().encode(address, BarcodeFormat.QR_CODE, 320, 320)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE)
            }
            bitmap.asImageBitmap()
        }.getOrNull()
    }
    if (image != null) Image(bitmap = image, contentDescription = "QR code de connexion",
        modifier = Modifier.size(244.dp))
    else Text("Impossible de générer le QR code : utilisez le lien.")
}

private fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "Taille inconnue"
    bytes < 1024 -> "$bytes octets"
    bytes < 1024 * 1024 -> "${bytes / 1024} Kio"
    else -> "${bytes / (1024 * 1024)} Mio"
}
