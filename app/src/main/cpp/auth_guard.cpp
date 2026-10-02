#include "auth_guard.h"

#include <jni.h>
#include <cstring>
#include <string>

// 混淆存储的请求签名盐：真实值 = 每字节与 0x5A 异或后再叠加以字节序扰动。
// 解码：((encoded[i] - i*7) & 0xFF) ^ 0x5A。修改密钥时同步更新 server/config.php 的 ONYX_SIGN_SECRET。
static const unsigned char kObfuscatedSecret[] = {
    0x15, 0x3B, 0x11, 0x37, 0x21, 0x5B, 0x59, 0x64, 0x6E, 0x7D, 0x60, 0x78, 0x7F, 0xD7, 0xA0, 0x9C,
    0x99, 0xB0, 0xB3, 0xAD, 0xCA, 0xF4, 0x0A, 0x11, 0xD9, 0x1F, 0x26, 0xEB, 0xF9, 0xF5, 0x05, 0x12,
    0x0D, 0x16, 0x10, 0x24, 0x73, 0x13, 0x39, 0x83, 0x94, 0x92, 0x2B, 0x42, 0x3E, 0x55, 0xB3, 0xB1,
    0xBD, 0xCE, 0x8B, 0x94, 0x95, 0xA6, 0xB8, 0xFA, 0xFF, 0xA4, 0xAC, 0xAC, 0xB6, 0x28, 0x2D, 0x30,
    0xD3, 0xF1, 0x03, 0x4C, 0xF1, 0x17, 0xED, 0x13, 0xFD, 0x17, 0x35, 0x40, 0x4A, 0x59, 0x27, 0x39,
    0xA7, 0x45, 0x73, 0x6F, 0xE4, 0x4C,
};
static const size_t kSecretLen = sizeof(kObfuscatedSecret);

namespace {

std::string deobfuscateSecret() {
    std::string out;
    out.reserve(kSecretLen);
    for (size_t i = 0; i < kSecretLen; ++i) {
        const unsigned char perturbed =
            static_cast<unsigned char>((kObfuscatedSecret[i] - (i * 7)) & 0xFF);
        out.push_back(static_cast<char>(perturbed ^ 0x5A));
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
