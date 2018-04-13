package com.filters.widget;


import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.filters.camera.utils.CameraInfo;
import com.filters.encoder.video.TextureMovieEncoder;
import com.filters.filter.advanced.BeautyFilter;
import com.filters.filter.base.CameraInputFilter;
import com.filters.filter.helper.FilterType;
import com.filters.helper.SavePictureTask;
import com.filters.utils.FilterParams;
import com.filters.widget.base.FilterBaseView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Frank on 2016/2/25.
 */
public class FilterCameraView extends FilterBaseView {

    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;
    private static TextureMovieEncoder videoEncoder = new TextureMovieEncoder();
    private final String TAG = "FilterCameraView";
    private CameraInputFilter cameraInputFilter;
    private BeautyFilter beautyFilter;
    private SurfaceTexture surfaceTexture;
    private boolean recordingEnabled;
    private int recordingStatus;
    private File outputFile;
    private SurfaceTexture.OnFrameAvailableListener onFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            requestRender();
        }
    };

    public FilterCameraView(Context context) {
        this(context, null);
    }

    public FilterCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.getHolder().addCallback(this);
        outputFile = new File(FilterParams.videoPath, FilterParams.videoName);
        recordingStatus = -1;
        recordingEnabled = false;
        scaleType = ScaleType.CENTER_CROP;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        super.onSurfaceCreated(gl, config);
        recordingEnabled = videoEncoder.isRecording();
        if (recordingEnabled) {
            recordingStatus = RECORDING_RESUMED;
        } else {
            recordingStatus = RECORDING_OFF;
        }
        if (cameraInputFilter == null) {
            cameraInputFilter = new CameraInputFilter();
        }
        cameraInputFilter.init();
        if (textureId == com.filters.utils.OpenGlUtils.NO_TEXTURE) {
            textureId = com.filters.utils.OpenGlUtils.getExternalOESTextureID();
            if (textureId != com.filters.utils.OpenGlUtils.NO_TEXTURE) {
                surfaceTexture = new SurfaceTexture(textureId);
                surfaceTexture.setOnFrameAvailableListener(onFrameAvailableListener);
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        super.onSurfaceChanged(gl, width, height);
        openCamera();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        super.onDrawFrame(gl);
        if (surfaceTexture == null) {
            return;
        }
        surfaceTexture.updateTexImage();
        if (recordingEnabled) {
            switch (recordingStatus) {
                case RECORDING_OFF:
                    CameraInfo info = com.filters.camera.CameraEngine.getCameraInfo();
                    videoEncoder.setPreviewSize(info.previewWidth, info.previewHeight);
                    videoEncoder.setTextureBuffer(gLTextureBuffer);
                    videoEncoder.setCubeBuffer(gLCubeBuffer);
                    videoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(
                            outputFile, info.pictureWidth, info.pictureHeight,
                            1000000, EGL14.eglGetCurrentContext(),
                            info));
                    recordingStatus = RECORDING_ON;
                    break;
                case RECORDING_RESUMED:
                    videoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    recordingStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    break;
                default:
                    throw new RuntimeException("unknown status " + recordingStatus);
            }
        } else {
            switch (recordingStatus) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    videoEncoder.stopRecording();
                    recordingStatus = RECORDING_OFF;
                    break;
                case RECORDING_OFF:
                    break;
                default:
                    throw new RuntimeException("unknown status " + recordingStatus);
            }
        }
        float[] mtx = new float[16];
        surfaceTexture.getTransformMatrix(mtx);
        cameraInputFilter.setTextureTransformMatrix(mtx);
        int id = textureId;
        if (filter == null) {
            cameraInputFilter.onDrawFrame(textureId, gLCubeBuffer, gLTextureBuffer);
        } else {
            id = cameraInputFilter.onDrawToTexture(textureId);
            filter.onDrawFrame(id, gLCubeBuffer, gLTextureBuffer);
        }
        videoEncoder.setTextureId(id);
        videoEncoder.frameAvailable(surfaceTexture);
    }

    @Override
    public void setFilter(FilterType type) {
        super.setFilter(type);
        videoEncoder.setFilter(type);
    }

    private void openCamera() {
        if (com.filters.camera.CameraEngine.getCamera() == null) {
            com.filters.camera.CameraEngine.openCamera();
        }
        CameraInfo info = com.filters.camera.CameraEngine.getCameraInfo();
        if (info.orientation == 90 || info.orientation == 270) {
            imageWidth = info.previewHeight;
            imageHeight = info.previewWidth;
        } else {
            imageWidth = info.previewWidth;
            imageHeight = info.previewHeight;
        }
        cameraInputFilter.onInputSizeChanged(imageWidth, imageHeight);
        adjustSize(info.orientation, info.isFront, true);
        if (surfaceTexture != null) {
            com.filters.camera.CameraEngine.startPreview(surfaceTexture);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        com.filters.camera.CameraEngine.releaseCamera();
    }

    public void changeRecordingState(boolean isRecording) {
        recordingEnabled = isRecording;
    }

    @Override
    protected void onFilterChanged() {
        super.onFilterChanged();
        cameraInputFilter.onDisplaySizeChanged(surfaceWidth, surfaceHeight);
        if (filter != null) {
            cameraInputFilter.initCameraFrameBuffer(imageWidth, imageHeight);
        } else {
            cameraInputFilter.destroyFramebuffers();
        }
    }

    @Override
    public void savePicture(final SavePictureTask savePictureTask) {
        com.filters.camera.CameraEngine.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                com.filters.camera.CameraEngine.stopPreview();
                final Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap photo = drawPhoto(bitmap, com.filters.camera.CameraEngine.getCameraInfo().isFront);
                        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
                        if (photo != null) {
                            savePictureTask.execute(photo);
                        }
                    }
                });
                com.filters.camera.CameraEngine.startPreview();
            }
        });
    }

    private Bitmap drawPhoto(Bitmap bitmap, boolean isRotated) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] mFrameBuffers = new int[1];
        int[] mFrameBufferTextures = new int[1];
        if (beautyFilter == null) {
            beautyFilter = new BeautyFilter();
        }
        beautyFilter.init();
        beautyFilter.onDisplaySizeChanged(width, height);
        beautyFilter.onInputSizeChanged(width, height);

        if (filter != null) {
            filter.onInputSizeChanged(width, height);
            filter.onDisplaySizeChanged(width, height);
        }
        GLES20.glGenFramebuffers(1, mFrameBuffers, 0);
        GLES20.glGenTextures(1, mFrameBufferTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mFrameBufferTextures[0], 0);

        GLES20.glViewport(0, 0, width, height);
        int textureId = com.filters.utils.OpenGlUtils.loadTexture(bitmap, com.filters.utils.OpenGlUtils.NO_TEXTURE, true);

        FloatBuffer gLCubeBuffer = ByteBuffer.allocateDirect(com.filters.utils.TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        FloatBuffer gLTextureBuffer = ByteBuffer.allocateDirect(com.filters.utils.TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(com.filters.utils.TextureRotationUtil.CUBE).position(0);
        if (isRotated) {
            gLTextureBuffer.put(com.filters.utils.TextureRotationUtil.getRotation(com.filters.utils.Rotation.NORMAL, false, false)).position(0);
        } else {
            gLTextureBuffer.put(com.filters.utils.TextureRotationUtil.getRotation(com.filters.utils.Rotation.NORMAL, false, true)).position(0);
        }


        if (filter == null) {
            beautyFilter.onDrawFrame(textureId, gLCubeBuffer, gLTextureBuffer);
        } else {
            beautyFilter.onDrawFrame(textureId);
            filter.onDrawFrame(mFrameBufferTextures[0], gLCubeBuffer, gLTextureBuffer);
        }
        IntBuffer ib = IntBuffer.allocate(width * height);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ib);
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.copyPixelsFromBuffer(ib);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
        GLES20.glDeleteFramebuffers(mFrameBuffers.length, mFrameBuffers, 0);
        GLES20.glDeleteTextures(mFrameBufferTextures.length, mFrameBufferTextures, 0);

        beautyFilter.destroy();
        beautyFilter = null;
        if (filter != null) {
            filter.onDisplaySizeChanged(surfaceWidth, surfaceHeight);
            filter.onInputSizeChanged(imageWidth, imageHeight);
        }
        return result;
    }

    public void onBeautyLevelChanged() {
        cameraInputFilter.onBeautyLevelChanged();
    }

    /**
     * 实现反转、镜像
     *
     * @param data    [源数据]
     * @param degrees [旋转角度]
     * @param mirror  [是否镜像]
     * @return [description]
     */
    public byte[] rotateAndMirror(byte[] data, int degrees, boolean mirror) {
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
        if ((degrees != 0 || mirror) && b != null) {
            Matrix m = new Matrix();
            // Mirror first.
            // horizontal flip + rotation = -rotation + horizontal flip
            if (mirror) {
                m.postScale(-1, 1);
                degrees = (degrees + 360) % 360;
                if (degrees == 0 || degrees == 180) {
                    m.postTranslate(b.getWidth(), 0);
                } else if (degrees == 90 || degrees == 270) {
                    m.postTranslate(b.getHeight(), 0);
                } else {
                    throw new IllegalArgumentException("Invalid degrees=" + degrees);
                }
            }
            if (degrees != 0) {
                // clockwise
                m.postRotate(degrees,
                        (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            }

            try {
                Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 30, outputStream);
                byte[] scaleData = outputStream.toByteArray();
                b.recycle();
                return scaleData;
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
                ex.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 保证预览方向正确
     *
     * @param activity
     * @param cameraId
     * @param camera
     */
    private void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG, "[setCameraDisplayOrientation] >> rotation = " + rotation);
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }
}
