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

import android.graphics.Color
import androidx.annotation.ColorInt
import breezyweather.domain.weather.reference.AlertSeverity
import breezyweather.domain.weather.reference.WeatherCode

/**
 * Caiyun (ColorfulClouds) weather conditions mapping.
 *
 * The list of skycon values is documented at
 * https://docs.caiyunapp.com/weather-api/v2/v2.6/tables/skycon.html
 * Note that the API has also been observed returning "OVERCAST" for overcast
 * weather, which is not listed in the documentation.
 */
internal fun getWeatherCode(skycon: String?): WeatherCode? {
    return when (skycon) {
        "CLEAR_DAY", "CLEAR_NIGHT" -> WeatherCode.CLEAR
        "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT" -> WeatherCode.PARTLY_CLOUDY
        "CLOUDY", "OVERCAST" -> WeatherCode.CLOUDY
        "LIGHT_HAZE", "MODERATE_HAZE", "HEAVY_HAZE" -> WeatherCode.HAZE
        "LIGHT_RAIN", "MODERATE_RAIN", "HEAVY_RAIN", "STORM_RAIN" -> WeatherCode.RAIN
        "FOG" -> WeatherCode.FOG
        "LIGHT_SNOW", "MODERATE_SNOW", "HEAVY_SNOW", "STORM_SNOW" -> WeatherCode.SNOW
        "DUST", "SAND", "WIND" -> WeatherCode.WIND
        "HAIL" -> WeatherCode.HAIL
        "THUNDER", "THUNDER_SHOWER" -> WeatherCode.THUNDERSTORM
        else -> null
    }
}

internal fun getWeatherText(skycon: String?, chinese: Boolean): String? {
    return when (skycon) {
        "CLEAR_DAY", "CLEAR_NIGHT" -> if (chinese) "晴" else "Clear"
        "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT" -> if (chinese) "多云" else "Partly cloudy"
        "CLOUDY", "OVERCAST" -> if (chinese) "阴" else "Cloudy"
        "LIGHT_HAZE" -> if (chinese) "轻度霾" else "Light haze"
        "MODERATE_HAZE" -> if (chinese) "中度霾" else "Moderate haze"
        "HEAVY_HAZE" -> if (chinese) "重度霾" else "Heavy haze"
        "LIGHT_RAIN" -> if (chinese) "小雨" else "Light rain"
        "MODERATE_RAIN" -> if (chinese) "中雨" else "Moderate rain"
        "HEAVY_RAIN" -> if (chinese) "大雨" else "Heavy rain"
        "STORM_RAIN" -> if (chinese) "暴雨" else "Storm rain"
        "FOG" -> if (chinese) "雾" else "Fog"
        "LIGHT_SNOW" -> if (chinese) "小雪" else "Light snow"
        "MODERATE_SNOW" -> if (chinese) "中雪" else "Moderate snow"
        "HEAVY_SNOW" -> if (chinese) "大雪" else "Heavy snow"
        "STORM_SNOW" -> if (chinese) "暴雪" else "Storm snow"
        "DUST" -> if (chinese) "浮尘" else "Dust"
        "SAND" -> if (chinese) "沙尘" else "Sand"
        "WIND" -> if (chinese) "大风" else "Windy"
        "HAIL" -> if (chinese) "冰雹" else "Hail"
        "THUNDER" -> if (chinese) "雷阵雨" else "Thunder"
        "THUNDER_SHOWER" -> if (chinese) "雷阵雨伴有冰雹" else "Thunder shower"
        else -> null
    }
}

/**
 * Maps the Caiyun alert severity (following NOAA alert levels) to an
 * [AlertSeverity]
 */
internal fun getAlertSeverity(severity: String?): AlertSeverity {
    return when (severity?.lowercase()) {
        "minor" -> AlertSeverity.MINOR
        "moderate" -> AlertSeverity.MODERATE
        "major" -> AlertSeverity.SEVERE
        "critical", "extreme" -> AlertSeverity.EXTREME
        else -> AlertSeverity.UNKNOWN
    }
}

/**
 * Maps the Caiyun alert subtype color to a color, following the Chinese
 * warning color levels (blue, yellow, orange, red)
 */
@ColorInt
internal fun getAlertColor(color: String?): Int? {
    return when (color?.lowercase()) {
        "red" -> Color.rgb(215, 48, 42)
        "orange" -> Color.rgb(249, 138, 30)
        "yellow" -> Color.rgb(250, 237, 36)
        "blue" -> Color.rgb(51, 100, 255)
        else -> null
    }
}

/**
 * Maps the Caiyun alert subtype color to Chinese color name for display
 */
internal fun getAlertColorName(color: String?): String? {
    return when (color?.lowercase()) {
        "red" -> "红色"
        "orange" -> "橙色"
        "yellow" -> "黄色"
        "blue" -> "蓝色"
        else -> null
    }
}
