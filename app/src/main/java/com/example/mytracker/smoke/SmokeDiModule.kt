package com.example.mytracker.smoke

import com.example.mytracker.core.backup.BackupExportProvider
import com.example.mytracker.core.metrics.MetricSeriesProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface SmokeDiModule {
    @Binds
    @IntoSet
    fun bindSmokeSessionsExportProvider(impl: SmokeSessionsExportProvider): BackupExportProvider

    @Binds
    @IntoSet
    fun bindSmokeSessionCountMetricSeriesProvider(
        impl: SmokeSessionCountMetricSeriesProvider,
    ): MetricSeriesProvider

    @Binds
    @IntoSet
    fun bindSmokePuffsMetricSeriesProvider(impl: SmokePuffsMetricSeriesProvider): MetricSeriesProvider
}
