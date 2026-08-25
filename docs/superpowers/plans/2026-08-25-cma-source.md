# CMA（中国气象数据网）天气数据源实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `cma` 天气数据源，通过抓取 data.cma.cn 格点 GIS 页面的免费接口，提供中国区域的 7 天白天/夜间预报、当前实况与气象预警。

**Architecture:** 标准 breezy-weather 源结构：Retrofit API + kotlinx.serialization JSON 模型 + Service 类（`HttpSource, WeatherSource, LocationParametersSource, NonFreeNetSource`）。站点查找结果存入 location parameters；全国预警列表客户端按距离过滤。

**Tech Stack:** Kotlin, Retrofit(rxjava3), kotlinx.serialization, Hilt, RxJava3, breeze 单位库。

## Global Constraints

- 用户明确要求：**不做编译测试**；每个任务的验证步骤为代码走查 + 必要的 curl 重放；最终统一对抗式审查。
- 遵循 CONTRIBUTING.md：字段尽量 nullable（缺字段自动为 null，无需 `= null` 默认值——项目 Json 配置已处理）；不要“计算”源里没有的数据（Beaufort↔m/s、风向文本↔角度属于单位制转换，允许）。
- 源码风格：文件头 LGPL 注释（复制 TJWeather 文件头）；无注释除非必要；spotless 格式（4 空格缩进、行尾逗号按项目风格）。
- 所有时间解析用 `Locale.ENGLISH` + 显式时区（北京时间 `Asia/Shanghai`，datetime 参数用 UTC）。
- 接口实测记录见规格：`docs/superpowers/specs/2026-08-25-cma-source-design.md`。

---

### Task 1: JSON 数据模型

**Files:**
- Create: `app/src/main/kotlin/org/breezyweather/sources/cma/json/CmaNearStationResult.kt`
- Create: `app/src/main/kotlin/org/breezyweather/sources/cma/json/CmaStationLatest.kt`
- Create: `app/src/main/kotlin/org/breezyweather/sources/cma/json/CmaGridLiveResult.kt`
- Create: `app/src/main/kotlin/org/breezyweather/sources/cma/json/CmaAlert.kt`

**Interfaces:**
- Produces（后续任务依赖的确切类型）:
  - `CmaNearStationResult(code: String?, message: String?, data: CmaNearStationData?)`;
    `CmaNearStationData(returnCode: Int?, errorMsg: String?, dataMethod: String?, DS: CmaNearStation?)`;
    `CmaNearStation(stationId: String?, stationName: String?, province: String?, city: String?, district: String?, areacode: String?)`
  - `CmaStationLatestResult(status: Int?, code: Int?, message: String?, content: CmaStationContent?)`;
    `CmaStationContent(datetime: String?, temperature: Double?, humidity: Double?, pressure: Double?, windDirectionText: String?, windSpeed: Double?, weatherText: String?, precipitation: Double?, visibility: Double?, forecast: CmaStationForecast?)`;
    `CmaStationForecast(pre24h: String?, foreList: List<CmaForecastItem>?)`;
    `CmaForecastItem(period: String?, date: String?, weatherCode: Int?, weatherText: String?, wind: String?, temperature: String?)`
  - `CmaGridLiveResult(returnCode: String?, message: String?, list: List<CmaGridLiveElement>?)`;
    `CmaGridLiveElement(unit: String?, validTime: String?, value: String?, fastEle: String?)`
  - `CmaAlertResult(code: String?, message: String?, data: List<CmaAlert>?)`;
    `CmaAlert(identifier: String?, severity: String?, effective: String?, expires: String?, headline: String?, description: String?, senderName: String?, areaName: String?, lon: Double?, lat: Double?, status: String?)`

- [ ] **Step 1: 写入四个模型文件**

每个文件带 LGPL 文件头（从 `sources/tjweather/json/TJWeatherResult.kt` 复制），包名
`org.breezyweather.sources.cma.json`。内容：

`CmaNearStationResult.kt`：
```kotlin
package org.breezyweather.sources.cma.json

import kotlinx.serialization.Serializable

@Serializable
data class CmaNearStationResult(
    val code: String? = null,
    val message: String? = null,
    val data: CmaNearStationData? = null,
)

@Serializable
data class CmaNearStationData(
    val returnCode: Int? = null,
    val errorMsg: String? = null,
    val dataMethod: String? = null,
    val DS: CmaNearStation? = null,
)

@Serializable
data class CmaNearStation(
    val stationId: String? = null,
    val stationName: String? = null,
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val areacode: String? = null,
)
```

