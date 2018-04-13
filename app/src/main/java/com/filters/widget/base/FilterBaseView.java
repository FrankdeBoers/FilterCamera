package com.filters.widget.base;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import com.filters.filter.base.gpuimage.GPUImageFilter;
import com.filters.filter.helper.FilterFactory;
import com.filters.filter.helper.FilterType;
import com.filters.helper.SavePictureTask;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Frank on 2016/2/25.
 */
public abstract class FilterBaseView extends GLSurfaceView implements GLSurfaceView.Renderer {
    /**
     * 顶点坐标
     */
    protected final FloatBuffer gLCubeBuffer;
    /**
     * 纹理坐标
     */
    protected final FloatBuffer gLTextureBuffer;
    /**
     * 所选择的滤镜，类型为BaseGroupFilter
     * 1.mCameraInputFilter将SurfaceTexture中YUV数据绘制到FrameBuffer
     * 2.filter将FrameBuffer中的纹理绘制到屏幕中
     * 一般来说，offscreen render的用处主要是做GPU加速，如果你的算法是计算密集型的，
     * 可以通过一些方法将其转化成位图形式，作为纹理（texture）载入到GPU显存中，再利用GPU的并行计算能力，对其进行处理，
     * 最后利用glReadPixels将计算结果读取到内存中。
     */
    protected GPUImageFilter filter;
    /**
     * SurfaceTexure纹理id
     */
    protected int textureId = com.filters.utils.OpenGlUtils.NO_TEXTURE;
    /**
     * GLSurfaceView的宽高
     */
    protected int surfaceWidth, surfaceHeight;

    /**
     * 图像宽高
     */
    protected int imageWidth, imageHeight;

    protected ScaleType scaleType = ScaleType.FIT_XY;

    public FilterBaseView(Context context) {
        this(context, null);
    }

    public FilterBaseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gLCubeBuffer = ByteBuffer.allocateDirect(com.filters.utils.TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(com.filters.utils.TextureRotationUtil.CUBE).position(0);

        gLTextureBuffer = ByteBuffer.allocateDirect(com.filters.utils.TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBuffer.put(com.filters.utils.TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glDisable(GL10.GL_DITHER);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glEnable(GL10.GL_CULL_FACE);
        GLES20.glEnable(GL10.GL_DEPTH_TEST);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        surfaceWidth = width;
        surfaceHeight = height;
        onFilterChanged();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    protected void onFilterChanged() {
        if (filter != null) {
            filter.onDisplaySizeChanged(surfaceWidth, surfaceHeight);
            filter.onInputSizeChanged(imageWidth, imageHeight);
        }
    }

    public void setFilter(final FilterType type) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (filter != null) {
                    filter.destroy();
                }
                filter = null;
                filter = FilterFactory.initFilters(type);
                if (filter != null) {
                    filter.init();
                }
                onFilterChanged();
            }
        });
        requestRender();
    }

    protected void deleteTextures() {
        if (textureId != com.filters.utils.OpenGlUtils.NO_TEXTURE) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLES20.glDeleteTextures(1, new int[]{
                            textureId
                    }, 0);
                    textureId = com.filters.utils.OpenGlUtils.NO_TEXTURE;
                }
            });
        }
    }

    public abstract void savePicture(SavePictureTask savePictureTask);

    protected void adjustSize(int rotation, boolean flipHorizontal, boolean flipVertical) {
        float[] textureCords = com.filters.utils.TextureRotationUtil.getRotation(com.filters.utils.Rotation.fromInt(rotation),
                flipHorizontal, flipVertical);
        float[] cube = com.filters.utils.TextureRotationUtil.CUBE;
        float ratio1 = (float) surfaceWidth / imageWidth;
        float ratio2 = (float) surfaceHeight / imageHeight;
        float ratioMax = Math.max(ratio1, ratio2);
        int imageWidthNew = Math.round(imageWidth * ratioMax);
        int imageHeightNew = Math.round(imageHeight * ratioMax);

        float ratioWidth = imageWidthNew / (float) surfaceWidth;
        float ratioHeight = imageHeightNew / (float) surfaceHeight;

        if (scaleType == ScaleType.CENTER_INSIDE) {
            cube = new float[]{
                    com.filters.utils.TextureRotationUtil.CUBE[0] / ratioHeight, com.filters.utils.TextureRotationUtil.CUBE[1] / ratioWidth,
                    com.filters.utils.TextureRotationUtil.CUBE[2] / ratioHeight, com.filters.utils.TextureRotationUtil.CUBE[3] / ratioWidth,
                    com.filters.utils.TextureRotationUtil.CUBE[4] / ratioHeight, com.filters.utils.TextureRotationUtil.CUBE[5] / ratioWidth,
                    com.filters.utils.TextureRotationUtil.CUBE[6] / ratioHeight, com.filters.utils.TextureRotationUtil.CUBE[7] / ratioWidth,
            };
        } else if (scaleType == ScaleType.FIT_XY) {

        } else if (scaleType == ScaleType.CENTER_CROP) {
            float distHorizontal = (1 - 1 / ratioWidth) / 2;
            float distVertical = (1 - 1 / ratioHeight) / 2;
            textureCords = new float[]{
                    addDistance(textureCords[0], distVertical), addDistance(textureCords[1], distHorizontal),
                    addDistance(textureCords[2], distVertical), addDistance(textureCords[3], distHorizontal),
                    addDistance(textureCords[4], distVertical), addDistance(textureCords[5], distHorizontal),
                    addDistance(textureCords[6], distVertical), addDistance(textureCords[7], distHorizontal),
            };
        }
        gLCubeBuffer.clear();
        gLCubeBuffer.put(cube).position(0);
        gLTextureBuffer.clear();
        gLTextureBuffer.put(textureCords).position(0);
    }

    private float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    public enum ScaleType {
        CENTER_INSIDE,
        CENTER_CROP,
        FIT_XY;
    }
}
