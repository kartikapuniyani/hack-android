package com.example.sensorsride.base

import com.example.sensorsride.data.DetectionApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiInterfaceProvider {

    @Provides
    fun provideHomeApiService(@DefaultRetrofit retrofit: Retrofit): DetectionApiService =
        retrofit.create(DetectionApiService::class.java)


}