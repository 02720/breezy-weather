package org.breezyweather.sources

import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.breezyweather.sources.caiyun.getAlertColor
import org.breezyweather.sources.caiyun.getAlertLevelCode
import org.breezyweather.sources.caiyun.getAlertLevelColor
import org.breezyweather.sources.caiyun.getAlertLevelName
import org.breezyweather.sources.caiyun.getAlertLevelSeverity
import org.breezyweather.sources.caiyun.getAlertSeverity
import org.breezyweather.sources.caiyun.getAlertTypeCode
import org.breezyweather.sources.caiyun.getAlertTypeName
import org.breezyweather.sources.caiyun.getWeatherCode
import org.breezyweather.sources.caiyun.getWeatherText
import org.breezyweather.sources.common.buildChineseAlertHeadline
import org.breezyweather.sources.common.getCleanChineseAlertTitle
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

    @Test
    fun getAlertTypeCodeTest() = runTest {
        getAlertTypeCode(null) shouldBe null
        getAlertTypeCode("") shouldBe null
        getAlertTypeCode("123") shouldBe null
        getAlertTypeCode("0902") shouldBe "09"
    }

    @Test
    fun getAlertLevelCodeTest() = runTest {
        getAlertLevelCode(null) shouldBe null
        getAlertLevelCode("123") shouldBe null
        getAlertLevelCode("0902") shouldBe "02"
    }

    @Test
    fun getAlertTypeNameTest() = runTest {
        getAlertTypeName(null) shouldBe null
        getAlertTypeName("0902") shouldBe "雷电"
        getAlertTypeName("0501") shouldBe "大风"
        getAlertTypeName("1701") shouldBe "春季沙尘天气趋势预警"
        getAlertTypeName("9901") shouldBe null
    }

    @Test
    fun getAlertLevelNameTest() = runTest {
        getAlertLevelName(null) shouldBe null
        getAlertLevelName("0902") shouldBe "黄色"
        getAlertLevelName("0501") shouldBe "蓝色"
        getAlertLevelName("0204") shouldBe "红色"
        getAlertLevelName("0909") shouldBe null
    }

    @Test
    fun getAlertLevelColorTest() = runTest {
        getAlertLevelColor(null) shouldBe null
        getAlertLevelColor("00") shouldBe android.graphics.Color.rgb(155, 163, 170)
        getAlertLevelColor("01") shouldBe android.graphics.Color.rgb(51, 100, 255)
        getAlertLevelColor("02") shouldBe android.graphics.Color.rgb(250, 237, 36)
        getAlertLevelColor("03") shouldBe android.graphics.Color.rgb(249, 138, 30)
        getAlertLevelColor("04") shouldBe android.graphics.Color.rgb(215, 48, 42)
        getAlertLevelColor("05") shouldBe null
    }

    @Test
    fun getAlertLevelSeverityTest() = runTest {
        getAlertLevelSeverity(null) shouldBe null
        getAlertLevelSeverity("00") shouldBe AlertSeverity.MINOR
        getAlertLevelSeverity("01") shouldBe AlertSeverity.MINOR
        getAlertLevelSeverity("02") shouldBe AlertSeverity.MODERATE
        getAlertLevelSeverity("03") shouldBe AlertSeverity.SEVERE
        getAlertLevelSeverity("04") shouldBe AlertSeverity.EXTREME
        getAlertLevelSeverity("99") shouldBe null
    }

    @Test
    fun buildChineseAlertHeadlineTest() = runTest {
        buildChineseAlertHeadline(null, null) shouldBe null
        buildChineseAlertHeadline("", "黄色") shouldBe null
        buildChineseAlertHeadline("雷电", null) shouldBe null
        buildChineseAlertHeadline("雷电", "") shouldBe null
        buildChineseAlertHeadline("雷电", "黄色") shouldBe "雷电黄色预警"
        buildChineseAlertHeadline("春季沙尘天气趋势预警", "黄色") shouldBe "春季沙尘天气趋势预警"
        buildChineseAlertHeadline("  雷电  ", " 黄色 ") shouldBe "雷电黄色预警"
    }

    @Test
    fun getCleanChineseAlertTitleTest() = runTest {
        getCleanChineseAlertTitle(null) shouldBe null
        getCleanChineseAlertTitle("   ") shouldBe null
        getCleanChineseAlertTitle("雷电黄色预警") shouldBe "雷电黄色预警"
        getCleanChineseAlertTitle("平南县气象台发布雷电黄色预警信号[III级/较重]") shouldBe
            "雷电黄色预警"
        getCleanChineseAlertTitle("平南发布雷电黄色预警") shouldBe "雷电黄色预警"
        getCleanChineseAlertTitle("北京市气象台发布大风蓝色预警") shouldBe "大风蓝色预警"
        getCleanChineseAlertTitle("大雾橙色预警（已解除）") shouldBe "大雾橙色预警"
        getCleanChineseAlertTitle("大风蓝色预警（已发布）") shouldBe "大风蓝色预警"
        getCleanChineseAlertTitle("平南县气象台更新雷电黄色预警") shouldBe "雷电黄色预警"
        getCleanChineseAlertTitle("XX县气象台继续发布高温橙色预警信号") shouldBe "高温橙色预警"
        getCleanChineseAlertTitle("XX县气象台将暴雨蓝色预警升级为暴雨黄色预警") shouldBe
            "暴雨黄色预警"
        getCleanChineseAlertTitle("XX市气象台雷电黄色预警信号") shouldBe "雷电黄色预警"
        getCleanChineseAlertTitle("雷电黄色预警解除") shouldBe "雷电黄色预警"
        getCleanChineseAlertTitle("XX县人民政府防汛抗旱指挥部暴雨红色预警") shouldBe
            "暴雨红色预警"
        getCleanChineseAlertTitle("暴雨红色预警【I级/特别严重】") shouldBe "暴雨红色预警"
        getCleanChineseAlertTitle("广东省气象台发布台风白色预警信号") shouldBe "台风白色预警"
        getCleanChineseAlertTitle("XX气象台发布：暴雨红色预警信号") shouldBe "暴雨红色预警"
        getCleanChineseAlertTitle("Weather Warning issued for Pingnan") shouldBe
            "Weather Warning issued for Pingnan"
    }
}
