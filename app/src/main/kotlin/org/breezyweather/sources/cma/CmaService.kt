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
import breezyweather.domain.weather.model.AlertSeverity
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.wrappers.CurrentWrapper
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HalfDayWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.LocationParametersSource
import org.breezyweather.common.source.NonFreeNetSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_NONE
import org.breezyweather.sources.cma.json.CmaAlert
import org.breezyweather.sources.cma.json.CmaForecastItem
import org.breezyweather.sources.cma.json.CmaGridLiveElement
import org.breezyweather.sources.cma.json.CmaStationContent
import org.breezyweather.unit.distance.Distance.Companion.meters
import org.breezyweather.unit.pressure.Pressure.Companion.hectopascals
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
private data class CmaLatestResult(
    val content: CmaStationContent?,
    val error: Throwable?,
)

private data class CmaGridResult(
    val elements: List<CmaGridLiveElement>?,
    val error: Throwable?,
)

private data class CmaAlertFetchResult(
    val alerts: List<CmaAlert>?,
    val error: Throwable?,
)

class CmaService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") val client: Retrofit.Builder,
) : HttpSource(), WeatherSource, LocationParametersSource, NonFreeNetSource {

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
    override val knownAmbiguousCountryCodes: Array<String>? = arrayOf("CN")

    // LocationParametersSource
    override fun needsLocationParametersRefresh(
        location: Location,
        coordinatesChanged: Boolean,
        features: List<SourceFeature>,
    ): Boolean {
        if (coordinatesChanged) return true
        val stationId = location.parameters.getOrElse(id) { null }?.getOrElse("stationId") { null }
        return stationId.isNullOrEmpty()
    }

    override fun requestLocationParameters(
        context: Context,
        location: Location,
    ): Observable<Map<String, String>> {
        return mApi.getNearStation(
            longitude = location.longitude,
            latitude = location.latitude,
            dist = NEAR_STATION_SEARCH_DISTANCE_KM
        ).map { result ->
            // No station nearby must not fail the whole refresh: alerts and the
            // gridded current fallback work without a station
            result.data
                ?.takeIf { it.returnCode == 0 && it.dataMethod == "station" }
                ?.DS?.stationId?.takeIf { it.isNotBlank() }
                ?.let { mapOf("stationId" to it) }
                ?: emptyMap()
        }
    }

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

        val stationId = location.parameters.getOrElse(id) { null }?.getOrElse("stationId") { null }
        val wantLatest = SourceFeature.FORECAST in features ||
            (SourceFeature.CURRENT in features && !stationId.isNullOrEmpty())

        val latestObservable: Observable<CmaLatestResult> = when {
            !wantLatest -> Observable.just(CmaLatestResult(null, null))
            stationId.isNullOrEmpty() -> Observable.just(
                CmaLatestResult(null, InvalidLocationException())
            )
            else -> mApi.getStationLatest(stationId, getUtcDatetime())
                .map { result ->
                    if (result.code == 200 && result.content != null) {
                        CmaLatestResult(result.content, null)
                    } else {
                        CmaLatestResult(null, InvalidLocationException())
                    }
                }
                .onErrorResumeNext { e -> Observable.just(CmaLatestResult(null, e)) }
        }

        val gridObservable: Observable<CmaGridResult> =
            if (SourceFeature.CURRENT in features && stationId.isNullOrEmpty()) {
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

        val alertObservable: Observable<CmaAlertFetchResult> =
            if (SourceFeature.ALERT in features) {
                mApi.getEffectiveAlerts()
                    .map { result ->
                        if (result.code == "200") {
                            CmaAlertFetchResult(result.data.orEmpty(), null)
                        } else {
                            CmaAlertFetchResult(null, RuntimeException(result.message))
                        }
                    }
                    .onErrorResumeNext { e -> Observable.just(CmaAlertFetchResult(null, e)) }
            } else {
                Observable.just(CmaAlertFetchResult(null, null))
            }

        return Observable.zip(latestObservable, gridObservable, alertObservable) { latest, grid, alerts ->
            var dailyForecast: List<DailyWrapper>? = null
            var current: CurrentWrapper? = null
            var alertList: List<Alert>? = null

            if (SourceFeature.FORECAST in features) {
                val foreList = latest.content?.forecast?.foreList
                if (foreList.isNullOrEmpty()) {
                    failedFeatures[SourceFeature.FORECAST] =
                        latest.error ?: InvalidLocationException()
                } else {
                    getDailyList(foreList)?.let { dailyForecast = it }
                        ?: run {
                            failedFeatures[SourceFeature.FORECAST] = InvalidLocationException()
                        }
                }
            }

            if (SourceFeature.CURRENT in features) {
                when {
                    latest.content != null -> current = getCurrent(latest.content)
                    grid.elements != null -> current = getCurrent(grid.elements)
                    else -> failedFeatures[SourceFeature.CURRENT] =
                        latest.error ?: grid.error ?: InvalidLocationException()
                }
            }

            if (SourceFeature.ALERT in features) {
                if (alerts.alerts != null) {
                    alertList = getAlertList(alerts.alerts, location)
                } else {
                    failedFeatures[SourceFeature.ALERT] = alerts.error ?: RuntimeException()
                }
            }

            WeatherWrapper(
                dailyForecast = dailyForecast,
                current = current,
                alertList = alertList,
                failedFeatures = failedFeatures
            )
        }.flatMap { wrapper ->
            // The gridded endpoint was only queried upfront when no station was
            // known: retry it once as a fallback when the station request failed
            val shouldFallbackToGrid = SourceFeature.CURRENT in features &&
                !stationId.isNullOrEmpty() &&
                wrapper.current == null &&
                wrapper.failedFeatures?.containsKey(SourceFeature.CURRENT) == true
            if (!shouldFallbackToGrid) {
                Observable.just(wrapper)
            } else {
                mApi.getGridLiveData(location.latitude, location.longitude)
                    .map { result ->
                        if (result.returnCode == "0" && !result.list.isNullOrEmpty()) {
                            getCurrent(result.list)
                        } else {
                            null
                        }
                    }
                    .map { gridCurrent ->
                        if (gridCurrent == null) {
                            wrapper
                        } else {
                            WeatherWrapper(
                                dailyForecast = wrapper.dailyForecast,
                                current = gridCurrent,
                                alertList = wrapper.alertList,
                                failedFeatures = wrapper.failedFeatures
                                    ?.toMutableMap()
                                    ?.apply { remove(SourceFeature.CURRENT) }
                            )
                        }
                    }
                    .onErrorResumeNext { Observable.just(wrapper) }
            }
        }
    }

    private fun getDailyList(
        foreList: List<CmaForecastItem>,
    ): List<DailyWrapper>? {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val days = LinkedHashMap<Date, Pair<CmaForecastItem?, CmaForecastItem?>>()
        for (item in foreList) {
            val date = try {
                dateFormatter.parse(item.date ?: continue)
            } catch (e: ParseException) {
                continue
            } ?: continue
            val halves = days.getOrPut(date) { null to null }
            days[date] = if (item.period == "夜间") {
                halves.copy(second = item)
            } else {
                halves.copy(first = item)
            }
        }
        if (days.isEmpty()) return null

        return days.map { (date, halves) ->
            DailyWrapper(
                date = date,
                day = halves.first?.let { getHalfDay(it) },
                night = halves.second?.let { getHalfDay(it) }
            )
        }
    }

    private fun getHalfDay(item: CmaForecastItem): HalfDayWrapper {
        return HalfDayWrapper(
            weatherText = item.weatherText,
            weatherCode = getCmaWeatherCode(item.weatherCode),
            temperature = TemperatureWrapper(
                temperature = item.temperature
                    ?.toDoubleOrNull()
                    ?.cmaSanitized(-100.0, 100.0)
                    ?.celsius
            ),
            wind = Wind(
                degree = getCmaWindDirectionDegree(item.wind),
                speed = getCmaWindSpeed(item.wind)?.metersPerSecond
            )
        )
    }

    private fun getCurrent(content: CmaStationContent): CurrentWrapper {
        return CurrentWrapper(
            weatherText = content.weatherText?.takeIf { it.isNotBlank() },
            weatherCode = getCmaWeatherCodeFromText(content.weatherText),
            temperature = TemperatureWrapper(
                temperature = content.temperature.cmaSanitized(-100.0, 100.0)?.celsius
            ),
            wind = Wind(
                degree = getCmaWindDirectionDegree(content.windDirectionText),
                speed = content.windSpeed.cmaSanitized(0.0, 200.0)?.metersPerSecond
            ),
            relativeHumidity = content.humidity.cmaSanitized(0.0, 100.0)?.percent,
            pressure = content.pressure.cmaSanitized(300.0, 1200.0)?.hectopascals,
            visibility = content.visibility.cmaSanitized(0.0, 100_000.0)?.meters
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
        location: Location,
    ): List<Alert> {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        return alerts.asSequence()
            .filter { it.status == "Actual" }
            .filter { it.lat != null && it.lon != null }
            .filter {
                getCmaDistanceKm(location.latitude, location.longitude, it.lat!!, it.lon!!) <=
                    ALERT_DISTANCE_THRESHOLD_KM
            }
            .map { alert ->
                val severity = when (alert.severity?.lowercase(Locale.ENGLISH)) {
                    "red" -> AlertSeverity.EXTREME
                    "orange" -> AlertSeverity.SEVERE
                    "yellow" -> AlertSeverity.MODERATE
                    "blue" -> AlertSeverity.MINOR
                    else -> AlertSeverity.UNKNOWN
                }
                Alert(
                    alertId = alert.identifier
                        ?: Objects.hash(alert.headline, alert.severity, alert.effective).toString(),
                    startDate = parseDate(dateFormatter, alert.effective),
                    endDate = parseDate(dateFormatter, alert.expires),
                    headline = alert.headline,
                    description = alert.description,
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

    private fun getUtcDatetime(): String {
        val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date())
    }

    companion object {
        private const val BASE_URL = "https://data.cma.cn/"
        private const val NEAR_STATION_SEARCH_DISTANCE_KM = 100
        private const val ALERT_DISTANCE_THRESHOLD_KM = 100.0
    }
}
