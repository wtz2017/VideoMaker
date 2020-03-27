package com.wtz.libvideomaker.renderer;

import android.content.Context;

import com.wtz.libvideomaker.R;

public class LuminanceScreenRenderer extends OnScreenRenderer {
    private static final String TAG = LuminanceScreenRenderer.class.getSimpleName();

    public LuminanceScreenRenderer(Context mContext) {
        super(mContext, TAG);
    }

    @Override
    protected int getVertexShaderResId() {
        return R.raw.vertex_onscreen_shader;
    }

    @Override
    protected int getFragmentShaderResId() {
        return R.raw.fragment_luminance_texture2d_shader;
    }

}
