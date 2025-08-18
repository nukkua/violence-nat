package com.example.violenceapp.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.violenceapp.services.VoiceRecognitionService
import com.example.violenceapp.viewmodel.AppViewModel

@Composable
fun HomeScreen(navController: NavController? = null, appViewModel: AppViewModel? = null) {
    val context = LocalContext.current

    // Estados para el reconocimiento de voz
    var isServiceRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Estado: Inactivo") }
    var recognizedTexts by remember { mutableStateOf(listOf<String>()) }
    var partialText by remember { mutableStateOf("") }
    var recognitionCount by remember { mutableStateOf(0) }

    // BroadcastReceiver para recibir resultados del servicio
    val voiceReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "VOICE_RECOGNIZED" -> {
                        val recognizedText = intent.getStringExtra("recognized_text") ?: ""
                        val count = intent.getIntExtra("recognition_count", 0)

                        recognizedTexts = recognizedTexts + recognizedText
                        recognitionCount = count
                        partialText = ""
                    }
                    "VOICE_PARTIAL" -> {
                        val partial = intent.getStringExtra("partial_text") ?: ""
                        partialText = partial
                    }
                }
            }
        }
    }

    // Registrar el receiver cuando se crea el composable
    DisposableEffect(context) {
        val filter =
                IntentFilter().apply {
                    addAction("VOICE_RECOGNIZED")
                    addAction("VOICE_PARTIAL")
                }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(voiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(voiceReceiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(voiceReceiver)
            } catch (e: Exception) {
                // Receiver ya no registrado
            }
        }
    }

    // Funciones para controlar el servicio
    fun startVoiceService() {
        try {
            val intent =
                    Intent(context, VoiceRecognitionService::class.java).apply {
                        action = "START_RECOGNITION"
                    }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            isServiceRunning = true
            statusText = "Estado: Servicio iniciado"
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopVoiceService() {
        try {
            val intent =
                    Intent(context, VoiceRecognitionService::class.java).apply {
                        action = "STOP_RECOGNITION"
                    }
            context.startService(intent)

            isServiceRunning = false
            statusText = "Estado: Servicio detenido"
            recognizedTexts = listOf()
            partialText = ""
            recognitionCount = 0
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // UI del HomeScreen
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor =
                                            if (isServiceRunning) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                            )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = if (isServiceRunning) "🎤" else "🔇", fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(text = "Reconocimiento de Voz", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                        text =
                                if (recognitionCount > 0) "Textos: $recognitionCount"
                                else statusText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botones
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                    onClick = { startVoiceService() },
                    enabled = !isServiceRunning,
                    modifier = Modifier.weight(1f)
            ) { Text("Iniciar") }

            OutlinedButton(
                    onClick = { stopVoiceService() },
                    enabled = isServiceRunning,
                    modifier = Modifier.weight(1f)
            ) { Text("Detener") }

            if (recognizedTexts.isNotEmpty()) {
                OutlinedButton(
                        onClick = {
                            recognizedTexts = listOf()
                            recognitionCount = 0
                        },
                        modifier = Modifier.weight(1f)
                ) { Text("Limpiar") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Texto parcial
        if (partialText.isNotEmpty()) {
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
            ) {
                Text(text = "🔄 $partialText", modifier = Modifier.padding(16.dp), fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Lista de textos
        if (recognizedTexts.isNotEmpty()) {
            Text(
                    text = "Textos Reconocidos:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true
            ) {
                itemsIndexed(recognizedTexts.reversed()) { index, text ->
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.primaryContainer
                                    )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "#${recognizedTexts.size - index}",
                                    fontSize = 12.sp,
                                    color =
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                    alpha = 0.7f
                                            )
                            )
                            Text(
                                    text = text,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        } else if (isServiceRunning) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                        text = "🎤 Esperando que hables...",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                        text = "Presiona 'Iniciar' para comenzar\nel reconocimiento de voz",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                )
            }
        }
    }
}
