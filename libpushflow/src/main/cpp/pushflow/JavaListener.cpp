/**
 * Created by WTZ on 2019/11/8.
 */

#include "JavaListener.h"
#include "AndroidLog.h"


/**
 * 此构造方法需要在 C++ 主线程中调用，即直接从 java 层调用下来的线程
 *
 * @param jvm
 * @param mainEnv C++ 主线程 env
 * @param obj
 */
JavaListener::JavaListener(JavaVM *jvm, JNIEnv *mainEnv, jobject obj) {
    _mainTid = gettid();

    _jvm = jvm;
    _mainEnv = mainEnv;

    // Fix: JNI ERROR (app bug): accessed stale local reference
    _globalObj = _mainEnv->NewGlobalRef(obj);
}

JavaListener::~JavaListener() {
    JNIEnv *env = initCallbackEnv();
    env->DeleteGlobalRef(_globalObj);// 回收 GlobalReference
    releaseCallbackEnv();
}

JNIEnv *JavaListener::initCallbackEnv() {
    needDetach = false;
    pid_t currentTid = gettid();
    if (currentTid == _mainTid) {
        // 在 C++ 主线程中直接使用主线程 env
        return _mainEnv;
    }

    JNIEnv *env1;
    if (_jvm->GetEnv(reinterpret_cast<void **> (&env1), JNI_VERSION_1_6) == JNI_OK) {
        // 虽然切换了线程，能 GetEnv 成功，说明这是直接从 Java 层切换的线程
        return env1;
    }

    // 到这里，说明是在 C++ 中开启的子线程，此时需要用 AttachCurrentThread 来获取 env，
    // 否则会报错 JNI ERROR: non-VM thread making JNI call
    JNIEnv *env2;
    if (_jvm->AttachCurrentThread(&env2, 0) != JNI_OK) {
        LOGE(LOG_TAG, "AttachCurrentThread exception! currentTid: %d", currentTid);
        return NULL;
    }
    needDetach = true;
    return env2;
}

void JavaListener::releaseCallbackEnv() {
    pid_t currentTid = gettid();
    if (currentTid == _mainTid || !needDetach) {
        return;
    }

    _jvm->DetachCurrentThread();
    needDetach = false;
}

void JavaListener::callback(int argCount, ...) {
    JNIEnv *env = initCallbackEnv();
    if (_methodID == NULL) {
        _methodID = env->GetMethodID(env->GetObjectClass(_globalObj), getMethodName(),
                                     getMethodSignature());
        if (env->ExceptionCheck()) {
            LOGE(LOG_TAG, "GetMethodID exception! method: %s %s", getMethodName(), getMethodSignature());
            env->Throw(env->ExceptionOccurred());
            return;
        }
    }

    va_list args;
    va_start(args, argCount);

    reallyCallback(env, _globalObj, _methodID, args);

    va_end(args);
    releaseCallbackEnv();
}
