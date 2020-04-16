//
// Created by WTZ on 2019/12/16.
//

#ifndef FFMPEG_ONPCMDATACALL_H
#define FFMPEG_ONPCMDATACALL_H


#include "JavaListener.h"

class OnPCMDataCall : public JavaListener {

private:
    const char *LOG_TAG = "_OnPCMDataCall";

public:
    OnPCMDataCall(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
            : JavaListener(jvm, mainEnv, obj) {
    }

    ~OnPCMDataCall() {
    };

    const char *getMethodName() {
        return "onNativePCMDataCall";
    }

    const char *getMethodSignature() {
        return "([BI)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        void *data = va_arg(args, void *);
        int size = va_arg(args, int);

        jbyteArray jbyteBuffer = env->NewByteArray(size);

        env->SetByteArrayRegion(jbyteBuffer, 0, size, static_cast<const jbyte *>(data));
        env->CallVoidMethod(obj, methodId, jbyteBuffer, size);

        env->DeleteLocalRef(jbyteBuffer);
    }

};


#endif //FFMPEG_ONPCMDATACALL_H
