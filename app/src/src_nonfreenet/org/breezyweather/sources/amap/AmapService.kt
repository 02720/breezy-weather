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

import android.content.Context
import breezyweather.domain.location.model.LocationAddressInfo
import com.google.maps.android.SphericalUtil
import com.google.maps.android.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.BuildConfig
import org.breezyweather.R
import org.breezyweather.common.exceptions.ApiLimitReachedException
import org.breezyweather.common.exceptions.ApiUnauthorizedException
import org.breezyweather.common.exceptions.LocationSearchException
import org.breezyweather.common.exceptions.ReverseGeocodingException
import org.breezyweather.common.preference.EditTextPreference
import org.breezyweather.common.preference.Preference
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.sources.amap.json.AmapAddressComponent
import org.breezyweather.sources.amap.json.AmapPoi
import org.breezyweather.sources.amap.json.AmapRegeocode
import retrofit2.Retrofit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

class AmapService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") client: Retrofit.Builder,
) : AmapServiceStub() {

    override val attributionLinks = mapOf(
        name to "https://www.amap.com/"
    )

    private val mApi by lazy {
        client
            .baseUrl(AMAP_BASE_URL)
            .build()
            .create(AmapApi::class.java)
    }

    override fun requestLocationSearch(
        context: Context,
        query: String,
    ): Observable<List<LocationAddressInfo>> {
        return mApi.searchPlaces(
            keywords = query,
            offset = 20,
            page = 1,
            extensions = "all",
            key = getApiKeyOrDefault()
        ).map { result ->
            if (result.status != "1") {
                throw getException(result.infocode)
            }

            result.pois
                ?.mapNotNull { convertSearchedLocation(it) }
                ?: emptyList()
        }
    }

    override fun requestNearestLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
    ): Observable<List<LocationAddressInfo>> {
        // Amap expects coordinates as "longitude,latitude"
        val location = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
        return mApi.getNearestPlace(
            location = location,
            radius = NEAREST_POI_RADIUS_METERS,
            extensions = "all",
            key = getApiKeyOrDefault()
        ).map { result ->
            if (result.status != "1") {
                throw getException(result.infocode)
            }

            convertNearestLocation(latitude, longitude, result.regeocode)
                ?.let { listOf(it) }
                ?: throw ReverseGeocodingException()
        }
    }

    /**
     * Amap returns an error infocode instead of an HTTP error code
     * https://lbs.amap.com/api/webservice/guide/tools/info
     */
    private fun getException(infocode: String?): Exception {
        return when (infocode) {
            // Key invalid, expired, deleted, in conflict with IP/domain/signature binding,
            // or bound to a banned/restricted account
            "10001", "10005", "10006", "10007", "10009", "10012", "10013", "10026", "10041",
            -> ApiUnauthorizedException()

            // Daily quota or rate limit reached
            "10003", "10004", "10010", "10014", "10019", "10020", "10021", "10029", "10044", "10045",
            -> ApiLimitReachedException()

            else -> LocationSearchException()
        }
    }

    /**
     * Maps a POI returned by the keyword search API (v3/place/text).
     * The POI name (e.g. "故宫博物院") is the most specific identifier and is therefore
     * used as the city, following the same convention as ChinaService. It ends up displayed
     * by [getPlace] on the home screen, in location lists, widgets and notifications.
     */
    private fun convertSearchedLocation(poi: AmapPoi): LocationAddressInfo? {
        // Amap returns coordinates as "longitude,latitude"
        val coordinates = poi.location?.split(",") ?: return null
        val longitude = coordinates.getOrNull(0)?.toDoubleOrNull() ?: return null
        val latitude = coordinates.getOrNull(1)?.toDoubleOrNull() ?: return null

        // cityname is an empty array for direct-controlled municipalities (Beijing, Shanghai, ...)
        val cityName = poi.cityname?.takeIf { it.isNotBlank() && it != poi.pname }
        val placeName = poi.name?.takeIf { it.isNotBlank() }
            ?: cityName
            ?: poi.adname?.takeIf { it.isNotBlank() }
            ?: poi.pname?.takeIf { it.isNotBlank() }
            ?: return null

        return LocationAddressInfo(
            latitude = latitude,
            longitude = longitude,
            // Timezone is not provided by Amap: it will be resolved from the location
            countryCode = "CN",
            admin1 = poi.pname,
            admin1Code = poi.pcode,
            admin2 = cityName,
            city = placeName,
            district = poi.adname?.takeIf { it.isNotBlank() && it != placeName },
            cityCode = poi.id
        )
    }

    /**
     * Maps the response of the reverse geocoding API (v3/geocode/regeo).
     * Uses the nearest named POI when available so that the current position is labelled with
     * an actual place ("北京大学"), falls back to the street/township, district, city and
     * finally province (e.g. in a rural area or when no POI is nearby).
     */
    private fun convertNearestLocation(
        latitude: Double,
        longitude: Double,
        regeocode: AmapRegeocode?,
    ): LocationAddressInfo? {
        val component: AmapAddressComponent = regeocode?.addressComponent ?: return null
        val province = component.province?.takeIf { it.isNotBlank() }

        // Abroad: Amap coverage is China-centric; let the caller fall back to its default source.
        // Domestic responses either omit country or return "中国" (including HK/MO/TW claims).
        val country = component.country?.takeIf { it.isNotBlank() }
        if (country != null && country != "中国") {
            return null
        }

        val city = component.city?.takeIf { it.isNotBlank() && it != province }
        val district = component.district?.takeIf { it.isNotBlank() }
        val township = component.township?.takeIf { it.isNotBlank() }

        val nearestPoi = regeocode.pois
            ?.asSequence()
            ?.filter { !it.name.isNullOrBlank() }
            // Pick the geographically closest POI, don't trust the API ordering blindly
            ?.mapNotNull { poi ->
                val coordinates = poi.location?.split(",") ?: return@mapNotNull null
                val poiLongitude = coordinates.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val poiLatitude = coordinates.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                val distance = SphericalUtil.computeDistanceBetween(
                    LatLng(latitude, longitude),
                    LatLng(poiLatitude, poiLongitude)
                )
                Pair(poi, distance)
            }
            ?.minByOrNull { it.second }
            ?.first
            ?: regeocode.pois?.firstOrNull { !it.name.isNullOrBlank() }

        val placeName = nearestPoi?.name
            ?: township
            ?: district
            ?: city
            ?: province
            ?: return null // No usable address: coordinates are outside Amap coverage

        return LocationAddressInfo(
            latitude = latitude,
            longitude = longitude,
            // Timezone is not provided by Amap: it will be resolved from the location
            countryCode = "CN",
            admin1 = province,
            admin1Code = nearestPoi?.pcode,
            admin2 = city,
            admin3 = township?.takeIf { it != placeName && it != district },
            city = placeName,
            district = district?.takeIf { it != placeName },
            cityCode = component.adcode
        )
    }

    // CONFIG
    private val config = SourceConfigStore(context, id)
    private var apikey: String
        set(value) {
            config.edit().putString("apikey", value).apply()
        }
        get() = config.getString("apikey", null) ?: ""

    private fun getApiKeyOrDefault(): String {
        return apikey.ifEmpty { BuildConfig.AMAP_KEY }
    }

    override val isConfigured
        get() = getApiKeyOrDefault().isNotEmpty()

    override val isRestricted
        get() = apikey.isEmpty()

    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            EditTextPreference(
                titleId = R.string.settings_source_amap_api_key,
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
        private const val AMAP_BASE_URL = "https://restapi.amap.com/"

        /** Search radius (in meters) used to find a name for the current position */
        private const val NEAREST_POI_RADIUS_METERS = 1000
    }
}