`CmaStationLatest.kt`：
```kotlin
package org.breezyweather.sources.cma.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CmaStationLatestResult(
    val status: Int? = null,
    val code: Int? = null,
    val message: String? = null,
    val content: CmaStationContent? = null,
)

@Serializable
data class CmaStationContent(
    @SerialName("D_datetime") val datetime: String? = null,
    @SerialName("V12001") val temperature: Double? = null,
    @SerialName("V13003") val humidity: Double? = null,
    @SerialName("V10004") val pressure: Double? = null,
    @SerialName("V11292T") val windDirectionText: String? = null,
    @SerialName("V11293") val windSpeed: Double? = null,
    @SerialName("V20003T") val weatherText: String? = null,
    @SerialName("V13019") val precipitation: Double? = null,
    @SerialName("V20001") val visibility: Double? = null,
    @SerialName("foreCast") val forecast: CmaStationForecast? = null,
)

@Serializable
data class CmaStationForecast(
    @SerialName("PRE_24h") val pre24h: String? = null,
    @SerialName("foreList") val foreList: List<CmaForecastItem>? = null,
)

@Serializable
data class CmaForecastItem(
    @SerialName("DAN") val period: String? = null,
    @SerialName("dataShow") val date: String? = null,
    @SerialName("WEP_Past_12h") val weatherCode: Int? = null,
    @SerialName("Wth") val weatherText: String? = null,
    @SerialName("win") val wind: String? = null,
    @SerialName("tem") val temperature: String? = null,
)
```

`CmaGridLiveResult.kt`：
```kotlin
package org.breezyweather.sources.cma.json

import kotlinx.serialization.Serializable

@Serializable
data class CmaGridLiveResult(
    val returnCode: String? = null,
    val message: String? = null,
    val list: List<CmaGridLiveElement>? = null,
)

@Serializable
data class CmaGridLiveElement(
    val unit: String? = null,
    val validTime: String? = null,
    val value: String? = null,
    val fastEle: String? = null,
)
```

`CmaAlert.kt`：
```kotlin
package org.breezyweather.sources.cma.json

import kotlinx.serialization.Serializable

@Serializable
data class CmaAlertResult(
    val code: String? = null,
    val message: String? = null,
    val data: List<CmaAlert>? = null,
)

@Serializable
data class CmaAlert(
    val identifier: String? = null,
    val severity: String? = null,
    val effective: String? = null,
    val expires: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val senderName: String? = null,
    val areaName: String? = null,
    val lon: Double? = null,
    val lat: Double? = null,
    val status: String? = null,
)
```

- [ ] **Step 2: 走查验证**

对照实测响应核对：`getNearStation` 的 `code` 是字符串 `"200"`、`data.returnCode` 是数字；
`station/latest` 的 `code` 是数字 `200`；`getAPILiveDataInfo` 的 `returnCode` 是字符串 `"0"`、
`list[].value/fastEle` 全为字符串；预警端点顶层 `code` 为字符串。类型不匹配即失败。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/org/breezyweather/sources/cma/json/
git commit -m "Add CMA source JSON models"
```

---

### Task 2: Retrofit API 接口

**Files:**
- Create: `app/src/main/kotlin/org/breezyweather/sources/cma/CmaApi.kt`

**Interfaces:**
- Consumes: Task 1 的全部模型。
- Produces:
  - `getNearStation(longitude: Double, latitude: Double, dist: Int): Observable<CmaNearStationResult>`
  - `getStationLatest(stationId: String, datetime: String): Observable<CmaStationLatestResult>`
  - `getGridLiveData(latitude: Double, longitude: Double): Observable<CmaGridLiveResult>`
  - `getEffectiveAlerts(): Observable<CmaAlertResult>`
  - `const val CMA_BASE_URL = "https://data.cma.cn/"`（定义在 Task 4 的 companion，API 文件只写相对路径）

- [ ] **Step 1: 写入 CmaApi.kt**（LGPL 头 + 以下内容）

```kotlin
package org.breezyweather.sources.cma

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.cma.json.CmaAlertResult
import org.breezyweather.sources.cma.json.CmaGridLiveResult
import org.breezyweather.sources.cma.json.CmaNearStationResult
import org.breezyweather.sources.cma.json.CmaStationLatestResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CmaApi {

    @GET("dataGis/api/station/getNearStation")
    fun getNearStation(
        @Query("lag") longitude: Double,
        @Query("lat") latitude: Double,
        @Query("dist") dist: Int,
        @Query("location") location: Int = 1,
        @Query("stationName") stationName: String = "none",
        @Query("apiTp") apiTp: Int = 1,
    ): Observable<CmaNearStationResult>

    @GET("app/Rest/liveDataService/station/{stationId}/latest")
    fun getStationLatest(
        @Path("stationId") stationId: String,
        @Query("datetime") datetime: String,
    ): Observable<CmaStationLatestResult>

    @GET("dataGis/multiSource/getAPILiveDataInfo")
    fun getGridLiveData(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): Observable<CmaGridLiveResult>

    @GET("dataGis/api/internetWarn/getEffectiveAlert")
    fun getEffectiveAlerts(
        @Query("areaCode") areaCode: Long = 100000,
        @Query("eventCode") eventCode: Long = 10000,
        @Query("isAreaRecursion") isAreaRecursion: Int = 1,
        @Query("severity") severity: String = "all",
    ): Observable<CmaAlertResult>
}
```

注意：最近站点的经度参数名是 **`lag`**（源站拼写如此），不是 `lon`。

- [ ] **Step 2: 走查验证**

对照规格 §2 各 URL 逐字核对 path 与 query 名；确认 baseUrl 以 `/` 结尾时相对路径不带前导 `/`。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/org/breezyweather/sources/cma/CmaApi.kt
git commit -m "Add CMA source Retrofit API"
```

