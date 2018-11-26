package com.process.record;

import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.process.RecordLib.IVideoRecordController;
import com.process.RecordLib.VideoRecordController;
import com.process.record.permission.PermissionHelper;
import com.process.record.widget.AspectFrameLayout;
import com.process.record.widget.AspectSurfaceView;

public class MainActivity extends AppCompatActivity implements VideoRecordController.OnCameraPreviewSizeChangeListener {
    private static final String TAG = "MainActivity";

    private IVideoRecordController mVideoRecordController = null;
    private AspectSurfaceView mAspectSurfaceView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAspectSurfaceView = (AspectSurfaceView) findViewById(R.id.aspect_surface_view);

        VideoRecordController.Builder builder = new VideoRecordController.Builder();
        builder.setSurfaceView(mAspectSurfaceView.getSurfaceView());
        mVideoRecordController = builder.builder();

        mVideoRecordController.setOnCameraPreviewSizeChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mVideoRecordController == null) {
            Log.w(TAG, "onResume() video record controller is null.");
        } else {
            mVideoRecordController.startPreview();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mVideoRecordController == null) {
            Log.w(TAG, "onPause() video record controller is null.");
        } else {
            mVideoRecordController.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mVideoRecordController == null) {
            Log.w(TAG, "onDestroy() video record controller is null.");
        } else {
            mVideoRecordController.release();
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
            mVideoRecordController.openCamera();
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
     * 设置显示的
     * */
    private void setDisplayOrientation(int orientation) {
        if(mVideoRecordController == null) {
            Log.w(TAG, "onCameraPreviewSizeChange() video record controller is null.");
        } else {
            mVideoRecordController.setDisplayOrientation(orientation);
        }
    }
}
