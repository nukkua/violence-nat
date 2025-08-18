package com.example.violenceapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.violenceapp.viewmodel.AppViewModel

@Composable
fun VoiceSetupScreen(navController: NavController? = null, appViewModel: AppViewModel? = null) {
    // Usar el estado del ViewModel o estado local como fallback
    val currentKeyword = appViewModel?.keywordState?.keyword ?: ""
    var keywordInput by remember { mutableStateOf(currentKeyword) }
    val scrollState = rememberScrollState()

    val recommendedKeywords =
            listOf(
                    "alerta",
                    "auxilio",
                    "emergencia",
                    "socorro",
                    "ayuda",
                    "peligro",
                    "pánico",
                    "asistencia",
                    "rescate"
            )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
            // Header con botón de regreso
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { navController?.popBackStack() }) { Text("← Atrás") }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                        text = "🎤 Palabra Clave",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Nota importante
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                            )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚠️", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = "Importante",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                            text =
                                    "Este gesto de voz debe usarse únicamente cuando te encuentres en una situación de peligro real. El uso indebido o con fines falsos puede tener consecuencias legales y compromete la efectividad del sistema de protección.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de palabras recomendadas
            Text(
                    text = "Palabras recomendadas",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                    text =
                            "Selecciona una palabra que sea fácil de recordar en situaciones de estrés",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(recommendedKeywords) { keyword ->
                    FilterChip(
                            onClick = { keywordInput = keyword },
                            label = { Text(keyword) },
                            selected = keywordInput == keyword,
                            leadingIcon =
                                    if (keywordInput == keyword) {
                                        { Text("✓", fontSize = 12.sp) }
                                    } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Input personalizado
            Text(
                    text = "O escribe tu propia palabra",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it.lowercase().trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Palabra clave") },
                    placeholder = { Text("Ej: alerta, auxilio, emergencia...") },
                    trailingIcon = {
                        if (keywordInput.isNotEmpty()) {
                            TextButton(onClick = { keywordInput = "" }) { Text("✕ Limpiar") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    singleLine = true
            )

            if (keywordInput.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = "Vista previa: \"$keywordInput\"",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Consejos
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            text = "💡 Consejos",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val tips =
                            listOf(
                                    "Elige una palabra que no uses frecuentemente",
                                    "Evita nombres propios o palabras complejas",
                                    "Debe ser fácil de pronunciar bajo estrés",
                                    "Una sola palabra es más efectiva que frases"
                            )

                    tips.forEach { tip ->
                        Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                        ) {
                            Text("• ", color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                    text = tip,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Espaciado para el botón fijo abajo
            Spacer(modifier = Modifier.height(120.dp))
        }

        // Botón guardar fijo en la parte inferior
        Card(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (keywordInput.isNotEmpty())
                                                MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                        ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            TextButton(
                    onClick = {
                        if (keywordInput.isNotEmpty()) {
                            // Guardar en el ViewModel
                            appViewModel?.setKeyword(keywordInput)
                            navController?.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    enabled = keywordInput.isNotEmpty()
            ) {
                Text(
                        text =
                                if (keywordInput.isNotEmpty()) "💾 Guardar palabra clave"
                                else "Introduce una palabra clave",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color =
                                if (keywordInput.isNotEmpty()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