---

### Task 3: 转换辅助（天气码 / 风向文本 / Beaufort / 距离）

**Files:**
- Create: `app/src/main/kotlin/org/breezyweather/sources/cma/CmaConverters.kt`

**Interfaces:**
- Produces（全部为文件内 top-level internal 函数）：
  - `internal fun getCmaWeatherCode(wepCode: Int?): WeatherCode?` — WEP 数值码映射
  - `internal fun getCmaWeatherCodeFromText(text: String?): WeatherCode?` — 实况天气现象文本反查
  - `internal fun getCmaWindDirectionDegree(text: String?): Double?` — “北偏东”/“东北风”→角度
  - `internal fun getCmaWindSpeed(text: String?): Double?` — 提取“N级”并转 Beaufort 中值 m/s
  - `internal fun getCmaDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double` — Haversine

- [ ] **Step 1: 写入 CmaConverters.kt**（LGPL 头 + 以下内容）

```kotlin
package org.breezyweather.sources.cma

import breezyweather.domain.weather.reference.WeatherCode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WEP weather phenomenon codes (天气预报常用天气现象代码) mapped to Breezy Weather codes.
 */
internal fun getCmaWeatherCode(wepCode: Int?): WeatherCode? {
    return when (wepCode) {
        0 -> WeatherCode.CLEAR
        1 -> WeatherCode.PARTLY_CLOUDY
        2 -> WeatherCode.CLOUDY
        3, 7, 8, 9, 10, 11, 12, 21, 22, 23, 24, 25 -> WeatherCode.RAIN
        4 -> WeatherCode.THUNDERSTORM
        5 -> WeatherCode.HAIL
        6, 19 -> WeatherCode.SLEET
        13, 14, 15, 16, 17, 26, 27, 28 -> WeatherCode.SNOW
        18 -> WeatherCode.FOG
        20, 29, 30, 31, 32, 53, 54, 55, 56 -> WeatherCode.HAZE
        else -> null
    }
}

internal fun getCmaWeatherCodeFromText(text: String?): WeatherCode? {
    if (text.isNullOrBlank()) return null
    return when {
        text.contains("雷") -> WeatherCode.THUNDERSTORM
        text.contains("冰雹") -> WeatherCode.HAIL
        text.contains("雨夹雪") || text.contains("冻雨") -> WeatherCode.SLEET
        text.contains("雪") -> WeatherCode.SNOW
        text.contains("雨") -> WeatherCode.RAIN
        text.contains("雾") -> WeatherCode.FOG
        text.contains("霾") || text.contains("沙尘") || text.contains("浮尘") -> WeatherCode.HAZE
        text.contains("阴") -> WeatherCode.CLOUDY
        text.contains("多云") -> WeatherCode.PARTLY_CLOUDY
        text.contains("晴") -> WeatherCode.CLEAR
        else -> null
    }
}
```

风向解析规则：
- 取字符串中第一个数字字符之前的部分，去掉结尾的 `风`；
- 若包含 `偏`：基准向 = 第一个出现的 `东南西北`，偏向 = 另一个出现的方向字符，
  结果 = 基准 ± 22.5°（沿最短弧）；
- 否则若出现两个相邻方向字符（如 `西北`）：取二者沿最短弧的中点（±45°）；
- 仅一个方向字符（如 `北`）：其角度；
- 无方向字符或含 `无持续`/`旋转`：null。
角度表：东 90、南 180、西 270、北 0。

