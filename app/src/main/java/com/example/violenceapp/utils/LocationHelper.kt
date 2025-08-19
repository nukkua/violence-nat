package com.example.violenceapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.app.ActivityCompat
import java.text.DecimalFormat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class LocationHelper(private val context: Context) {

    private val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val decimalFormat = DecimalFormat("#.######")

    /** Verifica si tiene permisos de ubicación */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /** Obtiene la ubicación actual como string - VERSIÓN MEJORADA */
    suspend fun getCurrentLocation(): String? {
        if (!hasLocationPermission()) {
            return "Sin permisos de ubicación"
        }

        return try {
            // Primero intentar obtener ubicación fresca
            val freshLocation = getFreshLocation()

            if (freshLocation != null) {
                formatLocation(freshLocation)
            } else {
                // Fallback a última ubicación conocida
                val lastKnown = getLastKnownLocation()
                if (lastKnown != null) {
                    "${formatLocation(lastKnown)} (ubicación aproximada)"
                } else {
                    "Ubicación no disponible"
                }
            }
        } catch (e: Exception) {
            "Error obteniendo ubicación: ${e.message}"
        }
    }

    /** Obtiene ubicación fresca en tiempo real (nueva función) */
    private suspend fun getFreshLocation(): Location? {
        if (!hasLocationPermission()) return null

        return withTimeoutOrNull(10000) { // Timeout de 10 segundos
            suspendCancellableCoroutine { continuation ->
                var locationReceived = false

                val locationListener =
                        object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (!locationReceived) {
                                    locationReceived = true
                                    locationManager.removeUpdates(this)
                                    continuation.resume(location)
                                }
                            }

                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                            @Deprecated("Deprecated in API level 29")
                            override fun onStatusChanged(
                                    provider: String?,
                                    status: Int,
                                    extras: Bundle?
                            ) {}
                        }

                try {
                    // Intentar GPS primero (más preciso)
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                0L, // Sin delay mínimo
                                0f, // Sin distancia mínima
                                locationListener,
                                Looper.getMainLooper()
                        )
                    }
                    // También intentar Network (más rápido)
                    else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                                LocationManager.NETWORK_PROVIDER,
                                0L,
                                0f,
                                locationListener,
                                Looper.getMainLooper()
                        )
                    } else {
                        continuation.resume(null)
                    }

                    // Cleanup cuando se cancele
                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(locationListener)
                    }
                } catch (e: SecurityException) {
                    continuation.resume(null)
                }
            }
        }
    }

    /** Obtiene la última ubicación conocida (fallback) */
    private fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        val providers =
                listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER
                )

        var bestLocation: Location? = null
        var bestTime = Long.MIN_VALUE
        val maxAge = 5 * 60 * 1000L // 5 minutos máximo

        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null) {
                        val age = System.currentTimeMillis() - location.time

                        // Solo usar ubicaciones recientes
                        if (age < maxAge && location.time > bestTime) {
                            bestLocation = location
                            bestTime = location.time
                        }
                    }
                }
            } catch (e: SecurityException) {
                continue
            } catch (e: Exception) {
                continue
            }
        }

        return bestLocation
    }

    /** Formatea una ubicación a string legible */
    private fun formatLocation(location: Location): String {
        val lat = decimalFormat.format(location.latitude)
        val lon = decimalFormat.format(location.longitude)
        val accuracy = if (location.hasAccuracy()) "${location.accuracy.toInt()}m" else "?"
        val provider = location.provider ?: "unknown"
        val age = (System.currentTimeMillis() - location.time) / 1000

        return """
Lat: $lat, Lon: $lon
Precisión: ±$accuracy ($provider)
Hace: ${age}s
Google Maps: https://maps.google.com/?q=$lat,$lon
        """.trimIndent()
    }

    /** Obtiene coordenadas como par de doubles - VERSIÓN MEJORADA */
    suspend fun getCurrentCoordinates(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null

        return try {
            // Intentar ubicación fresca primero
            val freshLocation = getFreshLocation()

            if (freshLocation != null) {
                Pair(freshLocation.latitude, freshLocation.longitude)
            } else {
                // Fallback a última ubicación conocida
                val lastKnown = getLastKnownLocation()
                if (lastKnown != null) {
                    Pair(lastKnown.latitude, lastKnown.longitude)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Verifica si el GPS está habilitado */
    fun isGPSEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /** Verifica si algún proveedor de ubicación está disponible */
    fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
