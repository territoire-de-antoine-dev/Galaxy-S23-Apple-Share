package dev.territoireantoine.appleshare

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local-only transfer bridge. It does NOT speak AirDrop or AWDL.
 * A random secret in the QR URL fragment is required for every API call.
 * Transport is HTTP, NOT TLS: use only on a trusted private Wi-Fi network.
 */
class LocalTransferServer(private val context: Context) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(4)
    private val server = ServerSocket(0, 16)
    private val secret = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
    private val ip = localIp()
    val address: String = "http://$ip:${server.localPort}/#key=$secret"

    fun start() {
        running.set(true)
        TransferState.address = address
        TransferState.active = true
        TransferState.status = if (ip == "127.0.0.1") {
            "Aucune adresse réseau locale détectée. Connectez le Wi-Fi."
        } else "Actif sur le réseau local (HTTP non chiffré)"
        Thread({
            while (running.get()) {
                try {
                    val socket = server.accept()
                    pool.submit { handle(socket) }
                } catch (_: Exception) { if (!running.get()) break }
            }
        }, "opendrop-accept").apply { isDaemon = true; start() }
    }

    fun close() {
        running.set(false)
        try { server.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use {
            it.soTimeout = 120_000
            try {
                val input = BufferedInputStream(it.getInputStream())
                val output = BufferedOutputStream(it.getOutputStream())
                val first = readLine(input) ?: return
                val parts = first.split(" ")
                if (parts.size < 3) return
                val method = parts[0]
                val path = parts[1].substringBefore('?')
                val headers = mutableMapOf<String, String>()
                repeat(40) {
                    val line = readLine(input) ?: return@use
                    if (line.isEmpty()) return@repeat
                    val split = line.indexOf(':')
                    if (split > 0) headers[line.substring(0, split).lowercase(Locale.US)] =
                        line.substring(split + 1).trim()
                }
                // Headers end at first empty line. readHeaders() below is preferred for
                // request bodies, and intentionally handles no chunked transfer encoding.
                route(method, path, headers, input, output)
            } catch (_: Exception) {
                // Bad clients do not crash the transfer service.
            }
        }
    }

    private fun route(method: String, path: String, headers: Map<String, String>,
                      input: InputStream, output: OutputStream) {
        if (method == "GET" && path == "/") {
            sendText(output, 200, "text/html; charset=utf-8", PORTAL)
            return
        }
        val received = headers["x-pairing-code"] ?: ""
        if (!MessageDigest.isEqual(secret.toByteArray(StandardCharsets.UTF_8),
                received.toByteArray(StandardCharsets.UTF_8))) {
            sendText(output, 403, "application/json", """{"error":"Code invalide"}""")
            return
        }
        when {
            method == "GET" && path == "/api/files" -> {
                val data = JSONArray()
                ShareStore.items.forEach { item ->
                    data.put(JSONObject().put("id", item.id).put("name", item.name)
                        .put("size", item.size).put("type", item.mime))
                }
                sendText(output, 200, "application/json; charset=utf-8", data.toString())
            }
            method == "GET" && path.startsWith("/api/files/") -> {
                val id = path.removePrefix("/api/files/")
                val file = ShareStore.items.firstOrNull { it.id == id }
                if (file == null) { sendText(output, 404, "text/plain", "Fichier absent"); return }
                val source = try { context.contentResolver.openInputStream(file.uri) }
                    catch (_: Exception) { null }
                if (source == null) { sendText(output, 404, "text/plain", "Accès perdu"); return }
                source.use { stream -> streamFile(output, stream, file) }
            }
            method == "POST" && path == "/api/upload" -> receive(headers, input, output)
            else -> sendText(output, 404, "text/plain", "Route inconnue")
        }
    }

    private fun receive(headers: Map<String, String>, input: InputStream, output: OutputStream) {
        val length = headers["content-length"]?.toLongOrNull()
        if (length == null || length !in 1..MAX_FILE_BYTES) {
            sendText(output, 413, "text/plain", "Taille absente ou trop élevée (limite 500 Mio)")
            return
        }
        val filename = try {
            URLDecoder.decode(headers["x-file-name"] ?: "fichier", "UTF-8")
        } catch (_: Exception) { "fichier" }
        val clean = filename.replace(Regex("[/\\\\\\x00-\\x1F]"), "_")
            .trim().take(120).ifEmpty { "fichier" }
        val pending = TransferState.requestApproval(clean, length)
        if (pending == null) { sendText(output, 409, "text/plain", "Une autre demande est en cours"); return }
        val approved = try { pending.decision.get(75, TimeUnit.SECONDS) }
            catch (_: Exception) { false }
        TransferState.finish(pending)
        if (!approved) { sendText(output, 403, "text/plain", "Envoi refusé ou expiré"); return }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, clean)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OpenDrop S23")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        var uri: android.net.Uri? = null
        try {
            uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Création impossible")
            context.contentResolver.openOutputStream(uri)?.use { target ->
                var remaining = length
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0L) {
                    val got = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (got < 0) error("Transfert interrompu")
                    target.write(buffer, 0, got)
                    remaining -= got
                }
            } ?: error("Stockage inaccessible")
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            sendText(output, 200, "application/json", """{"ok":true}""")
        } catch (_: Exception) {
            uri?.let { context.contentResolver.delete(it, null, null) }
            sendText(output, 500, "text/plain", "Échec du stockage")
        }
    }

    private fun streamFile(output: OutputStream, input: InputStream, file: SharedItem) {
        val encoded = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val base = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "Content-Disposition: attachment; filename*=UTF-8''$encoded\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n"
        if (file.size >= 0) {
            output.write((base + "Content-Length: ${file.size}\r\n\r\n").toByteArray())
            input.copyTo(output, 64 * 1024)
        } else {
            output.write((base + "Transfer-Encoding: chunked\r\n\r\n").toByteArray())
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                output.write(("${n.toString(16)}\r\n").toByteArray())
                output.write(buffer, 0, n)
                output.write("\r\n".toByteArray())
            }
            output.write("0\r\n\r\n".toByteArray())
        }
        output.flush()
    }

    private fun sendText(out: OutputStream, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"
            409 -> "Conflict"; 413 -> "Payload Too Large"; else -> "Internal Server Error"
        }
        val header = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
            "Referrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (buf.size() < 8192) {
            val c = input.read()
            if (c == -1) return null
            if (c == 10) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(c)
        }
        return null
    }

    private fun localIp(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val addresses = interfaces.filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
            addresses.firstOrNull()?.hostAddress ?: "127.0.0.1"
        } catch (_: Exception) { "127.0.0.1" }
    }

    companion object {
        private const val MAX_FILE_BYTES = 500L * 1024 * 1024
        private val PORTAL = """
<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>OpenDrop S23 · Partage</title>
<style>
:root{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color-scheme:light}
body{background:#f3f5fa;color:#1c2539;max-width:680px;margin:0 auto;padding:25px}
h1{font-size:32px} .card{background:#fff;border-radius:22px;padding:22px;margin:18px 0;box-shadow:0 5px 24px #24365313}
button,.pick{background:#1467ed;color:#fff;border:0;border-radius:12px;padding:12px 18px;font-size:16px;cursor:pointer}
input{margin:12px 0;max-width:100%}li{padding:9px 0}small{color:#59677a}#status{white-space:pre-wrap}
</style></head><body>
<h1>◉ OpenDrop S23</h1><p>Partage local avec votre Galaxy S23.</p>
<div class="card"><h2>Envoyer vers le Galaxy</h2><input id="files" type="file" multiple>
<p><button id="send">Envoyer les fichiers</button></p>
<small>Le Galaxy doit accepter chaque fichier. Limite : 500 Mio par fichier.</small></div>
<div class="card"><h2>Recevoir depuis le Galaxy</h2>
<p><button id="refresh">Actualiser la liste</button></p><ul id="list"></ul>
<small>Sélectionnez d'abord les fichiers dans l'application Android.</small></div>
<p id="status" role="status"></p>
<small>Réseau privé seulement : version expérimentale, liaison HTTP non chiffrée.
Ne pas utiliser sur un Wi-Fi public.</small>
<script>
"use strict";
const code = new URLSearchParams(location.hash.slice(1)).get("key") || "";
const statusNode = document.getElementById("status");
const status = text => { statusNode.textContent = text; };
async function api(path, opts={}) {
  const headers = Object.assign({}, opts.headers || {}, {"X-Pairing-Code":code});
  const r=await fetch(path, Object.assign({},opts,{headers,cache:"no-store"}));
  if(!r.ok) throw new Error("Code HTTP " + r.status);
  return r;
}
async function refresh(){
 try {
   const data=await (await api("/api/files")).json();
   const list=document.getElementById("list");list.replaceChildren();
   if(!data.length){const li=document.createElement("li");li.textContent="Aucun fichier proposé.";list.append(li)}
   for(const item of data){
     const li=document.createElement("li"),btn=document.createElement("button");
     btn.textContent="Télécharger · "+item.name;
     btn.onclick=async()=>{
       try {
         status("Téléchargement en cours…");
         const blob=await (await api("/api/files/"+encodeURIComponent(item.id))).blob();
         const url=URL.createObjectURL(blob),a=document.createElement("a");
         a.href=url;a.download=item.name;document.body.append(a);a.click();a.remove();
         setTimeout(()=>URL.revokeObjectURL(url),30000);status("Fichier prêt à enregistrer ou partager.");
       }catch(e){status("Erreur : "+e.message)}
     };
     li.append(btn);list.append(li);
   }
 }catch(e){status("Impossible de lister : "+e.message)}
}
document.getElementById("refresh").onclick=refresh;
document.getElementById("send").onclick=async()=>{
 const files=document.getElementById("files").files;
 if(!files.length){status("Choisissez un fichier.");return}
 for(const f of files){
   if(f.size>500*1024*1024){status("Fichier trop grand : "+f.name);return}
   try {
     status("En attente de confirmation sur le Galaxy : "+f.name);
     await api("/api/upload",{method:"POST",headers:{"X-File-Name":encodeURIComponent(f.name)},
       body:f});
     status("Fichier reçu par le Galaxy : "+f.name);
   }catch(e){status("Transfert refusé ou échoué : "+f.name+" ("+e.message+")");return}
 }
};
if(!code){status("Lien d'association incomplet. Scannez le QR code du Galaxy.")}
else refresh();
</script></body></html>
        """.trimIndent()
    }
}
