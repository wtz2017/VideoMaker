//
// Created by WTZ on 2020/4/24.
//

#ifndef VIDEOMAKER_ONPUSHDISCONNECTCALL_H
#define VIDEOMAKER_ONPUSHDISCONNECTCALL_H

#include "JavaListener.h"

class OnPushDisconnectCall : public JavaListener {

public:
    OnPushDisconnectCall(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
    : JavaListener(jvm, mainEnv, obj) {
    }

    ~OnPushDisconnectCall() {
    };

    const char *getMethodName() {
        return "onNativePushDisconnect";
    }

    const char *getMethodSignature() {
        return "()V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        env->CallVoidMethod(obj, methodId);
    }

};


#endif //VIDEOMAKER_ONPUSHDISCONNECTCALL_H
