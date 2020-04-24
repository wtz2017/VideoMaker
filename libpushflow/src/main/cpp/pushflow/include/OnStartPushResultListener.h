//
// Created by WTZ on 2020/4/24.
//

#ifndef VIDEOMAKER_ONSTARTPUSHRESULTLISTENER_H
#define VIDEOMAKER_ONSTARTPUSHRESULTLISTENER_H

#include "JavaListener.h"

class OnStartPushResultListener : public JavaListener {

public:
    OnStartPushResultListener(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
    : JavaListener(jvm, mainEnv, obj) {
    }

    ~OnStartPushResultListener() {
    };

    const char *getMethodName() {
        return "onNativeStartPushResult";
    }

    const char *getMethodSignature() {
        return "(ZLjava/lang/String;)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        bool success = va_arg(args, bool);
        const char *info = va_arg(args, const char *);

        jstring jStr = env->NewStringUTF(info);
        env->CallVoidMethod(obj, methodId, success, jStr);
        env->DeleteLocalRef(jStr);
    }

};


#endif //VIDEOMAKER_ONSTARTPUSHRESULTLISTENER_H
