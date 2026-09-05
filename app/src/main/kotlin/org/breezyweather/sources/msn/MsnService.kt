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

package org.breezyweather.sources.msn

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.DailyDewPoint
import breezyweather.domain.weather.model.DailyPressure
import breezyweather.domain.weather.model.DailyRelativeHumidity
import breezyweather.domain.weather.model.DailyVisibility
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.PrecipitationProbability
import breezyweather.domain.weather.model.UV
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode
import breezyweather.domain.weather.wrappers.CurrentWrapper
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HalfDayWrapper
import breezyweather.domain.weather.wrappers.HourlyWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.common.exceptions.InvalidOrIncompleteDataException
import org.breezyweather.common.extensions.codeWithCountry
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.isChinese
import org.breezyweather.common.extensions.isTraditionalChinese
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.sources.msn.json.MsnAlert
import org.breezyweather.sources.msn.json.MsnCurrent
import org.breezyweather.sources.msn.json.MsnDailyForecast
import org.breezyweather.sources.msn.json.MsnHalfDay
import org.breezyweather.unit.distance.Distance.Companion.kilometers
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.pressure.Pressure.Companion.hectopascals
import org.breezyweather.unit.ratio.Ratio.Companion.percent
import org.breezyweather.unit.speed.Speed.Companion.kilometersPerHour
import org.breezyweather.unit.temperature.Temperature
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.util.Objects
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named

