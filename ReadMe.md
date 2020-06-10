# VideoMaker

Video Making base on OpenGL ES + OpenSL ES + SurfaceTexture + SurfaceView + MediaCodec + MediaMuxer + AudioRecord + lame + rtmpdump + WePlayer.

此项目是在学习 CSDN 学院[杨万理](https://edu.csdn.net/lecturer/1846)的视频编码和直播推流课程基础上，加上自己的理解、查阅相关专业理论资料和一步步软件构思设计调试而成的，在此记录学习成果，也同时感谢杨老师的知识传播！

## libvideomaker 模块

实现视频制作的核心功能库，Java 层提供的主要最外层接口类有：
WeCameraView、WeJAudioRecorder、WeImageVideoView、WeVideoRecorder、WeVideoPusher。

###  WeCameraView

基于 OpenGL ES + SurfaceView + SurfaceTexture 实现的相机预览器。

- 支持相机基本功能：画面预览、自动对焦、前后摄像头切换、拍照图片保存；
- 支持水印：文字水印、图片水印、图文叠加水印、动态改变水印位置；
- 支持简单滤镜：灰度滤镜、反色滤镜；
- 支持预览画面自适应 SurfaceView 容器大小；
- 支持纹理 TextureId 和 EGLContext 共享，可用于视频录制；

### WeJAudioRecorder

对 Java 层的 AudioRecord 的封装。

- 支持基本的录音功能：回调 byte[] 数据；
- 支持获取录音时间、声音分贝数等参数；
- 使用单线程执行功能，避免并发调用导致的问题；

### WeImageVideoView

基于 OpenGL ES + SurfaceView 实现的图片渲染器。

- 支持图片播放功能：可动态设置改变图片资源；
- 支持水印：文字水印、图片水印、图文叠加水印、动态改变水印位置；
- 支持纹理 TextureId 和 EGLContext 共享，可用于视频录制；

### WeVideoRecorder

基于 OpenGL ES + MediaCodec + MediaMuxer 实现的视频录制器。

- 支持视频录制保存为 mp4，图像使用 h264 编码，音频使用 AAC 编码；
- 图像来源可以是相机或图片渲染的纹理；
- 声音来源可以是录音或本地音乐播放的字节数据；

### WeVideoPusher

基于 OpenGL ES + MediaCodec + rtmpdump 实现的视频直播推流器。

- 图像使用 h264 编码，音频使用 AAC 编码，通过 RTMP 协议对音视频数据分别做相应的封装发送；
- 图像来源可以是相机或图片渲染的纹理；
- 声音来源可以是录音或本地音乐播放的字节数据；
- 支持推流url、连接超时、音频相关参数等设置；
- 推流连接失败自动重试，采用指数递增延时策略重试；

## libpushflow 模块

移植 C 库 rtmpdump 到 Android 平台 + 直播推流接口封装。
Java 层提供的接口类为 WePushFlow。

- 支持推流url、连接超时、音频相关参数等设置；
- 分别提供 SPS/PPS、图像 H264 数据、音频 AAC 数据推送接口；

## libnaudiorecord 模块

使用 Native 层 OpenSL ES 实现的录音器。Java 层提供的接口类为 WeNAudioRecorder。

- 支持基本的录音功能：回调 byte[] 数据；
- 支持获取录音时间、声音分贝数等参数；
- 使用单线程执行功能，避免并发调用导致的问题；

## libmp3util 模块

集成 C 库 lame 到 Android 平台 + mp3 编码接口封装。
Java 层提供的接口类为 WeMp3Encoder。

- 支持同一进程多实例操作。
- 支持设置通道数、编码位数、输出质量等参数设置。
- MP3 编码数据来源分两种：
  - 来源为 PCM 文件 / WAV 文件。每个文件独立编码，可以多任务、多线程、多文件操作。适用场景：批量文件异步编码。
  - 来源为 PCM buffer。从 PCM buffer 取数据编码的方法分成了多步操作，涉及状态切换，适合单任务执行。支持多线程操作。

## app 模块

主要是对以上各个库模块的使用验证测试，如下：
![测试目录](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-menu.jpg)

- EGL 环境测试：单纹理、多 Surface 渲染；
  ![单纹理、多 Surface](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-single-texture-multi-surface.jpg)
  
- EGL 环境测试：多纹理、单 Surface 渲染；
  ![多纹理、单 Surface](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-multi-texture-single-surface.jpg)
  
- 音频录制测试：使用 WeJAudioRecorder、WeNAudioRecorder 录音，使用 WeMp3Encoder 保存为 mp3文件，使用 WePlayer 播放录音文件；
  ![音频录制](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-audio-record.jpg)
  
- 音频混音测试：使用线性叠加平均混音算法对录音与背景音乐进行混音测试；
  ![音频混音](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-audio-mix.jpg)
  
- 摄像头预览测试：使用 WeCameraView 实现画面预览、自动对焦、前后摄像头切换、拍照图片保存、水印、滤镜效果；
  ![camera-1](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-camera-1.jpg)
  ![camera-2](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-camera-2.jpg)
  ![camera-3](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-camera-3.jpg)
  
- 视频录制测试：使用 WeVideoRecorder 实现视频编码保存为 mp4，支持声音来源有：麦克风、音乐、麦克风与音乐混音，图像来自 WeCameraView；
  ![video-record-1](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-video-record-1.jpg)
  ![video-record-2](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-video-record-2.jpg)
  
- 图像合成视频测试：使用 WeImageVideoView 手动或自动连续加载渲染多张图片，配合音乐播放，使用 WeVideoRecorder 合成为视频；
  ![图像合成视频](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-image-video.jpg)
  
- 直播推流测试：与视频录制测试类似，区别在于使用 WeVideoPusher 而不是 WeVideoRecorder 来编码和封装数据并发送到直播服务器；
  ![直播推流](https://github.com/wtz2017/VideoMaker/raw/master/images/VideoMaker-app-video-push.jpg)
