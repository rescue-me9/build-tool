package com.kong.buildtool

object AuthGuard {
    private var libraryReady = false

    @Synchronized
    private fun ensureLibrary(): Boolean {
        if (libraryReady) return true
        libraryReady = try {
            System.loadLibrary("weibosdkcore")
            true
        } catch (error: Throwable) {
            false
        }
        return libraryReady
    }

    fun sign(username: String, deviceId: String, timestamp: String): String {
        if (!ensureLibrary()) return ""
        return runCatching { nativeSign(username, deviceId, timestamp) }.getOrDefault("")
    }

    private external fun nativeSign(username: String, deviceId: String, timestamp: String): String
}
