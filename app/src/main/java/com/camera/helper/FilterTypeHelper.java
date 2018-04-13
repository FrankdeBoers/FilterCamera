package com.camera.helper;

import com.camera.R;
import com.filters.filter.helper.FilterType;


public class FilterTypeHelper {

    public static int FilterType2Color(FilterType filterType) {
        switch (filterType) {
            case NONE:
                return R.color.filter_color_grey_light;
            case WHITE:
            case BLACK:
                return R.color.filter_color_brown_light;
            case COOL:
                return R.color.filter_color_blue_dark;
            case EVERGREEN:
                return R.color.filter_color_blue_dark_dark;
            case SAKURA:
                return R.color.filter_color_pink;
            default:
                return R.color.filter_color_grey_light;
        }
    }

    public static int FilterType2Thumb(FilterType filterType) {
        switch (filterType) {
            case NONE:
                return R.drawable.filter_thumb_original;
            case WHITE:
                return R.drawable.filter_thumb_whitecat;
            case BLACK:
                return R.drawable.filter_thumb_blackcat;
            case SAKURA:
                return R.drawable.filter_thumb_sakura;
            case COOL:
                return R.drawable.filter_thumb_cool;
            case EVERGREEN:
                return R.drawable.filter_thumb_evergreen;
            default:
                return R.drawable.filter_thumb_original;
        }
    }

    public static int FilterType2Name(FilterType filterType) {
        switch (filterType) {
            case NONE:
                return R.string.filter_none;
            case WHITE:
                return R.string.filter_white;
            case BLACK:
                return R.string.filter_black;
            case EVERGREEN:
                return R.string.filter_evergreen;
            case SAKURA:
                return R.string.filter_sakura;
            case COOL:
                return R.string.filter_cool;
            default:
                return R.string.filter_none;
        }
    }
}
