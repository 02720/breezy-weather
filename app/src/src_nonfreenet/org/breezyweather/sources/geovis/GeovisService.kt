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

package org.breezyweather.sources.geovis

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.AirQuality
import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.DailyCloudCover
import breezyweather.domain.weather.model.DailyPressure
import breezyweather.domain.weather.model.DailyRelativeHumidity
import breezyweather.domain.weather.model.DailyVisibility
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.PrecipitationProbability
import breezyweather.domain.weather.model.UV
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode
import breezyweather.domain.weather.wrappers.AirQualityWrapper
import breezyweather.domain.weather.wrappers.CurrentWrapper
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HalfDayWrapper
import breezyweather.domain.weather.wrappers.HourlyWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.BuildConfig
import org.breezyweather.R
import org.breezyweather.common.exceptions.WeatherException
import org.breezyweather.common.preference.EditTextPreference
import org.breezyweather.common.preference.Preference
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.sources.common.getCleanChineseAlertTitle
import org.breezyweather.sources.geovis.json.GeovisAlert
import org.breezyweather.sources.geovis.json.GeovisAlertResult
import org.breezyweather.sources.geovis.json.GeovisAqi
import org.breezyweather.sources.geovis.json.GeovisAqiResult
import org.breezyweather.sources.geovis.json.GeovisDaily
import org.breezyweather.sources.geovis.json.GeovisDailyResult
import org.breezyweather.sources.geovis.json.GeovisHourly
import org.breezyweather.sources.geovis.json.GeovisHourlyResult
import org.breezyweather.sources.geovis.json.GeovisRealtime
import org.breezyweather.sources.geovis.json.GeovisRealtimeResult
import org.breezyweather.unit.distance.Distance.Companion.meters
import org.breezyweather.unit.pollutant.PollutantConcentration.Companion.microgramsPerCubicMeter
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.pressure.Pressure.Companion.hectopascals
import org.breezyweather.unit.ratio.Ratio.Companion.percent
import org.breezyweather.unit.speed.Speed.Companion.metersPerSecond
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named

/**
 * 中科星图 (Geovis Earth Data Cloud) service.
 *
 * Authentication is done with a personal token passed as the `token` query parameter
 * (https://datacloud.geovisearth.com/support/meteorological/summary). The API uses GCJ-02
 * ("Mars") coordinates, so the WGS-84 coordinates stored in the locations are converted before
 * every request (see [wgs84ToGcj02]).
 *
 * The Chinese product is used for Chinese locations (richer data: air quality, alerts, more
 * forecast days), and the global product elsewhere. Each user provides their own token.
 */
