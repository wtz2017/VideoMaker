package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;

import com.wtz.libvideomaker.renderer.GrayScreenRenderer;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;

public class GraySurfaceView extends FilterSurfaceView{
    private static final String TAG = GraySurfaceView.class.getSimpleName();

    public GraySurfaceView(Context context) {
        super(context);
    }

    public GraySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GraySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    @Override
    protected OnScreenRenderer createRenderer(Context context) {
        return new GrayScreenRenderer(context);
    }

}
