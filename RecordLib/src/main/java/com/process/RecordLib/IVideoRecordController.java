package com.process.RecordLib;

/**
 * 录制视频控制器接口
 * Created by kerwin on 2018/11/22
 */
public interface IVideoRecordController {
    /**
     * 打开摄像头模块
     * */
    void openCamera();

    /**
     * 开始预览画面
     * */
    void startPreview();

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
    void setOnCameraPreviewSizeChangeListener(VideoRecordController.OnCameraPreviewSizeChangeListener listener);

    /**
     * 设置显示
     * */
    void setDisplayOrientation(int orientation);
}
