package com.wtz.videomaker.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateTimeUtil {

    /**
     * @param format e.g. "yy-MM-dd_HH-mm-ss"
     * @return DateTime
     */
    public static String getCurrentDateTime(String format) {
        Date date = new Date();
        SimpleDateFormat df = new SimpleDateFormat(format);
        String nowTime = df.format(date);
        return nowTime;
    }

    /**
     * 把剩余毫秒数转化成“时:分:秒”字符串
     *
     * @param timeMilli
     * @return
     */
    public static String changeRemainTimeToHms(long timeMilli) {
        if (timeMilli == 0) {
            return "00:00:00";
        }
        int totalSeconds = Math.round((float) timeMilli / 1000);// 毫秒数转秒数，毫秒部分四舍五入
        int second = totalSeconds % 60;// 秒数除60得分钟数再取余得秒数
        int minute = totalSeconds / 60 % 60;// 秒数除两个60得小时再取余得分钟数
        int hour = totalSeconds / 60 / 60;// 秒数除两个60得小时数
        String hourString = formatTime(String.valueOf(hour));
        String minuteString = formatTime(String.valueOf(minute));
        String secondString = formatTime(String.valueOf(second));
        return hourString + ":" + minuteString + ":" + secondString;
    }

    private static String formatTime(String original) {
        if (original != null && original.length() < 2) {
            original = "0" + original;
        }
        return original;
    }

}
