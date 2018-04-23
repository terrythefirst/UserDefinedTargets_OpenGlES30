/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package bn.com.userdefinedtargetssample.UserDefinedTargets;

import android.content.res.Configuration;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.Vec4F;
import com.vuforia.VideoBackgroundConfig;

import bn.com.userdefinedtargetssample.BnUtils.LoadUtil;
import bn.com.userdefinedtargetssample.BnUtils.ShaderUtil;
import bn.com.userdefinedtargetssample.SampleApplication.SampleApplicationSession;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;


public class RefFreeFrameGL
{
    
    private static final String LOGTAG = "RefFreeFrameGL";
    
    UserDefinedTargets mActivity;
    SampleApplicationSession vuforiaAppSession;
    
    private final class TEXTURE_NAME
    {
        // 竖屏模式的纹理编号
        // Viewfinder in portrait mode
        public final static int TEXTURE_VIEWFINDER_MARKS_PORTRAIT = 0;

        // 横屏模式的纹理编号
        // Viewfinder in landscape mode
        public final static int TEXTURE_VIEWFINDER_MARKS = 1;

        // 不是一个纹理，预先定义的纹理的总数
        // Not a texture, count of predef textures
        public final static int TEXTURE_COUNT = 2; 
    }

    private int shaderProgramID; // 着色器编号
    private int vertexHandle; // 顶点数据
    private int textureCoordHandle; // 纹理坐标数据
    private int colorHandle; // 颜色数据
    private int mvpMatrixHandle; // MVP矩阵
                                 // Handle to the product of the Projection
                                 // and Modelview Matrices
    String mVertexShader;//顶点着色器代码脚本
    String mFragmentShader;//片元着色器代码脚本

    // 投影矩阵和变换矩阵
    Matrix44F projectionOrtho, modelview;

    Vec4F color;
    
    // 纹理路径
    String textureNames[] = {
            "UserDefinedTargets/viewfinder_crop_marks_portrait.png",
            "UserDefinedTargets/viewfinder_crop_marks_landscape.png" };
    Map<String,Integer> textures = new HashMap<>();

    // 顶点，纹理坐标和向量的索引
    // Vertices, texture coordinates and vector indices
    int NUM_FRAME_VERTEX_TOTAL = 4;
    int NUM_FRAME_INDEX = 1 + NUM_FRAME_VERTEX_TOTAL;
    
    float frameVertices_viewfinder[] = new float[NUM_FRAME_VERTEX_TOTAL * 3];
    float frameTexCoords[] = new float[NUM_FRAME_VERTEX_TOTAL * 2];
    short frameIndices[] = new short[NUM_FRAME_INDEX];

    // 是否为竖屏
    boolean isActivityPortrait;

    public RefFreeFrameGL(bn.com.userdefinedtargetssample.UserDefinedTargets.UserDefinedTargets activity,
                          SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        shaderProgramID = 0;
        vertexHandle = 0;
        textureCoordHandle = 0;
        mvpMatrixHandle = 0;
        
        Log.d(LOGTAG, "RefFreeFrameGL Ctor");

        color = new Vec4F();
    }

    void setColor(float r, float g, float b, float a)
    {
        float[] tempColor = { r, g, b, a };
        color.setData(tempColor);
    }

    void setColor(float c[])
    {
        if (c.length != 4)
            throw new IllegalArgumentException(
                "Color length must be 4 floats length");
        
        color.setData(c);
    }
    