```kotlin
private fun directionDegrees(c: Char): Double? = when (c) {
    '东' -> 90.0
    '南' -> 180.0
    '西' -> 270.0
    '北' -> 0.0
    else -> null
}

private fun shortestDelta(from: Double, to: Double): Double {
    var delta = (to - from) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta < -180.0) delta += 360.0
    return delta
}

internal fun getCmaWindDirectionDegree(text: String?): Double? {
    if (text.isNullOrBlank()) return null
    val directionPart = text.takeWhile { !it.isDigit() }.removeSuffix("风")
    val directions = directionPart.filter { it in "东南西北" }.mapNotNull(::directionDegrees)
    if (directions.isEmpty()) return null
    val base = directions.first()
    val modifier = directions.getOrNull(1)
    return when {
        modifier == null -> (base + 360.0) % 360.0
        directionPart.contains('偏') -> (base + shortestDelta(base, modifier) / 4.0 + 360.0) % 360.0
        else -> (base + shortestDelta(base, modifier) / 2.0 + 360.0) % 360.0
    }
}
```

边界自检（写入后人工核对注释里的期望值，不符则修正实现）：
- `北风`→0.0；`东风`→90.0；`东北风`→45.0；`西北风`→315.0
- `北偏东1级`→22.5；`北偏西1级`→337.5；`西偏南2级`→247.5
- `无持续风向`→null；`旋转不定`→null；null/空串→null

Beaufort 中值（m/s）：级别 0..12 →
`0.0, 0.9, 2.45, 4.45, 6.7, 9.35, 12.3, 15.5, 18.95, 22.6, 26.45, 30.55, 34.0`，
超出范围取最近端点：

```kotlin
private val BEAUFORT_MIDPOINTS = doubleArrayOf(
    0.0, 0.9, 2.45, 4.45, 6.7, 9.35, 12.3, 15.5, 18.95, 22.6, 26.45, 30.55, 34.0
)

internal fun getCmaWindSpeed(text: String?): Double? {
    val level = text?.takeLastWhile { it.isDigit() }?.takeIf { it.isNotEmpty() }
        ?.toIntOrNull() ?: return null
    return BEAUFORT_MIDPOINTS[level.coerceIn(0, BEAUFORT_MIDPOINTS.lastIndex)]
}
```

Haversine（地球半径 6371.0 km，参数弧度化用 `Math.toRadians`）：

```kotlin
internal fun getCmaDistanceKm(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return earthRadiusKm * 2 * atan2(sqrt(h), sqrt(1.0 - h))
}
```

- [ ] **Step 2: 走查验证**

逐条核对 Step 1 中列出的边界自检值；确认未引入非 ASCII 包名。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/org/breezyweather/sources/cma/CmaConverters.kt
git commit -m "Add CMA source converters"
```

---

### Task 4: CmaService 服务类

**Files:**
- Create: `app/src/main/kotlin/org/breezyweather/sources/cma/CmaService.kt`

**Interfaces:**
- Consumes: Task 1 模型、Task 2 API、Task 3 转换器。
- Produces: `class CmaService @Inject constructor(@ApplicationContext context: Context, @Named("JsonClient") client: Retrofit.Builder) : HttpSource(), WeatherSource, LocationParametersSource, NonFreeNetSource`，`id = "cma"`。

- [ ] **Step 1: 写入 CmaService.kt**（LGPL 头 + 以下完整内容；导入按需整理，勿留未用导入）

```kotlin
package org.breezyweather.sources.cma

import android.content.Context
import breezyweather.domain.location.model.Location
import breezyweather.domain.source.SourceContinent
import breezyweather.domain.source.SourceFeature
import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.AlertSeverity
import breezyweather.domain.weather.model.Wind
import breezyweather.domain.weather.wrappers.CurrentWrapper
import breezyweather.domain.weather.wrappers.DailyWrapper
import breezyweather.domain.weather.wrappers.HalfDayWrapper
import breezyweather.domain.weather.wrappers.TemperatureWrapper
import breezyweather.domain.weather.wrappers.WeatherWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import org.breezyweather.common.exceptions.InvalidLocationException
import org.breezyweather.common.source.HttpSource
import org.breezyweather.common.source.LocationParametersSource
import org.breezyweather.common.source.NonFreeNetSource
import org.breezyweather.common.source.WeatherSource
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_HIGHEST
import org.breezyweather.common.source.WeatherSource.Companion.PRIORITY_NONE
import org.breezyweather.sources.cma.json.CmaAlert
import org.breezyweather.sources.cma.json.CmaForecastItem
import org.breezyweather.sources.cma.json.CmaGridLiveElement
import org.breezyweather.sources.cma.json.CmaStationContent
import org.breezyweather.unit.distance.Distance.Companion.meters
import org.breezyweather.unit.pressure.Pressure.Companion.hectopascals
import org.breezyweather.unit.ratio.Ratio.Companion.percent
import org.breezyweather.unit.speed.Speed.Companion.metersPerSecond
import org.breezyweather.unit.temperature.Temperature.Companion.celsius
import retrofit2.Retrofit
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Named

