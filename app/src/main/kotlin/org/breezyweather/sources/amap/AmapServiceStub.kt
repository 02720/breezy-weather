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

package org.breezyweather.sources.amap

import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.FeatureSource
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.LocationSearchSource
import org.breezyweather.common.source.NonFreeNetSource
import org.breezyweather.common.source.ReverseGeocodingSource

/**
 * The actual implementation is in the src_freenet and src_nonfreenet folders
 */
abstract class AmapServiceStub() :
    HttpSource(),
    LocationSearchSource,
    ReverseGeocodingSource,
    ConfigurableSource,
    NonFreeNetSource {

    override val id = "amap"
    override val name = "高德地图"
    override val continent = SourceContinent.ASIA
    override val privacyPolicyUrl = "https://lbs.amap.com/pages/privacy/"

    override val locationSearchAttribution = "高德地图 (Amap)"

    override val supportedFeatures = mapOf(
        SourceFeature.REVERSE_GEOCODING to locationSearchAttribution
    )

    /**
     * Amap only covers China and its claimed territories. An unknown country code is accepted
     * because reverse geocoding is what resolves the country of the current position: if the
     * service then fails, the standard offline fallback takes over.
     */
    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return when (feature) {
            SourceFeature.REVERSE_GEOCODING ->
                location.countryCode.isNullOrBlank() ||
                    CHINA_COUNTRY_CODES.any { it.equals(location.countryCode, ignoreCase = true) }
            else -> true
        }
    }

    override fun getFeaturePriorityForLocation(
        location: Location,
        feature: SourceFeature,
    ): Int {
        return if (feature == SourceFeature.REVERSE_GEOCODING &&
            isFeatureSupportedForLocation(location, feature)
        ) {
            FeatureSource.PRIORITY_HIGHEST
        } else {
            FeatureSource.PRIORITY_NONE
        }
    }

    // Results are always returned as "CN", even for Hong Kong, Macau or Taiwan
    override val knownAmbiguousCountryCodes = arrayOf("CN")

    companion object {
        private val CHINA_COUNTRY_CODES = arrayOf("CN", "HK", "MO", "TW")
    }
}
