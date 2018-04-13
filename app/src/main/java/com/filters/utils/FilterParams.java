package com.filters.utils;

import android.content.Context;
import android.os.Environment;
import android.text.format.DateFormat;

import com.filters.widget.base.FilterBaseView;

import java.util.Calendar;

/**
 * Created by Frank on 2016/2/26.
 */
public class FilterParams {
    public static Context context;
    public static FilterBaseView filterBaseView;

    public static String videoPath = Environment.getExternalStoragePublicDirectory("DCIM").getAbsolutePath() + "/Camera";
    public static String videoName = "FVID_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance()) + ".mp4";

    public static int beautyLevel = 0;

    public FilterParams() {

    }
}
