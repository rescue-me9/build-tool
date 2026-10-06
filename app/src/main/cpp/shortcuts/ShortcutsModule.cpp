#include "ShortcutsRuntime.h"
#include "../main.h"
#include <jni.h>
#include <cstring>
#include <cstdio>
#include <string>

#define LOG_TAG "Infinitecz_ShortcutsJni"
#include "../log_control.h"

namespace {

jstring toJString(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

std::string jstrToStd(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* utf8 = env->GetStringUTFChars(value, nullptr);
    if (!utf8) return {};
    std::string result(utf8);
    env->ReleaseStringUTFChars(value, utf8);
    return result;
}

void Java_com_vdl_kong520_ShortcutsModule_setShortcutEnabled(JNIEnv*, jclass, jint featureId, jboolean enabled) {
    shortcuts::ShortcutsRuntime::instance().setFeatureEnabled(
        static_cast<shortcuts::FeatureId>(featureId), enabled == JNI_TRUE);
}

jboolean Java_com_vdl_kong520_ShortcutsModule_isShortcutEnabled(JNIEnv*, jclass, jint featureId) {
    return shortcuts::ShortcutsRuntime::instance().isFeatureEnabled(
        static_cast<shortcuts::FeatureId>(featureId)) ? JNI_TRUE : JNI_FALSE;
}

void Java_com_vdl_kong520_ShortcutsModule_setShortcutIntParam(JNIEnv*, jclass, jint featureId, jint index, jint value) {
    shortcuts::ShortcutsRuntime::instance().setFeatureIntParam(
        static_cast<shortcuts::FeatureId>(featureId), index, value);
}

void Java_com_vdl_kong520_ShortcutsModule_setShortcutFloatParam(JNIEnv*, jclass, jint featureId, jint index, jfloat value) {
    shortcuts::ShortcutsRuntime::instance().setFeatureFloatParam(
        static_cast<shortcuts::FeatureId>(featureId), index, value);
}

void Java_com_vdl_kong520_ShortcutsModule_setShortcutStringParam(JNIEnv* env, jclass, jint featureId, jstring value) {
    shortcuts::ShortcutsRuntime::instance().setFeatureStringParam(
        static_cast<shortcuts::FeatureId>(featureId), jstrToStd(env, value));
}

jint Java_com_vdl_kong520_ShortcutsModule_getShortcutIntParam(JNIEnv*, jclass, jint featureId, jint index) {
    return shortcuts::ShortcutsRuntime::instance().getFeatureIntParam(
        static_cast<shortcuts::FeatureId>(featureId), index);
}

jfloat Java_com_vdl_kong520_ShortcutsModule_getShortcutFloatParam(JNIEnv*, jclass, jint featureId, jint index) {
    return shortcuts::ShortcutsRuntime::instance().getFeatureFloatParam(
        static_cast<shortcuts::FeatureId>(featureId), index);
}

jstring Java_com_vdl_kong520_ShortcutsModule_getShortcutStringParam(JNIEnv* env, jclass, jint featureId) {
    return toJString(env, shortcuts::ShortcutsRuntime::instance().getFeatureStringParam(
        static_cast<shortcuts::FeatureId>(featureId)));
}

jstring Java_com_vdl_kong520_ShortcutsModule_getShortcutPlayerInfo(JNIEnv* env, jclass) {
    shortcuts::PlayerInfo info;
    if (!shortcuts::ShortcutsRuntime::instance().getPlayerInfo(&info)) {
        return toJString(env, "");
    }
    char buf[256];
    snprintf(buf, sizeof(buf), "%d,%d,%d,%.1f,%.1f,%.1f,%.0f,%d,%lld",
             info.block_x, info.block_y, info.block_z,
             info.exact_x, info.exact_y, info.exact_z,
             info.yaw, info.dimension, (long long)info.tick_counter);
    return toJString(env, buf);
}

jboolean Java_com_vdl_kong520_ShortcutsModule_setShortcutHome(JNIEnv*, jclass) {
    return shortcuts::ShortcutsRuntime::instance().setHomePosition() ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_vdl_kong520_ShortcutsModule_setShortcutSafeZone(JNIEnv*, jclass) {
    return shortcuts::ShortcutsRuntime::instance().setSafeZoneCenter() ? JNI_TRUE : JNI_FALSE;
}

jstring Java_com_vdl_kong520_ShortcutsModule_getShortcutStatus(JNIEnv* env, jclass, jint featureId) {
    return toJString(env, shortcuts::ShortcutsRuntime::instance().getFeatureStatus(
        static_cast<shortcuts::FeatureId>(featureId)));
}

const JNINativeMethod kMethods[] = {
    {"setShortcutEnabled", "(IZ)V", (void*)Java_com_vdl_kong520_ShortcutsModule_setShortcutEnabled},
    {"isShortcutEnabled", "(I)Z", (void*)Java_com_vdl_kong520_ShortcutsModule_isShortcutEnabled},
    {"setShortcutIntParam", "(III)V", (void*)Java_com_vdl_kong520_ShortcutsModule_setShortcutIntParam},
    {"setShortcutFloatParam", "(IIF)V", (void*)Java_com_vdl_kong520_ShortcutsModule_setShortcutFloatParam},
    {"setShortcutStringParam", "(ILjava/lang/String;)V", (void*)Java_com_vdl_kong520_ShortcutsModule_setShortcutStringParam},
    {"getShortcutIntParam", "(II)I", (void*)Java_com_vdl_kong520_ShortcutsModule_getShortcutIntParam},
    {"getShortcutFloatParam", "(II)F", (void*)Java_com_vdl_kong520_ShortcutsModule_getShortcutFloatParam},
    {"getShortcutStringParam", "(I)Ljava/lang/String;", (void*)Java_com_vdl_kong520_ShortcutsModule_getShortcutStringParam},
    {"getShortcutPlayerInfo", "()Ljava/lang/String;", (void*)Java_com_vdl_kong520_ShortcutsModule_getShortcutPlayerInfo},
    {"setShortcutHome", "()Z", (void*)Java_com_vdl_kong520_ShortcutsModule_setShortcutHome},
    {"setShortcutSafeZone", "()Z", (void*)Java_com_vdl_kong520_ShortcutsModule_setShortcutSafeZone},
    {"getShortcutStatus", "(I)Ljava/lang/String;", (void*)Java_com_vdl_kong520_ShortcutsModule_getShortcutStatus},
};

}  // namespace

bool RegisterShortcutsNatives(JNIEnv* env) noexcept {
    jclass clazz = env->FindClass("com/vdl/kong520/ShortcutsModule");
    if (!clazz) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        LOGE("could not find ShortcutsModule class");
        return false;
    }
    const jint result = env->RegisterNatives(
        clazz, kMethods, sizeof(kMethods) / sizeof(kMethods[0]));
    env->DeleteLocalRef(clazz);
    if (result != JNI_OK) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        LOGE("RegisterNatives failed for ShortcutsModule");
        return false;
    }
    LOGI("ShortcutsModule JNI natives registered");
    return true;
}
