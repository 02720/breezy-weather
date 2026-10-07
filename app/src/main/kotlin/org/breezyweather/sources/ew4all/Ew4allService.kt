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

package org.breezyweather.sources.ew4all

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Precipitation
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HourlyWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.breezyweather.R
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.exceptions.WeatherException
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.preference.ListPreference
import org.breezyweather.common.preference.Preference
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.domain.settings.SourceConfigStore
import org.breezyweather.sources.ew4all.json.Ew4allPointStep
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.ratio.Ratio.Companion.percent
import org.breezyweather.unit.speed.Speed.Companion.metersPerSecond
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * EW4ALL (Cloud-based Early Warning Supporting System) service.
 *
 * Worldwide forecast from the point query interfaces of the CMA early warning
 * platform http://ew4all.wmc-bj.net/EW4ALL/predictions, with a choice of
 * three models: CMA-NDFS (5 km intelligent grid), CMA-GFS and the AI model
 * 风清AI (FENGQING). No API key required.
 *
 * The models output every 1, 3 or 6 hours depending on the model; the series
 * is linearly interpolated to an hourly forecast. Model runs are available per
 * element, and precipitation rasters are usually published some time after
 * the temperature and wind ones, so each element is queried with its own
 * latest run. The server is only reachable over cleartext HTTP, which is
 * allowed for this host in the network security configuration.
 */
