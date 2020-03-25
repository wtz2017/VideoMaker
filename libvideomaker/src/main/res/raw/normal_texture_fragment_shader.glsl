// 精度 precision 可以选择 lowp、mediump和 highp
// 顶点着色器由于位置的精确度，一般默认为高精度，所以不需要再去怎么修改；
// 而片段着色器则采用中等精度，主要是考虑到性能和兼容性。
precision mediump float;
varying vec2 v_TexCoordinate; // 从顶点着色器插入的纹理坐标
uniform sampler2D u_Texture;  // 用来传入纹理内容的句柄
void main(){
    // OpenGL 会使用 gl_FragColor 的值作为当前片段的最终颜色
    gl_FragColor = texture2D(u_Texture, v_TexCoordinate);// 使用 texture2D 得到在当前纹理坐标的纹理值
}
