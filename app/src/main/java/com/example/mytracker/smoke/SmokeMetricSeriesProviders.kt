package com.example.mytracker.smoke

import com.example.mytracker.core.metrics.EpochDayRange
import com.example.mytracker.core.metrics.MetricAggregation
import com.example.mytracker.core.metrics.MetricPoint
import com.example.mytracker.core.metrics.MetricSeriesDescriptor
import com.example.mytracker.core.metrics.MetricSeriesProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * How often per day. [MetricAggregation.SUM] rather than AVERAGE: bucketed into a week, the number
 * anyone wants is how many sessions that week held, not what an average day of it looked like.
 */
class SmokeSessionCountMetricSeriesProvider @Inject constructor(
    private val smokeRepository: SmokeRepository,
) : MetricSeriesProvider {
    override fun descriptor() = MetricSeriesDescriptor(
        id = "smoke_sessions_per_day",
        displayName = "Smoke-Sessions",
        unit = "Sessions",
        category = "Smoken",
        aggregation = MetricAggregation.SUM,
    )

    override fun getSeries(range: EpochDayRange): Flow<List<MetricPoint>> =
        smokeRepository.observeDailySessionCounts(range.startInclusive, range.endInclusive)
}

/**
 * Züge per day — only the ones that were counted, see [SmokeDao.observeDailyPuffs]. A separate
 * series rather than a second value on the one above, because the two have different units and a
 * day can carry either without the other.
 */
class SmokePuffsMetricSeriesProvider @Inject constructor(
    private val smokeRepository: SmokeRepository,
) : MetricSeriesProvider {
    override fun descriptor() = MetricSeriesDescriptor(
        id = "smoke_puffs_per_day",
        displayName = "Züge",
        unit = "Züge",
        category = "Smoken",
        aggregation = MetricAggregation.SUM,
    )

    override fun getSeries(range: EpochDayRange): Flow<List<MetricPoint>> =
        smokeRepository.observeDailyPuffs(range.startInclusive, range.endInclusive)
}
