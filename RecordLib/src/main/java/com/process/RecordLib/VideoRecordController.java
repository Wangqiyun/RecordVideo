package com.process.RecordLib;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


import com.process.RecordLib.encoder.CircularEncoder;
import com.process.RecordLib.opengl.EglCore;
import com.process.RecordLib.opengl.FullFrameRect;
import com.process.RecordLib.opengl.Texture2dProgram;
import com.process.RecordLib.opengl.WindowSurface;
import com.process.RecordLib.utils.CameraUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Created by kerwin on 2018/11/22
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class VideoRecordController implements
        IVideoRecordController,
        SurfaceHolder.Callback,
        SurfaceTexture.OnFrameAvailableListener,
        CircularEncoder.Callback {
    private static final String TAG = "VideoRecordController";

    private SurfaceView mSurfaceView;
    private EglCore mEglCore;

    private WindowSurface mDisplaySurface;
    private WindowSurface mEncoderSurface;

    private FullFrameRect mFullFrameBlit;
    private int mTextureId;
    private SurfaceTexture mCameraTexture;  // receives the output from the camera preview

    /** 处理线程名字 */
    private static final String HANDLER_THREAD_NAME = "handler_thread_name";
    /** 录制视频文件路径 */
    private static final String RECORD_FILE_PATH = "record_file_path";
    /** 开始录制视频 */
    private static final int START_RECORD_VIDEO_WHAT = 1;
    /** 停止录制视频 */
    private static final int STOP_RECORD_VIDEO_WHAT = 2;
    /** 保存当前帧 */
    private static final int SAVE_FRAME_FILE_WHAT = 3;

    private static final int VIDEO_WIDTH = 1280;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 720;
    private static final int DESIRED_PREVIEW_FPS = 15;

    /** 线程处理对象 */
    private HandlerThread mHandlerThread = null;
    /** 录制线程处理对象 */
    private RecordVideoHandler mRecordVideoHandler = null;
    /** 主线程处理对象 */
    private Handler mMainHandler = null;

    private Camera mCamera;
    private CircularEncoder mCircularEncoder;
    private int mCameraPreviewThousandFps;
    private boolean mFileSaveInProgress;

    private final float[] mTmpMatrix = new float[16];
    private int mFrameNum;
    private WeakReference<OnCameraPreviewSizeChangeListener> mWeakSizeChangeListener = null;

    private VideoRecordController() {
        mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
        mHandlerThread.start();

        mRecordVideoHandler = new RecordVideoHandler(mHandlerThread.getLooper(), this);
        mMainHandler = new MainHandler(Looper.getMainLooper(), this);
    }

    /**
     * 设置表层的View对象
     * @param surface 表层的View对象
     * */
    private void setSurfaceView(SurfaceView surface) {
        this.mSurfaceView = surface;
        SurfaceHolder holder = this.mSurfaceView.getHolder();
        holder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();

        mFullFrameBlit = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureId = mFullFrameBlit.createTextureObject();
        mCameraTexture = new SurfaceTexture(mTextureId);
        mCameraTexture.setOnFrameAvailableListener(this);

        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + ",size=" + width + "x" + height + ",holder=" + holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        sendMainMessage(MainHandler.MSG_FRAME_AVAILABLE);
    }

    @Override
    public void startPreview() {
        if(mCamera == null) {
            openCamera();
        }

        if(mEglCore != null) {
            if (mCamera != null) {
                Log.d(TAG, "starting camera preview");
                try {
                    mCamera.setPreviewTexture(mCameraTexture);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
                mCamera.startPreview();
            }

            // can we guarantee that camera preview size is compatible with AVC video encoder?
            try {
                mCircularEncoder = new CircularEncoder(VIDEO_WIDTH, VIDEO_HEIGHT, 6000000,
                        mCameraPreviewThousandFps / 1000, 7, this);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

            mEncoderSurface = new WindowSurface(mEglCore, mCircularEncoder.getInputSurface(), true);
        }
    }

    /**
     * 发送主线程消息
     * @param what 消息what标记
     * */
    private void sendMainMessage(int what) {
        if(mMainHandler == null) {
            Log.w(TAG, "sendMainMessage() main handler");
        } else {
            Message message = new Message();
            message.what = what;
            mMainHandler.sendMessage(message);
        }
    }

    @Override
    public void openCamera() {
        openCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * <p>
     * Sets mCameraPreviewFps to the expected frame rate (which might actually be variable).
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }

        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();

        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);

        mCamera.setParameters(parms);

        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.i(TAG, "Camera config: " + previewFacts);
        notifyCameraPreviewSizeChange(mCamera, parms);
    }

    @Override
    public void fileSaveComplete(int status) {

    }

    @Override
    public void bufferStatus(long totalTimeMsec) {

    }

    /**
     * 摄像头画布发生Size变化监听器
     * */
    public interface OnCameraPreviewSizeChangeListener {
        /**
         * 摄像头预览Size切换时触发回调
         * @param camera 摄像头实例
         * @param parms 参数实体
         * */
        void onCameraPreviewSizeChange(Camera camera, Camera.Parameters parms);
    }

    /**
     * 设置拍摄预览界面变化监听器
     * @param listener 设置拍摄预览大小监听器实体
     * */
    @Override
    public void setOnCameraPreviewSizeChangeListener(OnCameraPreviewSizeChangeListener listener) {
        this.mWeakSizeChangeListener = new WeakReference<>(listener);
    }

    @Override
    public void setDisplayOrientation(int orientation) {
        if(mCamera == null) {
            Log.w(TAG, "setDisplayOrientation() camera is null.");
        } else {
            mCamera.setDisplayOrientation(orientation);
        }
    }

    /**
     * 通知拍摄界面大小变化
     * @param camera 摄像头对象
     * @param parameters 摄像头参数对象
     * */
    private void notifyCameraPreviewSizeChange(Camera camera, Camera.Parameters parameters) {
        OnCameraPreviewSizeChangeListener listener = null;
        if(mWeakSizeChangeListener == null) {
            Log.w(TAG, "notifyCameraPreviewSizeChange() listener weak is null.");
        } else {
            listener = mWeakSizeChangeListener.get();
        }

        if(listener == null) {
            Log.w(TAG, "notifyCameraPreviewSizeChange() listener is null.");
        } else {
            listener.onCameraPreviewSizeChange(camera, parameters);
        }
    }

    @Override
    public void startRecordVideo(String filePath) {
        Bundle bundle = new Bundle();
        bundle.putString(RECORD_FILE_PATH, filePath);
        sendMessageBundle(START_RECORD_VIDEO_WHAT, bundle);
    }

    @Override
    public void stopRecordVideo() {
        sendMessageBundle(STOP_RECORD_VIDEO_WHAT, null);
    }

    @Override
    public void saveFrameFile(String filePath) {
        Bundle bundle = new Bundle();
        bundle.putString(RECORD_FILE_PATH, filePath);
        sendMessageBundle(SAVE_FRAME_FILE_WHAT, bundle);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void release() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "release -- done");
        }
        if (mCircularEncoder != null) {
            mCircularEncoder.shutdown();
            mCircularEncoder = null;
        }
        if (mCameraTexture != null) {
            mCameraTexture.release();
            mCameraTexture = null;
        }
        if (mDisplaySurface != null) {
            mDisplaySurface.release();
            mDisplaySurface = null;
        }
        if (mFullFrameBlit != null) {
            mFullFrameBlit.release(false);
            mFullFrameBlit = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
    }

    /**
     * 发送消息bundle
     * @param what 消息what值
     * @param bundle 传递数据的bundle值
     * */
    private void sendMessageBundle(int what, Bundle bundle) {
        Message message = new Message();
        message.what = what;
        if(bundle != null) {
            message.setData(bundle);
        }

        if(mRecordVideoHandler == null) {
            Log.w(TAG, "sendMessageBundle() record video is null.");
        } else {
            mRecordVideoHandler.sendMessage(message);
        }
    }

    private void onStartRecordVideo(String path) {
        if(TextUtils.isEmpty(path)) {
            Log.w(TAG, "start record video path is empty.");
            return;
        }
    }

    private void onStopRecordVideo() {
        Log.w(TAG, "stop record video.");
    }

    private void onSaveFrameFile(String path) {
        if(TextUtils.isEmpty(path)) {
            Log.w(TAG, "save frame file is empty.");
            return;
        }
    }

    /**
     * Draws a frame onto the SurfaceView and the encoder surface.
     * <p>
     * This will be called whenever we get a new preview frame from the camera.  This runs
     * on the UI thread, which ordinarily isn't a great idea -- you really want heavy work
     * to be on a different thread -- but we're really just throwing a few things at the GPU.
     * The upside is that we don't have to worry about managing state changes between threads.
     * <p>
     * If there was a pending frame available notification when we shut down, we might get
     * here after onPause().
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void drawFrame() {
        //Log.d(TAG, "drawFrame");
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }

        if (mSurfaceView == null) {
            Log.d(TAG, "draw frame surface view is null.");
            return;
        }

        // Latch the next frame from the camera.
        mDisplaySurface.makeCurrent();
        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mTmpMatrix);

        // Fill the SurfaceView with it.
        int viewWidth = mSurfaceView.getWidth();
        int viewHeight = mSurfaceView.getHeight();
        GLES20.glViewport(0, 0, viewWidth, viewHeight);
        mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);
        mDisplaySurface.swapBuffers();

        // Send it to the video encoder.
        if (!mFileSaveInProgress) {
            mEncoderSurface.makeCurrent();
            GLES20.glViewport(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
            mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);
            mCircularEncoder.frameAvailableSoon();
            mEncoderSurface.setPresentationTime(mCameraTexture.getTimestamp());
            mEncoderSurface.swapBuffers();
        }

        mFrameNum++;
    }

    /**
     * 录制视频处理实体类
     * */
    private static class RecordVideoHandler extends android.os.Handler {
        private WeakReference<VideoRecordController> mWeakRecordController;

        RecordVideoHandler(Looper looper, VideoRecordController controller) {
            super(looper);
            mWeakRecordController = new WeakReference<VideoRecordController>(controller);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            VideoRecordController controller = getVideoRecordController();
            if(controller == null) {
                Log.w(TAG, "handle message controller is null.");
                return;
            }

            Bundle bundle = msg.getData();
            int what = msg.what;
            if (what == START_RECORD_VIDEO_WHAT) {
                controller.onStartRecordVideo(bundle == null ? "" : bundle.getString(RECORD_FILE_PATH));
            } else if (what == STOP_RECORD_VIDEO_WHAT) {
                controller.onStopRecordVideo();
            } else if (what == SAVE_FRAME_FILE_WHAT) {
                controller.onSaveFrameFile(bundle == null ? "" : bundle.getString(RECORD_FILE_PATH));
            }
        }

        private VideoRecordController getVideoRecordController() {
            if(mWeakRecordController == null) {
                return null;
            }

            return mWeakRecordController.get();
        }
    }

    private static class MainHandler extends Handler {
        static final int MSG_FRAME_AVAILABLE = 1;
        private WeakReference<VideoRecordController> mWeakController = null;

        public MainHandler(Looper looper, VideoRecordController controller) {
            super(looper);
            mWeakController = new WeakReference<VideoRecordController>(controller);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(mWeakController == null) {
                Log.w(TAG, "handle message weak control is null.");
                return;
            }

            VideoRecordController controller = mWeakController.get();
            if(msg.what == MSG_FRAME_AVAILABLE) {
                controller.drawFrame();
            }
        }
    }

    public static class Builder {
        private SurfaceView mSurfaceView = null;

        /**
         * 设置表层View对象实体
         * @param surface 表层View对象实体
         * */
        public void setSurfaceView(SurfaceView surface) {
            this.mSurfaceView = surface;
        }

        /**
         * 构建视频录制控制器实体
         * */
        public IVideoRecordController builder() {
            VideoRecordController controller = new VideoRecordController();
            controller.setSurfaceView(this.mSurfaceView);
            return controller;
        }
    }
}
