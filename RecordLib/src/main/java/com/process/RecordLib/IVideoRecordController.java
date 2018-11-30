package com.process.RecordLib;

import android.hardware.Camera;

import java.io.File;

/**
 * 录制视频控制器接口
 * Created by kerwin on 2018/11/22
 */
public interface IVideoRecordController {
    /**
     * 视频录制控制模块进行初始化工作
     * @param file 路径文件对象
     * */
    void initialize(File file);

    /**
     * 处理控制模块resume工作
     * */
    void handleResume();

    /**
     * 处理控制模块pause工作
     * */
    void handlePause();

    /**
     * 处理控制模块destroy工作
     * */
    void handleDestroy();

    /**
     * 打开摄像头模块
     * @param width 渲染宽度
     * @param height 渲染高度
     * */
    void openCamera(int width, int height);

    /**
     * 设置文件路径进行录制视频
     * @param filePath 视频文件路径
     * */
    void startRecordVideo(String filePath);

    /**
     * 停止录制视频文件
     * */
    void stopRecordVideo();

    /**
     * 保存视频画帧为图片文件
     * @param filePath 保存文件路径
     * */
    void saveFrameFile(String filePath);

    /**
     * 销毁视频录制使用的资源
     * */
    void release();

    /**
     * 设置拍摄预览Size变换监听器实体
     * @param listener 拍摄预览Size变换监听器实体
     * */
    void setOnCameraPreviewSizeChangeListener(OnCameraPreviewSizeChangeListener listener);

    /**
     * 设置显示
     * */
    void setDisplayOrientation(int orientation);

    /**
     * 摄像头画布发生Size变化监听器
     * */
    interface OnCameraPreviewSizeChangeListener {
        /**
         * 摄像头预览Size切换时触发回调
         * @param camera 摄像头实例
         * @param parms 参数实体
         * */
        void onCameraPreviewSizeChange(Camera camera, Camera.Parameters parms);
    }
}