/**
 * zip() 不能发射 null，因此把三个请求结果包进 holder
 */
private data class CmaLatestResult(
    val content: CmaStationContent?,
    val error: Throwable?,
)

private data class CmaGridResult(
    val elements: List<CmaGridLiveElement>?,
    val error: Throwable?,
)

private data class CmaAlertFetchResult(
    val alerts: List<CmaAlert>?,
    val error: Throwable?,
)

class CmaService @Inject constructor(
    @ApplicationContext context: Context,
    @Named("JsonClient") val client: Retrofit.Builder,
) : HttpSource(), WeatherSource, LocationParametersSource, NonFreeNetSource {

    override val id = "cma"
    override val name = "中国气象数据网"
    override val continent = SourceContinent.ASIA
    override val privacyPolicyUrl = "https://data.cma.cn/"

    private val mApi by lazy {
        client
            .baseUrl(BASE_URL)
            .build()
            .create(CmaApi::class.java)
    }

    private val weatherAttribution = name
    override val supportedFeatures = mapOf(
        SourceFeature.FORECAST to weatherAttribution,
        SourceFeature.CURRENT to weatherAttribution,
        SourceFeature.ALERT to weatherAttribution
    )
    override val attributionLinks = mapOf(
        name to "https://data.cma.cn/"
    )

    override val testingLocations: List<Location> = listOf(
        Location(
            city = "Beijing",
            latitude = 39.9042,
            longitude = 116.4074,
            timeZone = TimeZone.getTimeZone("Asia/Shanghai"),
            country = "China",
            countryCode = "CN",
            forecastSource = id,
            currentSource = id,
            alertSource = id
        ),
        Location(
            city = "Lhasa",
            latitude = 29.6520,
            longitude = 91.1721,
            timeZone = TimeZone.getTimeZone("Asia/Shanghai"),
            country = "China",
            countryCode = "CN",
            forecastSource = id,
            currentSource = id,
            alertSource = id
        )
    )

    override fun isFeatureSupportedForLocation(
        location: Location,
        feature: SourceFeature,
    ): Boolean {
        return location.countryCode.equals("CN", ignoreCase = true)
    }

    override fun getFeaturePriorityForLocation(
        location: Location,
        feature: SourceFeature,
    ): Int {
        return when {
            isFeatureSupportedForLocation(location, feature) -> PRIORITY_HIGHEST
            else -> PRIORITY_NONE
        }
    }

    override val knownAmbiguousCountryCodes: Array<String>? = arrayOf("CN")

    // LocationParametersSource
    override fun needsLocationParametersRefresh(
        location: Location,
        coordinatesChanged: Boolean,
        features: List<SourceFeature>,
    ): Boolean {
        if (coordinatesChanged) return true
        val stationId = location.parameters.getOrElse(id) { null }?.getOrElse("stationId") { null }
        return stationId.isNullOrEmpty()
    }

    override fun requestLocationParameters(
        context: Context,
        location: Location,
    ): Observable<Map<String, String>> {
        return mApi.getNearStation(
            longitude = location.longitude,
            latitude = location.latitude,
            dist = NEAR_STATION_SEARCH_DISTANCE_KM
        ).map { result ->
            val stationId = result.data
                ?.takeIf { it.returnCode == 0 && it.dataMethod == "station" }
                ?.DS?.stationId?.takeIf { it.isNotBlank() }
                ?: throw InvalidLocationException()
            mapOf("stationId" to stationId)
        }
    }

    // WeatherSource
    override fun requestWeather(
        context: Context,
        location: Location,
        requestedFeatures: List<SourceFeature>,
    ): Observable<WeatherWrapper> {
        val failedFeatures = mutableMapOf<SourceFeature, Throwable>()
        val features = requestedFeatures.filter { isFeatureSupportedForLocation(location, it) }
        if (features.isEmpty()) {
            return Observable.just(WeatherWrapper(failedFeatures = failedFeatures))
        }

        val stationId = location.parameters.getOrElse(id) { null }?.getOrElse("stationId") { null }
        val wantLatest = SourceFeature.FORECAST in features ||
            (SourceFeature.CURRENT in features && !stationId.isNullOrEmpty())

        val latestObservable: Observable<CmaLatestResult> = when {
            !wantLatest -> Observable.just(CmaLatestResult(null, null))
            stationId.isNullOrEmpty() -> Observable.just(
                CmaLatestResult(null, InvalidLocationException())
            )
            else -> mApi.getStationLatest(stationId, getUtcDatetime())
                .map { result ->
                    if (result.code == 200 && result.content != null) {
                        CmaLatestResult(result.content, null)
                    } else {
                        CmaLatestResult(null, InvalidLocationException())
                    }
                }
                .onErrorResumeNext { e -> Observable.just(CmaLatestResult(null, e)) }
        }

        val gridObservable: Observable<CmaGridResult> =
            if (SourceFeature.CURRENT in features && stationId.isNullOrEmpty()) {
                mApi.getGridLiveData(location.latitude, location.longitude)
                    .map { result ->
                        if (result.returnCode == "0" && !result.list.isNullOrEmpty()) {
                            CmaGridResult(result.list, null)
                        } else {
                            CmaGridResult(null, InvalidLocationException())
                        }
                    }
                    .onErrorResumeNext { e -> Observable.just(CmaGridResult(null, e)) }
            } else {
                Observable.just(CmaGridResult(null, null))
            }

        val alertObservable: Observable<CmaAlertFetchResult> =
            if (SourceFeature.ALERT in features) {
                mApi.getEffectiveAlerts()
                    .map { result ->
                        if (result.code == "200") {
                            CmaAlertFetchResult(result.data.orEmpty(), null)
                        } else {
                            CmaAlertFetchResult(null, RuntimeException(result.message))
                        }
                    }
                    .onErrorResumeNext { e -> Observable.just(CmaAlertFetchResult(null, e)) }
            } else {
                Observable.just(CmaAlertFetchResult(null, null))
            }

        return Observable.zip(latestObservable, gridObservable, alertObservable) {
                latest,
                grid,
                alerts,
            ->
            var dailyForecast: List<DailyWrapper>? = null
            var current: CurrentWrapper? = null
            var alertList: List<Alert>? = null

            if (SourceFeature.FORECAST in features) {
                val foreList = latest.content?.forecast?.foreList
                if (foreList.isNullOrEmpty()) {
                    failedFeatures[SourceFeature.FORECAST] =
                        latest.error ?: InvalidLocationException()
                } else {
                    getDailyList(foreList, location)?.let { dailyForecast = it }
                        ?: run {
                            failedFeatures[SourceFeature.FORECAST] = InvalidLocationException()
                        }
                }
            }

            if (SourceFeature.CURRENT in features) {
                when {
                    latest.content != null -> current = getCurrent(latest.content)
                    grid.elements != null -> current = getCurrent(grid.elements)
                    else -> failedFeatures[SourceFeature.CURRENT] =
                        latest.error ?: grid.error ?: InvalidLocationException()
                }
            }

            if (SourceFeature.ALERT in features) {
                if (alerts.alerts != null) {
                    alertList = getAlertList(alerts.alerts, location)
                } else {
                    failedFeatures[SourceFeature.ALERT] = alerts.error ?: RuntimeException()
                }
            }

            WeatherWrapper(
                dailyForecast = dailyForecast,
                current = current,
                alertList = alertList,
                failedFeatures = failedFeatures
            )
        }
    }

    private fun getDailyList(
        foreList: List<CmaForecastItem>,
        location: Location,
    ): List<DailyWrapper>? {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val days = LinkedHashMap<Date, Pair<CmaForecastItem?, CmaForecastItem?>>()
        for (item in foreList) {
            val date = try {
                dateFormatter.parse(item.date ?: continue)
            } catch (e: ParseException) {
                continue
            } ?: continue
            val pair = days.getOrPut(date) { null to null }
            days[date] = if (item.period == "夜间") pair.copy(second = item) else pair.copy(first = item)
        }
        if (days.isEmpty()) return null

        return days.map { (date, halves) ->
            DailyWrapper(
                date = date,
                day = halves.first?.let { getHalfDay(it) },
                night = halves.second?.let { getHalfDay(it) }
            )
        }
    }

    private fun getHalfDay(item: CmaForecastItem): HalfDayWrapper {
        return HalfDayWrapper(
            weatherText = item.weatherText,
            weatherCode = getCmaWeatherCode(item.weatherCode),
            temperature = TemperatureWrapper(
                temperature = item.temperature?.toDoubleOrNull()?.celsius
            ),
            wind = Wind(
                degree = getCmaWindDirectionDegree(item.wind),
                speed = getCmaWindSpeed(item.wind)?.metersPerSecond
            )
        )
    }

    private fun getCurrent(content: CmaStationContent): CurrentWrapper {
        return CurrentWrapper(
            weatherText = content.weatherText,
            weatherCode = getCmaWeatherCodeFromText(content.weatherText),
            temperature = TemperatureWrapper(temperature = content.temperature?.celsius),
            wind = Wind(
                degree = getCmaWindDirectionDegree(content.windDirectionText),
                speed = content.windSpeed?.metersPerSecond
            ),
            relativeHumidity = content.humidity?.percent,
            pressure = content.pressure?.hectopascals,
            visibility = content.visibility?.meters
        )
    }

    private fun getElementValue(
        elements: List<CmaGridLiveElement>,
        elementName: String,
    ): Double? {
        return elements.firstOrNull { it.fastEle == elementName }?.value?.toDoubleOrNull()
    }

    private fun getCurrent(elements: List<CmaGridLiveElement>): CurrentWrapper {
        val weatherCode = getCmaWeatherCode(getElementValue(elements, "WEA")?.toInt())
        return CurrentWrapper(
            weatherCode = weatherCode,
            temperature = TemperatureWrapper(
                temperature = getElementValue(elements, "TEM")?.celsius
            ),
            wind = Wind(
                degree = getElementValue(elements, "WIND"),
                speed = getElementValue(elements, "WINS")?.metersPerSecond
            ),
            relativeHumidity = getElementValue(elements, "RHU")?.percent,
            cloudCover = getElementValue(elements, "TCDC")?.percent,
            visibility = getElementValue(elements, "VIS")?.meters
        )
    }

    private fun getAlertList(
        alerts: List<CmaAlert>,
        location: Location,
    ): List<Alert> {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        return alerts.asSequence()
            .filter { it.status == "Actual" }
            .filter { it.lat != null && it.lon != null }
            .filter {
                getCmaDistanceKm(location.latitude, location.longitude, it.lat!!, it.lon!!) <=
                    ALERT_DISTANCE_THRESHOLD_KM
            }
            .map { alert ->
                val severity = when (alert.severity?.lowercase(Locale.ENGLISH)) {
                    "red" -> AlertSeverity.EXTREME
                    "orange" -> AlertSeverity.SEVERE
                    "yellow" -> AlertSeverity.MODERATE
                    "blue" -> AlertSeverity.MINOR
                    else -> AlertSeverity.UNKNOWN
                }
                Alert(
                    alertId = alert.identifier
                        ?: Objects.hash(alert.headline, alert.severity, alert.effective).toString(),
                    startDate = parseDate(dateFormatter, alert.effective),
                    endDate = parseDate(dateFormatter, alert.expires),
                    headline = alert.headline,
                    description = alert.description,
                    source = alert.senderName,
                    severity = severity,
                    color = Alert.colorFromSeverity(severity)
                )
            }
            .toList()
    }

    private fun parseDate(
        formatter: SimpleDateFormat,
        text: String?,
    ): Date? {
        if (text == null) return null
        return try {
            formatter.parse(text)
        } catch (e: ParseException) {
            null
        }
    }

    private fun getUtcDatetime(): String {
        val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date())
    }

    companion object {
        private const val BASE_URL = "https://data.cma.cn/"
        private const val NEAR_STATION_SEARCH_DISTANCE_KM = 100
        private const val ALERT_DISTANCE_THRESHOLD_KM = 100.0
    }
}
```

**已核实的模型签名**（计划编写时已逐一读过源文件）：
- `CurrentWrapper` 字段：weatherText、weatherCode、temperature、wind、uV、relativeHumidity、
  dewPoint、pressure、cloudCover、visibility、ceiling、dailyForecast、hourlyForecast。
  **没有 precipitation 字段**，因此实况降水（V13019 / PRE_1H）无法输出，代码中不得出现该参数。
- `HalfDayWrapper(date 无, weatherText, weatherSummary, weatherCode, temperature,
  precipitation, precipitationProbability, precipitationDuration, wind)`；
  `DailyWrapper(date, day, night, ...)`。
- `location.parameters.getOrElse(id) { null }?.getOrElse(key) { null }` 为既有访问惯例。

- [ ] **Step 2: 走查验证**

- 对照规格 §3.4 流程图核对 zip 合并逻辑与 failedFeatures 分支；
- 核对每个 Observable 均有 onErrorResumeNext 兜底（zip 内不会抛未捕获异常）；
- 核对 `getDailyList` 白天/夜间归类：`period == "夜间"` 进 night，其余进 day；
- 确认没有任何 gradle 编译命令被执行（用户约束）。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/org/breezyweather/sources/cma/CmaService.kt
git commit -m "Add CMA weather source service"
```

