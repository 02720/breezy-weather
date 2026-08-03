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

package org.breezyweather.sources.caiyun

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.AirQuality
import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.DailyCloudCover
import breezyweather.domain.weather.model.DailyPressure
import breezyweather.domain.weather.model.DailyRelativeHumidity
import breezyweather.domain.weather.model.DailyVisibility
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
import org.breezyweather.common.exceptions.InvalidOrIncompleteDataException
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.toDate
import org.breezyweather.common.preference.EditTextPreference
import org.breezyweather.common.preference.Preference
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.sources.caiyun.json.CaiyunAlertContent
import org.breezyweather.sources.caiyun.json.CaiyunDaily
import org.breezyweather.sources.caiyun.json.CaiyunDailyPrecipitation
import org.breezyweather.sources.caiyun.json.CaiyunDailyWind
import org.breezyweather.sources.caiyun.json.CaiyunDailyWindValue
import org.breezyweather.sources.caiyun.json.CaiyunHourly
import org.breezyweather.sources.caiyun.json.CaiyunHourlyWind
import org.breezyweather.sources.caiyun.json.CaiyunResult
import org.breezyweather.sources.caiyun.json.CaiyunWeatherResult
import org.breezyweather.unit.distance.Distance.Companion.kilometers
import org.breezyweather.unit.pollutant.PollutantConcentration.Companion.microgramsPerCubicMeter
import org.breezyweather.unit.pollutant.PollutantConcentration.Companion.milligramsPerCubicMeter
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.pressure.Pressure.Companion.pascals
import org.breezyweather.unit.ratio.Ratio.Companion.fraction
import org.breezyweather.unit.ratio.Ratio.Companion.percent
import org.breezyweather.unit.speed.Speed.Companion.kilometersPerHour
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.util.Date
import java.util.Objects
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.Duration.Companion.seconds

