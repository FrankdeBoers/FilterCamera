package com.camera.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by guohongcheng on 2018/1/22.
 */

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("SplashActivity", "onCreate 1");
        startActivity(new Intent(SplashActivity.this, CameraMainActivity.class));
        Log.d("SplashActivity", "onCreate 2");
        finish();
    }
}