---

### Task 5: SourceManager 注册

**Files:**
- Modify: `app/src/main/kotlin/org/breezyweather/sources/SourceManager.kt`

**Interfaces:**
- Consumes: Task 4 的 `CmaService`。

- [ ] **Step 1: 三处插入（均按字母序 china 之后、cwa 之前）**

1. import 区：在 `import org.breezyweather.sources.china.ChinaService` 之后加
   `import org.breezyweather.sources.cma.CmaService`
2. 构造器参数：在 `chinaService: ChinaService,` 之后加 `cmaService: CmaService,`
3. `nationalWeatherSourceList`：在 `chinaService,` 之后加 `cmaService,`

- [ ] **Step 2: 走查验证**

grep 确认 `cmaService` 出现恰好 3 次；确认没有加入 worldwide 列表。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/org/breezyweather/sources/SourceManager.kt
git commit -m "Register CMA source in SourceManager"
```

---

### Task 6: 文档更新

**Files:**
- Modify: `docs/SOURCES.md`
- Modify: `docs/COVERAGE.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: SOURCES.md Summary 表**

在第 36 行 `| 🇨🇳 China | [China](#china) | … |` 行后新增一行：

```markdown
| 🇨🇳 China                         | [中国气象数据网](#中国气象数据网) 🔓                                                               | Forecast, Current, Alerts                                                        |
```

