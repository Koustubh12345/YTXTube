package com.thinker.flashtube.potoken

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject





fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JSONArray(rawChallengeData)

    val challengeData = if (scrambled.length() > 1 && scrambled.optString(1).isNotEmpty()) {
        val descrambled = descramble(scrambled.getString(1))
        JSONArray(descrambled)
    } else {
        scrambled.getJSONArray(0)
    }

    val messageId = challengeData.getString(0)
    val interpreterHash = challengeData.getString(3)
    val program = challengeData.getString(4)
    val globalName = challengeData.getString(5)
    val clientExperimentsStateBlob = challengeData.getString(7)

    var privateDoNotAccessOrElseSafeScriptWrappedValue: Any? = JSONObject.NULL
    var privateDoNotAccessOrElseTrustedResourceUrlWrappedValue: Any? = JSONObject.NULL

    if (!challengeData.isNull(1)) {
        val arr = challengeData.optJSONArray(1)
        if (arr != null) {
            for (i in 0 until arr.length()) {
                if (arr.get(i) is String) {
                    privateDoNotAccessOrElseSafeScriptWrappedValue = arr.getString(i)
                    break
                }
            }
        }
    }
    if (!challengeData.isNull(2)) {
        val arr = challengeData.optJSONArray(2)
        if (arr != null) {
            for (i in 0 until arr.length()) {
                if (arr.get(i) is String) {
                    privateDoNotAccessOrElseTrustedResourceUrlWrappedValue = arr.getString(i)
                    break
                }
            }
        }
    }

    val interp = JSONObject()
    interp.put("privateDoNotAccessOrElseSafeScriptWrappedValue", privateDoNotAccessOrElseSafeScriptWrappedValue)
    interp.put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", privateDoNotAccessOrElseTrustedResourceUrlWrappedValue)

    val result = JSONObject()
    result.put("messageId", messageId)
    result.put("interpreterJavascript", interp)
    result.put("interpreterHash", interpreterHash)
    result.put("program", program)
    result.put("globalName", globalName)
    result.put("clientExperimentsStateBlob", clientExperimentsStateBlob)

    return result.toString()
}






fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = JSONArray(rawIntegrityTokenData)
    return base64ToU8(integrityTokenData.getString(0)) to integrityTokenData.getLong(1)
}





fun stringToU8(identifier: String): String {
    return newUint8Array(identifier.toByteArray(Charsets.UTF_8))
}






fun u8ToBase64(poToken: String): String {
    val bytes = poToken.split(",")
        .filter { it.isNotBlank() }
        .map { it.trim().toInt().toByte() }
        .toByteArray()
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}


private fun descramble(scrambledChallenge: String): String {
    val bytes = base64ToByteArray(scrambledChallenge)
    val descrambled = ByteArray(bytes.size) { (bytes[it] + 97).toByte() }
    return String(descrambled, Charsets.UTF_8)
}





private fun base64ToU8(base64: String): String {
    return newUint8Array(base64ToByteArray(base64))
}

private fun newUint8Array(contents: ByteArray): String {
    return "new Uint8Array([" + contents.joinToString(separator = ",") { (it.toInt() and 0xFF).toString() } + "])"
}


private fun base64ToByteArray(base64: String): ByteArray {
    val base64Mod = base64.replace('-', '+').replace('_', '/').replace('.', '=')
    return Base64.decode(base64Mod, Base64.DEFAULT)
}
