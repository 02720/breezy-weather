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
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.BuildConfig
import org.breezyweather.R
import org.breezyweather.common.exceptions.ApiLimitReachedException
import org.breezyweather.common.exceptions.ApiUnauthorizedException
import org.breezyweather.common.exceptions.LocationSearchException
import org.breezyweather.common.preference.EditTextPreference
import org.breezyweather.common.preference.Preference
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.sources.amap.json.AmapPoi
import retrofit2.Retrofit
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
                ?.mapNotNull { convertLocation(it) }
                ?: emptyList()
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

    private fun convertLocation(poi: AmapPoi): LocationAddressInfo? {
        // Amap returns coordinates as "longitude,latitude"
        val coordinates = poi.location?.split(",") ?: return null
        val longitude = coordinates.getOrNull(0)?.toDoubleOrNull() ?: return null
        val latitude = coordinates.getOrNull(1)?.toDoubleOrNull() ?: return null

        return LocationAddressInfo(
            latitude = latitude,
            longitude = longitude,
            // Timezone is not provided by Amap: it will be resolved from the location
            countryCode = "CN",
            admin1 = poi.pname,
            admin1Code = poi.pcode,
            admin2 = poi.cityname,
            city = poi.cityname,
            district = poi.adname,
            cityCode = poi.id
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
    }
}
