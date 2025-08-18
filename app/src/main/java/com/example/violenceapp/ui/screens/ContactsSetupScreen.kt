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
fun ContactsSetupScreen(navController: NavController? = null, appViewModel: AppViewModel? = null) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header con botón de regreso
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { navController?.popBackStack() }) { Text("← Atrás") }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                    text = "📞 Contactos",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Placeholder content
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        ) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "👥", fontSize = 48.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                        text = "Contactos de Emergencia",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )

                Text(
                        text = "Próximamente: gestión de contactos de emergencia",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Espaciado para el navbar flotante
        Spacer(modifier = Modifier.height(100.dp))
    }
}
