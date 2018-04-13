package com.camera.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.camera.R;
import com.camera.helper.FilterTypeHelper;
import com.filters.filter.helper.FilterType;

/**
 * Created by Frank on 2016/3/17.
 * 滤镜适配器，在CameraActivity中的FilterListView使用
 */
public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.FilterHolder> {

    private com.filters.filter.helper.FilterType[] filters;
    private Context context;
    private int selected = 0;
    private onFilterChangeListener onFilterChangeListener;

    public FilterAdapter(Context context, FilterType[] filters) {
        this.filters = filters;
        this.context = context;
    }

    @Override
    public FilterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.filter_item_layout,
                parent, false);
        FilterHolder viewHolder = new FilterHolder(view);
        viewHolder.thumbImage = (ImageView) view
                .findViewById(R.id.filter_thumb_image);
        viewHolder.filterName = (TextView) view
                .findViewById(R.id.filter_thumb_name);
        viewHolder.filterRoot = (FrameLayout) view
                .findViewById(R.id.filter_root);
        viewHolder.thumbSelected = (FrameLayout) view
                .findViewById(R.id.filter_thumb_selected);
        viewHolder.thumbSelected_bg = view.
                findViewById(R.id.filter_thumb_selected_bg);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(FilterHolder holder, final int position) {
        holder.thumbImage.setImageResource(FilterTypeHelper.FilterType2Thumb(filters[position]));
        holder.filterName.setText(FilterTypeHelper.FilterType2Name(filters[position]));
        holder.filterName.setBackgroundColor(context.getResources().getColor(
                FilterTypeHelper.FilterType2Color(filters[position])));
        if (position == selected) {
            holder.thumbSelected.setVisibility(View.VISIBLE);
            holder.thumbSelected_bg.setBackgroundColor(context.getResources().getColor(
                    FilterTypeHelper.FilterType2Color(filters[position])));
            holder.thumbSelected_bg.setAlpha(0.7f);
        } else {
            holder.thumbSelected.setVisibility(View.GONE);
        }

        holder.filterRoot.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (selected == position) {
                    onFilterChangeListener.onFiterSame();
                    return;
                }
                int lastSelected = selected;
                selected = position;
                notifyItemChanged(lastSelected);
                notifyItemChanged(position);
                onFilterChangeListener.onFilterChanged(filters[position]);
            }
        });
    }

    public void setFilters4Activity() {
        Log.d("FilterAdapter", "setFilters4Activity selected: " + selected);
        if (selected != 0) {
            int lastSelected = selected;
            selected = 0;
            notifyItemChanged(0);
            notifyItemChanged(lastSelected);
        }
    }

    @Override
    public int getItemCount() {
        return filters == null ? 0 : filters.length;
    }

    public void setOnFilterChangeListener(onFilterChangeListener onFilterChangeListener) {
        this.onFilterChangeListener = onFilterChangeListener;
    }

    public interface onFilterChangeListener {
        void onFilterChanged(FilterType filterType);

        void onFiterSame();
    }

    class FilterHolder extends RecyclerView.ViewHolder {
        ImageView thumbImage;
        TextView filterName;
        FrameLayout thumbSelected;
        FrameLayout filterRoot;
        View thumbSelected_bg;

        public FilterHolder(View itemView) {
            super(itemView);
        }
    }
}
