package com.camera.activity;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.camera.R;
import com.camera.adapter.FilterAdapter;
import com.filters.FilterEngine;
import com.filters.filter.helper.FilterType;
import com.filters.widget.FilterCameraView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * Created by Frank on 2016/3/17.
 */
public class CameraMainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int STATE_IDLE = 11;        //空闲状态
    public static final int STATE_RECORDERING = 14; //录制状态
    private static final String TAG = "CameraMainActiavity";

    private final FilterType[] types = new FilterType[]{
            FilterType.NONE,
            FilterType.WHITE, // 白天
            FilterType.BLACK, // 黑夜
            FilterType.SAKURA, // 樱花
            FilterType.COOL, // 冰冷
            FilterType.EVERGREEN, // 常青
    };
    private int state = STATE_IDLE;              //当前按钮状态
    private long mPrePicTime;
    private LinearLayout mFilterLayout;
    private RecyclerView mFilterListView;
    private FilterAdapter mAdapter;
    private FilterEngine filterEngine;
    private ImageView btnPic = null;
    private boolean isQuit = true; // 解决滤镜左划冲突

    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager = null;


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
            btnPic.setVisibility(View.VISIBLE);
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
        FilterEngine.Builder builder = new FilterEngine.Builder();
        filterEngine = builder
                .build((FilterCameraView) findViewById(R.id.glsurfaceview_camera));

        mFilterLayout = (LinearLayout) findViewById(R.id.layout_filter);
        mFilterListView = (RecyclerView) findViewById(R.id.filter_listView);

        btnPic = (ImageView) findViewById(R.id.btn_camera_shutter);
        findViewById(R.id.btn_camera_filter).setOnClickListener(this);

        btnPic.setOnClickListener(this);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        mFilterListView.setLayoutManager(gridLayoutManager);

        mAdapter = new FilterAdapter(this, types);
        mFilterListView.setAdapter(mAdapter);
        mAdapter.setOnFilterChangeListener(onFilterChangeListener);

        Point screenSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(screenSize);
        FilterCameraView cameraView = (FilterCameraView) findViewById(R.id.glsurfaceview_camera);
        cameraView.setOnClickListener(this);
        mFilterListView.setOnClickListener(this);
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

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btn_camera_filter:
                showFilters();
                break;

            case R.id.btn_camera_shutter:
                if (System.currentTimeMillis() - mPrePicTime > 1400) {
                    takePhoto();
                }
                break;

            case R.id.filter_listView:
                isQuit = false;
                break;

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


}