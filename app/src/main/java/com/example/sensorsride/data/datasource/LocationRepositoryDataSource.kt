package com.example.sensorsride.data.datasource

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.example.sensorsride.domain.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class LocationRepositoryDataSource @Inject constructor(
    private val context: Context,
    private val fusedLocationProvider: FusedLocationProviderClient
) : LocationRepository {

    override suspend fun getLastUpdatedLocation(): Flow<Location?> = flow {
        // Emit null if permissions are not granted
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            emit(null)
            return@flow
        }
        // Request location and emit it
        val location = suspendCancellableCoroutine<Location?> { continuation ->
            fusedLocationProvider.getCurrentLocation(
                android.location.LocationRequest.QUALITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                continuation.resume(location)
            }.addOnFailureListener { exception ->
                continuation.resumeWith(Result.failure(exception))
            }
        }
        emit(location)
    }.catch { e ->
        emit(null)
    }
}