class MsnService @Inject constructor(
    @Named("JsonClient") client: Retrofit.Builder,
) : HttpSource(),
    WeatherSource {

    override val id = "msn"
    override val name = "MSN Weather"
    override val continent = SourceContinent.WORLDWIDE
    override val privacyPolicyUrl = "https://privacy.microsoft.com/en-us/privacystatement"

    private val mApi by lazy {
        client
            .baseUrl(MSN_BASE_URL)
            .build()
            .create(MsnApi::class.java)
    }

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to name,
        SourceFeature.CURRENT to name,
        SourceFeature.ALERT to name
    )
    override val attributionLinks = mapOf(
        name to "https://www.msn.cn/zh-cn/weather"
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
            forecastSource = id,
            currentSource = id,
            alertSource = id
        ),
        Location(
            city = "New York",
            latitude = 40.7128,
            longitude = -74.0060,
            timeZone = TimeZone.getTimeZone("America/New_York"),
            country = "United States",
            countryCode = "US",
            forecastSource = id,
            currentSource = id,
            alertSource = id
        )
    )

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        return mApi.getOverview(
            lat = location.latitude,
            lon = location.longitude,
            locale = getLocale(context),
            units = "C",
            days = MSN_WEATHER_DAYS,
            cutHour = true,
            wrapOData = false,
            apikey = MSN_API_KEY,
            appId = MSN_APP_ID,
            ocid = MSN_OCID
        ).map { result ->
            val weather = result.responses?.firstOrNull()?.weather?.firstOrNull()
                ?: throw InvalidOrIncompleteDataException()
            WeatherWrapper(
                dailyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getDailyForecast(weather.forecast?.days)
                } else {
                    null
                },
                hourlyForecast = if (SourceFeature.FORECAST in requestedFeatures) {
                    getHourlyForecast(weather.forecast?.days)
                } else {
                    null
                },
                current = if (SourceFeature.CURRENT in requestedFeatures) {
                    getCurrent(weather.current)
                } else {
                    null
                },
                alertList = if (SourceFeature.ALERT in requestedFeatures) {
                    getAlertList(weather.alerts)
                } else {
                    null
                }
            )
        }
    }

    private fun getCurrent(
        current: MsnCurrent?,
    ): CurrentWrapper? {
        if (current == null) return null
        return CurrentWrapper(
            weatherText = current.cap,
            weatherCode = getWeatherCode(current.symbol),
            temperature = TemperatureWrapper(
                temperature = current.temp?.celsius,
                feelsLike = current.feels?.celsius
            ),
            wind = Wind(
                degree = current.windDir,
                speed = current.windSpd?.kilometersPerHour,
                gusts = current.windGust?.kilometersPerHour
            ),
            uV = UV(index = current.uv),
            relativeHumidity = current.rh?.percent,
            dewPoint = current.dewPt?.celsius,
            pressure = current.baro?.hectopascals,
            cloudCover = current.cloudCover?.percent,
            visibility = current.vis?.kilometers
        )
    }

    private fun getDailyForecast(
        dailyForecast: List<MsnDailyForecast>?,
    ): List<DailyWrapper>? {
        return dailyForecast?.map { result ->
            DailyWrapper(
                date = result.valid ?: throw InvalidOrIncompleteDataException(),
                day = getHalfDay(
                    result.day,
                    result.tempHi?.celsius,
                    result.feelsHi?.celsius
                ),
                night = getHalfDay(
                    result.night,
                    result.tempLo?.celsius,
                    result.feelsLo?.celsius
                ),
                uV = UV(index = result.uv),
                relativeHumidity = DailyRelativeHumidity(
                    average = result.rh?.percent,
                    max = result.rhHi?.percent,
                    min = result.rhLo?.percent
                ),
                dewPoint = DailyDewPoint(average = result.dewPt?.celsius),
                pressure = DailyPressure(average = result.baro?.hectopascals),
                visibility = DailyVisibility(average = result.vis?.kilometers)
            )
        }
    }

    private fun getHalfDay(
        halfDay: MsnHalfDay?,
        temperature: Temperature?,
        feelsLike: Temperature?,
    ): HalfDayWrapper? {
        if (halfDay == null) return null
        return HalfDayWrapper(
            weatherText = halfDay.cap,
            weatherSummary = halfDay.summary,
            weatherCode = getWeatherCode(halfDay.symbol),
            temperature = TemperatureWrapper(
                temperature = temperature,
                feelsLike = feelsLike
            ),
            precipitationProbability = PrecipitationProbability(
                total = halfDay.precip?.percent
            ),
            wind = Wind(
                degree = halfDay.windDir,
                speed = halfDay.windSpd?.kilometersPerHour
            )
        )
    }

    private fun getHourlyForecast(
        dailyForecast: List<MsnDailyForecast>?,
    ): List<HourlyWrapper>? {
        val hourlyList = dailyForecast?.flatMap { it.hourly ?: emptyList() }
        if (hourlyList.isNullOrEmpty()) return null
        return hourlyList.map { result ->
            HourlyWrapper(
                date = result.valid ?: throw InvalidOrIncompleteDataException(),
                weatherText = result.cap,
                weatherCode = getWeatherCode(result.symbol),
                temperature = TemperatureWrapper(
                    temperature = result.temp?.celsius,
                    feelsLike = result.feels?.celsius
                ),
                precipitation = Precipitation(
                    rain = result.rainAmount?.millimeters,
                    snow = result.snowAmount?.millimeters
                ),
                precipitationProbability = PrecipitationProbability(
                    total = result.precip?.percent
                ),
                wind = Wind(
                    degree = result.windDir,
                    speed = result.windSpd?.kilometersPerHour,
                    gusts = result.windGust?.kilometersPerHour
                ),
                uV = UV(index = result.uv),
                relativeHumidity = result.rh?.percent,
                dewPoint = result.dewPt?.celsius,
                pressure = result.baro?.hectopascals,
                cloudCover = result.cloudCover?.percent,
                visibility = result.vis?.kilometers
            )
        }
    }

    private fun getAlertList(
        alertList: List<MsnAlert>?,
    ): List<Alert>? {
        if (alertList.isNullOrEmpty()) return null
        return alertList.map { alert ->
            val severity = getAlertSeverity(alert.severity ?: alert.level)
            Alert(
                alertId = alert.id
                    ?: Objects.hash(alert.title, alert.severity, alert.start).toString(),
                startDate = alert.start,
                endDate = alert.end,
                headline = alert.title ?: alert.event,
                description = alert.safetyGuide,
                source = alert.credit,
                severity = severity,
                color = Alert.colorFromSeverity(severity)
            )
        }
    }

    private fun getWeatherCode(
        symbol: String?,
    ): WeatherCode? {
        // Sky codes are prefixed with "d" (daytime) or "n" (night), e.g. "d200".
        // A 4th digit may follow (e.g. "d1000"): the website truncates it, except
        // for its "1" value combined with a sky code starting with "0" to "4" and
        // a "0" second digit, which is a windy variant of the condition.
        if (symbol.isNullOrEmpty() || symbol.length < 4) return null
        if (symbol.length >= 5 &&
            symbol[1] in '0'..'4' &&
            symbol[2] == '0' &&
            symbol[4] == '1'
        ) {
            return WeatherCode.WIND
        }
        return when (symbol.substring(1, 4)) {
            "000", "100" -> WeatherCode.CLEAR // Sunny, mostly sunny
            "200" -> WeatherCode.PARTLY_CLOUDY
            "300", "400", "500" -> WeatherCode.CLOUDY // Mostly cloudy, cloudy
            "210", "220", "310", "320", "410", "420", "430" -> WeatherCode.RAIN
            "211", "221", "311", "321", "411", "421", "431", "603" -> WeatherCode.SLEET
            "212", "222", "312", "322", "412", "422", "432" -> WeatherCode.SNOW
            "240", "340", "440" -> WeatherCode.THUNDERSTORM
            "600" -> WeatherCode.FOG
            "605", "705", "905" -> WeatherCode.HAIL
            "900", "907" -> WeatherCode.HAZE
            else -> null
        }
    }

    private fun getAlertSeverity(
        severity: String?,
    ): AlertSeverity {
        return when (severity?.lowercase()) {
            "extreme" -> AlertSeverity.EXTREME
            "severe" -> AlertSeverity.SEVERE
            "warning", "moderate" -> AlertSeverity.MODERATE
            "advisory", "watch", "minor" -> AlertSeverity.MINOR
            else -> AlertSeverity.UNKNOWN
        }
    }

    /**
     * MSN serves localized weather text: most languages need a market part,
     * and Chinese needs an explicit script variant.
     */
    private fun getLocale(
        context: Context,
    ): String {
        val locale = context.currentLocale
        return if (locale.isChinese) {
            if (locale.isTraditionalChinese || locale.script.equals("Hant", ignoreCase = true)) {
                "zh-hant"
            } else {
                "zh-hans"
            }
        } else {
            locale.codeWithCountry
        }
    }

    companion object {
        private const val MSN_BASE_URL = "https://assets.msn.cn/"

        // Public key and identifiers used by the MSN Weather web app
        private const val MSN_API_KEY = "j5i4gDqHL6nGYwx5wi5kRhXjtf2c5qgFX9fzfk0TOo"
        private const val MSN_APP_ID = "9e21380c-ff19-4c78-b4ea-19558e93a5d3"
        private const val MSN_OCID = "msftweather"
        private const val MSN_WEATHER_DAYS = 10
    }
}