（🔓 表示无需 key；对齐既有表格列宽风格即可。）

- [ ] **Step 2: SOURCES.md 正文小节**

在 `### China` 小节结束后（`### Danmarks Meteorologiske Institut` 之前）插入：

```markdown
### 中国气象数据网

**[中国气象数据网](https://data.cma.cn/)** (China Meteorological Data Service Center) is the official open data portal of the China Meteorological Administration, operated by the National Meteorological Information Center. Data is retrieved from public interfaces powering its gridded GIS display page.

| Feature                        | Detail                                                           |
|--------------------------------|------------------------------------------------------------------|
| 🗺️ **Coverage**               | 🇨🇳 China                                                       |
| 📆 **Daily forecast**          | Up to 7 days, split into half-days (daytime / nighttime)         |
| ⏱️ **Hourly forecast**         | Not available                                                    |
| ▶️ **Current observation**     | Available: can complement another source as a **Current Source** |
| 😶‍🌫️ **Air quality**         | Not available                                                    |
| 🤧 **Pollen**                  | Not available                                                    |
| ☔ **Precipitation nowcasting** | Not available                                                    |
| ⚠️ **Alerts**                  | Available (official CMA warnings, filtered within 100 km)        |
| 📊 **Normals**                 | Not available                                                    |
| 🧭 **Address lookup**          | Not available                                                    |

<details><summary><h4>Details of available data from 中国气象数据网</h4></summary>

| Data                      | Available | Data              | Available   |
|---------------------------|-----------|-------------------|-------------|
| Weather Condition         | ✅         | Humidity          | ✅ (Current) |
| Temperature               | ✅         | Dew Point         | ❌           |
| Precipitation             | ❌         | UV Index          | ❌           |
| Precipitation Probability | ❌         | Sunshine Duration | ❌           |
| Precipitation Duration    | ❌         | Cloud Cover       | ✅ (grid fallback only) |
| Wind                      | ✅         | Visibility        | ✅ (Current) |
| Pressure                  | ✅ (Current) | Ceiling         | ❌           |
</details>
```

