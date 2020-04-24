//
// Created by WTZ on 2020/4/20.
//

#include <jni.h>
#include "WePushFlow.h"
#include "AndroidLog.h"

#define LOG_TAG "WePushFlowJNI"

JavaVM *jvm;
WePushFlow *pWePushFlow;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGW(LOG_TAG, "JNI_OnLoad...");
    JNIEnv *env;
    jvm = vm;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE(LOG_TAG, "JNI_OnLoad vm->GetEnv exception!");
        return -1;
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGW(LOG_TAG, "JNI_OnUnload...");
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeCreatePushFlow(JNIEnv *env, jobject thiz) {
    if (pWePushFlow == NULL) {
        OnStartPushResultListener *listener = new OnStartPushResultListener(jvm, env, thiz);
        pWePushFlow = new WePushFlow(listener);
    }

    return true;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeSetPushUrl(JNIEnv *env, jobject thiz, jstring url) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativeSetPushUrl but pWePushFlow == NULL");
        return;
    }

    if (url == NULL || env->GetStringUTFLength(url) == 0) {
        LOGE(LOG_TAG, "Can't set a 'null' string to push url!");
        return;
    }

    int jstrUtf16Len = env->GetStringLength(url);
    int jstrUtf8Len = env->GetStringUTFLength(url);
    char *source = new char[jstrUtf8Len + 1];// 回收放在 WePushFlow 中
    env->GetStringUTFRegion(url, 0, jstrUtf16Len, source);
    source[jstrUtf8Len] = '\0';

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "nativeSetPushUrl: %s", source);
    }
    pWePushFlow->setPushUrl(source);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeSetConnectTimeout(JNIEnv *env, jobject thiz,
                                                            jint seconds) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativeSetConnectTimeout but pWePushFlow == NULL");
        return;
    }

    pWePushFlow->setConnectTimeout(seconds);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeSetAudioEncodeBits(JNIEnv *env, jobject thiz,
                                                             jint audio_encode_bits) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativeSetAudioEncodeBits but pWePushFlow == NULL");
        return;
    }

    pWePushFlow->setAudioEncodeBits(audio_encode_bits);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeSetAudioChannels(JNIEnv *env, jobject thiz,
                                                           jint audio_channels) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativeSetAudioChannels but pWePushFlow == NULL");
        return;
    }

    pWePushFlow->setAudioChannels(audio_channels);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeStartPush(JNIEnv *env, jobject thiz) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativeStartPush but pWePushFlow == NULL");
        return;
    }

    pWePushFlow->startPush();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativePushSpsPps(JNIEnv *env, jobject thiz, jbyteArray sps,
                                                     jint sps_length, jbyteArray pps,
                                                     jint pps_length) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativePushSpsPps but pWePushFlow == NULL");
        return;
    }

    jbyte *spsJbyte = env->GetByteArrayElements(sps, NULL);
    jbyte *ppsJbyte = env->GetByteArrayElements(pps, NULL);

    // typedef signed char     jbyte;
    pWePushFlow->pushSpsPps(reinterpret_cast<char *>(spsJbyte), sps_length,
                            reinterpret_cast<char *>(ppsJbyte), pps_length);

    env->ReleaseByteArrayElements(sps, spsJbyte, 0);
    env->ReleaseByteArrayElements(pps, ppsJbyte, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativePushVideoData(JNIEnv *env, jobject thiz, jbyteArray data,
                                                        jint data_length, jboolean is_keyframe) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativePushVideoData but pWePushFlow == NULL");
        return;
    }

    jbyte *dataJbyte = env->GetByteArrayElements(data, NULL);

    // typedef signed char     jbyte;
    pWePushFlow->pushVideoData(reinterpret_cast<char *>(dataJbyte), data_length, is_keyframe);

    env->ReleaseByteArrayElements(data, dataJbyte, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativePushAudioData(JNIEnv *env, jobject thiz, jbyteArray data,
                                                        jint data_length) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativePushAudioData but pWePushFlow == NULL");
        return;
    }

    jbyte *dataJbyte = env->GetByteArrayElements(data, NULL);

    // typedef signed char     jbyte;
    pWePushFlow->pushAudioData(reinterpret_cast<char *>(dataJbyte), data_length);

    env->ReleaseByteArrayElements(data, dataJbyte, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeSetStopFlag(JNIEnv *env, jobject thiz) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativeSetStopFlag but pWePushFlow == NULL");
        return;
    }

    pWePushFlow->setStopFlag();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeStopPush(JNIEnv *env, jobject thiz) {
    if (pWePushFlow == NULL) {
        LOGE(LOG_TAG, "invoke nativeStopPush but pWePushFlow == NULL");
        return;
    }

    pWePushFlow->stopPush();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libpushflow_WePushFlow_nativeDestroyPushFlow(JNIEnv *env, jobject thiz) {
    delete pWePushFlow;
    pWePushFlow = NULL;
}