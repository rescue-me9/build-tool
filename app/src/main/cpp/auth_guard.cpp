#include "auth_guard.h"

#include <jni.h>
#include <cstring>
#include <string>

// 混淆存储的请求签名盐：真实值 = 每字节与 0x5A 异或。
// 修改密钥时同步更新 server/config.php 的 ONYX_SIGN_SECRET。
static const unsigned char kObfuscatedSecret[] = {
    0x15, 0x34, 0x03, 0x22, 0x05, 0x38, 0x2F, 0x33, 0x36, 0x3E, 0x1A, 0x2B, 0x2B, 0x7C, 0x3E, 0x33,
    0x29, 0x39, 0x35, 0x28, 0x3E, 0x61, 0x70, 0x70, 0x31, 0x70, 0x70, 0x2E, 0x35, 0x2A, 0x33, 0x39,
    0x2D, 0x2F, 0x22, 0x2F, 0x77, 0x10, 0x2F, 0x72, 0x7C, 0x73, 0x05, 0x15, 0x0A, 0x1A, 0x71, 0x68,
    0x6D, 0x77, 0x2D, 0x2F, 0x29, 0x33, 0x3E, 0x79, 0x77, 0x15, 0x16, 0x0F, 0x12, 0x7D, 0x7B, 0x77,
    0x13, 0x2A, 0x35, 0x77, 0x15, 0x34, 0x03, 0x22, 0x05, 0x18, 0x2F, 0x33, 0x36, 0x3E, 0x05, 0x10,
    0x77, 0x0E, 0x35, 0x2A, 0x98, 0xF9,
};
static const size_t kSecretLen = sizeof(kObfuscatedSecret);

namespace {

std::string deobfuscateSecret() {
    std::string out;
    out.reserve(kSecretLen);
    for (size_t i = 0; i < kSecretLen; ++i) {
        out.push_back(static_cast<char>(kObfuscatedSecret[i] ^ 0x5A));
    }
    return out;
}

std::string bytesToHex(const unsigned char* data, size_t length) {
    static const char kHex[] = "0123456789abcdef";
    std::string out;
    out.reserve(length * 2);
    for (size_t i = 0; i < length; ++i) {
        out.push_back(kHex[data[i] >> 4]);
        out.push_back(kHex[data[i] & 0x0F]);
    }
    return out;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_kong_buildtool_AuthGuard_nativeSign(
    JNIEnv* env, jclass, jstring username, jstring deviceId, jstring timestamp) {
    if (!username || !deviceId || !timestamp) return env->NewStringUTF("");

    const char* user = env->GetStringUTFChars(username, nullptr);
    const char* device = env->GetStringUTFChars(deviceId, nullptr);
    const char* ts = env->GetStringUTFChars(timestamp, nullptr);
    if (!user || !device || !ts) {
        if (user) env->ReleaseStringUTFChars(username, user);
        if (device) env->ReleaseStringUTFChars(deviceId, device);
        if (ts) env->ReleaseStringUTFChars(timestamp, ts);
        return env->NewStringUTF("");
    }

    const std::string secret = deobfuscateSecret();
    const std::string material = std::string(user) + device + ts + secret;

    // SHA-256 由公共实现提供（见 sha256.cpp）
    unsigned char digest[32];
    sha256_bytes(reinterpret_cast<const unsigned char*>(material.data()), material.size(), digest);

    env->ReleaseStringUTFChars(username, user);
    env->ReleaseStringUTFChars(deviceId, device);
    env->ReleaseStringUTFChars(timestamp, ts);
    return env->NewStringUTF(bytesToHex(digest, sizeof(digest)).c_str());
}