class Ew4allService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") client: Retrofit.Builder,
) : HttpSource(), WeatherSource, ConfigurableSource {

    override val id = "ew4all"
    override val name = "EW4ALL"
    override val continent = SourceContinent.WORLDWIDE
    override val privacyPolicyUrl = "http://ew4all.wmc-bj.net/EW4ALL/predictions"
    override val attributionLinks = mapOf(
        name to "http://ew4all.wmc-bj.net/EW4ALL/predictions"
    )

    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to name
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
            forecastSource = id
        ),
        Location(
            city = "New York",
            latitude = 40.7128,
            longitude = -74.0060,
            timeZone = TimeZone.getTimeZone("America/New_York"),
            country = "United States",
            countryCode = "US",
            forecastSource = id
        )
    )

    private val mApi by lazy {
        client
            .baseUrl(BASE_URL)
            .build()
            .create(Ew4allApi::class.java)
    }

    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()
        if (SourceFeature.FORECAST !in requestedFeatures) {
            return Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
        }
        val model = getModel()
        val longitude = location.longitude.normalizeLongitude()
        return getLatestRunObservable(model.dataType, ELEMENT_TEMPERATURE)
            .flatMap { modelRun ->
                if (modelRun == null) {
                    // No model run published at the moment
                    failedFeatures[SourceFeature.FORECAST] = WeatherException()
                    Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
                } else {
                    // The temperature series carries the finest time step and
                    // defines the timeline of the forecast
                    Observable.zip(
                        getPointObservable(
                            model.dataType, ELEMENT_TEMPERATURE, modelRun, longitude, location.latitude
                        ),
                        getRequiredPointObservable(
                            model.dataType, ELEMENT_WIND, longitude, location.latitude
                        ),
                        getOptionalPointObservable(
                            model.dataType, ELEMENT_HUMIDITY, longitude, location.latitude
                        ),
                        getOptionalPointObservable(
                            model.dataType, ELEMENT_PRECIPITATION, longitude, location.latitude
                        )
                    ) { temperatureSteps, windSteps, humiditySteps, precipitationSteps ->
                        convert(
                            temperatureSteps,
                            windSteps,
                            humiditySteps,
                            precipitationSteps,
                            location,
                            failedFeatures
                        )
                    }
                }
            }
            .onErrorResumeNext { e ->
                failedFeatures[SourceFeature.FORECAST] = e
                Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
            }
    }

    /**
     * Returns the latest model run available for one element, as a UTC
     * "yyyyMMddHH" string, or null when none is published. Availability is
     * tracked per element by the API.
     */
    private fun getLatestRunObservable(
        dataType: String,
        element: String,
    ): Observable<String?> {
        return mApi.getModelTimeList(dataType, element)
            .map { timeList ->
                if (timeList.code != CODE_SUCCESS) {
                    null
                } else {
                    // The runs are returned as descending UTC "yyyyMMddHHmmss"
                    // strings, so the lexicographic maximum is the latest run;
                    // the point query expects it truncated to "yyyyMMddHH"
                    timeList.data.orEmpty()
                        .mapNotNull { it.dataTime?.takeIf { time -> time.length >= 10 } }
                        .maxOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?.substring(0, 10)
                }
            }
    }

    /**
     * Queries one element series at one point for one model run.
     */
    private fun getPointObservable(
        dataType: String,
        element: String,
        dataTime: String,
        longitude: Double,
        latitude: Double,
    ): Observable<List<Ew4allPointStep>> {
        val body = ("""{"mode":"$dataType",""" +
            """"elements":"$element",""" +
            """"projection":$PROJECTION,""" +
            """"dataTime":"$dataTime",""" +
            """"level":0,""" +
            """"point":[[$longitude,$latitude]]}""")
            .toRequestBody("application/json".toMediaTypeOrNull())
        return mApi.findByPoint(body)
            .map { result ->
                if (result.code != CODE_SUCCESS) throw WeatherException()
                result.data.orEmpty()
            }
    }

    /**
     * Queries one element series at one point, using the latest run available
     * for that element. A missing run or an empty series degrades to an empty
     * list, whereas a query error propagates.
     */
    private fun getRequiredPointObservable(
        dataType: String,
        element: String,
        longitude: Double,
        latitude: Double,
    ): Observable<List<Ew4allPointStep>> {
        return getLatestRunObservable(dataType, element)
            .flatMap { run ->
                if (run == null) {
                    Observable.just(emptyList<Ew4allPointStep>())
                } else {
                    getPointObservable(dataType, element, run, longitude, latitude)
                }
            }
    }

    /**
     * Humidity and precipitation support varies the most between models, so
     * any failure degrades to an empty series instead of failing the whole
     * forecast.
     */
    private fun getOptionalPointObservable(
        dataType: String,
        element: String,
        longitude: Double,
        latitude: Double,
    ): Observable<List<Ew4allPointStep>> {
        return getRequiredPointObservable(dataType, element, longitude, latitude)
            .onErrorResumeNext { Observable.just(emptyList<Ew4allPointStep>()) }
    }

    private fun convert(
        temperatureSteps: List<Ew4allPointStep>,
        windSteps: List<Ew4allPointStep>,
        humiditySteps: List<Ew4allPointStep>,
        precipitationSteps: List<Ew4allPointStep>,
        location: Location,
        failedFeatures: MutableMap<SourceFeature, Throwable>,
    ): WeatherWrapper {
        val windByTime = windSteps.mapNotNull { step ->
            step.datetime?.toUtcDate()?.let { it to step }
        }.toMap()
        val humidityByTime = humiditySteps.mapNotNull { step ->
            step.datetime?.toUtcDate()?.let { it to step }
        }.toMap()
        val precipitationByTime = precipitationSteps.mapNotNull { step ->
            step.datetime?.toUtcDate()?.let { it to step }
        }.toMap()

        val points = temperatureSteps.mapNotNull { step ->
            val date = step.datetime?.toUtcDate() ?: return@mapNotNull null
            val direction = clean(windByTime[date]?.windDirection)
            val speed = clean(windByTime[date]?.windSpeed)
            Ew4allPoint(
                date = date,
                temperature = clean(step.temperature),
                relativeHumidity = clean(humidityByTime[date]?.relativeHumidity),
                // Convert the wind given as speed/direction to u/v components
                // so that the direction stays continuous when interpolating
                u = if (direction != null && speed != null) -speed * sin(Math.toRadians(direction)) else null,
                v = if (direction != null && speed != null) -speed * cos(Math.toRadians(direction)) else null,
                // SIXTPE is the precipitation accumulated over the preceding 6
                // hours: turn it into an average hourly rate before interpolating
                precipitationRate = clean(precipitationByTime[date]?.precipitation6Hours)
                    ?.let { it / ACCUMULATION_HOURS }
            )
        }.sortedBy { it.date.time }
        if (points.isEmpty()) {
            // The API answers with an empty "data" for locations without coverage
            failedFeatures[SourceFeature.FORECAST] = InvalidLocationException()
            return WeatherWrapper(failedFeatures = failedFeatures)
        }
        val hourlyForecast = interpolateHourly(points)
        return WeatherWrapper(
            dailyForecast = getDailyList(hourlyForecast, location),
            hourlyForecast = hourlyForecast,
            failedFeatures = failedFeatures
        )
    }

    /**
     * The models output one point every 1, 3 or 6 hours depending on the model
     * (CMA-NDFS starts hourly then switches to 3-hourly). Linearly interpolate
     * every requested variable between two consecutive points to build an
     * hourly series. Wind components are interpolated rather than
     * speed/direction to keep the circular direction continuous.
     */
    private fun interpolateHourly(
        points: List<Ew4allPoint>,
    ): List<HourlyWrapper> {
        if (points.size == 1) {
            val point = points.first()
            return listOf(buildHourly(point.date, point, point, 0.0))
        }
        val hourly = mutableListOf<HourlyWrapper>()
        val startTime = points.first().date.time
        val endTime = points.last().date.time
        var index = 0
        var time = startTime
        while (time <= endTime) {
            while (index < points.size - 2 && points[index + 1].date.time < time) {
                index++
            }
            val previous = points[index]
            val next = points[index + 1]
            val span = (next.date.time - previous.date.time).toDouble()
            val ratio = if (span > 0) (time - previous.date.time) / span else 0.0
            hourly.add(buildHourly(Date(time), previous, next, ratio))
            time += HOUR_IN_MILLIS
        }
        return hourly
    }

    private fun buildHourly(
        date: Date,
        previous: Ew4allPoint,
        next: Ew4allPoint,
        ratio: Double,
    ): HourlyWrapper {
        val u = interpolate(previous.u, next.u, ratio)
        val v = interpolate(previous.v, next.v, ratio)
        return HourlyWrapper(
            date = date,
            temperature = TemperatureWrapper(
                temperature = interpolate(previous.temperature, next.temperature, ratio)?.celsius
            ),
            relativeHumidity = interpolate(previous.relativeHumidity, next.relativeHumidity, ratio)?.percent,
            // The rate is in mm/h, so it is also the accumulation over the next hour
            precipitation = interpolate(previous.precipitationRate, next.precipitationRate, ratio)?.let {
                Precipitation(total = it.millimeters)
            },
            wind = if (u != null && v != null) {
                // Wind speed and direction from the u/v components, following
                // the meteorological convention (direction the wind blows from)
                Wind(
                    degree = (180.0 + 180.0 / Math.PI * atan2(u, v)).mod(360.0),
                    speed = sqrt(u * u + v * v).metersPerSecond
                )
            } else {
                null
            }
        )
    }

    private fun interpolate(
        previous: Double?,
        next: Double?,
        ratio: Double,
    ): Double? = when {
        previous == null -> next
        next == null -> previous
        else -> previous + (next - previous) * ratio
    }

    /**
     * Returns one DailyWrapper per local day covered by the hourly forecast, with only the date set.
     * The app computes the rest of the daily data from the hourly forecast.
     */
    private fun getDailyList(
        hourlyForecast: List<HourlyWrapper>,
        location: Location,
    ): List<DailyWrapper> {
        val dailyList = mutableListOf<DailyWrapper>()
        var lastDayDate: Date? = null
        for (hourly in hourlyForecast) {
            // Day boundaries are at local midnight
            val dayDate = hourly.date
                .toCalendarWithTimeZone(location.timeZone)
                .apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
            if (lastDayDate == null || dayDate.time != lastDayDate.time) {
                dailyList.add(DailyWrapper(date = dayDate))
                lastDayDate = dayDate
            }
        }
        return dailyList
    }

    /**
     * Parses a UTC time, e.g. "2026-10-07 06:00:00"
     */
    private fun String.toUtcDate(): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(this)
        } catch (_: Exception) {
            null
        }
    }

    /** The API reports missing values with the -9999 sentinel */
    private fun clean(value: Double?): Double? {
        return value?.takeIf { it > INVALID_VALUE }
    }

    /**
     * The website normalizes longitudes to the [-180, 180) range before
     * querying a point, so any longitude value is accepted
     */
    private fun Double.normalizeLongitude(): Double {
        return (this + 180.0).mod(360.0) - 180.0
    }

    // CONFIG
    private val config = SourceConfigStore(context, id)
    private var modelKey: String
        set(value) {
            config.edit().putString("model", value).apply()
        }
        get() = config.getString("model", null) ?: MODELS.first().key

    private fun getModel(): Ew4allModel {
        return MODELS.firstOrNull { it.key == modelKey } ?: MODELS.first()
    }

    // The source works without an API key
    override val isConfigured
        get() = true

    override val isRestricted
        get() = false

    override fun getPreferences(context: Context): List<Preference> {
        return listOf(
            ListPreference(
                titleId = R.string.settings_weather_source_ew4all_model,
                valueArrayId = R.array.ew4all_model_values,
                nameArrayId = R.array.ew4all_model_names,
                selectedKey = modelKey,
                onValueChanged = {
                    modelKey = it
                }
            )
        )
    }

    /**
     * A model selectable in the source settings. Element availability is not
     * part of the definition: the API tracks it per element through the run
     * list, so unsupported elements simply degrade to empty series.
     */
    private data class Ew4allModel(
        val key: String,
        /** data_type code of the API */
        val dataType: String,
    )

    companion object {
        private const val BASE_URL = "http://ew4all.wmc-bj.net/"

        /** The API reports success with this code */
        private const val CODE_SUCCESS = 200

        private const val PROJECTION = 4326

        private const val ELEMENT_TEMPERATURE = "TEM"

        /** 10 m wind, returned as WIN_D (direction) and WIN_S (speed) */
        private const val ELEMENT_WIND = "UV"

        private const val ELEMENT_HUMIDITY = "RHU"

        /** Precipitation accumulated over the 6 hours ending at the valid time */
        private const val ELEMENT_PRECIPITATION = "SIXTPE"

        /** SIXTPE is the precipitation accumulated over the preceding 6 hours */
        private const val ACCUMULATION_HOURS = 6.0

        private const val INVALID_VALUE = -9999.0

        private const val HOUR_IN_MILLIS = 3600_000L

        private val MODELS = listOf(
            // CMA-NDFS: 5 km intelligent grid, hourly for the first 3 days
            Ew4allModel("cma_ndfs", "GDFS5KM"),
            // CMA-GFS: global assimilation and forecast model, 3-hourly
            Ew4allModel("cma_gfs", "GRAPESGLOBAL"),
            // 风清AI: AI model, 6-hourly, up to 15 days (precipitation up to 10 days)
            Ew4allModel("fengqing", "NMCFENGQING")
        )
    }
}
