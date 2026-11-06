package com.aero.bar.di

import com.aero.bar.data.repository.AppRepositoryImpl
import com.aero.bar.data.repository.QingganVehicleHvacRepositoryImpl
import com.aero.bar.data.repository.RecentAppsRepositoryImpl
import com.aero.bar.domain.repository.AppRepository
import com.aero.bar.domain.repository.RecentAppsRepository
import com.aero.bar.domain.repository.VehicleHvacRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository

    @Binds
    @Singleton
    abstract fun bindRecentAppsRepository(impl: RecentAppsRepositoryImpl): RecentAppsRepository

    @Binds
    @Singleton
    abstract fun bindVehicleHvacRepository(impl: QingganVehicleHvacRepositoryImpl): VehicleHvacRepository
}
