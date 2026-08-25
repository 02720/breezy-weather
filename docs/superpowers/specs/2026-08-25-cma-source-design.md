# CMA（中国气象数据网）天气数据源设计

日期：2026-08-25
状态：已获用户批准（方案 A）

## 1. 目标与范围

在 Breezy Weather 中新增天气数据源 **中国气象数据网**（中国气象局国家气象信息中心，
https://data.cma.cn/ ），通过抓取其「格点 GIS 综合显示」页面
（`dataGis/static/gridgis/#/pcindex`）背后的免费 HTTP 接口接入。

功能范围（均为可选辅助功能，非主源——按 CONTRIBUTING.md，无逐小时预报的源不能作主源）：

| SourceFeature | 数据 |
|---|---|
| FORECAST | 7 天逐日预报（白天/夜间两个半日：最高/最低温、天气码、风向风级） |
| CURRENT | 站点实况（温度、湿度、气压、风、能见度、天气现象）；无站点时回落格点实况 |
| ALERT | 全国预警，按与位置的距离过滤 |

不实现：LocationSearchSource、ReverseGeocodingSource、ConfigurableSource、空气质量、
逐小时预报、Minutely。覆盖范围仅中国大陆（`countryCode == "CN"`）。

## 2. 已验证的后端接口（2026-08-25 实测通过）

Base URL：`https://data.cma.cn/`（接口均相对该域名；无需鉴权、无需 API key）。

### 2.1 最近站点查询

```
GET /dataGis/api/station/getNearStation?lag={lon}&lat={lat}&dist={km}&location=1&stationName=none&apiTp=1
```

注意参数名是 `lag`（经度）而非 `lon`。响应：

```json
{"code":"200","message":"ok","data":{"returnCode":0,"dataMethod":"station",
 "DS":{"stationId":"54433","stationName":"朝阳","country":"中国","province":"北京",
 "city":"市辖区","district":"朝阳区","areacode":"110105","valueTem":"27.0",
 "valueRhu":"78.0","winDir":"东风","winSpeed":"1级","valuePre":"0.0"}},"costs":"54"}
```

失败形态：
- 坐标在中国境外 → `data.returnCode = 1` 且 `errorMsg` 非空；
- 服务端异常 → `code: "500"`。
两种情况都必须视为“无站点”。

### 2.2 站点实况 + 7 天预报（核心端点）

```
GET /app/Rest/liveDataService/station/{stationId}/latest?datetime={yyyyMMddHHmmss}
```

`datetime` 为 UTC 的 `yyyyMMddHHmmss`，可省略。响应（节选）：

```json
{"status":0,"code":200,
 "content":{"D_datetime":"2026-08-25 14:00:00",   // 观测时间，北京时区
  "V12001":29.7,        // 气温 ℃
  "V13003":82,          // 相对湿度 %
  "V10004":1003.5,      // 海平面气压 hPa
  "V11292T":"北风",      // 风向文本
  "V11293":1.8,         // 风速 m/s
  "V11293T":"2级",       // 风力等级（备用）
  "V20003T":"霾",        // 天气现象文本
  "V13019":0.0,         // 降水 mm
  "V20001":4400,        // 能见度 m
  "foreCast":{"PRE_24h":"28.5mm",
   "foreList":[{"DAN":"白天","dataShow":"2026-08-25","WEP_Past_12h":9,"Wth":"大雨",
                "win":"北偏东1级","tem":"31.0"},
               {"DAN":"夜间","dataShow":"2026-08-25","WEP_Past_12h":7,"Wth":"小雨",
                "win":"北偏西2级","tem":"23.9"}]}}}
```

foreList 固定 14 条 = 7 天 × {白天, 夜间}。`tem` 在白天条目为日最高温、夜间条目为日最低温。
缺测约定：数值字段可能出现 `999999` / `9999` / 空串，必须判为 null。

### 2.3 格点实况（CURRENT 兜底）

```
GET /dataGis/multiSource/getAPILiveDataInfo?lat={lat}&lon={lon}
```

```json
{"returnCode":"0","list":[{"unit":"deg","validTime":"0","value":"30.0","fastEle":"TEM"},
 {"fastEle":"RHU","value":"88"},{"fastEle":"WINS","value":"1.0"},
 {"fastEle":"WIND","value":"323"},{"fastEle":"WEA","value":"3"},{"fastEle":"VIS","value":"3528"},
 {"fastEle":"TCDC","value":"85"},{"fastEle":"PRE_1H","value":"0.0"},...]}
```

要素：TEM(℃)、RHU(%)、WINS(m/s)、WIND(方位角 deg)、WEA(天气现象码)、VIS(m)、TCDC(云量 %)、
PRE_1H/3H/6H/12H/24H(mm)。`returnCode != "0"` 或 list 空 → 失败。

### 2.4 全国预警列表

```
GET /dataGis/api/internetWarn/getEffectiveAlert?areaCode=100000&eventCode=10000&isAreaRecursion=1&severity=all
```

