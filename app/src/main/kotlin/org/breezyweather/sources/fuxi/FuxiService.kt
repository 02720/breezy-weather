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

package org.breezyweather.sources.fuxi

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
import io.reactivex.rxjava3.core.Observable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.exceptions.WeatherException
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.sources.fuxi.json.FuxiStep
import org.breezyweather.sources.fuxi.json.FuxiWeatherInfoResult
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.speed.Speed.Companion.metersPerSecond
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * FuXi (伏羲天气) service.
 *
 * Worldwide hourly forecast from the medium-range AI weather model 伏羲中期
 * (FuXi-C88) of the FuXi project (Fudan University / Shanghai AI Laboratory),
 * fetched from the public interfaces of the official visualization website
 * https://fuxi-ai.cn/visual/weather. No API key is required.
 */
class FuxiService @Inject constructor(
    @Named("JsonClient") client: Retrofit.Builder,
) : HttpSource(), WeatherSource {

    override val id = "fuxi"
    override val name = "伏羲天气"
    override val continent = SourceContinent.WORLDWIDE
    // The fuxi-ai.cn website does not publish a dedicated privacy policy page,
    // so this links to the operator's official website
    override val privacyPolicyUrl = "https://fuxi-ai.cn/"
    override val attributionLinks = mapOf(
        name to "https://fuxi-ai.cn/visual/weather"
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
            .create(FuxiApi::class.java)
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
        return mApi.getWeatherTiles()
            .flatMap { tiles ->
                val startTime = tiles.data
                    ?.firstOrNull { it.forecastType == FORECAST_TYPE_MEDIUM_RANGE }
                    ?.startTime
                    // Guards against a malformed base time silently producing
                    // a wrong timeline, as lenient parsing would still succeed
                    ?.takeIf { it.length == 10 }
                if (tiles.msgCode != MSG_CODE_OK || startTime == null) {
                    // No medium-range model run published at the moment
                    failedFeatures[SourceFeature.FORECAST] = WeatherException()
                    Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
                } else {
                    // Body: {"lat":<lat>,"lon":<lon>,"forecastType":"1"}
                    val body = """{"lat":${location.latitude},""" +
                        """"lon":${normalizeLongitude(location.longitude)},""" +
                        """"forecastType":"$FORECAST_TYPE_MEDIUM_RANGE"}"""
                    mApi.getWeatherInfo(body.toRequestBody("application/json".toMediaTypeOrNull()))
                        .map { info ->
                            convert(startTime, info, location, failedFeatures)
                        }
                }
            }
            .onErrorResumeNext { e ->
                failedFeatures[SourceFeature.FORECAST] = e
                Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
            }
    }

    private fun convert(
        startTime: String,
        result: FuxiWeatherInfoResult,
        location: Location,
        failedFeatures: MutableMap<SourceFeature, Throwable>,
    ): WeatherWrapper {
        if (result.msgCode != MSG_CODE_OK) {
            failedFeatures[SourceFeature.FORECAST] = WeatherException()
            return WeatherWrapper(failedFeatures = failedFeatures)
        }
        val baseTime = startTime.toModelRunDate() ?: run {
            failedFeatures[SourceFeature.FORECAST] = WeatherException()
            return WeatherWrapper(failedFeatures = failedFeatures)
        }
        // Steps are expected in ascending order, but sorting is cheap insurance:
        // the daily grouping below relies on the order of the hourly forecast
        val hourlyForecast = result.data?.weatherInfoList.orEmpty()
            .sortedBy { it.step }
            .mapNotNull { step ->
                getHourly(baseTime, step)
            }
        if (hourlyForecast.isEmpty()) {
            // The API answers with a null "data" for locations without coverage
            failedFeatures[SourceFeature.FORECAST] = InvalidLocationException()
            return WeatherWrapper(failedFeatures = failedFeatures)
        }
        return WeatherWrapper(
            dailyForecast = getDailyList(hourlyForecast, location),
            hourlyForecast = hourlyForecast,
            failedFeatures = failedFeatures
        )
    }

    /**
     * Each step N of the model output is the hourly forecast valid at
     * N hours after the base time of the model run (both in UTC), matching
     * the time axis of the official website
     */
    private fun getHourly(
        baseTime: Date,
        step: FuxiStep,
    ): HourlyWrapper? {
        val stepCount = step.step ?: return null
        val u = step.u10m?.toDoubleOrNull()
        val v = step.v10m?.toDoubleOrNull()
        return HourlyWrapper(
            date = Date(baseTime.time + stepCount * HOUR_IN_MILLIS),
            temperature = TemperatureWrapper(
                temperature = step.t2m?.toDoubleOrNull()?.celsius
            ),
            precipitation = step.tp?.toDoubleOrNull()?.let {
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
     * The website normalizes longitudes to the [-180, 180) range before
     * querying a point, so any longitude value is accepted
     */
    private fun normalizeLongitude(longitude: Double): Double {
        return (longitude + 180.0).mod(360.0) - 180.0
    }

    /**
     * Parses a model run base time, e.g. "2026092412" for 2026-09-24 12:00 UTC
     */
    private fun String.toModelRunDate(): Date? {
        return try {
            SimpleDateFormat("yyyyMMddHH", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(this)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val BASE_URL = "https://fuxi-ai.cn/"

        /** forecastType of the medium-range model 伏羲中期 (FuXi-C88) */
        private const val FORECAST_TYPE_MEDIUM_RANGE = "1"

        /** The API reports "ok" through this code, whatever the HTTP status is */
        private const val MSG_CODE_OK = "10000"

        private const val HOUR_IN_MILLIS = 3600_000L
    }
}
