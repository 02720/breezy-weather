/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.sources.fengwu

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HourlyWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.R
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.exceptions.WeatherException
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.preference.EditTextPreference
import org.breezyweather.common.preference.Preference
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.sources.fengwu.json.FengwuResult
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.speed.Speed.Companion.metersPerSecond
import org.breezyweather.unit.temperature.Temperature.Companion.kelvin
import retrofit2.Retrofit
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Fengwu (相风科技) service.
 *
 * Worldwide forecast from the medium-range AI weather model FengWu-GHR-9km
 * (风乌, Shanghai AI Laboratory), fetched from the public interfaces of the
 * query website https://fengwuai.com/simple-query.
 *
 * No API key is required for a limited horizon (about 7 days); entering a
 * personal API key extends it up to the full length of the model run.
 */
class FengwuService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") client: Retrofit.Builder,
) : HttpSource(), WeatherSource, ConfigurableSource {

    override val id = "fengwu"
    override val name = "相风科技"
    override val continent = SourceContinent.WORLDWIDE
    override val privacyPolicyUrl = "https://fengwuai.com/"
    override val attributionLinks = mapOf(
        name to "https://fengwuai.com/simple-query"
    )

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to name
    )

    // Locations to test in the debug version
    override val testingLocations: List<Location> = listOf(
        Location(
            city = "Beijing",
            latitude = 39.9042,
            longitude = 116.4074,
            timeZone = TimeZone.getTimeZone("Asia/Shanghai"),
            country = "China",
            countryCode = "CN",
            forecastSource = id
        ),
        Location(
            city = "New York",
            latitude = 40.7128,
            longitude = -74.0060,
            timeZone = TimeZone.getTimeZone("America/New_York"),
            country = "United States",
            countryCode = "US",
            forecastSource = id
        )
    )

    private val mApi by lazy {
        client
            .baseUrl(BASE_URL)
            .build()
            .create(FengwuApi::class.java)
    }

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()
        if (SourceFeature.FORECAST !in requestedFeatures) {
            return Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
        }
        return mApi.getAvailability(MODEL_TYPE, REGION)
            .flatMap { availability ->
                val forecastTime = availability.apiEndTime?.takeIf { it.length == 20 }
                if (forecastTime == null) {
                    // No model run available at the moment
                    failedFeatures[SourceFeature.FORECAST] = WeatherException()
                    Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
                } else {
                    val request = if (apikey.isNotEmpty()) {
                        mApi.getQuery(
                            authorization = "Bearer $apikey",
                            longitude = location.longitude,
                            latitude = location.latitude,
                            variables = VARIABLES,
                            modelType = MODEL_TYPE,
                            region = REGION,
                            forecastTime = forecastTime
                        )
                    } else {
                        mApi.getVisualQuery(
                            longitude = location.longitude,
                            latitude = location.latitude,
                            variables = VARIABLES,
                            modelType = MODEL_TYPE,
                            region = REGION,
                            forecastTime = forecastTime
                        )
                    }
                    request.map { result ->
                        convert(result, location, failedFeatures)
                    }
                }
            }
            .onErrorResumeNext { e ->
                failedFeatures[SourceFeature.FORECAST] = e
                Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
            }
    }

    private fun convert(
        result: FengwuResult,
        location: Location,
        failedFeatures: MutableMap<SourceFeature, Throwable>,
    ): WeatherWrapper {
        val points = result.data.orEmpty().mapNotNull { step ->
            val date = step.time?.toUtcDate() ?: return@mapNotNull null
            val values = step.values.orEmpty()
            FengwuPoint(
                date = date,
                temperature = values["t2m"],
                // tp6h is the precipitation accumulated over the preceding 6 hours:
                // turn it into an average hourly rate before interpolating
                precipitationRate = values["tp6h"]?.let { it / ACCUMULATION_HOURS },
                u = values["u10"],
                v = values["v10"]
            )
        }.sortedBy { it.date.time }
        if (points.isEmpty()) {
            // The API answers with an empty "data" for locations without coverage
            failedFeatures[SourceFeature.FORECAST] = InvalidLocationException()
            return WeatherWrapper(failedFeatures = failedFeatures)
        }
        val hourlyForecast = interpolateHourly(points)
        return WeatherWrapper(
            dailyForecast = getDailyList(hourlyForecast, location),
            hourlyForecast = hourlyForecast,
            failedFeatures = failedFeatures
        )
    }

    /**
     * The model outputs one point every 3 hours. Linearly interpolate every
     * requested variable between two consecutive points to build an hourly series.
     * Wind components are interpolated rather than speed/direction to keep the
     * circular direction continuous.
     */
    private fun interpolateHourly(
        points: List<FengwuPoint>,
    ): List<HourlyWrapper> {
        if (points.size == 1) {
            val point = points.first()
            return listOf(buildHourly(point.date, point, point, 0.0))
        }
        val hourly = mutableListOf<HourlyWrapper>()
        val startTime = points.first().date.time
        val endTime = points.last().date.time
        var index = 0
        var time = startTime
        while (time <= endTime) {
            while (index < points.size - 2 && points[index + 1].date.time < time) {
                index++
            }
            val previous = points[index]
            val next = points[index + 1]
            val span = (next.date.time - previous.date.time).toDouble()
            val ratio = if (span > 0) (time - previous.date.time) / span else 0.0
            hourly.add(buildHourly(Date(time), previous, next, ratio))
            time += HOUR_IN_MILLIS
        }
        return hourly
    }

    private fun buildHourly(
        date: Date,
        previous: FengwuPoint,
        next: FengwuPoint,
        ratio: Double,
    ): HourlyWrapper {
        val u = interpolate(previous.u, next.u, ratio)
        val v = interpolate(previous.v, next.v, ratio)
        val precipitationRate = interpolate(previous.precipitationRate, next.precipitationRate, ratio)
        return HourlyWrapper(
            date = date,
            temperature = TemperatureWrapper(
                temperature = interpolate(previous.temperature, next.temperature, ratio)?.kelvin
            ),
            // The rate is in mm/h, so it is also the accumulation over the next hour
            precipitation = precipitationRate?.let {
                Precipitation(total = it.millimeters)
            },
            wind = if (u != null && v != null) {
                // Wind speed and direction from the u/v components, following
                // the meteorological convention (direction the wind blows from)
                Wind(
                    degree = (180.0 + 180.0 / Math.PI * atan2(u, v)).mod(360.0),
                    speed = sqrt(u * u + v * v).metersPerSecond
                )
            } else {
                null
            }
        )
    }

    private fun interpolate(
        previous: Double?,
        next: Double?,
        ratio: Double,
    ): Double? = when {
        previous == null -> next
        next == null -> previous
        else -> previous + (next - previous) * ratio
    }

    /**
     * Returns one DailyWrapper per local day covered by the hourly forecast, with only the date set.
     * The app computes the rest of the daily data from the hourly forecast.
     */
    private fun getDailyList(
        hourlyForecast: List<HourlyWrapper>,
        location: Location,
    ): List<DailyWrapper> {
        val dailyList = mutableListOf<DailyWrapper>()
        var lastDayDate: Date? = null
        for (hourly in hourlyForecast) {
            // Day boundaries are at local midnight
            val dayDate = hourly.date
                .toCalendarWithTimeZone(location.timeZone)
                .apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
            if (lastDayDate == null || dayDate.time != lastDayDate.time) {
                dailyList.add(DailyWrapper(date = dayDate))
                lastDayDate = dayDate
            }
        }
        return dailyList
    }

    /**
     * Parses a UTC ISO 8601 time, e.g. "2026-09-30T07:00:00Z"
     */
    private fun String.toUtcDate(): Date? {
        return try {
            Date.from(Instant.parse(this))
        } catch (_: Exception) {
            null
        }
    }

    // CONFIG
    private val config = SourceConfigStore(context, id)
    private var apikey: String
        set(value) {
            config.edit().putString("apikey", value).apply()
        }
        get() = config.getString("apikey", null) ?: ""

    // The source works without an API key, which just limits the forecast horizon
    override val isConfigured
        get() = true

    override val isRestricted
        get() = false

    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            EditTextPreference(
                titleId = R.string.settings_weather_source_fengwu_api_key,
                summary = { c, content ->
                    content.ifEmpty {
                        c.getString(R.string.settings_source_default_value)
                    }
                },
                content = apikey,
                onValueChanged = {
                    apikey = it
                }
            )
        )
    }

    /**
     * One sample of the model output, in the raw units returned by the API
     */
    private data class FengwuPoint(
        val date: Date,
        /** 2 m temperature, in K */
        val temperature: Double?,
        /** Average precipitation rate over the accumulation window, in mm/h */
        val precipitationRate: Double?,
        /** 10 m eastward wind component, in m/s */
        val u: Double?,
        /** 10 m northward wind component, in m/s */
        val v: Double?
    )

    companion object {
        private const val BASE_URL = "https://fengwuai.com/"

        /** Medium-range model 风乌 GHR 9km */
        private const val MODEL_TYPE = "FengWu-GHR-9km"

        /** The global region is the only one that accepts coordinates worldwide */
        private const val REGION = "global"

        /** Variables requested from the API, in mm/h or m/s or K */
        private const val VARIABLES = "u10,v10,t2m,tp6h"

        /** tp6h is the precipitation accumulated over the preceding 6 hours */
        private const val ACCUMULATION_HOURS = 6.0

        private const val HOUR_IN_MILLIS = 3600_000L
    }
}