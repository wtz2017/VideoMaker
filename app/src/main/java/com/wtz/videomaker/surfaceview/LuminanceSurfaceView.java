package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;

import com.wtz.libvideomaker.renderer.LuminanceScreenRenderer;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;

public class LuminanceSurfaceView extends FilterSurfaceView{
    private static final String TAG = "LuminanceSurfaceView";

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
    protected OnScreenRenderer createRenderer(Context context) {
        return new LuminanceScreenRenderer(context);
    }

}
