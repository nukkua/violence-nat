package com.example.violenceapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import java.text.DecimalFormat

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

    /** Obtiene la ubicación actual como string */
    fun getCurrentLocation(): String? {
        if (!hasLocationPermission()) {
            return "Sin permisos de ubicación"
        }

        return try {
            val location = getLastKnownLocation()
            if (location != null) {
                val lat = decimalFormat.format(location.latitude)
                val lon = decimalFormat.format(location.longitude)
                "Lat: $lat, Lon: $lon\nGoogle Maps: https://maps.google.com/?q=$lat,$lon"
            } else {
                "Ubicación no disponible"
            }
        } catch (e: Exception) {
            "Error obteniendo ubicación: ${e.message}"
        }
    }

    /** Obtiene la última ubicación conocida */
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

        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null && location.time > bestTime) {
                        bestLocation = location
                        bestTime = location.time
                    }
                }
            } catch (e: SecurityException) {
                // Sin permisos
                continue
            } catch (e: Exception) {
                // Proveedor no disponible
                continue
            }
        }

        return bestLocation
    }

    /** Obtiene coordenadas como par de doubles */
    fun getCurrentCoordinates(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null

        return try {
            val location = getLastKnownLocation()
            if (location != null) {
                Pair(location.latitude, location.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
