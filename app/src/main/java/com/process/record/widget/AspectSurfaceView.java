package com.process.record.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.widget.LinearLayout;

import com.process.record.R;

/**
 * Created by kerwin on 2018/11/21
 */
public class AspectSurfaceView extends LinearLayout {
    private static final String TAG = "AspectSurfaceView";

    private GLSurfaceView mSurfaceView = null;
    private AspectFrameLayout mAspectFrameLayout = null;

    public AspectSurfaceView(Context context) {
        super(context);
        initializationView();
    }

    public AspectSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializationView();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public AspectSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializationView();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AspectSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initializationView();
    }

    /**
     * 初始化组件视图界面
     * */
    private void initializationView() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.record_video_layout, this, true);

        mSurfaceView = (GLSurfaceView) findViewById(R.id.surface_view);
        mAspectFrameLayout = (AspectFrameLayout) findViewById(R.id.aspect_frame_layout);
    }

    /**
     * 得到surface view对象
     * @return 返回surface对象实体
     * */
    public GLSurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    public AspectFrameLayout getAspectFrameLayout() {
        return mAspectFrameLayout;
    }
}
