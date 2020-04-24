package com.wtz.libvideomaker.utils;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public final class ExponentialWaitStrategy {

    private final long multiplier;
    private final long maximumWait;

    public ExponentialWaitStrategy(long multiplier, long maximumWait, TimeUnit maximumTimeUnit) {
        this.multiplier = multiplier;
        this.maximumWait = maximumTimeUnit.toMillis(maximumWait);
    }

    public long computeSleepTime(long retryNumber) {
        double exp = Math.pow(2, retryNumber);
        long result = Math.round(multiplier * exp);
        if (result > maximumWait) {
            result = maximumWait;
        }
        return result >= 0L ? result : 0L;
    }

    public static long getRandomDelayMillis(int boundSeconds) {
        long seed = System.nanoTime() + android.os.Process.myPid() + android.os.Process.myTid();
        Random random = new Random(seed);
        int seconds = random.nextInt(boundSeconds);
        return seconds * 1000;
    }

}
