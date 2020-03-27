#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 v_TexCoordinate; // 从顶点着色器插入的纹理坐标
uniform samplerExternalOES u_Texture;  // 用来传入 Camera 预览内容的句柄
void main(){
    // OpenGL 会使用 gl_FragColor 的值作为当前片段的最终颜色
    gl_FragColor = texture2D(u_Texture, v_TexCoordinate);
}
