package com.process.record;

import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.process.RecordLib.IVideoRecordController;
import com.process.RecordLib.RecordMovieController;
import com.process.record.permission.PermissionHelper;
import com.process.record.widget.AspectFrameLayout;
import com.process.record.widget.AspectSurfaceView;

import java.io.File;

public class MainActivity extends AppCompatActivity implements
        View.OnClickListener,
        IVideoRecordController.OnCameraPreviewSizeChangeListener {
    private static final String TAG = "MainActivity";

    private IVideoRecordController mVideoRecordController = null;
    private AspectSurfaceView mAspectSurfaceView = null;
    private Button mStartRecord = null;
//    private Button mStartPhoto = null;

    /** 是否开始录制视频 */
    private boolean isStartRecord = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAspectSurfaceView = (AspectSurfaceView) findViewById(R.id.aspect_surface_view);
        mStartRecord = (Button) findViewById(R.id.start_record);
//        mStartPhoto = (Button) findViewById(R.id.start_photo);

        String path = getStoragePath() + File.separator + "demo" + File.separator + "demo.mp4";
        File file = new File(path);

        RecordMovieController.Builder builder = new RecordMovieController.Builder();
        mVideoRecordController = builder
                .setSurfaceView(mAspectSurfaceView.getSurfaceView())
                .builder();
        mVideoRecordController.setOnCameraPreviewSizeChangeListener(this);
        mVideoRecordController.initialize(file);

        mStartRecord.setOnClickListener(this);
//        mStartPhoto.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mVideoRecordController == null) {
            Log.w(TAG, "onResume() video record controller is null.");
        } else {
            mVideoRecordController.handleResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mVideoRecordController == null) {
            Log.w(TAG, "onPause() video record controller is null.");
        } else {
            mVideoRecordController.handlePause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mVideoRecordController == null) {
            Log.w(TAG, "onDestroy() video record controller is null.");
        } else {
            mVideoRecordController.release();
            mVideoRecordController.handleDestroy();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(mVideoRecordController == null) {
            Log.w(TAG, "onRequestPermissionsResult() video record controller is null.");
            return;
        }

        if(!PermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
            PermissionHelper.launchPermissionSettings(this);
            this.finish();
        } else {
            mVideoRecordController.openCamera(720, 1080);
        }
    }

    @Override
    public void onCameraPreviewSizeChange(Camera camera, Camera.Parameters parms) {
        WindowManager manager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        if(manager == null) {
            Log.d(TAG, "open camera main activity manager is null.");
            return;
        }

        double ratio = 0.0d;
        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        Display display = manager.getDefaultDisplay();
        if(display.getRotation() == Surface.ROTATION_0) {
            setDisplayOrientation(90);
            ratio = (double) cameraPreviewSize.height / cameraPreviewSize.width;
        } else if(display.getRotation() == Surface.ROTATION_270) {
            setDisplayOrientation(180);
            ratio = (double) cameraPreviewSize.height / cameraPreviewSize.width;
        } else {
            // Set the preview aspect ratio.
            ratio = (double) cameraPreviewSize.width / cameraPreviewSize.height;
        }

        setAspectRatio(ratio);
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if(viewId == R.id.start_record) {
            startRecordVideo();
        }
//        else if(viewId == R.id.start_photo) {
//
//        }
    }

    /**
     * 开始录制视频
     * */
    private void startRecordVideo() {
        isStartRecord = !isStartRecord;

        if(mStartRecord == null) {
            Log.w(TAG, "updateRecordTitle() start record is null.");
        } else {
            mStartRecord.setText(isStartRecord ? "停止拍摄" : "开始拍摄");
        }

        if(mVideoRecordController == null) {
            Log.w(TAG, "updateRecordTitle() video record controller is null.");
            return;
        }

        if(isStartRecord) {
            String path = getStoragePath() + File.separator + "demo" + File.separator + "demo.mp4";
            mVideoRecordController.startRecordVideo(path);
        } else {
            mVideoRecordController.stopRecordVideo();
        }
    }

    private String getStoragePath() {
        File file = Environment.getExternalStorageDirectory();
        return file.getPath();
    }

    /**
     * 设置画布的纵横比
     * @param ration 纵横比
     * */
    private void setAspectRatio(double ration) {
        if(mAspectSurfaceView == null) {
            Log.w(TAG, "setAspectRatio() aspect surface view is null.");
            return;
        }

        AspectFrameLayout aspectFrame = mAspectSurfaceView.getAspectFrameLayout();
        if(aspectFrame == null) {
            Log.w(TAG, "setAspectRatio() aspect frame is null.");
            return;
        }

        aspectFrame.setAspectRatio(ration);
    }

    /**
     * 设置画布渲染显示的方向
     * @param orientation 方向数值(0-360)
     * */
    private void setDisplayOrientation(int orientation) {
        if(mVideoRecordController == null) {
            Log.w(TAG, "onCameraPreviewSizeChange() video record controller is null.");
        } else {
            mVideoRecordController.setDisplayOrientation(orientation);
        }
    }
}