返回全国生效预警数组（CAP 风格）。单条关键字段：

```json
{"severity":"Blue","identifier":"11011741600000_20260825145657",
 "headline":"平谷区气象台发布大风蓝色预警[IV/一般]",
 "description":"…","effective":"2026-08-25 15:00:00","expires":"2026-08-28 15:00:00",
 "eventTypeCN":"大风","lon":117.128,"lat":40.147,"areaName":"北京市/平谷区",
 "senderName":"平谷区气象台","status":"Actual"}
```

说明：曾尝试 `getWarningDataByCnty`（按省/县过滤），该端点不稳定（连接被重置），
弃用；统一用本端点 + 客户端距离过滤。时间格式为北京时间 `yyyy-MM-dd HH:mm:ss`。

## 3. 架构

新目录 `app/src/main/kotlin/org/breezyweather/sources/cma/`：

| 文件 | 职责 |
|---|---|
| `CmaService.kt` | 服务类：`HttpSource() , WeatherSource, LocationParametersSource, NonFreeNetSource` |
| `CmaApi.kt` | Retrofit 接口（4 个 GET） |
| `json/CmaNearStationResult.kt` | §2.1 响应模型（含 DS） |
| `json/CmaStationLatest.kt` | §2.2 响应模型（content、foreCast、foreList 条目） |
| `json/CmaGridLiveResult.kt` | §2.3 响应模型 |
| `json/CmaAlert.kt` | §2.4 单条预警模型 |

依赖注入：`@Named("JsonClient") val client: Retrofit.Builder`（与 TJWeatherService 一致）。

### 3.1 基本信息

- `id = "cma"`，`name = "中国气象数据网"`，`continent = SourceContinent.ASIA`
- `privacyPolicyUrl = "https://data.cma.cn/"`（官网无法找到独立隐私页，用站点根 URL）
- `supportedFeatures = {FORECAST, CURRENT, ALERT → 天气归属}`，
  `attributionLinks = {name → https://data.cma.cn/}`
- `knownAmbiguousCountryCodes = arrayOf("CN")`（同 china 源先例）
- `testingLocations`：北京（39.9042,116.4074）+ 一个西部位置（如拉萨 29.6520,91.1721，CN）
- `NonFreeNetSource`：接口未提供公开文档化开放 API，属页面抓取，标记为 NonFreeNet
  （与 china/caiyun/geovis 先例一致，freenet 构建变体将排除该源）

### 3.2 功能支持与优先级

`isFeatureSupportedForLocation`：仅 `location.countryCode.equals("CN", true)` 时支持三功能；
`getFeaturePriorityForLocation`：CN 内三功能均 `PRIORITY_HIGHEST`（官方国家级源），否则 NONE。

### 3.3 LocationParametersSource

- 参数键：`"stationId"`（字符串，可为空）
- `needsLocationParametersRefresh`：参数缺失或 `coordinatesChanged` 时 true
- `requestLocationParameters`：调 §2.1（dist 用 100km），解析 `DS.stationId` 存入 map；
  无站点（境外/超距/500）→ 返回空 map 而非抛异常（对抗式审查结论：
  RefreshHelper 会把参数刷新异常放大为该源全部 feature 失败，而预警与格点兜底
  并不依赖站点；代价是无站点的位置每次刷新会重试一次站点查找）

### 3.4 requestWeather 流程

```
requestedFeatures 过滤后为空 → 直接返回空 WeatherWrapper
并行请求：
  FORECAST/CURRENT 需要 station/latest（若 location.parameters 有 stationId）
  仅 CURRENT 且无 stationId → 格点端点兜底
  ALERT → 全国预警列表
合并为 WeatherWrapper(dailyForecast?, current?, alertList?, failedFeatures)
```

- FORECAST：§3.5
- CURRENT：优先 station/latest 的 V 字段；stationId 缺失或请求失败时改用格点端点；
  两者都失败 → `failedFeatures[CURRENT]`
- ALERT：拉取后过滤（§3.6）；请求失败 → `failedFeatures[ALERT]`

每个 feature 的失败独立上报，互不影响（遵循 TJWeather/ClimWeb 的 onErrorResumeNext 模式）。

### 3.5 FORECAST 映射

- 按 `dataShow` 分组，每组取 白天(DAN=="白天") 与 夜间 条目构造一个 `DailyWrapper`：
  - `date`：`dataShow` 解析为该日 00:00（Asia/Shanghai）
  - `day = HalfDayWrapper(weatherText=Wth, weatherCode=wep(WEP_Past_12h),
    temperature=TemperatureWrapper(temperature=tem.celsius),
    wind=Wind(degree=windDirDegree(win), speed=null))`
  - `night` 同理（夜间 tem 即最低温，符合源定义，不做换算）
