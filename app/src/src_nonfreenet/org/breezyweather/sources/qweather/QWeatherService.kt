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

package org.breezyweather.sources.qweather

import android.content.Context
import android.graphics.Color
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.AirQuality
import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.DailyCloudCover
import breezyweather.domain.weather.model.Minutely
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.PrecipitationProbability
import breezyweather.domain.weather.model.UV
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.reference.AlertSeverity
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
import org.breezyweather.common.extensions.code
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.isTraditionalChinese
import org.breezyweather.common.extensions.toDateNoHour
import org.breezyweather.common.preference.EditTextPreference
import org.breezyweather.common.preference.Preference
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.sources.qweather.json.QWeatherAirCurrentResult
import org.breezyweather.sources.qweather.json.QWeatherAirHourlyResult
import org.breezyweather.sources.qweather.json.QWeatherAlertColor
import org.breezyweather.sources.qweather.json.QWeatherAlertResult
import org.breezyweather.sources.qweather.json.QWeatherCurrent
import org.breezyweather.sources.qweather.json.QWeatherDailyHalfDay
import org.breezyweather.sources.qweather.json.QWeatherDailyResult
import org.breezyweather.sources.qweather.json.QWeatherHourlyResult
import org.breezyweather.sources.qweather.json.QWeatherMinutelyResult
import org.breezyweather.sources.qweather.json.QWeatherPollutant
import org.breezyweather.sources.qweather.json.QWeatherPrecipitation
import org.breezyweather.sources.qweather.json.QWeatherValueUnit
import org.breezyweather.unit.distance.Distance.Companion.meters
import org.breezyweather.unit.pollutant.PollutantConcentration
import org.breezyweather.unit.pollutant.PollutantConcentration.Companion.microgramsPerCubicMeter
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.pressure.Pressure.Companion.hectopascals
import org.breezyweather.unit.ratio.Ratio
import org.breezyweather.unit.ratio.Ratio.Companion.fraction
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
 * QWeather (和风天气) service.
 *
 * Authentication: API Key passed via the `X-QW-Api-Key` header. JWT (Ed25519) is the preferred
 * method recommended by QWeather, but EdDSA signing is not available on the project's minSdk 23
 * without pulling in BouncyCastle; the API Key method is supported by every endpoint used here
 * and works on all Android versions. Each user provides their own key and personal API Host.
 */
