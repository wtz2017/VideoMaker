package com.wtz.libvideomaker.renderer;

import android.content.Context;

import com.wtz.libvideomaker.R;

public class GrayScreenRenderer extends OnScreenRenderer {
    private static final String TAG = GrayScreenRenderer.class.getSimpleName();

    public GrayScreenRenderer(Context mContext) {
        super(mContext, TAG);
    }

    @Override
    protected int getVertexShaderResId() {
        return R.raw.vertex_onscreen_shader;
    }

    @Override
    protected int getFragmentShaderResId() {
        return R.raw.fragment_gray_texture2d_shader;
    }

}