    // 设置模型矩阵的缩放大小 model view matrix
    void setModelViewScale(float scale)
    {
        float[] tempModelViewData = modelview.getData();
        tempModelViewData[14] = scale;
        modelview.setData(tempModelViewData);
    }
    
    
    boolean init(int screenWidth, int screenHeight)
    {
        float tempMatrix44Array[] = new float[16];

        // 设置为标准矩阵
        modelview = new Matrix44F();
        
        tempMatrix44Array[0] = tempMatrix44Array[5] = tempMatrix44Array[10] = tempMatrix44Array[15] = 1.0f;
        modelview.setData(tempMatrix44Array);

        // 颜色设为纯白
        float tempColor[] = { 1.0f, 1.0f, 1.0f, 0.6f };
        color.setData(tempColor);

        // 检测是否处于竖屏模式
        Configuration config = mActivity.getResources().getConfiguration();
        
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
            isActivityPortrait = false;
        else
            isActivityPortrait = true;

        //加载顶点着色器的脚本内容
        mVertexShader= ShaderUtil.loadFromAssetsFile("shader/frame_vertex.sh", mActivity.getResources());
        //加载片元着色器的脚本内容
        mFragmentShader=ShaderUtil.loadFromAssetsFile("shader/frame_frag.sh", mActivity.getResources());
        //基于顶点着色器与片元着色器创建程序
        shaderProgramID = ShaderUtil.createProgram(mVertexShader, mFragmentShader);
        if (shaderProgramID  == 0)
            return false;
        
        if ((vertexHandle = GLES30.glGetAttribLocation(shaderProgramID,
            "vertexPosition")) == -1)
            return false;
        if ((textureCoordHandle = GLES30.glGetAttribLocation(shaderProgramID,
            "vertexTexCoord")) == -1)
            return false;
        if ((mvpMatrixHandle = GLES30.glGetUniformLocation(shaderProgramID,
            "modelViewProjectionMatrix")) == -1)
            return false;
        if ((colorHandle = GLES30.glGetUniformLocation(shaderProgramID,
            "keyColor")) == -1)
            return false;

        // 获取屏幕尺寸和其他背景图像参数
        Renderer renderer = Renderer.getInstance();
        VideoBackgroundConfig vc = renderer.getVideoBackgroundConfig();
        
        // 正交矩阵
        projectionOrtho = new Matrix44F();
        for (int i = 0; i < tempMatrix44Array.length; i++)
        {
            tempMatrix44Array[i] = 0;
        }
        
        int tempVC[] = vc.getSize().getData();

        // 计算正交投影矩阵
        tempMatrix44Array[0] = 2.0f / (float) (tempVC[0]);
        tempMatrix44Array[5] = 2.0f / (float) (tempVC[1]);
        tempMatrix44Array[10] = 1.0f / (-10.0f);
        tempMatrix44Array[11] = -5.0f / (-10.0f);
        tempMatrix44Array[15] = 1.0f;

        // Viewfinder的尺寸基于正交矩阵，
        // 因为正交UI元素会使用得到的屏幕尺寸计算在操作系统中UI元素所占的比列.
        // 例如 ICS版本下的顶部状态栏
        // Viewfinder size based on the Ortho matrix because it is an Ortho UI
        // element use the ratio of the reported screen size and the calculated
        // screen size to account for on screen OS UI elements such as the 
        // action bar in ICS.
        float sizeH_viewfinder = ((float) screenWidth / tempVC[0])
            * (2.0f / tempMatrix44Array[0]);
        float sizeV_viewfinder = ((float) screenHeight / tempVC[1])
            * (2.0f / tempMatrix44Array[5]);
        
        Log.d(LOGTAG, "Viewfinder Size: " + sizeH_viewfinder + ", "
            + sizeV_viewfinder);

        // 初始化图像 适应当前透视矩阵
        // ** initialize the frame with the correct scale to fit the current
        // perspective matrix
        int cnt = 0, tCnt = 0;

        // 定义三角带的顶点和纹理坐标
        // Define the vertices and texture coords for a triangle strip that will
        // define the Quad where the viewfinder is rendered.
        //
        // 0---------1 | | | | 3---------2
        
        // / Vertex 0
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeH_viewfinder;
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeV_viewfinder;
        frameVertices_viewfinder[cnt++] = 0.0f;
        frameTexCoords[tCnt++] = 0.0f;
        frameTexCoords[tCnt++] = 1.0f;
        
        // / Vertex 1
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeH_viewfinder;
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeV_viewfinder;
        frameVertices_viewfinder[cnt++] = 0.0f;
        frameTexCoords[tCnt++] = 1.0f;
        frameTexCoords[tCnt++] = 1.0f;
        
        // / Vertex 2
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeH_viewfinder;
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeV_viewfinder;
        frameVertices_viewfinder[cnt++] = 0.0f;
        frameTexCoords[tCnt++] = 1.0f;
        frameTexCoords[tCnt++] = 0.0f;
        
        // / Vertex 3
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeH_viewfinder;
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeV_viewfinder;
        frameVertices_viewfinder[cnt++] = 0.0f;
        frameTexCoords[tCnt++] = 0.0f;
        frameTexCoords[tCnt++] = 0.0f;

        // 下标
        cnt = 0;
        for (short i = 0; i < NUM_FRAME_VERTEX_TOTAL; i++)
            frameIndices[cnt++] = i; // one full loop
        frameIndices[cnt++] = 0; // close the loop
        
        // 加载纹理
        for (String t : textureNames)
        {
            textures.put(t,LoadUtil.initTexture(t,mActivity.getResources()));
        }
        
        return true;
    }
    
    
    private Buffer fillBuffer(float[] array)
    {
        // 转换为float数组 因为OpenGL不能使用double，并且手工调整每一个输入值太耗时
        // Convert to floats because OpenGL doesnt work on doubles, and manually
        // casting each input value would take too much time.
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * array.length); // float占四个字节
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (double d : array)
            bb.putFloat((float) d);
        bb.rewind();
        
