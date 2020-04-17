package com.wtz.videomaker.utils;

public class AudioUtils {

    /**
     * 线性叠加平均混音
     * 这种办法的原理非常简单粗暴，也不会引入噪音。原理就是把不同音轨的通道值叠加之后取平均值，
     * 这样就不会有溢出的问题了。但是会带来的后果就是某一路或几路音量特别小那么整个混音结果的音量会被拉低。
     * 此混音方法适用于“采样率一致、通道数一致、通道采样精度统一为 16 位”
     * 参考：叶大侠 https://juejin.im/post/5aa40ff6f265da239866e12c
     * 自己添加了混合比例参数
     */
    public static byte[] linearMix16bitAudioBytes(byte[][] multiTrackAudioBytes, int sizePerTrack,
                                                  float[] trackRatios) {
        if (multiTrackAudioBytes == null || multiTrackAudioBytes.length == 0
                || trackRatios == null || trackRatios.length != multiTrackAudioBytes.length) {
            return null;
        }

        byte[] mixAudioBytes = multiTrackAudioBytes[0];
        if (mixAudioBytes == null || mixAudioBytes.length < sizePerTrack) {
            return null;
        }

        final int rowSize = multiTrackAudioBytes.length;

        //单路音轨
        if (rowSize == 1) {
            return mixAudioBytes;
        }

        //不同轨道长度要一致，不够要补齐
        for (int row = 0; row < rowSize; ++row) {
            if (multiTrackAudioBytes[row] == null ||
                    multiTrackAudioBytes[row].length != mixAudioBytes.length) {
                return null;
            }
        }

        // 精度为 16位，bytes --> shorts
        int columnSize = sizePerTrack / 2;
        short[][] multiTrackAudioShorts = new short[rowSize][columnSize];
        for (int row = 0; row < rowSize; ++row) {
            for (int col = 0; col < columnSize; ++col) {
                multiTrackAudioShorts[row][col] = (short) ((multiTrackAudioBytes[row][col * 2] & 0xff)
                        | (multiTrackAudioBytes[row][col * 2 + 1] & 0xff) << 8);
            }
        }

        // mix audio data
        short[] mixAudioShorts = new short[columnSize];
        int mixVal;
        int row = 0;
        int ratioVal = 0;
        for (int col = 0; col < columnSize; ++col) {
            mixVal = 0;
            for (row = 0; row < rowSize; ++row) {
                ratioVal = (int) (multiTrackAudioShorts[row][col] * trackRatios[row] + 0.5f);
                if (ratioVal > Short.MAX_VALUE) {
                    ratioVal = Short.MAX_VALUE;
                } else if (ratioVal < Short.MIN_VALUE) {
                    ratioVal = Short.MIN_VALUE;
                }
                mixVal += ratioVal;
            }
            mixAudioShorts[col] = (short) (mixVal / rowSize);
        }

        // shorts --> bytes
        for (row = 0; row < columnSize; ++row) {
            mixAudioBytes[row * 2] = (byte) (mixAudioShorts[row] & 0x00FF);
            mixAudioBytes[row * 2 + 1] = (byte) ((mixAudioShorts[row] & 0xFF00) >> 8);
        }
        return mixAudioBytes;
    }

    /**
     * 自适应混音
     * 参与混音的多路音频信号自身的特点，以它们自身的比例作为权重，从而决定它们在合成后的输出中所占的比重。
     * 这种方法对于音轨路数比较多的情况应该会比上面的平均法要好，但是可能会引入噪音。
     * 此混音方法适用于“采样率一致、通道数一致、通道采样精度统一为 16 位”
     * 参考：叶大侠 https://juejin.im/post/5aa40ff6f265da239866e12c
     */
    public static byte[] selfAdapMix16bitAudioBytes(byte[][] multiTrackAudioBytes) {
        if (multiTrackAudioBytes == null || multiTrackAudioBytes.length == 0){
            return null;
        }

        byte[] mixAudioBytes = multiTrackAudioBytes[0];
        if (mixAudioBytes == null) {
            return null;
        }

        final int rowSize = multiTrackAudioBytes.length;

        //单路音轨
        if (rowSize == 1) {
            return mixAudioBytes;
        }

        //不同轨道长度要一致，不够要补齐
        for (int row = 0; row < rowSize; ++row) {
            if (multiTrackAudioBytes[row] == null
                    || multiTrackAudioBytes[row].length != mixAudioBytes.length) {
                return null;
            }
        }

        // 精度为 16位，bytes --> shorts
        int columnSize = mixAudioBytes.length / 2;
        short[][] multiTrackAudioShorts = new short[rowSize][columnSize];
        for (int row = 0; row < rowSize; ++row) {
            for (int col = 0; col < columnSize; ++col) {
                multiTrackAudioShorts[row][col] = (short) ((multiTrackAudioBytes[row][col * 2] & 0xff)
                        | (multiTrackAudioBytes[row][col * 2 + 1] & 0xff) << 8);
            }
        }

        // mix audio data
        short[] mixAudioShorts = new short[columnSize];
        int row = 0;
        double wValue;
        double absSumVal;
        for (int col = 0; col < columnSize; ++col) {
            wValue = 0;
            absSumVal = 0;
            for (row = 0; row < rowSize; ++row) {
                wValue += Math.pow(multiTrackAudioShorts[row][col], 2)
                        * Math.signum(multiTrackAudioShorts[row][col]);
                absSumVal += Math.abs(multiTrackAudioShorts[row][col]);
            }
            mixAudioShorts[col] = absSumVal == 0 ? 0 : (short) (wValue / absSumVal);
        }

        // shorts --> bytes
        for (row = 0; row < columnSize; ++row) {
            mixAudioBytes[row * 2] = (byte) (mixAudioShorts[row] & 0x00FF);
            mixAudioBytes[row * 2 + 1] = (byte) ((mixAudioShorts[row] & 0xFF00) >> 8);
        }
        return mixAudioBytes;
    }

}
