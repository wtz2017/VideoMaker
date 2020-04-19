//
// Created by WTZ on 2020/4/17.
//

#include <jni.h>
#include <map> // STL 头文件没有扩展名 .h
#include "WeMp3Encoder.h"
#include "AndroidLog.h"

#define LOG_TAG "WeMp3JNI"

JavaVM *jvm = NULL;
std::map<int, WeMp3Encoder *> encoderMap;
int currentObjKey = 0;
WeMp3Encoder *currentEncoder = NULL;

/**
 * The VM calls JNI_OnLoad when the native library is loaded
 * (for example, through System.loadLibrary). 
 * JNI_OnLoad must return the JNI version needed by the native library.
 */
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

/**
 * The VM calls JNI_OnUnload when the class loader containing the native library is garbage collected.
 * This function can be used to perform cleanup operations.
 * Because this function is called in an unknown context (such as from a finalizer),
 * the programmer should be conservative on using Java VM services,
 * and refrain from arbitrary Java call-backs.
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGW(LOG_TAG, "JNI_OnUnload...");
}

WeMp3Encoder *findEncoder(int objKey) {
    if (objKey == currentObjKey && currentEncoder != NULL) {
        return currentEncoder;
    }

    std::map<int, WeMp3Encoder *>::iterator iterator = encoderMap.find(objKey);
    if (iterator != encoderMap.end()) {
        currentObjKey = objKey;
        currentEncoder = iterator->second;
        return currentEncoder;
    }

    return NULL;
}

void deleteEncoder(int objKey) {
    LOGW(LOG_TAG, "deleteEncoder objKey=%d", objKey)
    std::map<int, WeMp3Encoder *>::iterator iterator = encoderMap.find(objKey);
    if (iterator != encoderMap.end()) {
        delete iterator->second;
        encoderMap.erase(iterator);
    }
    if (objKey == currentObjKey) {
        currentObjKey = 0;
        // 这里不可再次 delete，因为指针内容可能不空，如果不空，那么通过前一步之前指针指向的内存已经回收了！
        // delete NULL是允许的！
//        delete currentEncoder;
        currentEncoder = NULL;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeInitMp3Encoder(JNIEnv *env, jobject thiz, jint objKey) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder == NULL) {
        OnEncodeProgressListener *listener = new OnEncodeProgressListener(jvm, env, thiz);
        WeMp3Encoder *newEncoder = new WeMp3Encoder(listener);
        LOGW(LOG_TAG, "nativeInitMp3Encoder can't find objKey=%d so new encoder instance=%d",
             objKey, newEncoder);
        encoderMap.insert(std::pair<int, WeMp3Encoder *>(objKey, newEncoder));
        currentObjKey = objKey;
        currentEncoder = newEncoder;
    } else {
        LOGW(LOG_TAG, "nativeInitMp3Encoder find exist: objKey=%d encoder=%d", objKey,
             pWeMp3Encoder);
    }
    return true;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeSetOutChannelMode(JNIEnv *env, jobject thiz, jint objKey,
                                                             jint mode) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder != NULL) {
        pWeMp3Encoder->setOutChannelMode(mode);
    } else {
        LOGE(LOG_TAG, "invoke nativeSetOutChannelMode but pWeMp3Encoder == NULL");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeSetOutBitrateMode(JNIEnv *env, jobject thiz, jint objKey,
                                                             jint mode, jint bitrate) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder != NULL) {
        pWeMp3Encoder->setOutBitrateMode(mode, bitrate);
    } else {
        LOGE(LOG_TAG, "invoke nativeSetOutBitrateMode but pWeMp3Encoder == NULL");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeSetOutQuality(JNIEnv *env, jobject thiz, jint objKey,
                                                         jint quality) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder != NULL) {
        pWeMp3Encoder->setOutQuality(quality);
    } else {
        LOGE(LOG_TAG, "invoke nativeSetOutQuality but pWeMp3Encoder == NULL");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeEnableEncodeFromPCMFile(JNIEnv *env, jobject thiz,
                                                                   jint objKey, jboolean enable) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder != NULL) {
        pWeMp3Encoder->enableEncodeFromPCMFile(enable);
    } else {
        LOGE(LOG_TAG, "invoke nativeEnableEncodeFromPCMFile but pWeMp3Encoder == NULL");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeEncodeFromPCMFile(JNIEnv *env, jobject thiz, jint objKey,
                                                             jstring pcm_file_path,
                                                             jint in_sample_rate,
                                                             jint in_channel_num,
                                                             jint bits_per_sample,
                                                             jboolean has_wav_head,
                                                             jstring save_path) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder == NULL) {
        LOGE(LOG_TAG, "invoke nativeEncodeFromPCMFile but pWeMp3Encoder == NULL");
        return;
    }

    const char *cPcmPath = env->GetStringUTFChars(pcm_file_path, NULL);
    const char *cSavePath = env->GetStringUTFChars(save_path, NULL);

    pWeMp3Encoder->encodeFromPCMFile(cPcmPath, in_sample_rate, in_channel_num, bits_per_sample,
                                     has_wav_head, cSavePath);

    env->ReleaseStringUTFChars(pcm_file_path, cPcmPath);
    env->ReleaseStringUTFChars(save_path, cSavePath);

}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeStartEncodePCMBuffer(JNIEnv *env, jobject thiz,
                                                                jint objKey,
                                                                jint in_sample_rate,
                                                                jint in_channel_num,
                                                                jint bits_per_sample,
                                                                jstring save_path) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder == NULL) {
        LOGE(LOG_TAG, "invoke nativeStartEncodePCMBuffer but pWeMp3Encoder == NULL");
        return false;
    }

    const char *cSavePath = env->GetStringUTFChars(save_path, NULL);

    bool ret = pWeMp3Encoder->startEncodePCMBuffer(in_sample_rate, in_channel_num, bits_per_sample,
                                                   cSavePath);

    env->ReleaseStringUTFChars(save_path, cSavePath);
    return ret;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeEncodeFromPCMBuffer(JNIEnv *env, jobject thiz,
                                                               jint objKey,
                                                               jshortArray pcm_buffer,
                                                               jint buffer_size) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder == NULL) {
        LOGE(LOG_TAG, "invoke nativeEncodeFromPCMBuffer but pWeMp3Encoder == NULL");
        return;
    }

    jshort *pArray = env->GetShortArrayElements(pcm_buffer, NULL);
    // 可以直接用 jshort，因为 typedef short jshort;
    pWeMp3Encoder->encodeFromPCMBuffer(pArray, buffer_size);
    env->ReleaseShortArrayElements(pcm_buffer, pArray, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeStopEncodePCMBuffer(JNIEnv *env, jobject thiz,
                                                               jint objKey) {
    WeMp3Encoder *pWeMp3Encoder = findEncoder(objKey);
    if (pWeMp3Encoder == NULL) {
        LOGE(LOG_TAG, "invoke nativeStopEncodePCMBuffer but pWeMp3Encoder == NULL");
        return;
    }

    pWeMp3Encoder->stopEncodePCMBuffer();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libmp3util_WeMp3Encoder_nativeReleaseMp3Encoder(JNIEnv *env, jobject thiz,
                                                             jint objKey) {
    deleteEncoder(objKey);
}