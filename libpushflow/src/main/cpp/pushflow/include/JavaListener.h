/**
 * Created by WTZ on 2019/11/8.
 */

#ifndef FFMPEG_JAVALISTENER_H
#define FFMPEG_JAVALISTENER_H

#include <jni.h>
#include <unistd.h>
#include <stdarg.h>

class JavaListener {

private:
    const char *LOG_TAG = "JavaListener";
    pid_t _mainTid;
    JavaVM *_jvm;
    JNIEnv *_mainEnv;
    jobject _globalObj;
    jmethodID _methodID = NULL;// 若不初始化，则值为非空；置空用于后边判断一次性懒加载
    bool needDetach = false;

public:
    /**
     * 此构造方法需要在 C++ 主线程中调用，即直接从 java 层调用下来的线程
     *
     * @param jvm
     * @param mainEnv C++ 主线程 env
     * @param obj
     */
    JavaListener(JavaVM *jvm, JNIEnv *mainEnv, jobject obj);

    virtual ~JavaListener();

    void callback(int argCount, ...);

private:
    JNIEnv *initCallbackEnv();

    void releaseCallbackEnv();

protected:
    virtual const char *getMethodName() = 0;

    virtual const char *getMethodSignature() = 0;

    virtual void reallyCallback(JNIEnv *env, jobject obj, jmethodID methodId, va_list args) = 0;

};

#endif //FFMPEG_JAVALISTENER_H
