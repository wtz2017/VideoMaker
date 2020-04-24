//
// Created by WTZ on 2020/4/18.
//

#ifndef VIDEOMAKER_ONENCODEPROGRESSLISTENER_H
#define VIDEOMAKER_ONENCODEPROGRESSLISTENER_H

#include "JavaListener.h"
class OnEncodeProgressListener : public JavaListener {

public:
    OnEncodeProgressListener(JavaVM *jvm, JNIEnv *mainEnv, jobject obj)
    : JavaListener(jvm, mainEnv, obj) {
    }

    ~OnEncodeProgressListener() {
    };

    const char *getMethodName() {
        return "onNativeEncodeProgressChanged";
    }

    const char *getMethodSignature() {
        return "(Ljava/lang/String;IZ)V";
    }

    void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) {
        const char *savePath = va_arg(args, const char *);
        int size = va_arg(args, int);
        bool complete = va_arg(args, bool);

        jstring jStrUtfPath = env->NewStringUTF(savePath);
        env->CallVoidMethod(obj, methodId, jStrUtfPath, size, complete);
        env->DeleteLocalRef(jStrUtfPath);
    }

};


#endif //VIDEOMAKER_ONENCODEPROGRESSLISTENER_H
