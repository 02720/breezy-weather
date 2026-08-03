package org.breezyweather.sources

import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.breezyweather.sources.caiyun.getAlertColor
import org.breezyweather.sources.caiyun.getAlertSeverity
import org.breezyweather.sources.caiyun.getWeatherCode
import org.breezyweather.sources.caiyun.getWeatherText
import org.junit.jupiter.api.Test

class CaiyunWeatherCodesTest {

    @Test
    fun getWeatherCodeTest() = runTest {
        getWeatherCode(null) shouldBe null
        getWeatherCode("CLEAR_DAY") shouldBe WeatherCode.CLEAR
        getWeatherCode("CLEAR_NIGHT") shouldBe WeatherCode.CLEAR
        getWeatherCode("PARTLY_CLOUDY_DAY") shouldBe WeatherCode.PARTLY_CLOUDY
        getWeatherCode("PARTLY_CLOUDY_NIGHT") shouldBe WeatherCode.PARTLY_CLOUDY
        getWeatherCode("CLOUDY") shouldBe WeatherCode.CLOUDY
        // Not documented in the API tables, but returned by the real API
        getWeatherCode("OVERCAST") shouldBe WeatherCode.CLOUDY
        getWeatherCode("LIGHT_HAZE") shouldBe WeatherCode.HAZE
        getWeatherCode("MODERATE_HAZE") shouldBe WeatherCode.HAZE
        getWeatherCode("HEAVY_HAZE") shouldBe WeatherCode.HAZE
        getWeatherCode("LIGHT_RAIN") shouldBe WeatherCode.RAIN
        getWeatherCode("MODERATE_RAIN") shouldBe WeatherCode.RAIN
        getWeatherCode("HEAVY_RAIN") shouldBe WeatherCode.RAIN
        getWeatherCode("STORM_RAIN") shouldBe WeatherCode.RAIN
        getWeatherCode("FOG") shouldBe WeatherCode.FOG
        getWeatherCode("LIGHT_SNOW") shouldBe WeatherCode.SNOW
        getWeatherCode("MODERATE_SNOW") shouldBe WeatherCode.SNOW
        getWeatherCode("HEAVY_SNOW") shouldBe WeatherCode.SNOW
        getWeatherCode("STORM_SNOW") shouldBe WeatherCode.SNOW
        getWeatherCode("DUST") shouldBe WeatherCode.WIND
        getWeatherCode("SAND") shouldBe WeatherCode.WIND
        getWeatherCode("WIND") shouldBe WeatherCode.WIND
        getWeatherCode("HAIL") shouldBe WeatherCode.HAIL
        getWeatherCode("THUNDER") shouldBe WeatherCode.THUNDERSTORM
        getWeatherCode("THUNDER_SHOWER") shouldBe WeatherCode.THUNDERSTORM
        getWeatherCode("UNKNOWN_CONDITION") shouldBe null
    }

    @Test
    fun getWeatherTextTest() = runTest {
        getWeatherText(null, chinese = false) shouldBe null
        getWeatherText("CLEAR_DAY", chinese = false) shouldBe "Clear"
        getWeatherText("CLEAR_DAY", chinese = true) shouldBe "晴"
        getWeatherText("PARTLY_CLOUDY_DAY", chinese = true) shouldBe "多云"
        getWeatherText("CLOUDY", chinese = true) shouldBe "阴"
        getWeatherText("LIGHT_RAIN", chinese = true) shouldBe "小雨"
        getWeatherText("MODERATE_RAIN", chinese = true) shouldBe "中雨"
        getWeatherText("HEAVY_RAIN", chinese = true) shouldBe "大雨"
        getWeatherText("STORM_RAIN", chinese = true) shouldBe "暴雨"
        getWeatherText("LIGHT_HAZE", chinese = true) shouldBe "轻度霾"
        getWeatherText("MODERATE_HAZE", chinese = true) shouldBe "中度霾"
        getWeatherText("HEAVY_HAZE", chinese = true) shouldBe "重度霾"
        getWeatherText("FOG", chinese = true) shouldBe "雾"
        getWeatherText("LIGHT_SNOW", chinese = true) shouldBe "小雪"
        getWeatherText("MODERATE_SNOW", chinese = true) shouldBe "中雪"
        getWeatherText("HEAVY_SNOW", chinese = true) shouldBe "大雪"
        getWeatherText("STORM_SNOW", chinese = true) shouldBe "暴雪"
        getWeatherText("DUST", chinese = true) shouldBe "浮尘"
        getWeatherText("SAND", chinese = true) shouldBe "沙尘"
        getWeatherText("WIND", chinese = true) shouldBe "大风"
        getWeatherText("HAIL", chinese = true) shouldBe "冰雹"
        getWeatherText("THUNDER", chinese = true) shouldBe "雷阵雨"
        getWeatherText("THUNDER_SHOWER", chinese = true) shouldBe "雷阵雨伴有冰雹"
        getWeatherText("MODERATE_RAIN", chinese = false) shouldBe "Moderate rain"
        getWeatherText("UNKNOWN_CONDITION", chinese = false) shouldBe null
    }

    @Test
    fun getAlertSeverityTest() = runTest {
        getAlertSeverity(null) shouldBe AlertSeverity.UNKNOWN
        getAlertSeverity("minor") shouldBe AlertSeverity.MINOR
        getAlertSeverity("MODERATE") shouldBe AlertSeverity.MODERATE
        getAlertSeverity("major") shouldBe AlertSeverity.SEVERE
        getAlertSeverity("critical") shouldBe AlertSeverity.EXTREME
        getAlertSeverity("extreme") shouldBe AlertSeverity.EXTREME
        getAlertSeverity("unknown") shouldBe AlertSeverity.UNKNOWN
    }

    @Test
    fun getAlertColorTest() = runTest {
        getAlertColor(null) shouldBe null
        getAlertColor("red") shouldBe android.graphics.Color.rgb(215, 48, 42)
        getAlertColor("ORANGE") shouldBe android.graphics.Color.rgb(249, 138, 30)
        getAlertColor("yellow") shouldBe android.graphics.Color.rgb(250, 237, 36)
        getAlertColor("blue") shouldBe android.graphics.Color.rgb(51, 100, 255)
        getAlertColor("green") shouldBe null
    }
}
