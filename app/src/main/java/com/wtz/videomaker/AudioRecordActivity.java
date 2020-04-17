package com.wtz.videomaker;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wtz.ffmpegapi.WAVSaver;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.libnaudiorecord.WeNAudioRecorder;
import com.wtz.libvideomaker.recorder.WeJAudioRecorder;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.videomaker.utils.DateTimeUtil;
import com.wtz.videomaker.utils.PermissionChecker;
import com.wtz.videomaker.utils.PermissionHandler;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


public class AudioRecordActivity extends AppCompatActivity implements View.OnClickListener, PermissionHandler.PermissionHandleListener {
    private static final String TAG = AudioRecordActivity.class.getSimpleName();

    private WeJAudioRecorder mWeJAudioRecorder;
    private WeNAudioRecorder mWeNAudioRecorder;
    private WAVSaver mWAVSaver;
    private boolean isInitSuccess;
    private boolean isJavaRecording;
    private boolean isNativeRecording;
    private boolean useJavaRecord;

    private TextView mRecordTimeTV;
    private TextView mSoundDecibelsTV;
    private Button mJavaRecordButton;
    private Button mNativeRecordButton;
    private static final DecimalFormat DECIBELS_FORMAT = new DecimalFormat("0.00dB");

    private WePlayer mWePlayer;
    private int mMusicDuration;
    private int mSeekPosition;
    private boolean isMusicPrepared;
    private boolean isMusicLoading;
    private boolean isMusicSeeking;
    private TextView mMusicPlayTimeView;
    private TextView mMusicTotalTimeView;
    private SeekBar mMusicSeekBar;
    private Button mControlMusicButton;
    private TextView mPitchView;
    private SeekBar mPitchSeekBar;
    private TextView mTempoView;
    private SeekBar mTempoSeekBar;
    private static final DecimalFormat PITCH_FORMAT = new DecimalFormat("0.0");
    private static final float MAX_PITCH = 3.0f;
    private static final float PITCH_ACCURACY = 0.1f;
    private static final DecimalFormat TEMPO_FORMAT = new DecimalFormat("0.0");
    private static final float MAX_TEMPO = 3.0f;
    private static final float TEMPO_ACCURACY = 0.1f;

    private String mSaveWavDir;
    private String mCurrentPathName;
    private static final String WAV_PREFIX = "We_AUD_";
    private static final String WAV_SUFFIX = ".wav";
    private final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private PermissionHandler mPermissionHandler;

    private static final int MSG_UPDATE_RECORD_INFO = 0;
    private static final int MSG_UPDATE_MUSIC_TIME = 1;
    private static final int UPDATE_RECORD_INFO_INTERVAL = 100;
    private static final int UPDATE_MUSIC_TIME_INTERVAL = 500;
    private WeakHandler mUIHandler = new WeakHandler(this);

    static class WeakHandler extends Handler {
        private final WeakReference<AudioRecordActivity> weakReference;

        // 为了避免非静态的内部类和匿名内部类隐式持有外部类的引用，改用静态类
        // 又因为内部类是静态类，所以不能直接操作宿主类中的方法了，
        // 于是需要传入宿主类实例的弱引用来操作宿主类中的方法
        public WeakHandler(AudioRecordActivity host) {
            super(Looper.getMainLooper());
            this.weakReference = new WeakReference(host);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioRecordActivity host = weakReference.get();
            if (host == null) {
                return;
            }

            switch (msg.what) {
                case MSG_UPDATE_RECORD_INFO:
                    host.updateRecordInfo();
                    removeMessages(MSG_UPDATE_RECORD_INFO);
                    sendEmptyMessageDelayed(MSG_UPDATE_RECORD_INFO, UPDATE_RECORD_INFO_INTERVAL);
                    break;
                case MSG_UPDATE_MUSIC_TIME:
                    host.updateMusicTime();
                    removeMessages(MSG_UPDATE_MUSIC_TIME);
                    sendEmptyMessageDelayed(MSG_UPDATE_MUSIC_TIME, UPDATE_MUSIC_TIME_INTERVAL);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_audio_record);
        mRecordTimeTV = findViewById(R.id.tv_record_time);
        mSoundDecibelsTV = findViewById(R.id.tv_record_decibels);
        mJavaRecordButton = findViewById(R.id.btn_java_record_audio);
        mNativeRecordButton = findViewById(R.id.btn_native_record_audio);
        mJavaRecordButton.setOnClickListener(this);
        mNativeRecordButton.setOnClickListener(this);
        initAudioPlayer();

        mPermissionHandler = new PermissionHandler(this, this);
        mPermissionHandler.handleCommonPermission(Manifest.permission.RECORD_AUDIO);
        mPermissionHandler.handleCommonPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        File savePath = new File(Environment.getExternalStorageDirectory(), "WePhotos");
        mSaveWavDir = savePath.getAbsolutePath();
    }

