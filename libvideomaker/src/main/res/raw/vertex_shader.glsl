attribute vec4 a_Position;// 我们将要传入的每个顶点的位置信息
attribute vec2 a_TexCoordinate;// 我们将要传入的每个顶点的纹理坐标信息，vec2是一个包含两个元素的数组
varying vec2 v_TexCoordinate;// 用来传入到片段着色器
void main(){
    // 传入纹理坐标到片段着色器
    v_TexCoordinate = a_TexCoordinate;
    // OpenGL 会把 gl_Position 中存储的值作为当前顶点的最终位置
    gl_Position = a_Position;
}
