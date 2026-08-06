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

import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.NonFreeNetSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_NONE

/**
 * 和风天气 (QWeather). A worldwide multi-model blended forecast with strong China coverage,
 * also providing air quality, minutely precipitation (China only) and official weather alerts.
 *
 * The actual implementation lives in the src_freenet and src_nonfreenet folders.
 */
abstract class QWeatherServiceStub() :
    HttpSource(),
    WeatherSource,
    ConfigurableSource,
    NonFreeNetSource {

    override val id = "qweather"
    override val name = "和风天气"
    override val continent = SourceContinent.WORLDWIDE

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to name,
        SourceFeature.CURRENT to name,
        SourceFeature.AIR_QUALITY to name,
        SourceFeature.MINUTELY to name,
        SourceFeature.ALERT to name
    )

    /**
     * Forecast, current, air quality and alerts are available worldwide.
     * Minutely precipitation is a China-only product (1 km resolution), so it is gated to CN.
     */
    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return when (feature) {
            SourceFeature.MINUTELY -> location.countryCode.equals("CN", ignoreCase = true)
            else -> true
        }
    }

    /**
     * QWeather is a worldwide commercial source, not a national meteorological service, so it is
     * never auto-recommended over national sources (returns [PRIORITY_NONE]). Users who configure
     * it explicitly can still select it in the source chooser.
     */
    override fun getFeaturePriorityForLocation(
        location: Location,
        feature: SourceFeature,
    ): Int = PRIORITY_NONE
}
