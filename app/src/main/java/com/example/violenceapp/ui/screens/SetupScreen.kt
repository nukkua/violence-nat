package com.example.violenceapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.violenceapp.viewmodel.AppViewModel

@Composable
fun SetupScreen(navController: NavController? = null, appViewModel: AppViewModel? = null) {
    val keywordState = appViewModel?.keywordState
    val configState = appViewModel?.configState

    val setupOptions =
            listOf(
                    SetupOption(
                            emoji = "🎤",
                            title = "Configurar Palabra Clave",
                            description =
                                    if (keywordState?.isConfigured == true)
                                            "Configurado: \"${keywordState.keyword}\""
                                    else "Define tu palabra de activación de emergencia",
                            route = "voice_setup"
                    ),
                    SetupOption(
                            emoji = "📞",
                            title = "Contactos de Emergencia",
                            description =
                                    if (configState?.hasEmergencyContacts == true)
                                            "Configurado: ${configState.emergencyContactsCount} contactos"
                                    else "Agrega contactos para alertas automáticas",
                            route = "contacts_setup"
                    ),
                    SetupOption(
                            emoji = "🤖",
                            title = "Bot de Telegram",
                            description =
                                    if (configState?.isTelegramConfigured == true)
                                            "Configurado correctamente"
                                    else "Configura tu bot para recibir alertas",
                            route = "telegram_setup"
                    ),
                    SetupOption(
                            emoji = "🔊",
                            title = "Configuración de Audio",
                            description = "Ajusta sensibilidad y calidad de grabación",
                            route = "audio_setup"
                    )
            )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⚙️", fontSize = 32.sp)

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                    text = "Configuración",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
                text = "Configura tu experiencia de protección",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Lista de opciones de configuración
        LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
        ) {
            items(setupOptions) { option ->
                SetupOptionCard(
                        option = option,
                        onClick = { navController?.navigate(option.route) }
                )
            }

            // Botón de reset al final de la lista
            item {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                        onClick = { appViewModel?.resetAllConfig() },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                )
                ) { Text("🗑️ Resetear toda la configuración") }
            }
        }

        // Espaciado para el navbar flotante
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun SetupOptionCard(option: SetupOption, onClick: () -> Unit) {
    Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon
            Card(
                    modifier = Modifier.size(56.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = option.emoji, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = option.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                        text = option.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                )
            }

            // Arrow indicator
            Text(text = "›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class SetupOption(
        val emoji: String,
        val title: String,
        val description: String,
        val route: String
)