        return bb;
    }
    
    
    private Buffer fillBuffer(short[] array)
    {
        ByteBuffer bb = ByteBuffer.allocateDirect(2 * array.length); //short占两个字节
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (short s : array)
            bb.putShort(s);
        bb.rewind();
        
        return bb;
        
    }

    
    // 渲染取景器
    void renderViewfinder()
    {
        if (textures == null)
            return;
        
        // 设置GL参数
        GLES30.glEnable(GLES30.GL_BLEND);//开启混合
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);//设置混合参数
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);//关闭深度检测
        GLES30.glDisable(GLES30.GL_CULL_FACE);//关闭背面剔除 保留背面

        // 选择着色器程序
        GLES30.glUseProgram(shaderProgramID);

        // 将投影矩阵和变换矩阵相乘传给着色器
        // Calculate the Projection * ModelView matrix and pass to shader
        float[] mvp = new float[16];
        Matrix.multiplyMM(mvp, 0, projectionOrtho.getData(), 0,
            modelview.getData(), 0);
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvp, 0);

        //将顶点坐标数据传入渲染管线
        Buffer verticesBuffer = fillBuffer(frameVertices_viewfinder);
        GLES30.glVertexAttribPointer(vertexHandle, 3, GLES30.GL_FLOAT, false,
            0, verticesBuffer);

        //将纹理坐标数据传入渲染管线
        Buffer texCoordBuffer = fillBuffer(frameTexCoords);
        GLES30.glVertexAttribPointer(textureCoordHandle, 2, GLES30.GL_FLOAT,
            false, 0, texCoordBuffer);

        //启用顶点位置、纹理坐标数据
        GLES30.glEnableVertexAttribArray(vertexHandle);
        GLES30.glEnableVertexAttribArray(textureCoordHandle);
        
        // 颜色传入管线
        GLES30.glUniform4fv(colorHandle, 1, color.getData(), 0);

        // 根据横竖屏决定纹理
        if (isActivityPortrait
            && textures.get(textureNames[TEXTURE_NAME.TEXTURE_VIEWFINDER_MARKS_PORTRAIT]) != null)
        {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30
                .glBindTexture(
                        GLES30.GL_TEXTURE_2D,
                        textures.get(textureNames[TEXTURE_NAME.TEXTURE_VIEWFINDER_MARKS_PORTRAIT]));
        } else if (!isActivityPortrait
            && textures.get(textureNames[TEXTURE_NAME.TEXTURE_VIEWFINDER_MARKS]) != null)
        {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,
                    textures.get(textureNames[TEXTURE_NAME.TEXTURE_VIEWFINDER_MARKS]));
        }
        
        // 绘制viewfinder
        Buffer indicesBuffer = fillBuffer(frameIndices);
        GLES30.glDrawElements(GLES30.GL_TRIANGLE_STRIP, NUM_FRAME_INDEX,
                GLES30.GL_UNSIGNED_SHORT, indicesBuffer);

        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        
    }
    
}
