/*******************************************************************************
 * Copyright 2015-2026 info-age GmbH, Basel
 *
 * This file is part of RFID Library Tools.
 * 
 * RFID Library Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid.webservice;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility for generating and verifying JSON Web Tokens (JWT) signed with HMAC-SHA256 (HS256).
 * Compatible with the Android NFC Reader application's JwtGenerator.
 */
public class JwtGenerator {

	private static final String ALGORITHM_HMAC_SHA256 = "HmacSHA256";
	public static final long DEFAULT_VALIDITY_SECONDS = 60L;

	private JwtGenerator() {
	}

	/**
	 * Generates a signed JWT token using the HS256 algorithm with default 60s validity.
	 *
	 * @param secret Secret key used for signing (HMAC-SHA256)
	 * @return Formatted JWT string: header.payload.signature
	 */
	public static String generateToken(String secret) {
		return generateToken(secret, DEFAULT_VALIDITY_SECONDS, System.currentTimeMillis(), null);
	}

	/**
	 * Generates a signed JWT token using the HS256 algorithm.
	 *
	 * @param secret Secret key used for signing (HMAC-SHA256)
	 * @param validitySeconds Token validity duration in seconds (defaults to 60 seconds)
	 * @param issuedAtMillis Timestamp in milliseconds when the token is issued
	 * @param customClaims Optional map of additional claims to include in the payload
	 * @return Formatted JWT string: header.payload.signature
	 */
	public static String generateToken(String secret, long validitySeconds, long issuedAtMillis, Map<String, Object> customClaims) {
		if (secret == null || secret.trim().isEmpty()) {
			throw new IllegalArgumentException("Secret key must not be blank");
		}

		String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

		long iatSeconds = issuedAtMillis / 1000;
		long expSeconds = iatSeconds + validitySeconds;

		StringBuilder payloadSb = new StringBuilder();
		payloadSb.append("{\"iat\":").append(iatSeconds).append(",\"exp\":").append(expSeconds);
		if (customClaims != null) {
			for (Map.Entry<String, Object> entry : customClaims.entrySet()) {
				payloadSb.append(",\"").append(escapeJson(entry.getKey())).append("\":");
				Object val = entry.getValue();
				if (val instanceof Number || val instanceof Boolean) {
					payloadSb.append(val);
				} else {
					payloadSb.append("\"").append(escapeJson(String.valueOf(val))).append("\"");
				}
			}
		}
		payloadSb.append("}");

		String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
		String encodedPayload = base64UrlEncode(payloadSb.toString().getBytes(StandardCharsets.UTF_8));

		String contentToSign = encodedHeader + "." + encodedPayload;
		byte[] signature = signHmacSha256(contentToSign, secret);
		String encodedSignature = base64UrlEncode(signature);

		return contentToSign + "." + encodedSignature;
	}

	private static byte[] signHmacSha256(String data, String secret) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM_HMAC_SHA256);
			SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM_HMAC_SHA256);
			mac.init(secretKeySpec);
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException("HMAC-SHA256 signing failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Verifies the signature of a JWT token against a secret key.
	 *
	 * @param token  The JWT token string
	 * @param secret The secret key
	 * @return true if the signature is valid, false otherwise
	 */
	public static boolean verifySignature(String token, String secret) {
		if (token == null || secret == null) {
			return false;
		}
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			return false;
		}
		String contentToSign = parts[0] + "." + parts[1];
		String expectedSignature = base64UrlEncode(signHmacSha256(contentToSign, secret));
		return parts[2].equals(expectedSignature);
	}

	/**
	 * URL-safe Base64 encoding without padding (RFC 7515 / RFC 4648 Section 5).
	 *
	 * @param data Byte array to encode
	 * @return Base64Url encoded string
	 */
	public static String base64UrlEncode(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	/**
	 * Decodes Base64Url string to byte array.
	 *
	 * @param str Base64Url string
	 * @return Decoded byte array
	 */
	public static byte[] base64UrlDecode(String str) {
		return Base64.getUrlDecoder().decode(str.trim());
	}

	private static String escapeJson(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			switch (ch) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (ch < ' ') {
						String hex = String.format("%04x", (int) ch);
						sb.append("\\u").append(hex);
					} else {
						sb.append(ch);
					}
					break;
			}
		}
		return sb.toString();
	}
}
