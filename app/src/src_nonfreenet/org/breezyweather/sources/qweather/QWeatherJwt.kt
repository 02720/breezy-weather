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

import android.util.Base64
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.PrivateKeyFactory
import java.nio.charset.StandardCharsets

/**
 * Creates a QWeather JWT authentication token, per
 * https://dev.qweather.com/docs/configuration/authentication/.
 *
 * QWeather expects a JWT signed with Ed25519 (EdDSA):
 * - header: `{"alg":"EdDSA","kid":"<credential ID>"}`
 * - payload: `{"sub":"<project ID>","iat":<issue time>,"exp":<expiry time>}`
 *
 * The token is valid for at most 24 hours; we follow QWeather's recommendation of a
 * 15-minute validity and an issue time 30 seconds in the past. As Ed25519 is not exposed
 * through `java.security` on the project's minSdk 23, signing uses BouncyCastle's low-level
 * [Ed25519Signer] directly (no provider registration involved).
 *
 * @param privateKey the user's Ed25519 private key, as a PEM-encoded PKCS#8 string
 *   ("-----BEGIN PRIVATE KEY-----…") or a raw base64-encoded 32-byte seed
 * @param projectId the QWeather project ID ("sub" claim)
 * @param credentialId the QWeather credential ID ("kid" claim)
 * @param now the current time in milliseconds, injectable for tests
 * @throws IllegalArgumentException when [privateKey] cannot be parsed as an Ed25519 key
 */
internal fun createQWeatherJwtToken(
    privateKey: String,
    projectId: String,
    credentialId: String,
    now: Long = System.currentTimeMillis(),
): String {
    val header = "{\"alg\":\"EdDSA\",\"kid\":\"${jsonEscape(credentialId)}\"}"
        .toByteArray(StandardCharsets.UTF_8)
    val iat = now / 1000 - JWT_IAT_LEEWAY_SECONDS
    val exp = iat + JWT_VALIDITY_SECONDS
    val payload = "{\"sub\":\"${jsonEscape(projectId)}\",\"iat\":$iat,\"exp\":$exp}"
        .toByteArray(StandardCharsets.UTF_8)

    val headerBase64 = base64Url(header)
    val payloadBase64 = base64Url(payload)
    val signingInput = "$headerBase64.$payloadBase64".toByteArray(StandardCharsets.UTF_8)

    val signer = Ed25519Signer()
    signer.init(true, parseEd25519PrivateKey(privateKey))
    signer.update(signingInput, 0, signingInput.size)
    val signatureBase64 = base64Url(signer.generateSignature())

    return "$headerBase64.$payloadBase64.$signatureBase64"
}

/**
 * Parses the user-provided Ed25519 private key. Accepts a PEM-encoded PKCS#8 key
 * ("-----BEGIN PRIVATE KEY-----…-----END PRIVATE KEY-----", line breaks ignored) or a raw
 * base64-encoded 32-byte seed.
 */
private fun parseEd25519PrivateKey(key: String): Ed25519PrivateKeyParameters {
    val base64 = key
        .replace(PEM_BEGIN_REGEX, "")
        .replace(PEM_END_REGEX, "")
        .filterNot { it.isWhitespace() }
    if (base64.isEmpty()) {
        throw IllegalArgumentException("QWeather JWT private key is empty")
    }
    val decoded = try {
        Base64.decode(base64, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("QWeather JWT private key is not valid base64", e)
    }
    try {
        val keyParameters = PrivateKeyFactory.createKey(PrivateKeyInfo.getInstance(decoded))
        if (keyParameters is Ed25519PrivateKeyParameters) {
            return keyParameters
        }
    } catch (_: Exception) {
        // Not a DER-encoded PKCS#8 structure; fall back to a raw seed below.
    }
    if (decoded.size == ED25519_SEED_SIZE) {
        return Ed25519PrivateKeyParameters(decoded, 0)
    }
    throw IllegalArgumentException(
        "QWeather JWT private key is not a valid Ed25519 PKCS#8 key or 32-byte seed"
    )
}

private fun base64Url(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}

/** Escapes [value] for inclusion in a JSON string. */
private fun jsonEscape(value: String): String = buildString {
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') {
                append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else {
                append(c)
            }
        }
    }
}

private const val ED25519_SEED_SIZE = 32

/** QWeather recommends issuing the token 30 seconds in the past. */
private const val JWT_IAT_LEEWAY_SECONDS = 30L

/** 15 minutes; QWeather allows at most 24 hours (86400 seconds). */
private const val JWT_VALIDITY_SECONDS = 900L

private val PEM_BEGIN_REGEX = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----")
private val PEM_END_REGEX = Regex("-----END [A-Z ]*PRIVATE KEY-----")
