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

package org.breezyweather.sources.common

/**
 * Builds a concise headline for a Chinese national-standard weather alert
 * from its type and level names, e.g. ("雷电", "黄色") -> "雷电黄色预警".
 *
 * Returns null when the type is not provided or the level is missing. Types
 * that already carry the "预警" suffix (e.g. "春季沙尘天气趋势预警") are
 * returned as-is.
 */
internal fun buildChineseAlertHeadline(
    typeName: String?,
    levelName: String?,
): String? {
    val type = typeName?.trim()?.ifEmpty { null } ?: return null
    if (type.endsWith("预警")) return type
    val level = levelName?.trim()?.ifEmpty { null } ?: return null
    return type + level + "预警"
}

/**
 * Reduces a raw China Meteorological Administration-style alert title to its
 * core "{type}{level}预警" form by removing:
 * - trailing bracketed qualifiers, e.g. "[III级/较重]", "（已解除）", "【已解除】"
 * - the issuing station and its action verb, e.g. "平南县气象台发布",
 *   "北京市气象台更新", "XX县继续发布", "XX台将…升级为", and bare station
 *   names without a verb, e.g. "北京市气象台"
 * - the trailing "信号" word, e.g. "雷电黄色预警信号"
 *
 * e.g. "平南县气象台更新雷电黄色预警[III级/较重]" -> "雷电黄色预警"
 *
 * Returns null when nothing meaningful remains.
 */
internal fun getCleanChineseAlertTitle(title: String?): String? {
    val trimmed = title?.trim()?.ifEmpty { null } ?: return null
    var result = removeTrailingBrackets(trimmed)

    // Chained verbs (e.g. "将…升级为…") and trailing verbs (e.g. "…解除") require the
    // stripping to be applied repeatedly until the title no longer changes.
    var previous: String
    do {
        previous = result
        result = stripIssuerAction(result)
    } while (result != previous)

    // Some stations separate the verb from the alert type with a colon (e.g. "发布：暴雨红色预警").
    result = result.removePrefix("：").removePrefix(":").trim()
    result = result.removeSuffix("信号").trim()

    return result.ifEmpty { null }
}

/**
 * Removes a leading issuer prefix from [text]: the issuing station together with its action
 * verb (e.g. "平南县气象台发布雷电黄色预警" -> "雷电黄色预警") or, when no verb is
 * present, a bare issuing-station name (e.g. "北京市气象台雷电黄色预警" -> "雷电黄色预警").
 */
private fun stripIssuerAction(text: String): String {
    // Drop the text up to the last occurrence of an action verb.
    var maxEnd = -1
    var maxVerb: String? = null
    for (verb in CHINESE_ALERT_ACTION_VERBS) {
        val index = text.lastIndexOf(verb)
        if (index != -1 && index + verb.length > maxEnd) {
            maxEnd = index + verb.length
            maxVerb = verb
        }
    }
    if (maxVerb != null) {
        // A verb ending exactly at the end of the title is a trailing action (e.g.
        // "雷电黄色预警解除"). Drop the verb itself and keep what precedes it; the loop
        // in [getCleanChineseAlertTitle] then strips any publisher prefix on the remainder.
        if (maxEnd == text.length) {
            return text.substring(0, text.lastIndexOf(maxVerb)).trim()
        }
        return text.substring(maxEnd).trim()
    }
    // No action verb: drop a bare issuing-station name when present.
    var issuerEnd = -1
    for (token in CHINESE_ALERT_ISSUER_TOKENS) {
        val index = text.lastIndexOf(token)
        if (index != -1) issuerEnd = maxOf(issuerEnd, index + token.length)
    }
    if (issuerEnd != -1 && issuerEnd < text.length) {
        return text.substring(issuerEnd).trim()
    }
    return text
}

/**
 * Action verbs used by Chinese alert issuers to announce or amend an alert. Ordered roughly
 * from the most specific to the most generic so that the longest verb wins when several
 * verbs overlap (e.g. "继续发布" over "发布").
 */
private val CHINESE_ALERT_ACTION_VERBS = listOf(
    "继续发布",
    "确认发布",
    "延迟发布",
    "升级为",
    "降级为",
    "变更为",
    "发布",
    "更新",
    "解除",
    "确认",
    "变更"
)

/**
 * Issuing-station names that may precede the alert type when the title carries no action verb,
 * e.g. "XX县人民政府防汛抗旱指挥部暴雨红色预警".
 */
private val CHINESE_ALERT_ISSUER_TOKENS = listOf(
    "气象台",
    "气象局",
    "气象站",
    "应急管理厅",
    "应急管理局",
    "水利厅",
    "水利局",
    "自然资源厅",
    "人民政府",
    "防汛抗旱指挥部",
    "防汛指挥部",
    "防台防汛指挥部",
    "地质灾害防治指挥部",
    "森林防灭火指挥部",
    "防灾减灾委员会"
)

/**
 * Removes bracketed content at the end of [text], e.g. "[III级/较重]", "（已解除）" or
 * "【已解除】", repeatedly in case of nested qualifiers.
 */
private fun removeTrailingBrackets(text: String): String {
    var result = text.trim()
    while (result.isNotEmpty()) {
        val open = when (result.last()) {
            ']' -> result.lastIndexOf('[')
            '）' -> result.lastIndexOf('（')
            ')' -> result.lastIndexOf('(')
            '】' -> result.lastIndexOf('【')
            '」' -> result.lastIndexOf('「')
            else -> return result
        }
        // Keep the whole string if the opening bracket is missing or at the start
        if (open <= 0 || open == result.lastIndex) return result
        result = result.substring(0, open).trim()
    }
    return result
}