- WEP 码 → WeatherCode：0 CLEAR；1 PARTLY_CLOUDY；2 CLOUDY；3 RAIN；4 THUNDERSTORM；
  5 HAIL；6 SLEET；7–12 RAIN；13–17 SNOW；18 FOG；19 SLEET(冻雨)；20/29/31 HAZE(沙尘)；
  21–25 RAIN；26–28 SNOW；30/32+/53 HAZE(霾)；未知 → null
- `windDirDegree("北偏东1级")` 文本解析：
  - 基向：东=90 南=180 西=270 北=360；"X偏Y" → X 向 Y ±22.5°；纯 "X风" → X；
  - "旋转不定"/"无持续风向"/无法解析 → degree=null
  - 尾部风级数字（如 "1级"）→ Beaufort 中值 m/s（0级0.0、1级0.9、2级2.45、3级4.45、
    4级6.7、5级9.35、6级12.3、7级15.5、8级18.95、9级22.6、10级26.45、11级30.55、
    12级34.0），作为 `speed` 输出（这是单位制转换而非推算，源本身只提供风级）
- 半日本身不再拆 hourly；daily 列表长度以 foreList 实际天数为准（≤7）

### 3.6 CURRENT 映射（station/latest 优先）

| V 字段 | 目标 |
|---|---|
| V12001 | current.temperature (℃) |
| V13003 | current.relativeHumidity (%) |
| V10004 | current.pressure (hPa) |
| V11293 | current.wind.speed (m/s)；V11292T → wind.degree（§3.5 同一解析器） |
| V20001 | current.visibility (m) |
| V20003T | current.weatherText；weatherCode 由文本反查（晴→CLEAR 等，查不到则 null） |

注：`CurrentWrapper` 无 precipitation 字段，V13019（实况降水）与格点 PRE_1H 无法通过
CURRENT 输出，不映射（规格 §7 非目标原则：不造字段）。

缺测哨兵清洗（对抗式审查补充）：源站把 ≥9999 的值视为缺测（网页 JS 以 `>9999` 判定），
且实测出现 `WEP_Past_12h: 999999`、`V20003T: ""`。所有数值字段按要素物理界限过滤
（温度 ±100、湿度/云量 0–100、气压 300–1200 hPa——实测高原站仅 680 hPa、风速 0–200 m/s、
能见度 0–100 km——实测合法值达 30000 m、WEP 码 −1–99），越界即视为 null；
蒲福风级 >17 视为缺测。
| D_datetime | （不单独使用；CurrentWrapper 无观测时间字段） |

格点兜底映射：TEM/RHU/WINS/WIND/WEA/VIS/TCDC 对应同名字段；WEA 数值码用 §3.5 映射。

### 3.7 ALERT 映射与过滤

- 过滤：计算预警坐标 (lat/lon) 与位置的 Haversine 距离，保留 ≤ 100 km 的条目
  （常量 `ALERT_DISTANCE_THRESHOLD_KM = 100.0`）
- `alertId = identifier`；`startDate=effective`、`endDate=expires`（北京时间解析）
- `headline/description` 原文（中文）；`source = senderName`
- severity/color：Red→EXTREME、Orange→SEVERE、Yellow→MODERATE、Blue→MINOR、其余 UNKNOWN；
  color 统一用 `Alert.colorFromSeverity(severity)`（CMA 四色与 breezy 内置色系一一对应）
- `status != "Actual"` 的条目丢弃

## 4. 错误处理原则

- 所有 JSON 字段 nullable，数值解析失败 → null，绝不抛非受控异常中断整次刷新
- HTTP/解析错误 → 对应 feature 写入 failedFeatures（InvalidLocationException 用于
  “无最近站点”场景）
- 站点 latest 返回 code!=200 或 content 缺失 → 视为该次请求失败

## 5. 注册与文档更新

1. `SourceManager`：构造器注入 `cmaService: CmaService`；加入
   `nationalWeatherSourceList`（按表内字母序插入：chinaService 之后、cwaService 之前）
2. `docs/SOURCES.md`：Summary 表 🇨🇳 China 行新增 Cma；正文新增小节
3. `docs/COVERAGE.md`：按现有格式登记 cma 三 feature
4. `CHANGELOG.md`：新增条目（New weather source: 中国气象数据网 (CMA)）
5. 字符串资源：无需新增（源名直接用中文常量，与 tjweather/china 先例一致）

## 6. 验证方式（用户指定）

**不做编译测试**。完成后进行对抗式审查：
- 以攻击者视角审查代码（空值、越界、时区、编码、并发、Retrofit 注解、注册遗漏、
  文档一致性、与项目惯例冲突等维度），发现的问题当场修复
- 用 curl 重放关键端点核对映射假设（已完成于设计期，审查期可复用）

## 7. 明确的非目标

- 不做逐小时预报、空气质量、生活指数、历史天气
- 不做 LocationSearch / ReverseGeocoding（默认回退 Open-Meteo/Nominatim）
- 不引入任何 API key / 配置界面
