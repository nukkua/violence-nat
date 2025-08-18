package com.example.violenceapp.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.violenceapp.ui.screens.ContactsSetupScreen
import com.example.violenceapp.ui.screens.HomeScreen
import com.example.violenceapp.ui.screens.SetupScreen
import com.example.violenceapp.ui.screens.VoiceSetupScreen
import com.example.violenceapp.viewmodel.AppViewModel

// Rutas de navegación
sealed class Screen(val route: String, val title: String, val emoji: String) {
    object Home : Screen("home", "Inicio", "🏠")
    object Setup : Screen("setup", "Configurar", "⚙️")

    // Pantallas de configuración específicas (no aparecen en navbar)
    object VoiceSetup : Screen("voice_setup", "Configurar Voz", "🎤")
    object ContactsSetup : Screen("contacts_setup", "Contactos", "📞")
}

@Composable
fun ViolenceAppNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Rutas donde NO se debe mostrar el navbar
    val routesWithoutNavbar = listOf(Screen.VoiceSetup.route, Screen.ContactsSetup.route)

    val showNavbar = currentRoute !in routesWithoutNavbar

    Box(modifier = Modifier.fillMaxSize()) {
        // Contenido principal
        NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Home.route) {
                HomeScreen(navController = navController, appViewModel = viewModel)
            }
            composable(Screen.Setup.route) {
                SetupScreen(navController = navController, appViewModel = viewModel)
            }
            composable(Screen.VoiceSetup.route) {
                VoiceSetupScreen(navController = navController, appViewModel = viewModel)
            }
            composable(Screen.ContactsSetup.route) {
                ContactsSetupScreen(navController = navController, appViewModel = viewModel)
            }
        }

        // Navbar flotante (solo mostrar en ciertas pantallas)
        if (showNavbar) {
            FloatingBottomNavBar(
                    navController = navController,
                    modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun FloatingBottomNavBar(navController: NavController, modifier: Modifier = Modifier) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(Screen.Home, Screen.Setup)

    Card(
            modifier = modifier.fillMaxWidth(0.9f).padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                NavBarItem(
                        screen = screen,
                        isSelected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun NavBarItem(
        screen: Screen,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Card(
            onClick = onClick,
            modifier = modifier.height(56.dp).padding(horizontal = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                    ),
            elevation =
                    CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Text(text = screen.emoji, fontSize = 20.sp)

            Text(
                    text = screen.title,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
            )
        }
    }
}
