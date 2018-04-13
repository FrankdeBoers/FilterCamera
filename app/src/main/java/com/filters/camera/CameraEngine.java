package com.filters.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;

import java.io.IOException;
import java.util.List;

public class CameraEngine {

    /*
    * 首先创建一个GLSurfaceView，并创建一个纹理ID，glGenTexture()
    * 通过这个纹理ID，创建一个SurfaceTexture，用于接收Camera preview 数据，通过setPreviewTexture绑定
    * 执行updateTextImage()方法可以将camera的一帧数据送到外部纹理上，一般是在Render的onDrawFrame()中执行
    * OpenGL就可以操作此纹理，比如加滤镜
    * 滤镜就是对图像的RGBA处理，处理后的数据通过OpenGL ES绘制出来。
    *
    * onSurfaceCretaed onSrufaceChanged onDrawFrame
    * */


    private static final String TAG = "CameraEngine";
    private static Camera camera = null;
    private static int cameraID = 0;
    private static SurfaceTexture surfaceTexture;

    public static Camera getCamera() {
        return camera;
    }

    public static boolean openCamera() {
        if (camera == null) {
            try {
                camera = Camera.open(cameraID);
                setDefaultParameters();
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    public static boolean openCamera(int id) {
        if (camera == null) {
            try {
                camera = Camera.open(id);
                cameraID = id;
                setDefaultParameters();
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    public static void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public static void switchCamera() {
        releaseCamera();
        cameraID = cameraID == 0 ? 1 : 0;
        startPreview(surfaceTexture);
    }

    private static void setDefaultParameters() {
        Parameters parameters = camera.getParameters();
        if (parameters.getSupportedFocusModes().contains(
                Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
//        Size previewSize = CameraUtils.getLargePreviewSize(camera);
        parameters.setPreviewSize(1280, 720);
//        Size pictureSize = CameraUtils.getLargePictureSize(camera);
        parameters.setPictureSize(1280, 720);
        parameters.setRotation(0);
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null && focusModes.size() > 0) {
            if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);  //设置自动对焦
            }
        }
        camera.setParameters(parameters);
    }

    public static void startPreview(SurfaceTexture surfaceTexture) {
        if (camera != null) {
            try {
                camera.setPreviewTexture(surfaceTexture);
                CameraEngine.surfaceTexture = surfaceTexture;
                camera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void startPreview() {
        if (camera != null) {
            camera.startPreview();
        }
    }

    public static void stopPreview() {
        camera.stopPreview();
    }

    public static void setRotation(int rotation) {
        Parameters params = camera.getParameters();
        params.setRotation(rotation);
        camera.setParameters(params);
    }

    public static void takePicture(Camera.ShutterCallback shutterCallback, Camera.PictureCallback rawCallback,
                                   Camera.PictureCallback jpegCallback) {
        camera.takePicture(shutterCallback, rawCallback, jpegCallback);
    }

    public static com.filters.camera.utils.CameraInfo getCameraInfo() {
        com.filters.camera.utils.CameraInfo info = new com.filters.camera.utils.CameraInfo();
        CameraInfo cameraInfo = new CameraInfo();
        info.previewWidth = 1280;
        info.previewHeight = 720;
        info.orientation = 90;
        info.isFront = /*cameraID == 1 ? true : */false;
        info.pictureWidth = 1280;
        info.pictureHeight = 720;
        return info;
    }
}