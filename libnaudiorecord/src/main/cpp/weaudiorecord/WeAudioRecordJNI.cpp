//
// Created by WTZ on 2020/4/15.
//

#include <jni.h>
#include "WeAudioRecorder.h"
#include "AndroidLog.h"


#define LOG_TAG "WeAudioRecordJNI"

JavaVM *jvm;
WeAudioRecorder *pWeAudioRecorder;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    jvm = vm;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE(LOG_TAG, "JNI_OnLoad vm->GetEnv exception!");
        return -1;
    }
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeInitRecorder(JNIEnv *env, jobject thiz) {
    OnPCMDataCall *onPcmDataCall = new OnPCMDataCall(jvm, env, thiz);
    pWeAudioRecorder = new WeAudioRecorder(onPcmDataCall);
    return pWeAudioRecorder->init();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeInitRecorder2(JNIEnv *env, jobject thiz,
                                                                  jint sampleRate,
                                                                  jint channelLayout,
                                                                  jint encodingBits) {
    OnPCMDataCall *onPcmDataCall = new OnPCMDataCall(jvm, env, thiz);
    pWeAudioRecorder = new WeAudioRecorder(onPcmDataCall);
    return pWeAudioRecorder->init2(sampleRate, channelLayout, encodingBits);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeGetSampleRate(JNIEnv *env, jobject thiz) {
    if (pWeAudioRecorder == NULL) {
        LOGE(LOG_TAG, "Invoke nativeGetSampleRate but pWeAudioRecorder is NULL!");
        return 0;
    }

    return pWeAudioRecorder->getSampleRate();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeGetChannelNums(JNIEnv *env, jobject thiz) {
    if (pWeAudioRecorder == NULL) {
        LOGE(LOG_TAG, "Invoke nativeGetChannelNums but pWeAudioRecorder is NULL!");
        return 0;
    }

    return pWeAudioRecorder->getChannelNums();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeGetBitsPerSample(JNIEnv *env, jobject thiz) {
    if (pWeAudioRecorder == NULL) {
        LOGE(LOG_TAG, "Invoke nativeGetBitsPerSample but pWeAudioRecorder is NULL!");
        return 0;
    }

    return pWeAudioRecorder->getBitsPerSample();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeStartRecord(JNIEnv *env, jobject thiz) {
    if (pWeAudioRecorder == NULL) {
        LOGE(LOG_TAG, "Invoke nativeStartRecord but pWeAudioRecorder is NULL!");
        return false;
    }

    return pWeAudioRecorder->start();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeResumeRecord(JNIEnv *env, jobject thiz) {
    if (pWeAudioRecorder == NULL) {
        LOGE(LOG_TAG, "Invoke nativeResumeRecord but pWeAudioRecorder is NULL!");
        return false;
    }

    return pWeAudioRecorder->resume();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativePauseRecord(JNIEnv *env, jobject thiz) {
    if (pWeAudioRecorder == NULL) {
        LOGE(LOG_TAG, "Invoke nativePauseRecord but pWeAudioRecorder is NULL!");
        return;
    }

    pWeAudioRecorder->pause();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeStopRecord(JNIEnv *env, jobject thiz) {
    if (pWeAudioRecorder == NULL) {
        LOGE(LOG_TAG, "Invoke nativeStopRecord but pWeAudioRecorder is NULL!");
        return;
    }

    pWeAudioRecorder->stop();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wtz_libnaudiorecord_WeNAudioRecorder_nativeReleaseRecorder(JNIEnv *env, jobject thiz) {
    if (pWeAudioRecorder == NULL) {
        LOGE(LOG_TAG, "Invoke nativeReleaseRecorder but pWeAudioRecorder is NULL!");
        return;
    }

    pWeAudioRecorder->release();
    delete pWeAudioRecorder;
    pWeAudioRecorder = NULL;
}