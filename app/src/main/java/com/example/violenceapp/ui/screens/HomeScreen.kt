package com.example.violenceapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
        hasLocationPermission: Boolean = false,
        onRequestMicrophonePermission: () -> Unit = {},
        onRequestLocationPermission: () -> Unit = {}
) {
    // Estados del ViewModel
    val keywordState = appViewModel?.keywordState
    val serviceState = appViewModel?.serviceState
    val configState = appViewModel?.configState
    val voiceRecognitionState = appViewModel?.voiceRecognitionState

    val isServiceRunning = serviceState?.isRunning ?: false
    val isListening = serviceState?.isListening ?: false
    val currentKeyword = keywordState?.keyword ?: "alerta"
    val isKeywordConfigured = keywordState?.isConfigured ?: false

    // Estados de reconocimiento de voz
    val recognizedTexts = voiceRecognitionState?.recognizedTexts ?: emptyList()
    val partialText = voiceRecognitionState?.partialText ?: ""
    val recognitionCount = voiceRecognitionState?.recognitionCount ?: 0
    val serviceStatus = voiceRecognitionState?.serviceStatus ?: "Inactivo"

    // Estado para el scroll
    val listState = rememberLazyListState()

    // Auto scroll hacia el último elemento cuando hay nuevos textos
    LaunchedEffect(recognizedTexts.size) {
        if (recognizedTexts.isNotEmpty()) {
            listState.animateScrollToItem(0) // 0 porque usamos reverseLayout
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Contenido con scroll
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                state = listState,
                reverseLayout = true, // Para que los nuevos elementos aparezcan arriba
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Espaciado para el navbar flotante (al final por reverseLayout)
            item { Spacer(modifier = Modifier.height(100.dp)) }

            // Lista de textos reconocidos
            if (recognizedTexts.isNotEmpty()) {
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
                            // Mostrar timestamp
                            Text(
                                    text =
                                            java.text.SimpleDateFormat(
                                                            "HH:mm:ss",
                                                            java.util.Locale.getDefault()
                                                    )
                                                    .format(java.util.Date()),
                                    fontSize = 10.sp,
                                    color =
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                    alpha = 0.5f
                                            )
                            )
                        }
                    }
                }

                // Header para la lista
                item {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = "Textos Reconocidos:",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                        )
                        if (recognizedTexts.isNotEmpty()) {
                            OutlinedButton(
                                    onClick = { appViewModel?.clearRecognizedTexts() },
                                    modifier = Modifier.height(32.dp)
                            ) { Text("🧹 Limpiar", fontSize = 12.sp) }
                        }
                    }
                }
            } else if (isServiceRunning) {
                item {
                    Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isListening) "🎤" else "⏸️", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = if (isListening) "Escuchando..." else "En pausa",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                            )
                            Text(
                                    text = serviceStatus,
                                    fontSize = 12.sp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                            ),
                                    textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Texto parcial (en tiempo real)
            if (partialText.isNotEmpty()) {
                item {
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
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Info Card (cuando está activo)
            if (isServiceRunning && hasMicrophonePermission) {
                item {
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                    )
                    ) {
                        Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                    text = "🎤 Reconocimiento Activo",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                    text = "Palabra clave: \"$currentKeyword\"",
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                    text = "Estado: $serviceStatus",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color =
                                            MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                                    alpha = 0.7f
                                            )
                            )
                            if (recognitionCount > 0) {
                                Text(
                                        text = "Textos reconocidos: $recognitionCount",
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        color =
                                                MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                                        alpha = 0.7f
                                                )
                                )
                            }
                            if (hasLocationPermission) {
                                Text(
                                        text = "📍 GPS habilitado para alertas",
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        color =
                                                MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                                        alpha = 0.7f
                                                )
                                )
                            }
                        }
                    }
                }
            }

            // Botones secundarios
            item {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                            onClick = { navController?.navigate("setup") },
                            modifier = Modifier.weight(1f)
                    ) { Text("⚙️ Configurar") }

                    OutlinedButton(
                            onClick = {
                                // Test functionality - solo para probar reconocimiento
                            },
                            modifier = Modifier.weight(1f),
                            enabled = hasMicrophonePermission
                    ) { Text("📤 Probar") }
                }
            }

            // Botón principal
            item {
                Column {
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
                                                    if (isServiceRunning)
                                                            MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.primary
                                    )
                    ) {
                        Text(
                                text =
                                        when {
                                            !hasMicrophonePermission ->
                                                    "🔒 Concede permisos de micrófono"
                                            !isKeywordConfigured ->
                                                    "⚙️ Configura palabra clave primero"
                                            isServiceRunning -> "⏸️ Detener Reconocimiento"
                                            else -> "▶️ Iniciar Reconocimiento"
                                        },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                        )
                    }

                    // Debug info
                    if (isKeywordConfigured) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                                text =
                                        "Debug: Keyword='$currentKeyword', Mic=$hasMicrophonePermission, GPS=$hasLocationPermission\nRunning=$isServiceRunning, Listening=$isListening, Status='$serviceStatus'",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Status Card
            item {
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                when {
                                                    !hasMicrophonePermission ->
                                                            MaterialTheme.colorScheme.errorContainer
                                                    isServiceRunning ->
                                                            MaterialTheme.colorScheme
                                                                    .primaryContainer
                                                    isKeywordConfigured ->
                                                            MaterialTheme.colorScheme.surfaceVariant
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text =
                                            when {
                                                !hasMicrophonePermission ->
                                                        "🔇 Sin Permisos de Micrófono"
                                                !hasLocationPermission ->
                                                        "📍 Sin Permisos de Ubicación"
                                                isServiceRunning -> "🔊 Reconocimiento Activo"
                                                isKeywordConfigured -> "⚪ Listo para Iniciar"
                                                else -> "⚠️ Sin Configurar"
                                            },
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                            )

                            // Indicador de configuración (solo keyword)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (isKeywordConfigured) {
                                    Text("🗣️", fontSize = 12.sp)
                                }
                                if (hasMicrophonePermission) {
                                    Text("🎤", fontSize = 12.sp)
                                }
                                if (hasLocationPermission) {
                                    Text("📍", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text =
                                        when {
                                            !hasMicrophonePermission ->
                                                    "Concede permisos de micrófono para continuar"
                                            !hasLocationPermission ->
                                                    "Concede permisos de ubicación para enviar alertas completas"
                                            !isKeywordConfigured ->
                                                    "Configura una palabra clave en Configuración"
                                            isServiceRunning ->
                                                    "Escuchando palabra clave: \"$currentKeyword\""
                                            else -> "Listo para comenzar el reconocimiento de voz"
                                        },
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Información adicional de configuración (simplificada)
                        if (isKeywordConfigured && isServiceRunning) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text =
                                            if (hasLocationPermission)
                                                    "✅ Sistema completo: Voz + GPS + Telegram"
                                            else "⚠️ Solo reconocimiento de voz (sin GPS)",
                                    fontSize = 12.sp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                            )
                            )
                        }
                    }
                }
            }

            // Permission Warning Card - Micrófono (si no tiene permiso)
            if (!hasMicrophonePermission) {
                item {
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.errorContainer
                                    )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                        text = "🎤 Permiso de Micrófono Requerido",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text =
                                            "ViolenceApp necesita acceso al micrófono para escuchar la palabra clave de emergencia y activar la protección automáticamente.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                    onClick = onRequestMicrophonePermission,
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                            )
                            ) { Text("🎤 Conceder Permiso de Micrófono") }
                        }
                    }
                }
            }

            // Permission Warning Card - Ubicación (si no tiene permiso)
            if (!hasLocationPermission) {
                item {
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.secondaryContainer
                                    )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                        text = "📍 Permiso de Ubicación Recomendado",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text =
                                            "Para enviar tu ubicación exacta por Telegram en caso de emergencia, necesitamos acceso a tu GPS.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                        onClick = onRequestLocationPermission,
                                        modifier = Modifier.weight(1f),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.tertiary
                                                )
                                ) { Text("📍 Conceder Ubicación") }
                                OutlinedButton(
                                        onClick = { /* Continuar sin ubicación */},
                                        modifier = Modifier.weight(1f)
                                ) { Text("Continuar sin GPS") }
                            }
                        }
                    }
                }
            }

            // Solo mostrar warning de configuración si falta la palabra clave
            if (hasMicrophonePermission && !isKeywordConfigured) {
                item {
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.secondaryContainer
                                    )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "⚙️ Configuración Requerida",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text =
                                            "Configura una palabra clave para comenzar a usar el reconocimiento de voz.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { navController?.navigate("setup") }) {
                                Text("⚙️ Configurar Palabra Clave")
                            }
                        }
                    }
                }
            }

            // Header (al principio por reverseLayout)
            item {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = "🎤 Reconocimiento de Voz",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
