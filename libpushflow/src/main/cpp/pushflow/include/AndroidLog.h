#ifndef FFMPEG_ANDROIDLOG_H
#define FFMPEG_ANDROIDLOG_H

#include <android/log.h>

#define LOG_DEBUG true // 表示一般步骤信息日志是否打开，比如初始化日志
#define LOG_REPEAT_DEBUG false // 表示重复性的大量日志是否打开，例如入队出队数据日志

#define LOGV(LOG_TAG, FORMAT, ...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, FORMAT, ##__VA_ARGS__);
#define LOGD(LOG_TAG, FORMAT, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, FORMAT, ##__VA_ARGS__);
#define LOGI(LOG_TAG, FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, FORMAT, ##__VA_ARGS__);
#define LOGW(LOG_TAG, FORMAT, ...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, FORMAT, ##__VA_ARGS__);
#define LOGE(LOG_TAG, FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, FORMAT, ##__VA_ARGS__);

#endif //FFMPEG_ANDROIDLOG_H
