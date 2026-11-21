/*
 * JavaScript token helpers adapted from LibreTube.
 * Copyright (C) LibreTube contributors.
 * Licensed under GPL-3.0-or-later. See THIRD_PARTY_NOTICES.md.
 */
package com.widgetly.app.potoken

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JSONArray(rawChallengeData)
    val challenge = if (scrambled.length() > 1 && scrambled.opt(1) is String) {
        JSONArray(descramble(scrambled.getString(1)))
    } else {
        scrambled.getJSONArray(0)
    }
    fun findString(value: Any?): Any {
        val array = value as? JSONArray ?: return JSONObject.NULL
        for (index in 0 until array.length()) {
            val item = array.opt(index)
            if (item is String) return item
        }
        return JSONObject.NULL
    }
    return JSONObject()
        .put("messageId", challenge.optString(0))
        .put("interpreterJavascript", JSONObject()
            .put("privateDoNotAccessOrElseSafeScriptWrappedValue", findString(challenge.opt(1)))
            .put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", findString(challenge.opt(2))))
        .put("interpreterHash", challenge.optString(3))
        .put("program", challenge.optString(4))
        .put("globalName", challenge.optString(5))
        .put("clientExperimentsStateBlob", challenge.optString(7))
        .toString()
}

fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val data = JSONArray(rawIntegrityTokenData)
    return base64ToU8(data.getString(0)) to data.getLong(1)
}

fun stringToU8(identifier: String): String =
    newUint8Array(identifier.toByteArray(Charsets.UTF_8))

fun u8ToBase64(poToken: String): String {
    val bytes = poToken.split(",").filter(String::isNotBlank)
        .map { it.trim().toInt().toByte() }.toByteArray()
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
}

private fun descramble(scrambledChallenge: String): String =
    base64ToByteArray(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .toString(Charsets.UTF_8)

private fun base64ToU8(value: String): String =
    newUint8Array(base64ToByteArray(value))

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" + contents.joinToString(",") { (it.toInt() and 0xff).toString() } + "])"

private fun base64ToByteArray(value: String): ByteArray {
    val normalized = value.replace('-', '+').replace('_', '/').replace('.', '=')
    return Base64.decode(normalized, Base64.DEFAULT)
}
