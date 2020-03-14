package com.wtz.libvideomaker.utils;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ShaderUtil {
    private static final String TAG = "ShaderUtil";

    public static String readRawText(Context context, int rawId) {
        BufferedReader reader = null;
        StringBuffer sb = new StringBuffer();
        try {
            InputStream inputStream = context.getResources().openRawResource(rawId);
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    public static int[] createAndLinkProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return new int[]{0, 0, 0};
        }
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            return new int[]{0, 0, 0};
        }
        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            LogUtils.d(TAG, "Shader Program " + program + " info:\n" + GLES20.glGetProgramInfoLog(program));

            int[] linsStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linsStatus, 0);
            if (linsStatus[0] != GLES20.GL_TRUE) {
                Log.d(TAG, "link program error");
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return new int[]{vertexShader, fragmentShader, program};
    }

    private static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            LogUtils.d(TAG, "Shader " + shader + " info:\n" + GLES20.glGetShaderInfoLog(shader));
            int[] compile = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compile, 0);
            if (compile[0] != GLES20.GL_TRUE) {
                LogUtils.e(TAG, "shader compile failed, type is " + shaderType);
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        } else {
            LogUtils.e(TAG, "shader create failed, type is " + shaderType);
        }
        return shader;
    }

}