class GeovisService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") client: Retrofit.Builder,
) : GeovisServiceStub() {

    override val privacyPolicyUrl = "https://www.geovisearth.com/service/agreement?id=1"

    override val attributionLinks = mapOf(
        name to "https://datacloud.geovisearth.com/"
    )

    private val client = client

    private fun getApi(): GeovisApi {
        return client
            .baseUrl(GEOVIS_BASE_URL)
            .build()
            .create(GeovisApi::class.java)
    }

    /**
     * Geovis always answers with HTTP 200 and reports failures through the `status` field of the
     * response body. Without this check, an invalid token or an unsupported location would look
     * like missing data instead of an error. Status 0 means success; every other status (e.g.
     * 18 "该定位暂无") is surfaced as a per-feature failure.
     */
    private fun throwOnErrorCode(status: Int?) {
        if (status != null && status != 0) {
            throw WeatherException()
        }
    }

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val api = getApi()
        val token = getTokenOrDefault()
        // The API expects GCJ-02 coordinates; the location is stored in WGS-84.
        // Geovis accepts at most 2 decimals and requires the longitude first.
        val (lat, lon) = wgs84ToGcj02(location.latitude, location.longitude)
        val position = "${roundCoord(lon)},${roundCoord(lat)}"
        val isChina = location.countryCode.equals("CN", ignoreCase = true)
        val region = if (isChina) REGION_CN else REGION_GLOBAL
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()

        val current = if (SourceFeature.CURRENT in requestedFeatures) {
            api.getRealtime(region, position, token)
                .map { it.apply { throwOnErrorCode(status) } }
                .onErrorResumeNext {
                    failedFeatures[SourceFeature.CURRENT] = it
                    Observable.just(GeovisRealtimeResult())
                }
        } else {
            Observable.just(GeovisRealtimeResult())
        }

        val daily = if (SourceFeature.FORECAST in requestedFeatures) {
            // The Chinese basic product returns 15 days while the global basic product returns
            // only 7 days, so the extended product is used for global locations.
            (if (isChina) api.getDaily(region, position, token) else
                api.getDailyProfessional(region, position, token))
                .map { it.apply { throwOnErrorCode(status) } }
                .onErrorResumeNext {
                    failedFeatures[SourceFeature.FORECAST] = it
                    Observable.just(GeovisDailyResult())
                }
        } else {
            Observable.just(GeovisDailyResult())
        }

        val hourly = if (SourceFeature.FORECAST in requestedFeatures) {
            api.getHourlyProfessional(region, position, token)
                .map { it.apply { throwOnErrorCode(status) } }
                .onErrorResumeNext {
                    failedFeatures[SourceFeature.FORECAST] = it
                    Observable.just(GeovisHourlyResult())
                }
        } else {
            Observable.just(GeovisHourlyResult())
        }

        val airQuality = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
            api.getAirQuality(position, token)
                .map { it.apply { throwOnErrorCode(status) } }
                .onErrorResumeNext {
                    failedFeatures[SourceFeature.AIR_QUALITY] = it
                    Observable.just(GeovisAqiResult())
                }
        } else {
            Observable.just(GeovisAqiResult())
        }

        val alert = if (SourceFeature.ALERT in requestedFeatures) {
            api.getAlert(position, token)
                .map { it.apply { throwOnErrorCode(status) } }
                .onErrorResumeNext {
                    failedFeatures[SourceFeature.ALERT] = it
                    Observable.just(GeovisAlertResult())
                }
        } else {
            Observable.just(GeovisAlertResult())
        }

        return Observable.zip(
            current,
            daily,
            hourly,
            airQuality,
            alert
        ) { currentResult, dailyResult, hourlyResult, airQualityResult, alertResult ->
            WeatherWrapper(
                dailyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getDailyList(dailyResult, location, isChina)
                } else {
                    null
                },
                hourlyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getHourlyList(hourlyResult, location)
                } else {
                    null
                },
                current = if (SourceFeature.CURRENT in requestedFeatures) {
                    getCurrent(currentResult)
                } else {
                    null
                },
                airQuality = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
                    getAirQuality(airQualityResult)
                } else {
                    null
                },
                alertList = if (SourceFeature.ALERT in requestedFeatures) {
                    getAlertList(alertResult, location)
                } else {
                    null
                },
                failedFeatures = failedFeatures
            )
        }
    }

    private fun getCurrent(result: GeovisRealtimeResult): CurrentWrapper? {
        val realtime = result.result
        if (realtime == null || (realtime.tem == null && realtime.wp_code == null && realtime.ws == null)) {
            return null
        }
        return CurrentWrapper(
            weatherText = realtime.wp,
            weatherCode = getGeovisWeatherCode(realtime.wp_code),
            temperature = TemperatureWrapper(
                temperature = realtime.tem?.celsius,
                feelsLike = realtime.real_tem?.celsius
            ),
            wind = Wind(
                degree = realtime.wd,
                speed = realtime.ws?.metersPerSecond,
                gusts = realtime.gust_speed?.metersPerSecond
            ),
            relativeHumidity = realtime.rh?.percent,
            dewPoint = realtime.dp_tem?.celsius,
            pressure = realtime.prs?.hectopascals,
            visibility = realtime.vis?.meters
        )
    }

    private fun getDailyList(
        result: GeovisDailyResult,
        location: Location,
        isChina: Boolean,
    ): List<DailyWrapper>? {
        val days = result.result?.datas
        if (days.isNullOrEmpty()) return null
        return days.mapNotNull { day ->
            val date = day.fc_time?.toGeovisDate(DAILY_DATE_PATTERN, location.timeZone)
                ?: return@mapNotNull null
            DailyWrapper(
                date = date,
                day = if (isChina) {
                    getHalfDay(
                        weatherText = day.wp_day,
                        weatherCode = getGeovisWeatherCode(day.wp_day_code),
                        temperature = day.tem_max,
                        precipitation = day.pre_day,
                        precipitationProbability = day.pre_pro_day
                    )
                } else {
                    // The global product returns a single daily phenomenon: the whole-day
                    // precipitation and its probability are attached to the day half so that the
                    // day/night total remains the daily accumulation (pre_day + pre_night = 0 +
                    // total).
                    getHalfDay(
                        weatherText = day.wp,
                        weatherCode = getGeovisWeatherCode(day.wp_code),
                        temperature = day.tem_max,
                        precipitation = day.pre,
                        precipitationProbability = day.pre_pro
                    )
                },
                night = if (isChina) {
                    getHalfDay(
                        weatherText = day.wp_night,
                        weatherCode = getGeovisWeatherCode(day.wp_night_code),
                        temperature = day.tem_min,
                        precipitation = day.pre_night,
                        precipitationProbability = day.pre_pro_night
                    )
                } else {
                    getHalfDay(
                        weatherText = day.wp,
                        weatherCode = getGeovisWeatherCode(day.wp_code),
                        temperature = day.tem_min,
                        precipitation = null,
                        precipitationProbability = null
                    )
                },
                uV = day.uv_level?.let { UV(index = it) },
                relativeHumidity = if (isChina) {
                    if (day.rh_max != null || day.rh_min != null) {
                        DailyRelativeHumidity(max = day.rh_max?.percent, min = day.rh_min?.percent)
                    } else {
                        null
                    }
                } else {
                    day.rh?.let { DailyRelativeHumidity(average = it.percent) }
                },
                pressure = day.prs?.let { DailyPressure(average = it.hectopascals) },
                cloudCover = day.cloud_cover?.let { DailyCloudCover(average = it.percent) },
                visibility = day.vis?.let { DailyVisibility(average = it.meters) }
            )
        }
    }

    private fun getHalfDay(
        weatherText: String?,
        weatherCode: WeatherCode?,
        temperature: Double?,
        precipitation: Double?,
        precipitationProbability: Double?,
    ): HalfDayWrapper? {
        if (weatherText == null && weatherCode == null && temperature == null && precipitation == null) {
            return null
        }
        return HalfDayWrapper(
            weatherText = weatherText,
            weatherCode = weatherCode,
            temperature = temperature?.let { TemperatureWrapper(temperature = it.celsius) },
            precipitation = precipitation?.let { Precipitation(total = it.millimeters) },
            precipitationProbability = precipitationProbability?.let {
                PrecipitationProbability(total = it.percent)
            }
        )
    }

    private fun getHourlyList(
        result: GeovisHourlyResult,
        location: Location,
    ): List<HourlyWrapper>? {
        val hours = result.result?.datas
        if (hours.isNullOrEmpty()) return null
        return hours.mapNotNull { hour ->
            val date = hour.fc_time?.toGeovisDate(HOURLY_DATE_PATTERN, location.timeZone)
                ?: return@mapNotNull null
            HourlyWrapper(
                date = date,
                weatherText = hour.wp,
                weatherCode = getGeovisWeatherCode(hour.wp_code),
                temperature = TemperatureWrapper(
                    temperature = hour.tem?.celsius,
                    feelsLike = hour.real_tem?.celsius
                ),
                precipitation = hour.pre?.let { Precipitation(total = it.millimeters) },
                precipitationProbability = hour.pre_pro?.let {
                    PrecipitationProbability(total = it.percent)
                },
                wind = Wind(
                    degree = hour.wd,
                    speed = hour.ws?.metersPerSecond
                ),
                uV = hour.uv_level?.let { UV(index = it) },
                relativeHumidity = hour.rh?.percent,
                dewPoint = hour.dp_tem?.celsius,
                pressure = hour.prs?.hectopascals,
                cloudCover = hour.cloud_cover?.percent,
                visibility = hour.vis?.meters
            )
        }
    }

    private fun getAirQuality(result: GeovisAqiResult): AirQualityWrapper? {
        val aqi = result.result ?: return null
        val airQuality = aqi.toAirQuality()
        if (airQuality == null) return null
        return AirQualityWrapper(current = airQuality)
    }

    /**
     * Geovis reports all pollutants in µg/m³ (the same unit the project stores them in).
     */
    private fun GeovisAqi.toAirQuality(): AirQuality? {
        if (pm25 == null && pm10 == null && so2 == null && no2 == null && o3 == null && co == null) {
            return null
        }
        return AirQuality(
            pM25 = pm25?.microgramsPerCubicMeter,
            pM10 = pm10?.microgramsPerCubicMeter,
            sO2 = so2?.microgramsPerCubicMeter,
            nO2 = no2?.microgramsPerCubicMeter,
            o3 = o3?.microgramsPerCubicMeter,
            cO = co?.microgramsPerCubicMeter
        )
    }

    private fun getAlertList(
        result: GeovisAlertResult,
        location: Location,
    ): List<Alert>? {
        val alerts = result.result?.alerts
        if (alerts.isNullOrEmpty()) return null
        return alerts.map { alert ->
            val severity = getGeovisAlertSeverity(alert.levelCode)
            Alert(
                // Hash the raw fields to build a stable ID, since the API does not provide one.
                alertId = Objects.hash(
                    alert.geoCode,
                    alert.typeCode,
                    alert.effective,
                    alert.title
                ).toString(),
                startDate = alert.effective?.toGeovisDate(ALERT_DATE_PATTERN, location.timeZone),
                endDate = alert.expires?.toGeovisDate(ALERT_DATE_PATTERN, location.timeZone),
                headline = getCleanChineseAlertTitle(alert.title) ?: alert.title?.ifEmpty { null },
                description = alert.detail,
                source = alert.sender,
                severity = severity,
                color = Alert.colorFromSeverity(severity)
            )
        }
    }

    private fun String?.toGeovisDate(pattern: String, timeZone: TimeZone): Date? {
        if (isNullOrEmpty()) return null
        return try {
            SimpleDateFormat(pattern, Locale.ENGLISH).apply { this.timeZone = timeZone }.parse(this)
        } catch (_: Exception) {
            null
        }
    }

    private fun roundCoord(value: Double): Double {
        return Math.round(value * 100.0) / 100.0
    }

    // CONFIG
    private val config = SourceConfigStore(context, id)
    private var token: String
        set(value) {
            config.edit().putString("token", value).apply()
        }
        get() = config.getString("token", null) ?: ""

    private fun getTokenOrDefault(): String {
        return token.ifEmpty { BuildConfig.GEOVIS_KEY }
    }

    override val isConfigured
        get() = getTokenOrDefault().isNotEmpty()

    override val isRestricted
        get() = token.isEmpty()

    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            EditTextPreference(
                titleId = R.string.settings_weather_source_geovis_token,
                summary = { c, content ->
                    content.ifEmpty {
                        c.getString(R.string.settings_source_default_value)
                    }
                },
                content = token,
                onValueChanged = {
                    token = it
                }
            )
        )
    }

    companion object {
        private const val GEOVIS_BASE_URL = "https://tiles.geovisearth.com/meteorology/v1/weather/"

        private const val REGION_CN = "cn"
        private const val REGION_GLOBAL = "global"

        private const val DAILY_DATE_PATTERN = "yyyyMMdd"
        private const val HOURLY_DATE_PATTERN = "yyyyMMddHH"
        private const val ALERT_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"
    }
}
