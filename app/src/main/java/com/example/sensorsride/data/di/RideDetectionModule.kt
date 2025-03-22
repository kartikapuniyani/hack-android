package com.example.sensorsride.data.di

import android.content.Context
import com.example.sensorsride.data.datasource.LocationRepositoryDataSource
import com.example.sensorsride.data.datasource.SensorDataSource
import com.example.sensorsride.data.datasource.VisionDataSource
import com.example.sensorsride.domain.LocationRepository
import com.example.sensorsride.domain.SensorRepository
import com.example.sensorsride.domain.VisionRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RideDetectionModule {

    @Binds
    @Singleton
    abstract fun bindSensorRepository(sensorDataSource: SensorDataSource) : SensorRepository

    @Binds
    @Singleton
    abstract fun bindVisionRepository(visionDataSource: VisionDataSource) : VisionRepository

}