class QWeatherService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") client: Retrofit.Builder,
) : QWeatherServiceStub() {

    override val privacyPolicyUrl = "https://www.qweather.com/en/privacy.html"

    override val attributionLinks = mapOf(
        name to "https://www.qweather.com/"
    )

    private val client = client

    private fun getApi(host: String): QWeatherApi {
        val baseUrl = normalizeHost(host)
        return this.client
            .baseUrl(baseUrl)
            .build()
            .create(QWeatherApi::class.java)
    }

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val apiKey = getApiKeyOrDefault()
        val languageCode = getLanguage(context)
        // QWeather accepts at most 2 decimals for coordinates.
        val lat = roundCoord(location.latitude)
        val lon = roundCoord(location.longitude)
        // Minutely uses the legacy v7 location query param, formatted as "lon,lat".
        val minutelyLocation = "$lon,$lat"
        val api = getApi(getHostOrDefault())
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()

        val current = if (SourceFeature.CURRENT in requestedFeatures) {
            api.getCurrent(apiKey, lat, lon, languageCode).onErrorResumeNext {
                failedFeatures[SourceFeature.CURRENT] = it
                Observable.just(QWeatherCurrent())
            }
        } else {
            Observable.just(QWeatherCurrent())
        }

        val daily = if (SourceFeature.FORECAST in requestedFeatures) {
            api.getDaily(apiKey, lat, lon, DAILY_DAYS, languageCode).onErrorResumeNext {
                failedFeatures[SourceFeature.FORECAST] = it
                Observable.just(QWeatherDailyResult())
            }
        } else {
            Observable.just(QWeatherDailyResult())
        }

        val hourly = if (SourceFeature.FORECAST in requestedFeatures) {
            api.getHourly(apiKey, lat, lon, HOURLY_HOURS, languageCode).onErrorResumeNext {
                failedFeatures[SourceFeature.FORECAST] = it
                Observable.just(QWeatherHourlyResult())
            }
        } else {
            Observable.just(QWeatherHourlyResult())
        }

        val minutely = if (SourceFeature.MINUTELY in requestedFeatures) {
            api.getMinutely(apiKey, minutelyLocation, languageCode).onErrorResumeNext {
                failedFeatures[SourceFeature.MINUTELY] = it
                Observable.just(QWeatherMinutelyResult())
            }
        } else {
            Observable.just(QWeatherMinutelyResult())
        }

        val alert = if (SourceFeature.ALERT in requestedFeatures) {
            api.getAlert(apiKey, lat, lon, languageCode).onErrorResumeNext {
                failedFeatures[SourceFeature.ALERT] = it
                Observable.just(QWeatherAlertResult())
            }
        } else {
            Observable.just(QWeatherAlertResult())
        }

        val airCurrent = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
            api.getAirCurrent(apiKey, lat, lon, languageCode).onErrorResumeNext {
                failedFeatures[SourceFeature.AIR_QUALITY] = it
                Observable.just(QWeatherAirCurrentResult())
            }
        } else {
            Observable.just(QWeatherAirCurrentResult())
        }

        val airHourly = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
            api.getAirHourly(apiKey, lat, lon, languageCode).onErrorResumeNext {
                failedFeatures[SourceFeature.AIR_QUALITY] = it
                Observable.just(QWeatherAirHourlyResult())
            }
        } else {
            Observable.just(QWeatherAirHourlyResult())
        }

        return Observable.zip(
            current,
            daily,
            hourly,
            minutely,
            alert,
            airCurrent,
            airHourly
        ) { currentResult, dailyResult, hourlyResult, minutelyResult, alertResult, airCurrentResult, airHourlyResult ->
            WeatherWrapper(
                dailyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getDailyList(dailyResult, location)
                } else {
                    null
                },
                hourlyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getHourlyList(hourlyResult)
                } else {
                    null
                },
                current = if (SourceFeature.CURRENT in requestedFeatures) {
                    getCurrent(currentResult)
                } else {
                    null
                },
                airQuality = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
                    getAirQuality(airCurrentResult, airHourlyResult)
                } else {
                    null
                },
                minutelyForecast = if (SourceFeature.MINUTELY in requestedFeatures) {
                    getMinutelyForecast(minutelyResult)
                } else {
                    null
                },
                alertList = if (SourceFeature.ALERT in requestedFeatures) {
                    getAlertList(alertResult)
                } else {
                    null
                },
                failedFeatures = failedFeatures
            )
        }
    }

    private fun getCurrent(result: QWeatherCurrent): CurrentWrapper? {
        if (result.condition == null && result.temperature == null && result.wind == null) return null
        return CurrentWrapper(
            weatherText = result.condition?.text,
            weatherCode = getWeatherCode(result.condition?.code),
            temperature = TemperatureWrapper(
                temperature = result.temperature?.value?.celsius,
                feelsLike = result.feelsLike?.value?.celsius
            ),
            wind = Wind(
                degree = result.wind?.direction?.degree,
                speed = result.wind?.speed?.value?.metersPerSecond,
                gusts = result.windGust?.value?.metersPerSecond
            ),
            uV = result.uvIndex?.let { UV(index = it) },
            relativeHumidity = result.humidity?.fraction,
            dewPoint = result.dewPoint?.value?.celsius,
            pressure = result.pressure?.value?.hectopascals,
            cloudCover = result.cloudCover?.fraction,
            visibility = result.visibility?.value?.meters
        )
    }

    private fun getDailyList(result: QWeatherDailyResult, location: Location): List<DailyWrapper>? {
        val days = result.days
        if (days.isNullOrEmpty()) return null
        return days.mapNotNull { day ->
            val date = day.forecastStartTime.toLocalDailyDate(location.timeZone) ?: return@mapNotNull null
            DailyWrapper(
                date = date,
                day = getHalfDay(day.daytime, isDay = true),
                night = getHalfDay(day.nighttime, isDay = false),
                uV = day.uvIndexMax?.let { UV(index = it) },
                cloudCover = averageRatio(day.daytime?.cloudCover, day.nighttime?.cloudCover)?.let {
                    DailyCloudCover(average = it)
                }
            )
        }
    }

    /**
     * @param isDay when true the half-day temperature prefers `temperatureMax` (the
     *     daytime high); when false it prefers `temperatureMin` (the nighttime low).
     */
    private fun getHalfDay(halfDay: QWeatherDailyHalfDay?, isDay: Boolean): HalfDayWrapper? {
        if (halfDay == null) return null
        val preferredTemp = if (isDay) {
            halfDay.temperatureMax?.value?.celsius ?: halfDay.temperatureMin?.value?.celsius
        } else {
            halfDay.temperatureMin?.value?.celsius ?: halfDay.temperatureMax?.value?.celsius
        }
        return HalfDayWrapper(
            weatherText = halfDay.condition?.text,
            weatherCode = getWeatherCode(halfDay.condition?.code),
            temperature = TemperatureWrapper(temperature = preferredTemp),
            precipitation = getPrecipitation(halfDay.precipitation),
            precipitationProbability = halfDay.precipitation?.probability?.let {
                PrecipitationProbability(total = it.fraction)
            },
            wind = Wind(
                degree = halfDay.wind?.direction?.degree,
                speed = halfDay.wind?.speed?.value?.metersPerSecond,
                gusts = halfDay.windGustMax?.value?.metersPerSecond
            )
        )
    }

    private fun getHourlyList(result: QWeatherHourlyResult): List<HourlyWrapper>? {
        val hours = result.hours
        if (hours.isNullOrEmpty()) return null
        return hours.mapNotNull { hour ->
            val date = hour.forecastTime.toQWeatherDate() ?: return@mapNotNull null
            HourlyWrapper(
                date = date,
                weatherText = hour.condition?.text,
                weatherCode = getWeatherCode(hour.condition?.code),
                temperature = TemperatureWrapper(
                    temperature = hour.temperature?.value?.celsius,
                    feelsLike = hour.feelsLike?.value?.celsius
                ),
                precipitation = getPrecipitation(hour.precipitation),
                precipitationProbability = hour.precipitation?.probability?.let {
                    PrecipitationProbability(total = it.fraction)
                },
                wind = Wind(
                    degree = hour.wind?.direction?.degree,
                    speed = hour.wind?.speed?.value?.metersPerSecond,
                    gusts = hour.windGust?.value?.metersPerSecond
                ),
                uV = hour.uvIndex?.let { UV(index = it) },
                relativeHumidity = hour.humidity?.fraction,
                dewPoint = hour.dewPoint?.value?.celsius,
                pressure = hour.pressure?.value?.hectopascals,
                cloudCover = hour.cloudCover?.fraction,
                visibility = hour.visibility?.value?.meters
            )
        }
    }

    private fun getMinutelyForecast(result: QWeatherMinutelyResult): List<Minutely>? {
        // A code other than "200" (e.g. "204" no data) means there is no minutely forecast for
        // this location; this is not an error, just an absence of data.
        if (result.code != "200") return null
        val items = result.minutely
        if (items.isNullOrEmpty()) return null
        return items.mapNotNull { item ->
            val date = item.fxTime.toQWeatherDate() ?: return@mapNotNull null
            // QWeather precip is the 5-minute accumulated amount in mm; convert to mm/h intensity.
            val intensity = item.precip?.toDoubleOrNull()?.let { (it * MINUTELY_TO_HOURLY).millimeters }
            Minutely(
                date = date,
                minuteInterval = MINUTELY_INTERVAL_MINUTES,
                precipitationIntensity = intensity
            )
        }
    }

    private fun getAlertList(result: QWeatherAlertResult): List<Alert>? {
        val alerts = result.alerts
        if (alerts.isNullOrEmpty()) return null
        return alerts.map { alert ->
            val severity = getAlertSeverity(alert.severity)
            Alert(
                alertId = alert.id
                    ?: Objects.hash(alert.headline, alert.issuedTime, alert.severity).toString(),
                startDate = alert.effectiveTime?.toQWeatherDate() ?: alert.issuedTime?.toQWeatherDate(),
                endDate = alert.expireTime?.toQWeatherDate(),
                headline = alert.headline,
                description = alert.description,
                instruction = alert.instruction,
                source = alert.senderName,
                severity = severity,
                color = getAlertColor(alert.color, severity)
            )
        }
    }

    private fun getAlertColor(color: QWeatherAlertColor?, severity: AlertSeverity): Int {
        val r = color?.red
        val g = color?.green
        val b = color?.blue
        if (r != null && g != null && b != null) {
            val alpha = ((color?.alpha ?: 1.0) * 255).toInt().coerceIn(0, 255)
            return Color.argb(alpha, r.toInt(), g.toInt(), b.toInt())
        }
        return Alert.colorFromSeverity(severity)
    }

    private fun getAirQuality(
        currentResult: QWeatherAirCurrentResult,
        hourlyResult: QWeatherAirHourlyResult,
    ): AirQualityWrapper? {
        val current = currentResult.pollutants.toAirQuality()
        val hourly = mutableMapOf<Date, AirQuality>()
        hourlyResult.hours?.forEach { hour ->
            val date = hour.forecastTime.toQWeatherDate()
            if (date != null) {
                val aq = hour.pollutants.toAirQuality()
                if (aq != null && aq.isValid) {
                    hourly[date] = aq
                }
            }
        }
        if (current == null && hourly.isEmpty()) return null
        return AirQualityWrapper(
            current = current,
            hourlyForecast = hourly.ifEmpty { null }
        )
    }

    private fun List<QWeatherPollutant>?.toAirQuality(): AirQuality? {
        if (isNullOrEmpty()) return null
        return AirQuality(
            pM25 = concentration("pm2p5")?.toMicrogramsPerCubicMeter(MOLAR_PARTICULATE),
            pM10 = concentration("pm10")?.toMicrogramsPerCubicMeter(MOLAR_PARTICULATE),
            sO2 = concentration("so2")?.toMicrogramsPerCubicMeter(MOLAR_SO2),
            nO2 = concentration("no2")?.toMicrogramsPerCubicMeter(MOLAR_NO2),
            o3 = concentration("o3")?.toMicrogramsPerCubicMeter(MOLAR_O3),
            cO = concentration("co")?.toMicrogramsPerCubicMeter(MOLAR_CO)
        )
    }

    private fun List<QWeatherPollutant>?.concentration(code: String): QWeatherValueUnit? {
        return this?.firstOrNull { it.code == code }?.concentration
    }

    /**
     * Converts a pollutant concentration to µg/m³ based on its unit. QWeather may return µg/m³,
     * mg/m³, ppb or ppm depending on the pollutant and endpoint; the project stores all pollutant
     * concentrations in µg/m³. Gas conversions use the 25 °C / 1 atm molar volume (24.45 L/mol).
     */
    private fun QWeatherValueUnit?.toMicrogramsPerCubicMeter(molarMass: Double): PollutantConcentration? {
        val value = this?.value ?: return null
        val unit = this.unit?.lowercase()
        val micrograms = when {
            unit == null -> value
            unit.contains("ppb") -> value * molarMass / MOLAR_VOLUME_25C
            unit.contains("ppm") -> value * molarMass / MOLAR_VOLUME_25C * 1000.0
            unit.contains("mg") -> value * 1000.0
            else -> value // assume µg/m³
        }
        return micrograms.microgramsPerCubicMeter
    }

    private fun getPrecipitation(precip: QWeatherPrecipitation?): Precipitation? {
        val amount = precip?.amount?.value
        if (amount == null) return null
        val total = amount.millimeters
        return when (precip.type?.lowercase()) {
            "rain" -> Precipitation(total = total, rain = total)
            "snow" -> Precipitation(total = total, snow = total)
            "ice" -> Precipitation(total = total, ice = total)
            else -> Precipitation(total = total)
        }
    }

    private fun averageRatio(a: Double?, b: Double?): Ratio? {
        return when {
            a != null && b != null -> ((a + b) / 2.0).fraction
            a != null -> a.fraction
            b != null -> b.fraction
            else -> null
        }
    }

    private fun roundCoord(value: Double): Double {
        return Math.round(value * 100.0) / 100.0
    }

    /**
     * Normalizes a user-provided API Host into a base URL Retrofit accepts
     * (with an `https://` scheme and a trailing `/`).
     */
    private fun normalizeHost(host: String): String {
        var h = host.trim()
        if (h.isEmpty()) return "https://devapi.qweather.com/"
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "https://$h"
        if (!h.endsWith("/")) h += "/"
        return h
    }

    /**
     * Maps the device locale to a QWeather `lang` code.
     *
     * QWeather expects `zh-hant` for Traditional Chinese, but [Locale.code] returns
     * `zh-tw`/`zh-hk`/`zh-mo` for those variants. For every other language the
     * lowercase language tag already matches QWeather's code table, and QWeather
     * falls back to the official language or English when a code is unsupported.
     */
    private fun getLanguage(context: Context): String {
        val locale = context.currentLocale
        return if (locale.isTraditionalChinese) "zh-hant" else locale.code
    }

    /**
     * Parses a QWeather date-time string. v1 endpoints return UTC times like `2024-08-10T22:00Z`,
     * while the legacy v7 minutely endpoint and the alert endpoint return local-typed times like
     * `2021-12-16T18:55+08:00`. Both shapes lack seconds; we try with then without seconds, and
     * the `X`/`XXX` zone pattern accepts both `Z` and `+08:00` offsets.
     */
    private fun String?.toQWeatherDate(): Date? {
        if (isNullOrEmpty()) return null
        for (pattern in QWEATHER_DATE_PATTERNS) {
            try {
                return SimpleDateFormat(pattern, Locale.ENGLISH).parse(this)
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    /**
     * Converts a QWeather daily `forecastStartTime` (a UTC ISO-8601 string such as
     * `2024-08-10T22:00Z`) into a midnight [Date] in the location's time zone.
     *
     * QWeather returns daily forecast start times in UTC. For a UTC+8 location,
     * `2024-08-10T22:00Z` is locally 2024-08-11 06:00, so the forecast day is
     * August 11th — not August 10th. We therefore format the parsed instant into
     * a `yyyy-MM-dd` string in the location's time zone before deferring to
     * [toDateNoHour], which reconstructs a midnight [Date] in that same zone.
     */
    private fun String?.toLocalDailyDate(timeZone: TimeZone): Date? {
        val parsed = toQWeatherDate() ?: return null
        val dayString = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
            this.timeZone = timeZone
        }.format(parsed)
        return dayString.toDateNoHour(timeZone)
    }

    // CONFIG
    private val config = SourceConfigStore(context, id)
    private var apikey: String
        set(value) {
            config.edit().putString("apikey", value).apply()
        }
        get() = config.getString("apikey", null) ?: ""

    private var host: String
        set(value) {
            config.edit().putString("host", value).apply()
        }
        get() = config.getString("host", null) ?: ""

    private fun getApiKeyOrDefault(): String {
        return apikey.ifEmpty { BuildConfig.QWEATHER_KEY }
    }

    private fun getHostOrDefault(): String {
        return host.ifEmpty { BuildConfig.QWEATHER_HOST }
    }

    override val isConfigured
        get() = getApiKeyOrDefault().isNotEmpty() && getHostOrDefault().isNotEmpty()

    override val isRestricted
        get() = apikey.isEmpty() || host.isEmpty()

    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            EditTextPreference(
                titleId = R.string.settings_weather_source_qweather_api_key,
                summary = { c, content ->
                    content.ifEmpty {
                        c.getString(R.string.settings_source_default_value)
                    }
                },
                content = apikey,
                onValueChanged = {
                    apikey = it
                }
            ),
            EditTextPreference(
                titleId = R.string.settings_weather_source_qweather_host,
                summary = { c, content ->
                    content.ifEmpty {
                        c.getString(R.string.settings_weather_source_qweather_host_summary)
                    }
                },
                content = host,
                onValueChanged = {
                    host = it
                }
            )
        )
    }

    companion object {
        private const val DAILY_DAYS = 10
        private const val HOURLY_HOURS = 240
        private const val MINUTELY_INTERVAL_MINUTES = 5
        private const val MINUTELY_TO_HOURLY = 12.0 // 60 min / 5 min

        // Molar volume at 25 °C and 1 atm (L/mol), used for ppb/ppm → µg/m³ conversion.
        private const val MOLAR_VOLUME_25C = 24.45
        private const val MOLAR_NO2 = 46.0055
        private const val MOLAR_O3 = 47.9982
        private const val MOLAR_SO2 = 64.066
        private const val MOLAR_CO = 28.010
        // Particulate matter is always returned in µg/m³; molar mass is unused but kept for symmetry.
        private const val MOLAR_PARTICULATE = 1.0

        private val QWEATHER_DATE_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mmX"
        )
    }
}
