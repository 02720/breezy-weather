package org.breezyweather.sources

import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import org.breezyweather.sources.cma.cmaMissingValue
import org.breezyweather.sources.cma.cmaSanitized
import org.breezyweather.sources.cma.getCmaAlertLevelName
import org.breezyweather.sources.cma.getCmaAlertSeverity
import org.breezyweather.sources.cma.getCmaDistanceKm
import org.breezyweather.sources.cma.getCmaProvinceCode
import org.breezyweather.sources.cma.getCmaWeatherCode
import org.junit.jupiter.api.Test

class CmaConvertersTest {

    @Test
    fun getWeatherCodeTest() {
        getCmaWeatherCode(null).shouldBeNull()
        getCmaWeatherCode(0) shouldBe WeatherCode.CLEAR
        getCmaWeatherCode(1) shouldBe WeatherCode.PARTLY_CLOUDY
        getCmaWeatherCode(2) shouldBe WeatherCode.CLOUDY
        getCmaWeatherCode(3) shouldBe WeatherCode.RAIN
        getCmaWeatherCode(4) shouldBe WeatherCode.THUNDERSTORM
        getCmaWeatherCode(5) shouldBe WeatherCode.HAIL
        getCmaWeatherCode(6) shouldBe WeatherCode.SLEET
        getCmaWeatherCode(7) shouldBe WeatherCode.RAIN
        getCmaWeatherCode(13) shouldBe WeatherCode.SNOW
        getCmaWeatherCode(18) shouldBe WeatherCode.FOG
        getCmaWeatherCode(19) shouldBe WeatherCode.SLEET
        getCmaWeatherCode(20) shouldBe WeatherCode.HAZE
        // Transition codes seen in production
        getCmaWeatherCode(21) shouldBe WeatherCode.RAIN
        getCmaWeatherCode(25) shouldBe WeatherCode.RAIN
        getCmaWeatherCode(28) shouldBe WeatherCode.SNOW
        getCmaWeatherCode(31) shouldBe WeatherCode.HAZE
        getCmaWeatherCode(53) shouldBe WeatherCode.HAZE
        // Missing-data sentinel and unmapped codes
        getCmaWeatherCode(9999).shouldBeNull()
        getCmaWeatherCode(-1).shouldBeNull()
        getCmaWeatherCode(99).shouldBeNull()
    }

    @Test
    fun cmaSanitizedTest() {
        (null as Double?).cmaSanitized(-100.0, 100.0).shouldBeNull()
        // Missing-data sentinels are always outside physical bounds
        (9999.0).cmaSanitized(0.0, 100.0).shouldBeNull()
        (9999.0).cmaSanitized(-100.0, 100.0).shouldBeNull()
        (-9999.0).cmaSanitized(0.0, 360.0).shouldBeNull()
        (-999.0).cmaSanitized(-100.0, 100.0).shouldBeNull()
        (Double.NaN).cmaSanitized(-100.0, 100.0).shouldBeNull()
        (28.4).cmaSanitized(-100.0, 100.0) shouldBe 28.4
        (0.0).cmaSanitized(0.0, 360.0) shouldBe 0.0
        (360.0).cmaSanitized(0.0, 360.0) shouldBe 360.0
    }

    @Test
    fun cmaMissingValueTest() {
        "9999".cmaMissingValue() shouldBe true
        "9998".cmaMissingValue() shouldBe true
        "-999".cmaMissingValue() shouldBe true
        "-9999".cmaMissingValue() shouldBe true
        " 9999 ".cmaMissingValue() shouldBe true
        "小雨".cmaMissingValue() shouldBe false
        "0".cmaMissingValue() shouldBe false
        "".cmaMissingValue() shouldBe false
        null.cmaMissingValue() shouldBe false
    }

    @Test
    fun getProvinceCodeTest() {
        getCmaProvinceCode("450405") shouldBe "450000"
        getCmaProvinceCode("110105") shouldBe "110000"
        getCmaProvinceCode("810000") shouldBe "810000" // Hong Kong
        getCmaProvinceCode(null).shouldBeNull()
        getCmaProvinceCode("").shouldBeNull()
        getCmaProvinceCode("4504").shouldBeNull()
        getCmaProvinceCode("4504056").shouldBeNull()
        getCmaProvinceCode("45040a").shouldBeNull()
    }

    @Test
    fun getAlertLevelNameTest() {
        getCmaAlertLevelName("Red") shouldBe "红色"
        getCmaAlertLevelName("ORANGE") shouldBe "橙色"
        getCmaAlertLevelName("Yellow") shouldBe "黄色"
        getCmaAlertLevelName("blue") shouldBe "蓝色"
        getCmaAlertLevelName("White") shouldBe "白色"
        getCmaAlertLevelName("Unknown").shouldBeNull()
        getCmaAlertLevelName(null).shouldBeNull()
    }

    @Test
    fun getAlertSeverityTest() {
        getCmaAlertSeverity("Red") shouldBe AlertSeverity.EXTREME
        getCmaAlertSeverity("Orange") shouldBe AlertSeverity.SEVERE
        getCmaAlertSeverity("Yellow") shouldBe AlertSeverity.MODERATE
        getCmaAlertSeverity("Blue") shouldBe AlertSeverity.MINOR
        getCmaAlertSeverity("White") shouldBe AlertSeverity.MINOR
        // Unknown severities are dropped, like the official website does
        getCmaAlertSeverity("Unknown").shouldBeNull()
        getCmaAlertSeverity(null).shouldBeNull()
    }

    @Test
    fun getDistanceKmTest() {
        // Same point
        getCmaDistanceKm(23.478, 111.278, 23.478, 111.278) shouldBe 0.0
        // Wuzhou (Changzhou district) -> Rong County: ~101 km, borderline for the
        // fallback distance filter, which is why area-based filtering is preferred
        val wuzhouToRong = getCmaDistanceKm(23.478, 111.278, 22.857, 110.555)
        (wuzhouToRong > 95.0 && wuzhouToRong < 110.0) shouldBe true
        // Beijing -> Lhasa, roughly 2600 km
        val beijingToLhasa = getCmaDistanceKm(39.9042, 116.4074, 29.6520, 91.1721)
        (beijingToLhasa > 2500.0 && beijingToLhasa < 2700.0) shouldBe true
    }
}
