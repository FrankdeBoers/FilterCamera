package com.camera.activity;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.camera.R;
import com.camera.adapter.FilterAdapter;
import com.filters.FilterEngine;
import com.filters.camera.CameraEngine;
import com.filters.filter.helper.FilterType;
import com.filters.widget.FilterCameraView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


/**
 * Created by Frank on 2016/3/17.
 */
public class CameraMainActivity extends Activity implements View.OnClickListener {

    public static final int STATE_IDLE = 11;        //空闲状态
    public static final int STATE_PRESS = 12;       //按下状态
    public static final int STATE_RECORDERING = 14; //录制状态
    public static final int PREVIEW_SIZE_WIDTH = 1280;
    public static final int PREVIEW_SIZE_HEIGHT = 960;
    private static final String TAG = "CameraMainActiavity";
    // 视频保存路径
    private static final String VIDEO_FILE_PATH
            = Environment.getExternalStoragePublicDirectory("DCIM").getAbsolutePath() + "/Camera";
    private static final int MSG_DISABLE_BTNPIC = 1;
    private static final int MSG_ENABLE_BTNPIC = 2;

    private final FilterType[] types = new FilterType[]{
            FilterType.NONE,
            FilterType.WHITE, // 白天
            FilterType.BLACK, // 黑夜
            FilterType.SAKURA, // 樱花
            FilterType.COOL, // 冰冷
            FilterType.EVERGREEN, // 常青
    };
    Camera camera = null;
    File videoFile = null;
    private int state = STATE_IDLE;              //当前按钮状态
    private MediaRecorder mMediaRecorder = null;
    private CamcorderProfile mCamcorderProfile = null;
    private String CURRTRT_VIDEO_NAME;
    private long mPrePicTime;
    private RelativeLayout gallery_filter;
    private LinearLayout mFilterLayout;
    private RecyclerView mFilterListView;
    private FilterAdapter mAdapter;
    private FilterEngine filterEngine;
    private ImageView btnPic = null;
    private Button btnVideo = null;
    private Button btnVideoStop = null;
    private boolean isQuit = true; // 解决滤镜左划冲突
    private RelativeLayout layout_video_mode = null;

    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager = null;

    // 计时器控件，用于录像时记录时间
    private Handler mMainHandler = null;
    private FilterAdapter.onFilterChangeListener onFilterChangeListener = new FilterAdapter.onFilterChangeListener() {

        @Override
        public void onFilterChanged(FilterType filterType) {
            filterEngine.setFilter(filterType);
            hideFilters();
            Log.d(TAG, "filterType: " + filterType);
        }

        @Override
        public void onFiterSame() {
            hideFilters();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        FilterEngine.Builder builder = new FilterEngine.Builder();
        filterEngine = builder
                .build((FilterCameraView) findViewById(R.id.glsurfaceview_camera));
        initView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setScreenOn(30 * 1000);
        Log.d(TAG, "onResume ..");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause ..");
        releaseWakeLock();
        if (state == STATE_RECORDERING) {
            stopRecordVideo();
            saveRecordVideo();
            btnVideo.setVisibility(View.VISIBLE);
            btnPic.setVisibility(View.VISIBLE);
            gallery_filter.setVisibility(View.VISIBLE);
            layout_video_mode.setVisibility(View.GONE);
            state = STATE_IDLE;
        }
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop ..");
    }

    private void initView() {
        mMainHandler = new MainHandler(this, getMainLooper());

        mFilterLayout = (LinearLayout) findViewById(R.id.layout_filter);
        mFilterListView = (RecyclerView) findViewById(R.id.filter_listView);
        gallery_filter = (RelativeLayout) findViewById(R.id.gallery_filter);

        btnPic = (ImageView) findViewById(R.id.btn_camera_shutter);
        btnVideo = (Button) findViewById(R.id.btn_camera_video);
        btnVideoStop = (Button) findViewById(R.id.btn_camera_video_stop);
        layout_video_mode = (RelativeLayout) findViewById(R.id.layout_video_mode);


        findViewById(R.id.btn_camera_filter).setOnClickListener(this);

        btnPic.setOnClickListener(this);
        btnVideo.setOnClickListener(this);
        btnVideoStop.setOnClickListener(this);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        mFilterListView.setLayoutManager(gridLayoutManager);

        mAdapter = new FilterAdapter(this, types);
        mFilterListView.setAdapter(mAdapter);
        mAdapter.setOnFilterChangeListener(onFilterChangeListener);

        Point screenSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(screenSize);
        FilterCameraView cameraView = (FilterCameraView) findViewById(R.id.glsurfaceview_camera);
//        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) cameraView.getLayoutParams();
//        params.width = screenSize.x;
//        params.height = screenSize.y /** 4 / 3*/;
//
//        cameraView.setLayoutParams(params);
        cameraView.setOnClickListener(this);

        mFilterListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        isQuit = false;
                        break;
                }
                return false;
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy " + isQuit);
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed isQuit " + isQuit);
        if (isQuit) {
            super.onBackPressed();
        } else {
            isQuit = true;
        }
    }


