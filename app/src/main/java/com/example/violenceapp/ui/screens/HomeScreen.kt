package com.example.violenceapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.violenceapp.viewmodel.AppViewModel

@Composable
fun HomeScreen(
        navController: NavController? = null,
        appViewModel: AppViewModel? = null,
        hasMicrophonePermission: Boolean = false,
        onRequestMicrophonePermission: () -> Unit = {}
) {
    // Estados del ViewModel
    val keywordState = appViewModel?.keywordState
    val serviceState = appViewModel?.serviceState
    val configState = appViewModel?.configState

    val isServiceRunning = serviceState?.isRunning ?: false
    val currentKeyword = keywordState?.keyword ?: "alerta"
    val isKeywordConfigured = keywordState?.isConfigured ?: false

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                    text = "ViolenceApp",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Permission Warning Card (si no tiene permiso)
        if (!hasMicrophonePermission) {
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                            )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                text = "⚠️ Permiso Requerido",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                            text =
                                    "ViolenceApp necesita acceso al micrófono para escuchar la palabra clave de emergencia.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRequestMicrophonePermission) { Text("Conceder Permiso") }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Status Card
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        when {
                                            !hasMicrophonePermission ->
                                                    MaterialTheme.colorScheme.surfaceVariant
                                            isServiceRunning ->
                                                    MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                        )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            text =
                                    when {
                                        !hasMicrophonePermission -> "🔇 ⚪ Sin Permisos"
                                        isServiceRunning -> "🔊 🔴 Protección Activa"
                                        else -> "🔇 ⚪ Protección Inactiva"
                                    },
                            fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text =
                                when {
                                    !hasMicrophonePermission ->
                                            "Concede permisos de micrófono para continuar"
                                    isServiceRunning ->
                                            "Escuchando palabra clave: \"$currentKeyword\""
                                    isKeywordConfigured ->
                                            "Palabra configurada: \"$currentKeyword\""
                                    else -> "Configura una palabra clave primero"
                                },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Button(
                onClick = {
                    if (isServiceRunning) {
                        appViewModel?.stopService()
                    } else {
                        appViewModel?.startService()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasMicrophonePermission && isKeywordConfigured,
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor =
                                        if (isServiceRunning) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                        )
        ) {
            Text(
                    text =
                            when {
                                !hasMicrophonePermission -> "🔒 Concede permisos primero"
                                isServiceRunning -> "⏸️ Detener Protección"
                                isKeywordConfigured -> "▶️ Iniciar Protección"
                                else -> "⚙️ Configura palabra clave primero"
                            }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary Buttons
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                    onClick = { navController?.navigate("setup") },
                    modifier = Modifier.weight(1f)
            ) { Text("⚙️ Configurar") }

            OutlinedButton(
                    onClick = { /* Test functionality */},
                    modifier = Modifier.weight(1f),
                    enabled = hasMicrophonePermission
            ) { Text("📤 Probar") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info Card
        if (isServiceRunning && hasMicrophonePermission) {
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
            ) {
                Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                            text = "🎤 Sistema Activo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                    )
                    Text(
                            text = "Escuchando: \"$currentKeyword\"",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Espaciado para el navbar flotante
        Spacer(modifier = Modifier.height(100.dp))
    }
}
