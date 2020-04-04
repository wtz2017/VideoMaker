package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;

import com.wtz.libvideomaker.renderer.filters.FilterRenderer;
import com.wtz.libvideomaker.renderer.filters.ReverseFilterRenderer;

public class ReverseSurfaceView extends FilterSurfaceView{
    private static final String TAG = ReverseSurfaceView.class.getSimpleName();

    public ReverseSurfaceView(Context context) {
        super(context);
    }

    public ReverseSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReverseSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    @Override
    protected FilterRenderer createFilterRenderer(Context context) {
        return new ReverseFilterRenderer(context);
    }

}
