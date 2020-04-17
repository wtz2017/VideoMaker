package com.wtz.videomaker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.wtz.ffmpegapi.WAVSaver;
import com.wtz.ffmpegapi.WePlayer;
import com.wtz.libnaudiorecord.WeNAudioRecorder;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.videomaker.utils.AudioUtils;
import com.wtz.videomaker.utils.DateTimeUtil;
import com.wtz.videomaker.utils.FileChooser;
import com.wtz.videomaker.utils.PermissionChecker;
import com.wtz.videomaker.utils.PermissionHandler;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;


public class MixAudioActivity extends AppCompatActivity implements PermissionHandler.PermissionHandleListener,
        View.OnClickListener, WePlayer.OnPCMDataCallListener {
    private static final String TAG = MixAudioActivity.class.getSimpleName();

    private PermissionHandler mPermissionHandler;

    private WePlayer mWePlayer;
    private int mSelectMusicRequestCode;
    private String mMusicUrl;
    private int mMusicDuration;
    private int mSeekPosition;
    private boolean isMusicPrepared;
    private boolean isMusicLoading;
    private boolean isMusicSeeking;
    private TextView mMusicNameView;
    private TextView mMusicPlayTimeView;
    private TextView mMusicTotalTimeView;
    private SeekBar mMusicSeekBar;
    private Button mSelectMusicButton;
    private Button mControlMusicButton;

    private WeNAudioRecorder mWeNAudioRecorder;
    private WAVSaver mWAVSaver;
    private boolean isInitSuccess;
    private boolean isRecording;
    private String mSaveWavDir;
    private String mCurrentPathName;
    private static final String WAV_PREFIX = "We_AUD_MIX_";
    private static final String WAV_SUFFIX = ".wav";
    private final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private Button mRecordButton;
    private TextView mRecordTimeTV;

    // 混音参数
    private boolean isMixing = false;
    private float[] mMultiTrackMixRatios = new float[]{
            2.0f, // 把录音声音稍微调大一些
            1.0f
    };
    private byte[][] mMultiTrackAudioBytes = null;
    private LinkedBlockingQueue<Byte> mPCMBytesQueue = new LinkedBlockingQueue<>();

    private static final int MSG_UPDATE_RECORD_INFO = 0;
    private static final int MSG_UPDATE_MUSIC_TIME = 1;
    private static final int UPDATE_RECORD_INFO_INTERVAL = 500;
    private static final int UPDATE_MUSIC_TIME_INTERVAL = 500;
    private WeakHandler mUIHandler = new WeakHandler(this);

    static class WeakHandler extends Handler {
        private final WeakReference<MixAudioActivity> weakReference;

        // 为了避免非静态的内部类和匿名内部类隐式持有外部类的引用，改用静态类
        // 又因为内部类是静态类，所以不能直接操作宿主类中的方法了，
        // 于是需要传入宿主类实例的弱引用来操作宿主类中的方法
        public WeakHandler(MixAudioActivity host) {
            super(Looper.getMainLooper());
            this.weakReference = new WeakReference(host);
        }

        @Override
        public void handleMessage(Message msg) {
            MixAudioActivity host = weakReference.get();
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

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_mix_audio);
        initViews();

        File savePath = new File(Environment.getExternalStorageDirectory(), "WePhotos");
        mSaveWavDir = savePath.getAbsolutePath();

        mPermissionHandler = new PermissionHandler(this, this);
        mPermissionHandler.handleCommonPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        mPermissionHandler.handleCommonPermission(Manifest.permission.RECORD_AUDIO);

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mBroadcastReceiver, new IntentFilter(FileChooser.ACTION_FILE_CHOOSE_RESULT));
    }

    private void initViews() {
        mSelectMusicButton = findViewById(R.id.btn_select_music);
        mSelectMusicButton.setOnClickListener(this);
        mMusicNameView = findViewById(R.id.tv_music_name);

        mControlMusicButton = findViewById(R.id.btn_music_play_control);
        mControlMusicButton.setOnClickListener(this);
        mMusicPlayTimeView = findViewById(R.id.tv_music_play_time);
        mMusicTotalTimeView = findViewById(R.id.tv_music_total_time);
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

        mRecordButton = findViewById(R.id.btn_record_mix);
        mRecordButton.setOnClickListener(this);
        mRecordTimeTV = findViewById(R.id.tv_record_time);
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
        if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            if (state == PermissionChecker.PermissionState.USER_NOT_GRANTED) {
                LogUtils.e(TAG, "onPermissionResult " + permission + " state is USER_NOT_GRANTED!");
                finish();
            }
        } else if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
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
        }
    }

    private void initAudioRecord() {
        // 为了方便混音：保证与 WePlayer 播放的采样率 44100Hz、通道数 2、编码 16bit 保持一致
        mWeNAudioRecorder = new WeNAudioRecorder(
                WeNAudioRecorder.SampleRate.SR_44100,
                WeNAudioRecorder.ChannelLayout.STEREO,
                WeNAudioRecorder.EncodingBits.PCM_16BIT);
        mWeNAudioRecorder.setOnAudioRecordDataListener(mNAudioListener);
        mWAVSaver = new WAVSaver();
        isInitSuccess = true;
    }

    private WeNAudioRecorder.OnAudioRecordDataListener mNAudioListener = new WeNAudioRecorder.OnAudioRecordDataListener() {
        @Override
        public void onAudioRecordData(byte[] data, int size) {
            if (!isRecording) {
                mPCMBytesQueue.clear();
                return;
            }
            synchronized (MixAudioActivity.this) {
                isMixing = true;
            }
            if (mMultiTrackAudioBytes == null || mMultiTrackAudioBytes[0].length < size) {
                // 尽可能避免频繁创建和释放内存造成内存抖动
                mMultiTrackAudioBytes = new byte[2][size];
            }
            for (int i = 0; i < size; i++) {
                mMultiTrackAudioBytes[0][i] = data[i];
                try {
                    mMultiTrackAudioBytes[1][i] = mPCMBytesQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // WeNAudioRecorder 录制 与 WePlayer 播放的采样率 44100Hz、通道数 2、编码 16bit 保持一致
            // 所以可以使用下边简单的混音方法
            byte[] mixOut = AudioUtils.linearMix16bitAudioBytes(mMultiTrackAudioBytes, size,
                    mMultiTrackMixRatios);

            if (mWAVSaver != null) {
                try {
                    mWAVSaver.encode(mixOut, mixOut.length);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            synchronized (MixAudioActivity.this) {
                isMixing = false;
            }
        }
    };

    @Override
    public void onPCMDataCall(byte[] bytes, int size) {
        synchronized (MixAudioActivity.this) {
            if (!isRecording && !isMixing) {
                if (mWePlayer != null) {
                    mWePlayer.enablePCMDataCall(false);
                }
                mPCMBytesQueue.clear();
                return;
            }
        }
        for (byte data : bytes) {
            try {
                mPCMBytesQueue.put(data);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onStart() {
        LogUtils.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        LogUtils.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_record_mix:
                record();
                break;

            case R.id.btn_select_music:
                chooseMusic();
                break;

            case R.id.btn_music_play_control:
                playPauseMusic();
                break;

        }
    }

    private void chooseMusic() {
        synchronized (MixAudioActivity.this) {
            if (isRecording || isMixing) {
                Toast.makeText(this, "先停止录音，才能更换音乐！", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        mSelectMusicRequestCode = FileChooser.chooseAudio(this);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtils.d(TAG, "mBroadcastReceiver onReceive: " + action);
            if (FileChooser.ACTION_FILE_CHOOSE_RESULT.equals(action)) {
                int code = intent.getIntExtra(FileChooser.RESULT_REQUEST_CODE, -1);
                if (code == mSelectMusicRequestCode) {
                    String url = intent.getStringExtra(FileChooser.RESULT_FILE_PATH);
                    LogUtils.d(TAG, "clickImageItem music url: " + url);
                    if (!TextUtils.isEmpty(url)) {
                        mMusicUrl = url;
                        openMusic(mMusicUrl);
                    }
                }
            }
        }
    };

    private void openMusic(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        isMusicPrepared = false;
        isMusicLoading = false;
        if (mWePlayer == null) {
            mWePlayer = new WePlayer(true);
            mWePlayer.setOnPCMDataCallListener(this);
            mWePlayer.setOnPreparedListener(new WePlayer.OnPreparedListener() {
                @Override
                public void onPrepared() {
                    LogUtils.d(TAG, "WePlayer onPrepared");
                    isMusicPrepared = true;

                    if (mSeekPosition > 0) {
                        mWePlayer.seekTo(mSeekPosition);
                        mSeekPosition = 0;
                    }
                    if (isRecording) {
                        mWePlayer.setVolume(0);
                        mWePlayer.enablePCMDataCall(true);
                    } else {
                        mWePlayer.setVolume(1);
                        mWePlayer.enablePCMDataCall(false);
                    }
                    mWePlayer.start();

                    mMusicDuration = mWePlayer.getDuration();
                    LogUtils.d(TAG, "mMusicDuration=" + mMusicDuration);
                    mMusicSeekBar.setMax(mMusicDuration);
                    mMusicTotalTimeView.setText(DateTimeUtil.changeRemainTimeToMs(mMusicDuration));
                    startUpdateTime();
                    mControlMusicButton.setText(R.string.pause_music);
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
        mMusicNameView.setText(name);

        mWePlayer.setDataSource(url);
        mWePlayer.prepareAsync();
    }

    private void playPauseMusic() {
        if (TextUtils.isEmpty(mMusicUrl)) {
            Toast.makeText(this, "请选择音乐", Toast.LENGTH_SHORT).show();
            return;
        }
        synchronized (MixAudioActivity.this) {
            if (isRecording || isMixing) {
                Toast.makeText(this, "先停止录音，才能控制音乐！", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (!isMusicPrepared) {
            openMusic(mMusicUrl);
        } else {
            if (mWePlayer.isPlaying()) {
                pauseMusic();
            } else {
                startMusic();
            }
        }
    }

    private void startMusic() {
        if (mWePlayer != null) {
            mWePlayer.start();
        }
        startUpdateTime();
        mControlMusicButton.setText(R.string.pause_music);
    }

    private void openOrStartMusic() {
        if (!isMusicPrepared) {
            openMusic(mMusicUrl);
        } else if (!mWePlayer.isPlaying()) {
            startMusic();
        }
    }

    private void pauseMusic() {
        if (mWePlayer != null) {
            mWePlayer.pause();
        }
        stopUpdateTime();
        mControlMusicButton.setText(R.string.play_music);
    }

    private void stopMusic() {
        if (mWePlayer != null) {
            mWePlayer.stop();
        }
        isMusicPrepared = false;
        mSeekPosition = 0;
        stopUpdateTime();
        mControlMusicButton.setText(R.string.play_music);
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

    private void record() {
        if (isInitSuccess) {
            if (isRecording) {
                isRecording = false;
                stopRecord();
            } else {
                startRecord();
                isRecording = true;
            }
        }
    }

    private void startRecord() {
        mCurrentPathName = getSavePathName();

        mWAVSaver.start(
                mWeNAudioRecorder.getSampleRate(),
                mWeNAudioRecorder.getChannelNums(),
                mWeNAudioRecorder.getBitsPerSample(),
                0, new File(mCurrentPathName));

        mPCMBytesQueue.clear();
        openOrStartMusic();
        if (mWePlayer != null) {
            mWePlayer.setVolume(0);
            mWePlayer.enablePCMDataCall(true);
        }

        mWeNAudioRecorder.startRecord();
        mRecordButton.setText("停止录音混音");

        startUpdateRecordInfo();
    }

    private void stopRecord() {
        mWeNAudioRecorder.stopRecord();
        mWAVSaver.stop();
        if (mWePlayer != null) {
            mWePlayer.setVolume(1);
            // 这里不要直接停止音乐数据PCM回调，避免队列取不到数据阻塞
        }
        mRecordButton.setText("开始录音混音");
        notifyScanMedia(mCurrentPathName);
        stopUpdateRecordInfo();
    }

    private String getSavePathName() {
        String time = mSimpleDateFormat.format(new Date());
        return new File(mSaveWavDir, WAV_PREFIX + time + WAV_SUFFIX).getAbsolutePath();
    }

    private void notifyScanMedia(String pathName) {
        if (TextUtils.isEmpty(pathName)) return;
        Uri contentUri = Uri.fromFile(new File(pathName));
        Intent i = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, contentUri);
        sendBroadcast(i);
    }

    private void startUpdateRecordInfo() {
        mUIHandler.sendEmptyMessage(MSG_UPDATE_RECORD_INFO);
    }

    private void stopUpdateRecordInfo() {
        mUIHandler.removeMessages(MSG_UPDATE_RECORD_INFO);
    }

    private void updateRecordInfo() {
        String time = DateTimeUtil.changeRemainTimeToHms(mWeNAudioRecorder.getRecordTimeMills());
        mRecordTimeTV.setText(time);
    }

    @Override
    protected void onPause() {
        LogUtils.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        LogUtils.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        mUIHandler.removeCallbacksAndMessages(null);

        stopRecord();
        if (mWeNAudioRecorder != null) {
            mWeNAudioRecorder.release();
            mWeNAudioRecorder = null;
        }

        if (mWAVSaver != null) {
            mWAVSaver.stop();
            mWAVSaver = null;
        }

        stopMusic();
        if (mWePlayer != null) {
            mWePlayer.release();
            mWePlayer = null;
        }

        if (mPermissionHandler != null) {
            mPermissionHandler.destroy();
            mPermissionHandler = null;
        }

        // 避免录音线程一直取数据阻塞无法退出
        while (isMixing) {
            LogUtils.w(TAG, "onDestroy isMixing...");
            try {
                mPCMBytesQueue.put((byte) 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mPCMBytesQueue.clear();
        LogUtils.w(TAG, "onDestroy mPCMBytesQueue clear finished");

        super.onDestroy();
    }

}
