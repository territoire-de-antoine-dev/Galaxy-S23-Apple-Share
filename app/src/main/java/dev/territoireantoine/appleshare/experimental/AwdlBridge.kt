package dev.territoireantoine.appleshare.experimental

import android.os.Build
import java.io.File
import java.net.NetworkInterface

/**
 * Experimental boundary ONLY. This module does not advertise AWDL capability,
 * pretend to be an iPhone, modify firmware, or silently acquire root.
 */
interface AwdlBridge {
    val available: Boolean
    fun diagnostic(): String
}

object DisabledAwdlBridge : AwdlBridge {
    override val available = false
    override fun diagnostic(): String = "AWDL natif non implémenté. Aucun pilote n'est modifié."
}

object HardwareDiagnostic {
    fun collect(): String {
        val kernel = try { File("/proc/version").readText().trim().take(220) }
            catch (_: Exception) { "Lecture indisponible" }
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .joinToString { it.name }.take(220)
        } catch (_: Exception) { "Indisponibles" }
        return listOf(
            "Modèle : ${Build.MANUFACTURER} ${Build.MODEL}",
            "Android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Noyau : $kernel",
            "Interfaces : $interfaces",
            "Root : KernelSU indiqué par l'utilisateur, non sollicité par l'application",
            "Mode moniteur / injection : non vérifiés",
            DisabledAwdlBridge.diagnostic()
        ).joinToString("\n")
    }
}
