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

package org.breezyweather.sources

import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.breezyweather.common.utils.ISO8601Utils
import org.breezyweather.sources.msn.getAlertSeverity
import org.breezyweather.sources.msn.getWeatherCode
import org.breezyweather.sources.msn.json.MsnWeatherResult
import org.junit.jupiter.api.Test

class MsnServiceTest {

    private val json = Json {
        // Same settings as the "JsonSerializer" converter factory
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodeOverviewTest() {
        // Real payload captured from the live endpoint on 2026-09-06:
        // Beijing current observation and forecast, Manila flood alert.
        // Hourly entries of day 2 were trimmed for size.
        val weather = json.decodeFromString<MsnWeatherResult>(
            javaClass.getResourceAsStream("/msn_overview.json")!!
                .bufferedReader()
                .use { it.readText() }
        ).responses?.firstOrNull()?.weather?.firstOrNull()!!

        val current = weather.current!!
        current.temp shouldBe 30.0
        current.symbol shouldBe "d0000"
        current.windDir shouldBe 168.0

        val days = weather.forecast!!.days!!
        days.size shouldBe 2

        // The daily summary is nested under "daily", it is not a sibling of
        // "hourly": forgetting this nesting makes the whole source fail
        val daily0 = days[0].daily!!
        daily0.valid shouldBe ISO8601Utils.parse("2026-09-06T00:00:00+08:00")

        val daily1 = days[1].daily!!
        daily1.valid shouldBe ISO8601Utils.parse("2026-09-07T00:00:00+08:00")
        daily1.tempHi shouldBe 31.0
        daily1.tempLo shouldBe 18.0
        daily1.day!!.cap shouldBe "小阵雨"
        daily1.day!!.symbol shouldBe "d2100"
        daily1.day!!.precip shouldBe 56.0
        daily1.night!!.symbol shouldBe "n4200"

        // "hourly" is a sibling of "daily" in each day
        days[0].hourly!!.size shouldBe 11
        days[1].hourly!!.size shouldBe 3
        val hourly0 = days[0].hourly!![0]
        hourly0.valid shouldBe ISO8601Utils.parse("2026-09-06T13:00:00+08:00")
        hourly0.temp shouldBe 31.0

        val alert = weather.alerts!![0]
        alert.id shouldBe "1357"
        alert.title shouldBe "Flood - Moderate"
        alert.level shouldBe "Moderate"
        alert.severity shouldBe "Moderate"
        alert.start shouldBe ISO8601Utils.parse("2026-09-06T05:33:30+08:00")
        alert.end shouldBe ISO8601Utils.parse("2026-09-06T17:33:30+08:00")
        alert.safetyGuide shouldBe
            "Prepare for possible flooding. Pay close attention to weather forecast and alerts."
    }

    @Test
    fun getWeatherCodeTest() {
        getWeatherCode(null) shouldBe null
        getWeatherCode("") shouldBe null
        getWeatherCode("d00") shouldBe null

        // Values observed from the live endpoint
        getWeatherCode("d0000") shouldBe WeatherCode.CLEAR
        getWeatherCode("n0000") shouldBe WeatherCode.CLEAR
        getWeatherCode("d1000") shouldBe WeatherCode.CLEAR // "Mostly sunny"
        getWeatherCode("n1000") shouldBe WeatherCode.CLEAR
        getWeatherCode("d2000") shouldBe WeatherCode.PARTLY_CLOUDY
        getWeatherCode("n2000") shouldBe WeatherCode.PARTLY_CLOUDY
        getWeatherCode("d3000") shouldBe WeatherCode.CLOUDY
        getWeatherCode("n3000") shouldBe WeatherCode.CLOUDY
        getWeatherCode("d4000") shouldBe WeatherCode.CLOUDY
        getWeatherCode("d2100") shouldBe WeatherCode.RAIN // "Light rain showers"
        getWeatherCode("d3100") shouldBe WeatherCode.RAIN
        getWeatherCode("n3100") shouldBe WeatherCode.RAIN
        getWeatherCode("n3200") shouldBe WeatherCode.RAIN // "Rain showers"
        getWeatherCode("d4100") shouldBe WeatherCode.RAIN // "Light rain"
        getWeatherCode("n4200") shouldBe WeatherCode.RAIN // "Rain"
        getWeatherCode("n3120") shouldBe WeatherCode.SNOW // "Light snow showers"
        getWeatherCode("d3220") shouldBe WeatherCode.SNOW
        getWeatherCode("n3110") shouldBe WeatherCode.SLEET
        getWeatherCode("d2400") shouldBe WeatherCode.THUNDERSTORM
        getWeatherCode("d6000") shouldBe WeatherCode.FOG
        getWeatherCode("d6050") shouldBe WeatherCode.HAIL
        getWeatherCode("d9000") shouldBe WeatherCode.HAZE
        getWeatherCode("d9999") shouldBe null

        // The 4th digit "1" is a windy variant, which wins over the base
        // condition of sky codes (e.g. "Partly sunny/Wind")
        getWeatherCode("d0001") shouldBe WeatherCode.WIND
        getWeatherCode("d2001") shouldBe WeatherCode.WIND
        getWeatherCode("n3001") shouldBe WeatherCode.WIND
        // Precipitation still wins for rain/wind combinations
        getWeatherCode("d3101") shouldBe WeatherCode.RAIN // "Light rain showers/Wind"
        // The 4th digit "0" is truncated (e.g. "d1000" is "Mostly sunny")
        getWeatherCode("d1000") shouldBe WeatherCode.CLEAR
    }

    @Test
    fun getAlertSeverityTest() {
        getAlertSeverity(null) shouldBe AlertSeverity.UNKNOWN
        getAlertSeverity("Extreme") shouldBe AlertSeverity.EXTREME
        getAlertSeverity("Severe") shouldBe AlertSeverity.SEVERE
        getAlertSeverity("Moderate") shouldBe AlertSeverity.MODERATE
        getAlertSeverity("Warning") shouldBe AlertSeverity.MODERATE
        getAlertSeverity("Advisory") shouldBe AlertSeverity.MINOR
        getAlertSeverity("Watch") shouldBe AlertSeverity.MINOR
        getAlertSeverity("Minor") shouldBe AlertSeverity.MINOR
        // Some providers localize "severity" (e.g. Japanese), the caller must
        // fall back to "level" in this case
        getAlertSeverity("注意報") shouldBe AlertSeverity.UNKNOWN
    }
}