    private void showFilters() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(mFilterLayout, "translationY", mFilterLayout.getHeight(), 0);
        animator.setDuration(200);
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                findViewById(R.id.btn_camera_shutter).setClickable(false);
                mFilterLayout.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {

            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }
        });
        animator.start();
    }

    private void hideFilters() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(mFilterLayout, "translationY", 0, mFilterLayout.getHeight());
        animator.setDuration(200);
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // TODO Auto-generated method stub
                mFilterLayout.setVisibility(View.INVISIBLE);
                findViewById(R.id.btn_camera_shutter).setClickable(true);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // TODO Auto-generated method stub
                mFilterLayout.setVisibility(View.INVISIBLE);
                findViewById(R.id.btn_camera_shutter).setClickable(true);
            }
        });
        animator.start();
    }

    public File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                "DCIM"), "Camera");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINESE).format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "FIMG_" + timeStamp + ".jpg");
        return mediaFile;
    }

    private void takePhoto() {
        Log.d(TAG, "takePhoto ");
        mPrePicTime = System.currentTimeMillis();
        filterEngine.savePicture(getOutputMediaFile(), null);
    }

    private void takeVideo() {
        Log.d(TAG, "takeVideo ");
        if (state == STATE_IDLE) {
            state = STATE_RECORDERING;
            camera = CameraEngine.getCamera();
            camera.setDisplayOrientation(180);
            filterEngine.setFilter(FilterType.NONE);
            mAdapter.setFilters4Activity();
            startRecord(camera);
        }
    }

    public void startRecord(Camera camera) {
        Log.d(TAG, "camera " + camera);
        try {
            // 视频存储的缓存路径
            CURRTRT_VIDEO_NAME = "VID_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance()) + ".mp4";
            File videoTempDir = new File(VIDEO_FILE_PATH);
            if (!videoTempDir.exists()) {
                videoTempDir.mkdirs();
            }
            videoFile = new File(VIDEO_FILE_PATH, CURRTRT_VIDEO_NAME);
            camera.unlock();

            //初始化一个MediaRecorder
            if (mMediaRecorder == null) {
                mMediaRecorder = new MediaRecorder();
            } else {
                mMediaRecorder.reset();
            }
            mMediaRecorder.setCamera(camera);

            // 视频源类型
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setAudioChannels(2);

            mMediaRecorder.setOrientationHint(180);

            if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
                mCamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
                Log.e(TAG, "QUALITY_720P");
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
                mCamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
                Log.e(TAG, "QUALITY_1080P");
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_HIGH)) {
                mCamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                Log.e(TAG, "QUALITY_HIGH");
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_LOW)) {
                mCamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
                Log.e(TAG, "QUALITY_LOW");
            }

            if (mCamcorderProfile != null) {
                mCamcorderProfile.audioCodec = MediaRecorder.AudioEncoder.AAC;
                mCamcorderProfile.audioChannels = 1;
                mCamcorderProfile.audioSampleRate = 16000;

                mCamcorderProfile.videoCodec = MediaRecorder.VideoEncoder.H264;
                mMediaRecorder.setProfile(mCamcorderProfile);
            }
            mMediaRecorder.setVideoSize(PREVIEW_SIZE_WIDTH, PREVIEW_SIZE_HEIGHT);
            mMediaRecorder.setOutputFile(videoFile.getAbsolutePath());
            mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                @Override
                public void onError(MediaRecorder mr, int what, int extra) {
                    // 发生错误，停止录制
                    if (mMediaRecorder != null) {
                        mMediaRecorder.stop();
                        mMediaRecorder.release();
                        mMediaRecorder = null;
                    }
                    Log.e(TAG, "onError: " + " what:" + what + " extra: " + extra);
                }
            });

            mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
                    //正在录制...
                }
            });

            // 准备、开始
            mMediaRecorder.prepare();
            mMediaRecorder.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecordVideo() {
        if (state == STATE_RECORDERING) {
            if (mMediaRecorder != null) {
                try {
                    mMediaRecorder.setOnErrorListener(null);
                    mMediaRecorder.setOnInfoListener(null);
                    mMediaRecorder.setPreviewDisplay(null);
                    mMediaRecorder.stop();
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                } catch (IllegalStateException e) {
                    Log.e("Exception", Log.getStackTraceString(e));
                } catch (RuntimeException e) {
                    Log.e("Exception", Log.getStackTraceString(e));
                } catch (Exception e) {
                    // TODO: handle exception
                    Log.i("Exception", Log.getStackTraceString(e));
                }
            }

            if (gallery_filter != null) {
                gallery_filter.setVisibility(View.VISIBLE);

            }
            // 拍照按钮设置为拍照图标
            btnPic.setImageResource(R.drawable.btn_pic_normal);
        }

    }

    private void saveRecordVideo() {
        if (videoFile != null) {
            scanFile(videoFile.getAbsolutePath());
        } else {
            Log.e(TAG, "videoFile = null !! ");
        }
    }

    /**
     * 扫描文件
     *
     * @param path
     */
    private void scanFile(String path) {
        MediaScannerConnection.scanFile(CameraMainActivity.this, new String[]{path},
                null, new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.e("TAG", "onScanCompleted");
                    }
                });
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_camera_video:
                takeVideo();
                btnVideo.setVisibility(View.GONE);
                btnPic.setVisibility(View.GONE);
                gallery_filter.setVisibility(View.GONE);
                layout_video_mode.setVisibility(View.VISIBLE);

            case R.id.btn_camera_video_stop:
                stopRecordVideo();
                saveRecordVideo();
                btnVideo.setVisibility(View.VISIBLE);
                btnPic.setVisibility(View.VISIBLE);
                gallery_filter.setVisibility(View.VISIBLE);
                layout_video_mode.setVisibility(View.GONE);
                state = STATE_IDLE;
                break;

            case R.id.btn_camera_filter:
                showFilters();
                break;

            case R.id.btn_camera_shutter:
                if (System.currentTimeMillis() - mPrePicTime > 1400) {
                    takePhoto();
                }

            default:
                break;
        }
    }

    private void setScreenOn(long time) {
        Log.d(TAG, "setScreenOn ");
        if (powerManager == null) {
            powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        }
        wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "filtercamera");
        wakeLock.acquire(time);
    }

    private void releaseWakeLock() {
        Log.d(TAG, "releaseWakeLock ");
        try {
            wakeLock.release();
            wakeLock = null;
        } catch (Exception e) {
            wakeLock = null;
        }
    }

    private static class MainHandler extends Handler {
        final WeakReference<CameraMainActivity> mActivity;

        public MainHandler(CameraMainActivity activity, Looper looper) {
            super(looper);
            mActivity = new WeakReference<CameraMainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraMainActivity activity = mActivity.get();
            if (activity == null) {
                return;
            }
            Log.d(TAG, "msg.what: " + msg.what);
            switch (msg.what) {
                case MSG_ENABLE_BTNPIC:
                    activity.btnPic.setEnabled(true);
                    break;

                case MSG_DISABLE_BTNPIC:
                    activity.btnPic.setEnabled(false);
                    break;

                default:
                    break;

            }
        }
    }
}