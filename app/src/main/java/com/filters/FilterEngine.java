package com.filters;

import com.filters.camera.CameraEngine;
import com.filters.filter.helper.FilterType;
import com.filters.helper.SavePictureTask;
import com.filters.utils.FilterParams;
import com.filters.widget.FilterCameraView;
import com.filters.widget.base.FilterBaseView;

import java.io.File;

//import com.xxun.morefilter.camera.CameraEngine;

/**
 * Created by Frank on 2016/2/25.
 */
public class FilterEngine {
    private static FilterEngine filterEngine;

    private FilterEngine(Builder builder) {

    }

    public static FilterEngine getInstance() {
        if (filterEngine == null) {
            throw new NullPointerException("FilterEngine must be built first");
        } else {
            return filterEngine;
        }
    }

    public void setFilter(FilterType type) {
        FilterParams.filterBaseView.setFilter(type);
    }

    public void savePicture(File file, SavePictureTask.OnPictureSaveListener listener) {
        SavePictureTask savePictureTask = new SavePictureTask(file, listener);
        FilterParams.filterBaseView.savePicture(savePictureTask);
    }

    public void startRecord() {
        if (FilterParams.filterBaseView instanceof FilterCameraView) {
//            ((FilterCameraView) FilterParams.filterBaseView).changeRecordingState(true);
//            CameraEngine.startRecord();
        }
    }

    public void stopRecord() {
        if (FilterParams.filterBaseView instanceof FilterCameraView) {
//            ((FilterCameraView) FilterParams.filterBaseView).changeRecordingState(false);
//            CameraEngine.stopRecordVideo();
        }
    }

    public void setBeautyLevel(int level) {
        if (FilterParams.filterBaseView instanceof FilterCameraView && FilterParams.beautyLevel != level) {
            FilterParams.beautyLevel = level;
            ((FilterCameraView) FilterParams.filterBaseView).onBeautyLevelChanged();
        }
    }

    public void switchCamera() {
        CameraEngine.switchCamera();
    }

    public static class Builder {

        public FilterEngine build(FilterBaseView filterBaseView) {
            FilterParams.context = filterBaseView.getContext();
            FilterParams.filterBaseView = filterBaseView;
            return new FilterEngine(this);
        }

        public Builder setVideoPath(String path) {
            FilterParams.videoPath = path;
            return this;
        }

        public Builder setVideoName(String name) {
            FilterParams.videoName = name;
            return this;
        }

    }
}
