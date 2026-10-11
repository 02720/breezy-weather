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

package org.breezyweather.sources.cma

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.wrappers.CurrentWrapper
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HalfDayWrapper
import breezyweather.domain.weather.wrappers.HourlyWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.NonFreeNetSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_NONE
import org.breezyweather.sources.cma.json.CmaAlert
import org.breezyweather.sources.cma.json.CmaGridForecastDay
import org.breezyweather.sources.cma.json.CmaGridForecastHalf
import org.breezyweather.sources.cma.json.CmaGridHourly
import org.breezyweather.sources.cma.json.CmaGridLiveElement
import org.breezyweather.sources.common.buildChineseAlertHeadline
import org.breezyweather.sources.common.getCleanChineseAlertTitle
import org.breezyweather.unit.distance.Distance.Companion.kilometers
import org.breezyweather.unit.distance.Distance.Companion.meters
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.ratio.Ratio.Companion.percent
import org.breezyweather.unit.speed.Speed.Companion.metersPerSecond
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named

/**
 * zip() cannot emit a null value, so each request result is wrapped in a holder
 */
private data class CmaGridResult(
    val elements: List<CmaGridLiveElement>?,
    val error: Throwable?,
)

private data class CmaForecastResult(
    val days: List<CmaGridForecastDay>?,
    val hours: List<CmaGridHourly>?,
    val error: Throwable?,
)

/** A rest/gowfs/hour entry with its parsed forecast time */
private data class CmaHourlyPoint(
    val date: Date,
    val hourly: CmaGridHourly,
)

private data class CmaAlertFetchResult(
    val alerts: List<CmaAlert>?,
    /** True when alerts could not be resolved to an administrative area and a distance filter was applied instead */
    val distanceFiltered: Boolean,
    val error: Throwable?,
)

