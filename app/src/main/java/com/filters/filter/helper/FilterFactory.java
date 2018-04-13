package com.filters.filter.helper;

import com.filters.filter.advanced.BlackFilter;
import com.filters.filter.advanced.CoolFilter;
import com.filters.filter.advanced.EvergreenFilter;
import com.filters.filter.advanced.SakuraFilter;
import com.filters.filter.advanced.WhiteFilter;
import com.filters.filter.base.gpuimage.GPUImageFilter;

public class FilterFactory {

    private static FilterType filterType = FilterType.NONE;

    public static GPUImageFilter initFilters(FilterType type) {
        filterType = type;
        switch (type) {
            case WHITE:
                return new WhiteFilter();
            case BLACK:
                return new BlackFilter();
            case SAKURA:
                return new SakuraFilter();
            case EVERGREEN:
                return new EvergreenFilter();
            case COOL:
                return new CoolFilter();
            default:
                return null;
        }
    }

    public FilterType getCurrentFilterType() {
        return filterType;
    }
}
