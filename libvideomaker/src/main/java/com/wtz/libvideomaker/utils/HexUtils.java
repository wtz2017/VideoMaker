package com.wtz.libvideomaker.utils;

public class HexUtils {

    public static String byteToHex(byte[] bytes, int max) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i]);
            if (hex.length() == 1) {
                stringBuffer.append("0" + hex);
            } else {
                stringBuffer.append(hex);
            }
            if (i > max) {
                break;
            }
        }
        return stringBuffer.toString();
    }

}
