package com.example.violenceapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.violenceapp.services.VoiceRecognitionService

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)
    private var statusText by mutableStateOf("Estado: Inactivo")
    private var recognizedTexts by mutableStateOf(listOf<String>())
    private var partialText by mutableStateOf("")
    private var recognitionCount by mutableStateOf(0)

    private val voiceReceiver =
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

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted) {
                    Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permisos necesarios", Toast.LENGTH_LONG).show()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()
        registerVoiceReceiver()

        setContent {
            MaterialTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) { VoiceRecognitionScreen() }
            }
        }
    }

    @Composable
    fun VoiceRecognitionScreen() {
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
                                                if (isServiceRunning)
                                                        MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = if (isServiceRunning) "🎤" else "🔇", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                            text = "Reconocimiento de Voz",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                    )
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
                                        containerColor =
                                                MaterialTheme.colorScheme.secondaryContainer
                                )
                ) {
                    Text(
                            text = "🔄 $partialText",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 16.sp
                    )
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

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun registerVoiceReceiver() {
        val filter =
                IntentFilter().apply {
                    addAction("VOICE_RECOGNIZED")
                    addAction("VOICE_PARTIAL")
                }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(voiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(voiceReceiver, filter)
        }
    }

    private fun startVoiceService() {
        try {
            val intent =
                    Intent(this, VoiceRecognitionService::class.java).apply {
                        action = "START_RECOGNITION"
                    }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            isServiceRunning = true
            statusText = "Estado: Servicio iniciado"
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVoiceService() {
        try {
            val intent =
                    Intent(this, VoiceRecognitionService::class.java).apply {
                        action = "STOP_RECOGNITION"
                    }
            startService(intent)

            isServiceRunning = false
            statusText = "Estado: Servicio detenido"
            recognizedTexts = listOf()
            partialText = ""
            recognitionCount = 0
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(voiceReceiver)
        } catch (e: Exception) {
            // Receiver ya no registrado
        }
    }
}
