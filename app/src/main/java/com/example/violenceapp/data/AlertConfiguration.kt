package com.example.violenceapp.data

/** Configuración de alertas para Telegram */
data class AlertConfiguration(
        // Palabra clave que activa la alerta
        val triggerWord: String = "alerta",

        // Plantilla del mensaje de emergencia
        val messageTemplate: String =
                """
🚨 ALERTA DE EMERGENCIA 🚨

¡NECESITO AYUDA URGENTE!

📍 Ubicación: {location}
🕐 Hora: {time}
🔊 Activado por: "{trigger}"

⚠️ Por favor contacta a las autoridades si no respondo en 15 minutos.

🤖 Este mensaje fue enviado automáticamente por el sistema de reconocimiento de voz.
    """.trimIndent(),

        // Estado del sistema de alertas
        val isEnabled: Boolean = true
)
