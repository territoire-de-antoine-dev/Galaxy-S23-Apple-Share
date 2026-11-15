package dev.territoireantoine.appleshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(topBar = { Text("OpenDrop S23", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium) }) { inset ->
                    Column(Modifier.padding(inset).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Galaxy S23 · Prototype", style = MaterialTheme.typography.titleLarge)
                        Text("Le moteur de transfert local et le moteur AWDL sont en cours d'intégration.")
                        Text("AirDrop natif : non disponible dans cette version.")
                        Text("Aucune autorisation root n'est demandée pour le moment.")
                    }
                }
            }
        }
    }
}
