package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;

import com.wtz.libvideomaker.renderer.filters.FilterRenderer;
import com.wtz.libvideomaker.renderer.filters.LuminanceFilterRenderer;

public class LuminanceSurfaceView extends FilterSurfaceView{
    private static final String TAG = LuminanceSurfaceView.class.getSimpleName();

    public LuminanceSurfaceView(Context context) {
        super(context);
    }

    public LuminanceSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LuminanceSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    @Override
    protected FilterRenderer createFilterRenderer(Context context) {
        return new LuminanceFilterRenderer(context);
    }

}