    private void initAudioPlayer() {
        mControlMusicButton = findViewById(R.id.btn_music_play_control);
        mControlMusicButton.setOnClickListener(this);
        mMusicPlayTimeView = findViewById(R.id.tv_music_play_time);
        mMusicTotalTimeView = findViewById(R.id.tv_music_total_time);
        mPitchView = findViewById(R.id.tv_pitch);
        mTempoView = findViewById(R.id.tv_tempo);

        mMusicSeekBar = findViewById(R.id.seek_bar_music);
        mMusicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (isMusicSeeking) {
                    // 因为主动 seek 导致的 seekbar 变化，此时只需要更新时间
                    updateMusicTime();
                } else {
                    // 因为实际播放时间变化而设置 seekbar 导致变化，什么都不用做
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mMusicSeekBar onStartTrackingTouch");
                isMusicSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mMusicSeekBar onStopTrackingTouch");
                if (mWePlayer != null && isMusicPrepared) {
                    mWePlayer.seekTo(seekBar.getProgress());
                } else {
                    mSeekPosition = seekBar.getProgress();
                    isMusicSeeking = false;
                }
            }
        });

        mPitchSeekBar = findViewById(R.id.seek_bar_pitch);
        mPitchSeekBar.setMax((int) (MAX_PITCH / PITCH_ACCURACY));
        mPitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (mWePlayer != null) {
                    float pitch = seekBar.getProgress() / (float) seekBar.getMax() * MAX_PITCH;
                    mPitchView.setText("音调：" + PITCH_FORMAT.format(pitch));
                    mWePlayer.setPitch(pitch);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mPitchSeekBar onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mPitchSeekBar onStopTrackingTouch");
            }
        });

        mTempoSeekBar = findViewById(R.id.seek_bar_tempo);
        mTempoSeekBar.setMax((int) (MAX_TEMPO / TEMPO_ACCURACY));
        mTempoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (mWePlayer != null) {
                    float tempo = seekBar.getProgress() / (float) seekBar.getMax() * MAX_TEMPO;
                    mTempoView.setText("音速：" + TEMPO_FORMAT.format(tempo));
                    mWePlayer.setTempo(tempo);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mTempoSeekBar onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                LogUtils.d(TAG, "mTempoSeekBar onStopTrackingTouch");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        LogUtils.d(TAG, "onRequestPermissionsResult requestCode=" + requestCode);
        mPermissionHandler.handleActivityRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogUtils.d(TAG, "onActivityResult requestCode=" + requestCode + ", resultCode=" + resultCode
                + ", data=" + data);
        mPermissionHandler.handleActivityResult(requestCode);
    }

    @Override
    public void onPermissionResult(String permission, PermissionChecker.PermissionState state) {
        LogUtils.w(TAG, "onPermissionResult " + permission + " state is " + state);
        if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
            if (state == PermissionChecker.PermissionState.ALLOWED) {
                initAudioRecord();
            } else if (state == PermissionChecker.PermissionState.UNKNOWN) {
                LogUtils.e(TAG, "onPermissionResult " + permission + " state is UNKNOWN!");
                initAudioRecord();
            } else if (state == PermissionChecker.PermissionState.USER_NOT_GRANTED) {
                LogUtils.e(TAG, "onPermissionResult " + permission + " state is USER_NOT_GRANTED!");
                finish();
            } else {
                LogUtils.w(TAG, "onPermissionResult " + permission + " state is " + state);
            }
        } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            // do something
        }
    }

    private void initAudioRecord() {
        mWeJAudioRecorder = new WeJAudioRecorder();
        mWeJAudioRecorder.setOnAudioRecordDataListener(mJAudioListener);
        mWeNAudioRecorder = new WeNAudioRecorder();
        mWeNAudioRecorder.setOnAudioRecordDataListener(mNAudioListener);
        mWAVSaver = new WAVSaver();
        isInitSuccess = true;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_java_record_audio:
                useJavaRecord = true;
                record();
                break;

            case R.id.btn_native_record_audio:
                useJavaRecord = false;
                record();
                break;

            case R.id.btn_music_play_control:
                playStopMusic();
                break;
        }
    }

    private void record() {
        if (isInitSuccess) {
            if (useJavaRecord) {
                if (isNativeRecording) {
                    stopNativeRecord();
                }
                if (isJavaRecording) {
                    stopJavaRecord();
                    isJavaRecording = false;
                } else {
                    startJavaRecord();
                    isJavaRecording = true;
                }
            } else {
                if (isJavaRecording) {
                    stopJavaRecord();
                }
                if (isNativeRecording) {
                    stopNativeRecord();
                    isNativeRecording = false;
                } else {
                    startNativeRecord();
                    isNativeRecording = true;
                }
            }
        }
    }

    private void startJavaRecord() {
        stopMusic();
        mCurrentPathName = getSavePathName();
        mWAVSaver.start(
                mWeJAudioRecorder.getSampleRate(),
                mWeJAudioRecorder.getChannelNums(),
                mWeJAudioRecorder.getBitsPerSample(),
                0, new File(mCurrentPathName));
        mWeJAudioRecorder.startRecord();
        mJavaRecordButton.setText("停止录音");
        startUpdateRecordInfo();
    }

    private void startNativeRecord() {
        stopMusic();
        mCurrentPathName = getSavePathName();

        mWAVSaver.start(
                mWeNAudioRecorder.getSampleRate(),
                mWeNAudioRecorder.getChannelNums(),
                mWeNAudioRecorder.getBitsPerSample(),
                0, new File(mCurrentPathName));
        mWeNAudioRecorder.startRecord();
        mNativeRecordButton.setText("停止录音");

        startUpdateRecordInfo();
    }

    private void stopJavaRecord() {
        mWeJAudioRecorder.stopRecord();
        mJavaRecordButton.setText("开始录音");
        mWAVSaver.stop();
        notifyScanMedia(mCurrentPathName);
        stopUpdateRecordInfo();
    }

    private void stopNativeRecord() {
        mWeNAudioRecorder.stopRecord();
        mNativeRecordButton.setText("开始录音");
        mWAVSaver.stop();
        notifyScanMedia(mCurrentPathName);
        stopUpdateRecordInfo();
    }

    private void startUpdateRecordInfo() {
        mUIHandler.sendEmptyMessage(MSG_UPDATE_RECORD_INFO);
    }

    private void stopUpdateRecordInfo() {
        mUIHandler.removeMessages(MSG_UPDATE_RECORD_INFO);
    }

    private void updateRecordInfo() {
        long timemills = 0;
        double decibels = 0;
        if (useJavaRecord) {
            if (mWeJAudioRecorder != null) {
                timemills = mWeJAudioRecorder.getRecordTimeMills();
                decibels = mWeJAudioRecorder.getSoundDecibels();
            }
        } else {
            if (mWeNAudioRecorder != null) {
                timemills = mWeNAudioRecorder.getRecordTimeMills();
                decibels = mWeNAudioRecorder.getSoundDecibels();
            }
        }
        String time = DateTimeUtil.changeRemainTimeToHms(timemills);
        mRecordTimeTV.setText(time);
        mSoundDecibelsTV.setText(DECIBELS_FORMAT.format(decibels));
    }

    private WeJAudioRecorder.OnAudioRecordDataListener mJAudioListener = new WeJAudioRecorder.OnAudioRecordDataListener() {
        @Override
        public void onAudioRecordData(byte[] data, int size) {
            if (mWAVSaver != null) {
                mWAVSaver.encode(data, size);
            }
        }
    };

    private WeNAudioRecorder.OnAudioRecordDataListener mNAudioListener = new WeNAudioRecorder.OnAudioRecordDataListener() {
        @Override
        public void onAudioRecordData(byte[] data, int size) {
            if (mWAVSaver != null) {
                mWAVSaver.encode(data, size);
            }
        }
    };

    private String getSavePathName() {
        String time = mSimpleDateFormat.format(new Date());
        return new File(mSaveWavDir, WAV_PREFIX + time + WAV_SUFFIX).getAbsolutePath();
    }

    private void notifyScanMedia(String pathName) {
        Uri contentUri = Uri.fromFile(new File(pathName));
        Intent i = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, contentUri);
        sendBroadcast(i);
    }

    private void playStopMusic() {
        if (TextUtils.isEmpty(mCurrentPathName)) {
            Toast.makeText(this, "请先录音", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isMusicPrepared) {
            openMusic(mCurrentPathName);
        } else {
            stopMusic();
        }
    }

    private void openMusic(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        isMusicPrepared = false;
        isMusicLoading = false;
        if (mWePlayer == null) {
            mWePlayer = new WePlayer(true);
            mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                @Override
                public void onPrepared() {
                    LogUtils.d(TAG, "WePlayer onPrepared");
                    isMusicPrepared = true;

                    if (mSeekPosition > 0) {
                        mWePlayer.seekTo(mSeekPosition);
                        mSeekPosition = 0;
                    }
                    mWePlayer.start();

                    mMusicDuration = mWePlayer.getDuration();
                    LogUtils.d(TAG, "mMusicDuration=" + mMusicDuration);
                    mMusicSeekBar.setMax(mMusicDuration);
                    mMusicTotalTimeView.setText(DateTimeUtil.changeRemainTimeToMs(mMusicDuration));

                    float pitch = mWePlayer.getPitch();
                    mPitchView.setText("音调：" + PITCH_FORMAT.format(pitch));
                    mPitchSeekBar.setProgress((int) (pitch / MAX_PITCH * mPitchSeekBar.getMax()));

                    float tempo = mWePlayer.getTempo();
                    mTempoView.setText("音速：" + TEMPO_FORMAT.format(tempo));
                    mTempoSeekBar.setProgress((int) (tempo / MAX_TEMPO * mTempoSeekBar.getMax()));

                    startUpdateTime();
                    mControlMusicButton.setText("停止播放录音");
                }
            });
            mWePlayer.setOnPlayLoadingListener(new WePlayer.OnPlayLoadingListener() {
                @Override
                public void onPlayLoading(boolean isLoading) {
                    LogUtils.d(TAG, "WePlayer onPlayLoading: " + isLoading);
                    isMusicLoading = isLoading;
                    if (isMusicLoading) {
                        stopUpdateTime();
                    } else {
                        startUpdateTime();
                    }
                }
            });
            mWePlayer.setOnSeekCompleteListener(new WePlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete() {
                    LogUtils.d(TAG, "mWePlayer onSeekComplete");
                    isMusicSeeking = false;
                }
            });
            mWePlayer.setOnErrorListener(new WePlayer.OnErrorListener() {
                @Override
                public void onError(int code, String msg) {
                    LogUtils.e(TAG, "WePlayer onError: " + code + "; " + msg);
                }
            });
            mWePlayer.setOnCompletionListener(new WePlayer.OnCompletionListener() {
                @Override
                public void onCompletion() {
                    LogUtils.d(TAG, "WePlayer onCompletion");
                    mWePlayer.start();
                }
            });
        } else {
            mWePlayer.reset();
        }
        String name = url;
        int index = url.lastIndexOf("/");
        if (index >= 0) {
            name = url.substring(index + 1);
        }

        mWePlayer.setDataSource(url);
        mWePlayer.prepareAsync();
    }

    private void stopMusic() {
        if (mWePlayer != null) {
            mWePlayer.stop();
        }
        isMusicPrepared = false;
        mSeekPosition = 0;
        stopUpdateTime();
        mControlMusicButton.setText("播放最新录音");
        mMusicSeekBar.setProgress(0);
        mMusicPlayTimeView.setText(R.string.zero_time_ms);
    }

    private void startUpdateTime() {
        mUIHandler.sendEmptyMessage(MSG_UPDATE_MUSIC_TIME);
    }

    private void stopUpdateTime() {
        mUIHandler.removeMessages(MSG_UPDATE_MUSIC_TIME);
    }

    private void updateMusicTime() {
        if (mWePlayer == null || isMusicLoading) return;

        if (isMusicSeeking) {
            // seek 时 seekbar 会自动更新位置，只需要根据 seek 位置更新时间
            String currentPosition = DateTimeUtil.changeRemainTimeToMs(mMusicSeekBar.getProgress());
            mMusicPlayTimeView.setText(currentPosition);
        } else if (mWePlayer.isPlaying()) {
            // 没有 seek 时，如果还在播放中，就正常按实际播放时间更新时间和 seekbar
            int position = mWePlayer.getCurrentPosition();
            String currentPosition = DateTimeUtil.changeRemainTimeToMs(position);
            mMusicPlayTimeView.setText(currentPosition);
            mMusicSeekBar.setProgress(position);
        } else {
            // 既没有 seek，也没有播放，那就不更新
        }
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        isInitSuccess = false;
        mUIHandler.removeCallbacksAndMessages(null);

        if (mWeJAudioRecorder != null) {
            mWeJAudioRecorder.release();
            mWeJAudioRecorder = null;
        }
        if (mWeNAudioRecorder != null) {
            mWeNAudioRecorder.release();
            mWeNAudioRecorder = null;
        }
        mWAVSaver = null;

        stopMusic();
        if (mWePlayer != null) {
            mWePlayer.release();
            mWePlayer = null;
        }

        if (mPermissionHandler != null) {
            mPermissionHandler.destroy();
            mPermissionHandler = null;
        }
        super.onDestroy();
    }

}
