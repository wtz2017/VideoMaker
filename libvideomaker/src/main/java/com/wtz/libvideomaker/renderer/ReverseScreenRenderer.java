package com.wtz.libvideomaker.renderer;

import android.content.Context;

import com.wtz.libvideomaker.R;

public class ReverseScreenRenderer extends OnScreenRenderer {
    private static final String TAG = "ReverseScreenRenderer";

    public ReverseScreenRenderer(Context mContext) {
        super(mContext, TAG);
    }

    @Override
    protected int getVertexShaderResId() {
        return R.raw.vertex_onscreen_shader;
    }

    @Override
    protected int getFragmentShaderResId() {
        return R.raw.reverse_texture_fragment_shader;
    }

}
