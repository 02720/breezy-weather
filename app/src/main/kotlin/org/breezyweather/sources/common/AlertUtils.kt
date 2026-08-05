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
 * - trailing bracketed qualifiers, e.g. "[III级/较重]", "（已解除）"
 * - the issuing station prefix, e.g. "平南县气象台发布"
 * - the trailing "信号" word, e.g. "雷电黄色预警信号"
 *
 * e.g. "平南县气象台发布雷电黄色预警信号[III级/较重]" -> "雷电黄色预警"
 *
 * Returns null when nothing meaningful remains.
 */
internal fun getCleanChineseAlertTitle(title: String?): String? {
    val trimmed = title?.trim()?.ifEmpty { null } ?: return null
    var result = removeTrailingBrackets(trimmed)

    val publishIndex = result.indexOf("发布")
    if (publishIndex != -1) {
        result = result.substring(publishIndex + "发布".length).trim()
    }
    result = result.removeSuffix("信号").trim()

    return result.ifEmpty { null }
}

/**
 * Removes bracketed content at the end of [text], e.g. "[III级/较重]" or
 * "（已解除）", repeatedly in case of nested qualifiers.
 */
private fun removeTrailingBrackets(text: String): String {
    var result = text.trim()
    while (result.isNotEmpty()) {
        val open = when (result.last()) {
            ']' -> result.lastIndexOf('[')
            '）' -> result.lastIndexOf('（')
            ')' -> result.lastIndexOf('(')
            else -> return result
        }
        // Keep the whole string if the opening bracket is missing or at the start
        if (open <= 0 || open == result.lastIndex) return result
        result = result.substring(0, open).trim()
    }
    return result
}