class CaiyunService @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("JsonClient") client: Retrofit.Builder,
) : CaiyunServiceStub(context) {

    override val privacyPolicyUrl = "https://docs.caiyunapp.com/weather-api/privacy.html"

    private val mApi by lazy {
        client
            .baseUrl(CAIYUN_BASE_URL)
            .build()
            .create(CaiyunApi::class.java)
    }

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val token = getApiKeyOrDefault()

        return mApi.getWeather(
            token = token,
            lng = location.longitude,
            lat = location.latitude,
            lang = getLanguage(context),
            unit = "metric:v2",
            dailysteps = DAILY_STEPS,
            dailystart = -1,
            hourlysteps = HOURLY_STEPS,
            alert = true
        ).map { result ->
            if (result.status != "ok" || result.result == null) {
                throw InvalidOrIncompleteDataException()
            }
            WeatherWrapper(
                dailyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getDailyForecast(result.result)
                } else {
                    null
                },
                hourlyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getHourlyForecast(result.result.hourly)
                } else {
                    null
                },
                current = if (SourceFeature.CURRENT in requestedFeatures) {
                    getCurrent(result.result)
                } else {
                    null
                },
                airQuality = if (SourceFeature.AIR_QUALITY in requestedFeatures) {
                    getAirQuality(result.result)
                } else {
                    null
                },
                minutelyForecast = if (SourceFeature.MINUTELY in requestedFeatures) {
                    getMinutelyForecast(result)
                } else {
                    null
                },
                alertList = if (SourceFeature.ALERT in requestedFeatures) {
                    getAlertList(result.result.alert?.content)
                } else {
                    null
                }
            )
        }
    }

    /**
     * Returns current weather
     */
    private fun getCurrent(result: CaiyunResult): CurrentWrapper? {
        val realtime = result.realtime
        if (realtime == null || realtime.status != "ok") return null
        return CurrentWrapper(
            weatherText = getWeatherText(realtime.skycon, chinese),
            weatherCode = getWeatherCode(realtime.skycon),
            temperature = TemperatureWrapper(
                temperature = realtime.temperature?.celsius,
                feelsLike = realtime.apparent_temperature?.celsius
            ),
            wind = if (realtime.wind != null) {
                Wind(
                    degree = realtime.wind.direction,
                    speed = realtime.wind.speed?.kilometersPerHour,
                    gusts = realtime.gust?.kilometersPerHour
                )
            } else {
                null
            },
            uV = realtime.life_index?.ultraviolet?.index?.toDoubleOrNull()?.let { UV(index = it) },
            relativeHumidity = realtime.humidity?.fraction,
            dewPoint = realtime.dewpoint?.celsius,
            pressure = realtime.pressure?.pascals,
            cloudCover = realtime.cloudrate?.fraction,
            visibility = realtime.visibility?.kilometers,
            hourlyForecast = result.minutely?.description
        )
    }

    /**
     * Returns daily forecast
     */
    private fun getDailyForecast(result: CaiyunResult): List<DailyWrapper>? {
        val daily = result.daily
        val skyconList = daily?.skycon
        if (daily == null || skyconList.isNullOrEmpty()) return null

        val dailyList: MutableList<DailyWrapper> = ArrayList(skyconList.size)
        skyconList.forEachIndexed { index, skycon ->
            dailyList.add(
                DailyWrapper(
                    date = skycon.date ?: return@forEachIndexed,
                    day = HalfDayWrapper(
                        weatherText = getWeatherText(getDaySkycon(daily, index), chinese),
                        weatherCode = getWeatherCode(getDaySkycon(daily, index)),
                        temperature = TemperatureWrapper(
                            temperature = getDayTemperature(daily, index)?.celsius
                        ),
                        precipitation = Precipitation(
                            total = getDayPrecipitation(daily, index)?.max?.millimeters
                        ),
                        precipitationProbability = PrecipitationProbability(
                            total = daily.precipitation?.getOrNull(index)?.probability?.percent
                        ),
                        wind = getWind(daily.wind_08h_20h?.getOrNull(index) ?: daily.wind?.getOrNull(index))
                    ),
                    night = HalfDayWrapper(
                        weatherText = getWeatherText(getNightSkycon(daily, index), chinese),
                        weatherCode = getWeatherCode(getNightSkycon(daily, index)),
                        temperature = TemperatureWrapper(
                            temperature = getNightTemperature(daily, index)?.celsius
                        ),
                        precipitation = Precipitation(
                            total = getNightPrecipitation(daily, index)?.max?.millimeters
                        ),
                        precipitationProbability = PrecipitationProbability(
                            total = daily.precipitation?.getOrNull(index)?.probability?.percent
                        ),
                        wind = getWind(daily.wind_20h_32h?.getOrNull(index) ?: daily.wind?.getOrNull(index))
                    ),
                    uV = daily.life_index?.ultraviolet?.getOrNull(index)?.index?.toDoubleOrNull()?.let {
                        UV(index = it)
                    },
                    relativeHumidity = daily.humidity?.getOrNull(index)?.avg?.fraction?.let {
                        DailyRelativeHumidity(average = it)
                    },
                    pressure = daily.pressure?.getOrNull(index)?.avg?.pascals?.let {
                        DailyPressure(average = it)
                    },
                    cloudCover = daily.cloudrate?.getOrNull(index)?.avg?.fraction?.let {
                        DailyCloudCover(average = it)
                    },
                    visibility = daily.visibility?.getOrNull(index)?.avg?.kilometers?.let {
                        DailyVisibility(average = it)
                    }
                )
            )
        }
        return dailyList
    }

    /**
     * Returns hourly forecast
     */
    private fun getHourlyForecast(hourly: CaiyunHourly?): List<HourlyWrapper>? {
        val skyconList = hourly?.skycon
        if (hourly == null || skyconList.isNullOrEmpty()) return null

        val hourlyList: MutableList<HourlyWrapper> = ArrayList(skyconList.size)
        skyconList.forEachIndexed { index, skycon ->
            hourlyList.add(
                HourlyWrapper(
                    date = skycon.date ?: return@forEachIndexed,
                    weatherText = getWeatherText(skycon.value, chinese),
                    weatherCode = getWeatherCode(skycon.value),
                    temperature = TemperatureWrapper(
                        temperature = hourly.temperature?.getOrNull(index)?.value?.celsius,
                        feelsLike = hourly.apparent_temperature?.getOrNull(index)?.value?.celsius
                    ),
                    precipitation = Precipitation(
                        total = hourly.precipitation?.getOrNull(index)?.value?.millimeters
                    ),
                    precipitationProbability = PrecipitationProbability(
                        total = hourly.precipitation?.getOrNull(index)?.probability?.percent
                    ),
                    wind = hourly.wind?.getOrNull(index)?.let { getWind(it) },
                    relativeHumidity = hourly.humidity?.getOrNull(index)?.value?.fraction,
                    pressure = hourly.pressure?.getOrNull(index)?.value?.pascals,
                    cloudCover = hourly.cloudrate?.getOrNull(index)?.value?.fraction,
                    visibility = hourly.visibility?.getOrNull(index)?.value?.kilometers
                )
            )
        }
        return hourlyList
    }

    /**
     * Returns minutely forecast
     */
    private fun getMinutelyForecast(result: CaiyunWeatherResult): List<Minutely>? {
        val minutely = result.result?.minutely
        val precipitationList = minutely?.precipitation_2h
        if (minutely == null || minutely.status != "ok" || precipitationList.isNullOrEmpty()) {
            return null
        }

        // The minutely array starts at the current minute, aligned on the
        // server time rounded down to the minute
        val start = result.server_time?.let { Date((it / 60) * 60 * 1000) } ?: return null
        return precipitationList.mapIndexed { index, intensity ->
            Minutely(
                date = Date(start.time + index * MINUTE_IN_MILLISECONDS),
                minuteInterval = 1,
                precipitationIntensity = intensity.millimeters
            )
        }
    }

    /**
     * Returns air quality
     */
    private fun getAirQuality(result: CaiyunResult): AirQualityWrapper? {
        val current = result.realtime?.air_quality
        val dailyPm25 = result.daily?.air_quality?.pm25

        val dailyForecast = dailyPm25?.mapNotNull { element ->
            element.date?.let { date ->
                date to AirQuality(
                    pM25 = element.avg?.microgramsPerCubicMeter
                )
            }
        }?.toMap()

        if (current == null && dailyForecast.isNullOrEmpty()) return null
        return AirQualityWrapper(
            current = if (current != null) {
                AirQuality(
                    pM25 = current.pm25?.microgramsPerCubicMeter,
                    pM10 = current.pm10?.microgramsPerCubicMeter,
                    sO2 = current.so2?.microgramsPerCubicMeter,
                    nO2 = current.no2?.microgramsPerCubicMeter,
                    o3 = current.o3?.microgramsPerCubicMeter,
                    cO = current.co?.milligramsPerCubicMeter
                )
            } else {
                null
            },
            dailyForecast = dailyForecast
        )
    }

    /**
     * Returns alerts
     */
    private fun getAlertList(alertList: List<CaiyunAlertContent>?): List<Alert>? {
        if (alertList.isNullOrEmpty()) return null
        return alertList.map { alert ->
            val severity = getAlertSeverity(alert.severity)
            Alert(
                alertId = alert.alertId ?: Objects.hash(alert.title, alert.pubtimestamp).toString(),
                startDate = alert.effective ?: alert.pubtimestamp?.seconds?.inWholeMilliseconds?.toDate(),
                endDate = alert.expires,
                headline = alert.headline,
                description = alert.description,
                source = alert.source,
                severity = severity,
                color = getAlertColor(alert.subtype?.color) ?: Alert.colorFromSeverity(severity)
            )
        }
    }

    /**
     * The daytime half-day is expected from 08:00 to 19:59 and the nighttime
     * half-day from 20:00 to 07:59 (or 31:59) according to Caiyun
     * recommendations.
     */
    private fun getDaySkycon(daily: CaiyunDaily, index: Int): String? {
        return daily.skycon_08h_20h?.getOrNull(index)?.value ?: daily.skycon?.getOrNull(index)?.value
    }

    private fun getNightSkycon(daily: CaiyunDaily, index: Int): String? {
        return daily.skycon_20h_32h?.getOrNull(index)?.value ?: daily.skycon?.getOrNull(index)?.value
    }

    private fun getDayTemperature(daily: CaiyunDaily, index: Int): Double? {
        return daily.temperature_08h_20h?.getOrNull(index)?.max ?: daily.temperature?.getOrNull(index)?.max
    }

    private fun getNightTemperature(daily: CaiyunDaily, index: Int): Double? {
        return daily.temperature_20h_32h?.getOrNull(index)?.min ?: daily.temperature?.getOrNull(index)?.min
    }

    private fun getDayPrecipitation(daily: CaiyunDaily, index: Int): CaiyunDailyPrecipitation? {
        return daily.precipitation_08h_20h?.getOrNull(index) ?: daily.precipitation?.getOrNull(index)
    }

    private fun getNightPrecipitation(daily: CaiyunDaily, index: Int): CaiyunDailyPrecipitation? {
        return daily.precipitation_20h_32h?.getOrNull(index) ?: daily.precipitation?.getOrNull(index)
    }

    private fun getWind(wind: CaiyunDailyWind?): Wind? {
        if (wind == null) return null
        val value = wind.value
        return getWind(
            wind.max ?: value?.max,
            wind.min ?: value?.min,
            wind.avg ?: value?.avg
        )
    }

    private fun getWind(wind: CaiyunHourlyWind?): Wind? {
        if (wind == null) return null
        val value = wind.value
        return Wind(
            degree = value?.direction ?: wind.direction,
            speed = (value?.speed ?: wind.speed)?.kilometersPerHour,
            gusts = null
        )
    }

    private fun getWind(
        max: CaiyunDailyWindValue?,
        min: CaiyunDailyWindValue?,
        avg: CaiyunDailyWindValue?,
    ): Wind? {
        return Wind(
            degree = max?.direction ?: avg?.direction ?: min?.direction,
            speed = max?.speed?.kilometersPerHour ?: avg?.speed?.kilometersPerHour
                ?: min?.speed?.kilometersPerHour,
            gusts = null
        )
    }

    /**
     * Whether to use Chinese for weather descriptions
     */
    private val chinese: Boolean
        get() = context.currentLocale.language.startsWith("zh", ignoreCase = true)

    private fun getLanguage(context: Context): String {
        val locale = context.currentLocale
        return when {
            locale.language == "zh" && locale.country == "TW" -> "zh_TW"
            locale.language == "zh" -> "zh_CN"
            locale.language == "en" && locale.country == "GB" -> "en_GB"
            locale.language == "en" -> "en_US"
            locale.language == "ja" -> "ja"
            else -> "zh_CN"
        }
    }

    // CONFIG
    private val config = SourceConfigStore(context, id)
    private var apikey: String
        set(value) {
            config.edit().putString("apikey", value).apply()
        }
        get() = config.getString("apikey", null) ?: ""

    private fun getApiKeyOrDefault(): String {
        return apikey.ifEmpty { BuildConfig.CAIYUN_KEY }
    }
    override val isConfigured
        get() = getApiKeyOrDefault().isNotEmpty()

    override val isRestricted
        get() = apikey.isEmpty()

    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            EditTextPreference(
                titleId = R.string.settings_weather_source_caiyun_api_key,
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

    companion object {
        private const val CAIYUN_BASE_URL = "https://api.caiyunapp.com/"
        private const val DAILY_STEPS = 16
        private const val HOURLY_STEPS = 360
        private const val MINUTE_IN_MILLISECONDS = 60 * 1000L
    }
}
