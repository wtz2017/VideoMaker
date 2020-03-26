package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;

import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.renderer.ReverseScreenRenderer;

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
    protected OnScreenRenderer createRenderer(Context context) {
        return new ReverseScreenRenderer(context);
    }

}