class CmaService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") val client: Retrofit.Builder,
) : HttpSource(), WeatherSource, NonFreeNetSource {

    override val id = "cma"
    override val name = "中国气象数据网"
    override val continent = SourceContinent.ASIA
    override val privacyPolicyUrl = "https://data.cma.cn/"

    private val mApi by lazy {
        client
            .baseUrl(BASE_URL)
            .build()
            .create(CmaApi::class.java)
    }

    private val weatherAttribution = name
    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to weatherAttribution,
        SourceFeature.CURRENT to weatherAttribution,
        SourceFeature.ALERT to weatherAttribution
    )
    override val attributionLinks = mapOf(
        name to "https://data.cma.cn/"
    )

    override val testingLocations: List<Location> = listOf(
        Location(
            city = "Beijing",
            latitude = 39.9042,
            longitude = 116.4074,
            timeZone = TimeZone.getTimeZone("Asia/Shanghai"),
            country = "China",
            countryCode = "CN",
            forecastSource = id,
            currentSource = id,
            alertSource = id
        ),
        Location(
            city = "Lhasa",
            latitude = 29.6520,
            longitude = 91.1721,
            timeZone = TimeZone.getTimeZone("Asia/Shanghai"),
            country = "China",
            countryCode = "CN",
            forecastSource = id,
            currentSource = id,
            alertSource = id
        )
    )

    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return location.countryCode.equals("CN", ignoreCase = true)
    }

    override fun getFeaturePriorityForLocation(
        location: Location,
        feature: SourceFeature,
    ): Int {
        return when {
            isFeatureSupportedForLocation(location, feature) -> PRIORITY_HIGHEST
            else -> PRIORITY_NONE
        }
    }

    // Only supports its own country
    val knownAmbiguousCountryCodes: Array<String>? = arrayOf("CN")

    // WeatherSource
    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()
        val features = requestedFeatures.filter { isFeatureSupportedForLocation(location, it) }
        if (features.isEmpty()) {
            return Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
        }

        val gridObservable: Observable<CmaGridResult> =
            if (SourceFeature.CURRENT in features) {
                mApi.getGridLiveData(location.latitude, location.longitude)
                    .map { result ->
                        if (result.returnCode == "0" && !result.list.isNullOrEmpty()) {
                            CmaGridResult(result.list, null)
                        } else {
                            CmaGridResult(null, InvalidLocationException())
                        }
                    }
                    .onErrorResumeNext { e -> Observable.just(CmaGridResult(null, e)) }
            } else {
                Observable.just(CmaGridResult(null, null))
            }

        val forecastObservable: Observable<CmaForecastResult> =
            if (SourceFeature.FORECAST in features) {
                // The day and hour endpoints are independent: each one is fetched
                // in parallel and can fail without taking the other one down
                Observable.zip(
                    mApi.getGridForecast(location.latitude, location.longitude)
                        .map { result ->
                            if (!result.detail.isNullOrEmpty()) {
                                CmaForecastResult(result.detail, null, null)
                            } else {
                                CmaForecastResult(null, null, InvalidLocationException())
                            }
                        }
                        .onErrorResumeNext { e -> Observable.just(CmaForecastResult(null, null, e)) },
                    mApi.getGridHourlyForecast(location.latitude, location.longitude)
                        .map { result ->
                            if (!result.detail.isNullOrEmpty()) {
                                CmaForecastResult(null, result.detail, null)
                            } else {
                                CmaForecastResult(null, null, InvalidLocationException())
                            }
                        }
                        .onErrorResumeNext { e -> Observable.just(CmaForecastResult(null, null, e)) },
                ) { day, hour ->
                    CmaForecastResult(day.days, hour.hours, day.error ?: hour.error)
                }
            } else {
                Observable.just(CmaForecastResult(null, null, null))
            }

        val alertObservable: Observable<CmaAlertFetchResult> =
            if (SourceFeature.ALERT in features) {
                getAlertObservable(location)
            } else {
                Observable.just(CmaAlertFetchResult(null, false, null))
            }

        return Observable.zip(gridObservable, forecastObservable, alertObservable) { grid, forecast, alerts ->
            var dailyForecast: List<DailyWrapper>? = null
            var hourlyForecast: List<HourlyWrapper>? = null
            var current: CurrentWrapper? = null
            var alertList: List<Alert>? = null

            if (SourceFeature.FORECAST in features) {
                // The day and hour series are converted independently: the feature
                // is only reported as failed when neither one could be built
                dailyForecast = forecast.days?.let(::getDailyList)
                hourlyForecast = forecast.hours?.let(::getHourlyList)
                if (dailyForecast == null && hourlyForecast == null) {
                    failedFeatures[SourceFeature.FORECAST] =
                        forecast.error ?: InvalidLocationException()
                }
            }

            if (SourceFeature.CURRENT in features) {
                if (grid.elements == null) {
                    failedFeatures[SourceFeature.CURRENT] =
                        grid.error ?: InvalidLocationException()
                } else {
                    current = getCurrent(grid.elements)
                }
            }

            if (SourceFeature.ALERT in features) {
                if (alerts.alerts == null) {
                    failedFeatures[SourceFeature.ALERT] = alerts.error ?: RuntimeException()
                } else {
                    alertList = getAlertList(alerts.alerts, alerts.distanceFiltered, location)
                }
            }

            WeatherWrapper(
                dailyForecast = dailyForecast,
                hourlyForecast = hourlyForecast,
                current = current,
                alertList = alertList,
                failedFeatures = failedFeatures
            )
        }
    }

    /**
     * Alerts applicable to a location, queried the same way the official website
     * does: by administrative area (province + county codes resolved through
     * reverse geocoding). When the area cannot be resolved or the area-scoped
     * query fails, falls back to a nationwide query filtered by distance.
     */
    private fun getAlertObservable(
        location: Location,
    ): Observable<CmaAlertFetchResult> {
        return mApi.getRegeo(location = "${location.longitude},${location.latitude}")
            .flatMap { result ->
                fetchAreaAlerts(result.regeocode?.addressComponent?.adcode)
            }
            .onErrorResumeNext { fetchNationwideAlerts() }
    }

    private fun fetchAreaAlerts(
        adcode: String?,
    ): Observable<CmaAlertFetchResult> {
        val provinceCode = adcode?.let(::getCmaProvinceCode)
        if (adcode == null || provinceCode == null || adcode in UNRESOLVABLE_AREA_CODES) {
            return fetchNationwideAlerts()
        }
        return mApi.getEffectiveAlerts(areaCode = "$provinceCode,$adcode")
            .flatMap { result ->
                if (result.code == "200") {
                    Observable.just(CmaAlertFetchResult(result.data.orEmpty(), false, null))
                } else {
                    fetchNationwideAlerts()
                }
            }
            .onErrorResumeNext { fetchNationwideAlerts() }
    }

    private fun fetchNationwideAlerts(): Observable<CmaAlertFetchResult> {
        return mApi.getEffectiveAlerts(areaCode = NATIONWIDE_AREA_CODE)
            .map { result ->
                if (result.code == "200") {
                    CmaAlertFetchResult(result.data.orEmpty(), true, null)
                } else {
                    CmaAlertFetchResult(null, true, RuntimeException(result.message))
                }
            }
            .onErrorResumeNext { e ->
                Observable.just(CmaAlertFetchResult(null, true, e))
            }
    }

    private fun getDailyList(
        days: List<CmaGridForecastDay>,
    ): List<DailyWrapper>? {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            isLenient = false
        }
        return days.take(MAX_FORECAST_DAYS)
            .mapNotNull { day ->
                val date = try {
                    dateFormatter.parse(day.date ?: return@mapNotNull null)
                } catch (e: ParseException) {
                    null
                } ?: return@mapNotNull null
                DailyWrapper(
                    date = date,
                    day = day.day?.let(::getHalfDay),
                    night = day.night?.let(::getHalfDay)
                )
            }
            .takeIf { it.isNotEmpty() }
    }

    private fun getHalfDay(
        half: CmaGridForecastHalf,
    ): HalfDayWrapper {
        return HalfDayWrapper(
            weatherText = half.weather?.info
                ?.takeIf { it.isNotBlank() && !it.cmaMissingValue() },
            weatherCode = getCmaWeatherCode(half.weather?.img?.trim()?.toIntOrNull()),
            temperature = TemperatureWrapper(
                temperature = half.weather?.temperature
                    ?.trim()
                    ?.toDoubleOrNull()
                    ?.cmaSanitized(-100.0, 100.0)
                    ?.celsius
            ),
            wind = Wind(
                degree = half.wind?.direct
                    ?.trim()
                    ?.toDoubleOrNull()
                    ?.cmaSanitized(0.0, 360.0),
                speed = half.wind?.power
                    ?.trim()
                    ?.toDoubleOrNull()
                    ?.cmaSanitized(0.0, 200.0)
                    ?.metersPerSecond
            )
        )
    }

    /**
     * Hourly forecast from rest/gowfs/hour. The endpoint reports every hour for
     * the first 24 hours then every 3 hours, so the series is walked hour by
     * hour and each missing hour is linearly interpolated between the two
     * surrounding reported points.
     */
    private fun getHourlyList(
        hours: List<CmaGridHourly>,
    ): List<HourlyWrapper>? {
        val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            isLenient = false
        }
        val points = hours.mapNotNull { hourly ->
            val date = try {
                timeFormatter.parse(hourly.time ?: return@mapNotNull null)
            } catch (e: ParseException) {
                null
            } ?: return@mapNotNull null
            CmaHourlyPoint(date, hourly)
        }.sortedBy { it.date.time }
        if (points.isEmpty()) return null

        val hourlyList = mutableListOf<HourlyWrapper>()
        var index = 0
        var time = points.first().date.time
        val endTime = points.last().date.time
        while (time <= endTime) {
            // Move forward to the interval containing this hour
            while (index < points.size - 2 && points[index + 1].date.time < time) {
                index++
            }
            val previous = points[index]
            val next = points.getOrNull(index + 1) ?: previous
            val spanMillis = (next.date.time - previous.date.time).toDouble()
            val ratio = if (spanMillis > 0.0) (time - previous.date.time) / spanMillis else 0.0
            hourlyList.add(
                buildHourly(Date(time), previous.hourly, next.hourly, ratio, spanMillis / HOUR_IN_MILLIS)
            )
            time += HOUR_IN_MILLIS
        }
        return hourlyList
    }

    private fun buildHourly(
        date: Date,
        previous: CmaGridHourly,
        next: CmaGridHourly,
        ratio: Double,
        /** Number of hours the two reported points are apart */
        intervalHours: Double,
    ): HourlyWrapper {
        // Categorical data is not interpolated: take the nearest reported point
        val nearest = if (ratio <= 0.5) previous else next
        // The reported precipitation is the accumulation over the whole interval,
        // carried by its end point, so it is spread evenly over the hours it covers
        val precipitation = next.totalRain
            ?.trim()
            ?.toDoubleOrNull()
            ?.cmaSanitized(0.0, 500.0)
            ?.div(maxOf(intervalHours, 1.0))
        return HourlyWrapper(
            date = date,
            weatherText = nearest.weatherInfo
                ?.takeIf { it.isNotBlank() && !it.cmaMissingValue() },
            weatherCode = getCmaWeatherCode(nearest.weatherIcon?.trim()?.toIntOrNull()),
            temperature = TemperatureWrapper(
                temperature = cmaInterpolate(
                    previous.temperature?.toDoubleOrNull()?.cmaSanitized(-100.0, 100.0),
                    next.temperature?.toDoubleOrNull()?.cmaSanitized(-100.0, 100.0),
                    ratio,
                )?.celsius
            ),
            precipitation = precipitation?.let { Precipitation(total = it.millimeters) },
            wind = Wind(
                degree = cmaInterpolateDirection(
                    previous.windDirection?.cmaSanitized(0.0, 360.0),
                    next.windDirection?.cmaSanitized(0.0, 360.0),
                    ratio,
                ),
                speed = cmaInterpolate(
                    previous.windSpeed?.cmaSanitized(0.0, 200.0),
                    next.windSpeed?.cmaSanitized(0.0, 200.0),
                    ratio,
                )?.metersPerSecond
            ),
            relativeHumidity = cmaInterpolate(
                previous.humidity?.cmaSanitized(0.0, 100.0),
                next.humidity?.cmaSanitized(0.0, 100.0),
                ratio,
            )?.percent,
            cloudCover = cmaInterpolate(
                previous.totalCloud?.cmaSanitized(0.0, 100.0),
                next.totalCloud?.cmaSanitized(0.0, 100.0),
                ratio,
            )?.percent,
            visibility = cmaInterpolate(
                previous.visibility?.cmaSanitized(0.0, 100.0),
                next.visibility?.cmaSanitized(0.0, 100.0),
                ratio,
            )?.kilometers
        )
    }

    private fun getElementValue(
        elements: List<CmaGridLiveElement>,
        elementName: String,
        min: Double,
        max: Double,
    ): Double? {
        return elements.firstOrNull { it.fastEle == elementName }
            ?.value?.toDoubleOrNull()?.cmaSanitized(min, max)
    }

    private fun getCurrent(elements: List<CmaGridLiveElement>): CurrentWrapper {
        return CurrentWrapper(
            weatherCode = getCmaWeatherCode(getElementValue(elements, "WEA", -1.0, 99.0)?.toInt()),
            temperature = TemperatureWrapper(
                temperature = getElementValue(elements, "TEM", -100.0, 100.0)?.celsius
            ),
            wind = Wind(
                degree = getElementValue(elements, "WIND", 0.0, 360.0),
                speed = getElementValue(elements, "WINS", 0.0, 200.0)?.metersPerSecond
            ),
            relativeHumidity = getElementValue(elements, "RHU", 0.0, 100.0)?.percent,
            cloudCover = getElementValue(elements, "TCDC", 0.0, 100.0)?.percent,
            visibility = getElementValue(elements, "VIS", 0.0, 100_000.0)?.meters
        )
    }

    private fun getAlertList(
        alerts: List<CmaAlert>,
        distanceFiltered: Boolean,
        location: Location,
    ): List<Alert> {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            isLenient = false
        }
        var candidates = alerts.asSequence()
            .filter { it.status == "Actual" }
        if (distanceFiltered) {
            candidates = candidates
                .filter { it.lat != null && it.lon != null }
                .filter {
                    getCmaDistanceKm(location.latitude, location.longitude, it.lat!!, it.lon!!) <=
                        ALERT_DISTANCE_THRESHOLD_KM
                }
        }
        return candidates
            .mapNotNull { alert ->
                // The website drops alerts with an unknown severity: do the same
                val severity = getCmaAlertSeverity(alert.severity) ?: return@mapNotNull null
                Alert(
                    alertId = alert.identifier
                        ?: Objects.hash(alert.headline, alert.severity, alert.effective).toString(),
                    startDate = parseDate(dateFormatter, alert.effective),
                    endDate = parseDate(dateFormatter, alert.expires),
                    // Concise "{type}{level}预警" title like on the official website,
                    // e.g. "雷电黄色预警" instead of the verbose raw headline
                    headline = buildChineseAlertHeadline(
                        alert.eventTypeCN,
                        getCmaAlertLevelName(alert.severity)
                    )
                        ?: getCleanChineseAlertTitle(alert.headline)
                        ?: alert.headline?.trim()?.ifEmpty { null },
                    description = alert.description?.trim()?.ifEmpty { null },
                    source = alert.senderName,
                    severity = severity,
                    color = Alert.colorFromSeverity(severity)
                )
            }
            .toList()
    }

    private fun parseDate(
        formatter: SimpleDateFormat,
        text: String?,
    ): Date? {
        if (text == null) return null
        return try {
            formatter.parse(text)
        } catch (e: ParseException) {
            null
        }
    }

    companion object {
        private const val BASE_URL = "https://data.cma.cn/"
        private const val NATIONWIDE_AREA_CODE = "100000"

        // Sentinel codes returned by reverse geocoding for points that cannot be
        // placed in a county: "100000" (nationwide) and "900000" (unpopulated area)
        private val UNRESOLVABLE_AREA_CODES = setOf("100000", "900000")

        private const val ALERT_DISTANCE_THRESHOLD_KM = 100.0

        // The endpoint returns up to 30 days, but wind is only published for the
        // first 10; keep a comfortable horizon beyond it
        private const val MAX_FORECAST_DAYS = 16

        private const val HOUR_IN_MILLIS = 3600_000L
    }
}
