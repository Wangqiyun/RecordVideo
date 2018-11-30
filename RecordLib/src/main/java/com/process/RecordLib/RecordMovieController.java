package com.process.RecordLib;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.process.RecordLib.encoder.TextureMovieEncoder;
import com.process.RecordLib.utils.CameraUtils;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Created by kerwin on 2018/11/28
 */
public class RecordMovieController implements
        IVideoRecordController,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "RecordMovieController";

    // Camera filters; must match up with cameraFilterNames in strings.xml
    public static final int FILTER_NONE = 0;
    public static final int FILTER_BLACK_WHITE = 1;
    public static final int FILTER_BLUR = 2;
    public static final int FILTER_SHARPEN = 3;
    public static final int FILTER_EDGE_DETECT = 4;
    public static final int FILTER_EMBOSS = 5;

    private Camera mCamera;
    private CameraSurfaceRenderer mCameraSurfaceRenderer;
    private GLSurfaceView mGLSurfaceView;
    private CameraMovieHandler mCameraMovieHandler;

    private int mCameraPreviewWidth;
    private int mCameraPreviewHeight;

    private TextureMovieEncoder mTextureMovieEncoder = null;

    private WeakReference<OnCameraPreviewSizeChangeListener> mWeakOnCameraPreviewSizeChangeListener;

    private RecordMainHandler mRecordMainHandler = null;

    /**
     * 暂停视频录制处理
     * */
    private void pauseMovieRecord() {
        if(mCameraSurfaceRenderer == null) {
            Log.d(TAG, "camera surface renderer is null.");
        } else {
            mCameraSurfaceRenderer.notifyPausing();
        }
    }

    /**
     * 切换预览Size变化
     * @param width 预览画面宽度
     * @param height 预览画面高度
     * */
    private void changePreviewSizeChange(int width, int height) {
        if(mCameraSurfaceRenderer == null) {
            Log.d(TAG, "change preview size change width > " + width + ",height > " + height);
        } else {
            mCameraSurfaceRenderer.setCameraPreviewSize(width, height);
        }
    }

    private RecordMovieController() {
        mTextureMovieEncoder = new TextureMovieEncoder();
    }

    @Override
    public void initialize(File file) {
        mCameraMovieHandler = new CameraMovieHandler(this);
        mRecordMainHandler = new RecordMainHandler(Looper.getMainLooper(), this);
        mCameraSurfaceRenderer = new CameraSurfaceRenderer(mCameraMovieHandler, mTextureMovieEncoder, file);

        if(this.mGLSurfaceView != null) {
            this.mGLSurfaceView.setEGLContextClientVersion(2);
        }

        if(mGLSurfaceView != null) {
            mGLSurfaceView.setRenderer(mCameraSurfaceRenderer);
            mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }

        Log.d(TAG, "initialize complete: " + this);
    }

    @Override
    public void handleResume() {
        mGLSurfaceView.onResume();
        setSurfaceRendererSize(mCameraPreviewWidth, mCameraPreviewHeight);

        if (mCamera == null) {
            // updates mCameraPreviewWidth/Height
            openCamera(1280, 720);
        }
    }

    @Override
    public void handlePause() {
        release();

        if(mRecordMainHandler == null) {
            Log.w(TAG, "handlePause() record main handle not is null.");
        } else {
            Message message = new Message();
            message.what = RecordMainHandler.RECORD_PAUSING;
            mRecordMainHandler.sendMessage(message);
        }

        mGLSurfaceView.onPause();
    }

    @Override
    public void handleDestroy() {
        if(mCameraMovieHandler == null) {
            Log.w(TAG, "handleDestroy() camera movie handler not is null.");
        } else {
            mCameraMovieHandler.invalidateHandler();
        }
    }

    @Override
    public void openCamera(int width, int height) {
        if(mCamera != null) {
            throw new RuntimeException("camera already initialized.");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        int number = Camera.getNumberOfCameras();

        for(int i = 0; i < number; i ++) {
            Camera.getCameraInfo(i, info);
            if(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }

        if(mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();
        }

        if(mCamera == null) {
            throw  new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parameters = mCamera.getParameters();
        CameraUtils.choosePreviewSize(parameters, mCameraPreviewWidth, mCameraPreviewHeight);
        parameters.setRecordingHint(true);

        mCamera.setParameters(parameters);

        int[] fpsRange = new int[2];
        Camera.Size mCameraPreviewSize = parameters.getPreviewSize();
        parameters.getPreviewFpsRange(fpsRange);

        mCameraPreviewWidth = mCameraPreviewSize.width;
        mCameraPreviewHeight = mCameraPreviewSize.height;
        setSurfaceRendererSize(mCameraPreviewWidth, mCameraPreviewHeight);

        notifyCameraPreviewSizeChange(mCamera, parameters);
    }

    @Override
    public void startRecordVideo(String filePath) {
        if(mCameraSurfaceRenderer == null) {
            Log.w(TAG, "startRecordVideo() camera surface renderer not is null.");
        } else {
            Log.d(TAG, "start record video file path > " + filePath);
            mCameraSurfaceRenderer.setRecordingEnabled(true);
        }
    }

    @Override
    public void stopRecordVideo() {
        if(mCameraSurfaceRenderer == null) {
            Log.w(TAG, "stopRecordVideo() camera surface renderer not is null.");
        } else {
            Log.d(TAG, "stop record video");
            mCameraSurfaceRenderer.setRecordingEnabled(false);
        }
    }

    @Override
    public void saveFrameFile(String filePath) {
        Log.d(TAG, "save frame file file path > " + filePath);
    }

    @Override
    public void release() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    @Override
    public void setOnCameraPreviewSizeChangeListener(OnCameraPreviewSizeChangeListener listener) {
        mWeakOnCameraPreviewSizeChangeListener = new WeakReference<OnCameraPreviewSizeChangeListener>(listener);
    }

    @Override
    public void setDisplayOrientation(int orientation) {
        if(mCamera == null) {
            Log.w(TAG, "setDisplayOrientation() camera not is null.");
        } else {
            mCamera.setDisplayOrientation(orientation);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if(mGLSurfaceView == null) {
            Log.w(TAG, "onFrameAvailable() surface view not is null.");
        } else {
            mGLSurfaceView.requestRender();
        }
    }

    /**
     * 设置渲染面板的宽度及高度
     * @param width 面板的宽度
     * @param height 面板的高度
     * */
    private void setSurfaceRendererSize(final int width, final int height) {
        if(mGLSurfaceView == null) {
            Log.w(TAG, "setSurfaceRendererSize() surface view is null.");
            return;
        }

        if(mRecordMainHandler == null) {
            Log.w(TAG, "setSurfaceRendererSize() record main handler not is null.");
        } else {
            Bundle bundle = new Bundle();
            bundle.putInt(RecordMainHandler.RECORD_ARGS_WIDTH, width);
            bundle.putInt(RecordMainHandler.RECORD_ARGS_HEIGHT, height);

            Message message = new Message();
            message.what = RecordMainHandler.RECORD_PREVIEW_SIZE_CHANGE;
            message.setData(bundle);
            mRecordMainHandler.sendMessage(message);
        }
    }

    private void notifyCameraPreviewSizeChange(Camera camera, Camera.Parameters parms) {
        if(mWeakOnCameraPreviewSizeChangeListener == null) {
            Log.w(TAG, "notifyCameraPreviewSizeChange() weak listener is null.");
            return;
        }

        OnCameraPreviewSizeChangeListener listener = mWeakOnCameraPreviewSizeChangeListener.get();
        if(listener == null) {
            Log.w(TAG, "notifyCameraPreviewSizeChange() listener not is null.");
            return;
        }

        listener.onCameraPreviewSizeChange(camera, parms);
    }

    /**
     * 处理设置画布表层纹理
     * @param texture 表层纹理对象
     * */
    private void handleSetSurfaceTexture(SurfaceTexture texture) {
        if(texture == null) {
            Log.w(TAG, "handle set surface texture not is null.");
            return;
        }

        texture.setOnFrameAvailableListener(this);

        try {
            mCamera.setPreviewTexture(texture);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        mCamera.startPreview();
    }

    /**
     * 设置渲染表层组件实体
     * @param surfaceView 渲染表层组件实体
     * */
    public void setGLSurfaceView(GLSurfaceView surfaceView) {
        this.mGLSurfaceView = surfaceView;
    }

    /** 摄像头拍摄视频处理 */
    public static class CameraMovieHandler extends android.os.Handler {
        private static final String TAG = "CameraMovieHandler";

        // 设置画布的画笔
        public static final int MSG_SET_SURFACE_TEXTURE = 0;

        private WeakReference<RecordMovieController> mWeakRecordMovieController = null;

        public CameraMovieHandler(RecordMovieController controller) {
            mWeakRecordMovieController = new WeakReference<RecordMovieController>(controller);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(mWeakRecordMovieController == null) {
                Log.w(TAG, "handleMessage() controller is null.");
                return;
            }

            int what = msg.what;
            RecordMovieController controller = mWeakRecordMovieController.get();
            if(controller == null) {
                Log.w(TAG, "handleMessage() controller is null.");
                return;
            }

            if (what == MSG_SET_SURFACE_TEXTURE) {
                controller.handleSetSurfaceTexture((SurfaceTexture) msg.obj);
            } else {
                throw new RuntimeException("unknown msg " + what);
            }
        }

        /**
         * 释放视频控制处理
         * */
        void invalidateHandler() {
            if(mWeakRecordMovieController == null) {
                Log.w(TAG, "invalidateHandler() controller not is null.");
            } else {
                mWeakRecordMovieController.clear();
            }
        }
    }

    /**
     * 录制主线程处理
     * */
    public static class RecordMainHandler extends Handler {
        private WeakReference<RecordMovieController>  mRecordMainHandler = null;

        /** 录制暂停 */
        public static final int RECORD_PAUSING = 1;
        /** 录制画布Size变化 */
        public static final int RECORD_PREVIEW_SIZE_CHANGE = 2;

        /** 录制视频宽度参数 */
        public static final String RECORD_ARGS_WIDTH = "record_args_width";
        /** 录制视频高度参数 */
        public static final String RECORD_ARGS_HEIGHT = "record_args_height";

        public RecordMainHandler(Looper looper, RecordMovieController controller) {
            super(looper);
            mRecordMainHandler = new WeakReference<RecordMovieController>(controller);
        }

        @Override
        public void handleMessage(Message msg) {
            if(msg == null) {
                Log.w(TAG, "handleMessage() msg not is null.");
                return;
            }

            if(mRecordMainHandler == null) {
                Log.w(TAG, "handleMessage() record main handler not is null.");
                return;
            }

            RecordMovieController controller = mRecordMainHandler.get();
            if(controller == null) {
                Log.w(TAG, "handleMessage() controller not is null.");
                return;
            }

            if(msg.what == RECORD_PAUSING) {
                controller.pauseMovieRecord();
            } else if(msg.what == RECORD_PREVIEW_SIZE_CHANGE) {
                Bundle bundle = msg.getData();
                int width = bundle.getInt(RECORD_ARGS_WIDTH);
                int height = bundle.getInt(RECORD_ARGS_HEIGHT);

                controller.changePreviewSizeChange(width, height);
            }
        }
    }

    public static class Builder {
        private GLSurfaceView mSurfaceView = null;

        /**
         * 设置表层View对象实体
         * @param surface 表层View对象实体
         * */
        public Builder setSurfaceView(GLSurfaceView surface) {
            this.mSurfaceView = surface;
            return this;
        }

        /**
         * 构建视频录制控制器实体
         * */
        public IVideoRecordController builder() {
            RecordMovieController controller = new RecordMovieController();
            controller.setGLSurfaceView(this.mSurfaceView);
            return controller;
        }
    }
}
