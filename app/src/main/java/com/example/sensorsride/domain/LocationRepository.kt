package com.example.sensorsride.domain

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {

    suspend fun getLastUpdatedLocation(): Flow<Location?>
}