- [ ] **Step 3: COVERAGE.md**

将 Asia 表中 `| 🇨🇳 China | Mixed China sources | ✅ included | |` 行更新为：

```markdown
| 🇨🇳 China                | Mixed China sources incl. [CMA](https://data.cma.cn/) | ✅ included                                                                             | 2026-08-25   |
```

- [ ] **Step 4: CHANGELOG.md**

在 `# Version 6.2.3 (not yet released)` 区块内新增（若无则创建）`**Sources**` 小节：

```markdown
**Sources**
- Add 中国气象数据网 (CMA, China) source: up to 7-day day/night forecast, current observation and official weather alerts, from free public interfaces of the China Meteorological Administration open data portal (secondary source, China only).
```

- [ ] **Step 5: Commit**

```bash
git add docs/SOURCES.md docs/COVERAGE.md CHANGELOG.md
git commit -m "Document CMA weather source"
```

---

### Task 7: 对抗式审查与修复

**Files:**
- Review & Fix: Task 1–6 全部产出物

- [ ] **Step 1: curl 重放核验映射假设**（重放 4 个端点，确认字段名/类型/格式未变）
- [ ] **Step 2: 攻击面审查清单**（空值、类型混淆、时区、编码、并发 zip、注册遗漏、文档一致性、spotless 风格、NonFreeNet 标记、testingLocations 合法性、alert 过滤边界）
- [ ] **Step 3: 修复发现的问题并提交**

```bash
git add -A
git commit -m "Fix issues found in CMA source adversarial review"
```

（审查发现的具体问题在执行时逐条记录到 PR 描述。）
