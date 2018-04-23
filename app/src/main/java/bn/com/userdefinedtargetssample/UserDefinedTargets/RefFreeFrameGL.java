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
import bn.com.userdefinedtargetssample.SampleApplication.utils.Texture;

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

        // 不是一个纹理，算是预先定义的纹理
        // Not a texture, count of predef textures
        public final static int TEXTURE_COUNT = 2; 
    }
    
    // OpenGL handles for the various shader related variables
    private int shaderProgramID; // The Shaders themselves
    private int vertexHandle; // Handle to the Vertex Array
    private int textureCoordHandle; // Handle to the Texture Coord Array
    private int colorHandle; // Handle to the color vector
    private int mvpMatrixHandle; // Handle to the product of the Projection
                                 // and Modelview Matrices
    String mVertexShader;//顶点着色器代码脚本
    String mFragmentShader;//片元着色器代码脚本

    // 投影矩阵和变换矩阵
    // Projection and Modelview Matrices
    Matrix44F projectionOrtho, modelview;
    
    // Color vector
    Vec4F color;
    
    // Texture names and textures
    String textureNames[] = {
            "UserDefinedTargets/viewfinder_crop_marks_portrait.png",
            "UserDefinedTargets/viewfinder_crop_marks_landscape.png" };
    Map<String,Integer> textures = new HashMap<>();
    
    // Vertices, texture coordinates and vector indices
    int NUM_FRAME_VERTEX_TOTAL = 4;
    int NUM_FRAME_INDEX = 1 + NUM_FRAME_VERTEX_TOTAL;
    
    float frameVertices_viewfinder[] = new float[NUM_FRAME_VERTEX_TOTAL * 3];
    float frameTexCoords[] = new float[NUM_FRAME_VERTEX_TOTAL * 2];
    short frameIndices[] = new short[NUM_FRAME_INDEX];

    // 是否为竖屏
    // Portrait/Landscape status detected in init()
    boolean isActivityPortrait;
    
//    String frameVertexShader = " \n" + "attribute vec4 vertexPosition; \n"
//        + "attribute vec2 vertexTexCoord; \n" + "\n"
//        + "varying vec2 texCoord; \n" + "\n"
//        + "uniform mat4 modelViewProjectionMatrix; \n" + "\n"
//        + "void main() \n" + "{ \n"
//        + "gl_Position = modelViewProjectionMatrix * vertexPosition; \n"
//        + "texCoord = vertexTexCoord; \n" + "} \n";
//
//    String frameFragmentShader = " \n" + "precision mediump float; \n" + "\n"
//        + "varying vec2 texCoord; \n" + "\n"
//        + "uniform sampler2D texSampler2D; \n" + "uniform vec4 keyColor; \n"
//        + "\n" + "void main() \n" + "{ \n"
//        + "vec4 texColor = texture2D(texSampler2D, texCoord); \n"
//        + "gl_FragColor = keyColor * texColor; \n" + "} \n" + "";
    
    
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
    
    
    // Quickly set the color for rendering
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
    
    // 设置缩放大小
    // Set the scale for the model view matrix
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
        // modelview matrix set to identity
        modelview = new Matrix44F();
        
        tempMatrix44Array[0] = tempMatrix44Array[5] = tempMatrix44Array[10] = tempMatrix44Array[15] = 1.0f;
        modelview.setData(tempMatrix44Array);

        // 颜色设为纯白
        // color is set to pure white
        float tempColor[] = { 1.0f, 1.0f, 1.0f, 0.6f };
        color.setData(tempColor);

        // 检测是否处于竖屏模式
        // Detect if we are in portrait mode or not
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
        
        // retrieves the screen size and other video background config values
        Renderer renderer = Renderer.getInstance();
        VideoBackgroundConfig vc = renderer.getVideoBackgroundConfig();
        
        // makes ortho matrix
        projectionOrtho = new Matrix44F();
        for (int i = 0; i < tempMatrix44Array.length; i++)
        {
            tempMatrix44Array[i] = 0;
        }
        
        int tempVC[] = vc.getSize().getData();
        
        // Calculate the Orthograpic projection matrix
        tempMatrix44Array[0] = 2.0f / (float) (tempVC[0]);
        tempMatrix44Array[5] = 2.0f / (float) (tempVC[1]);
        tempMatrix44Array[10] = 1.0f / (-10.0f);
        tempMatrix44Array[11] = -5.0f / (-10.0f);
        tempMatrix44Array[15] = 1.0f;
        
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
        
        // ** initialize the frame with the correct scale to fit the current
        // perspective matrix
        int cnt = 0, tCnt = 0;

        // 定义一个三角带的顶点和纹理坐标
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
        
        // we also set the indices programmatically
        cnt = 0;
        for (short i = 0; i < NUM_FRAME_VERTEX_TOTAL; i++)
            frameIndices[cnt++] = i; // one full loop
        frameIndices[cnt++] = 0; // close the loop
        
        // loads the texture
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
    
    
//    void getTextures()
//    {
//        for (int i = 0; i < TEXTURE_NAME.TEXTURE_COUNT; i++)
//            textures[i] = LoadUtil.initTexture(textureNames[i],mActivity.getResources());
//    }
    
    // 渲染取景器
    // / Renders the viewfinder
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
        
        // Set the vertex handle
        Buffer verticesBuffer = fillBuffer(frameVertices_viewfinder);
        GLES30.glVertexAttribPointer(vertexHandle, 3, GLES30.GL_FLOAT, false,
            0, verticesBuffer);
        
        // Set the Texture coordinate handle
        Buffer texCoordBuffer = fillBuffer(frameTexCoords);
        GLES30.glVertexAttribPointer(textureCoordHandle, 2, GLES30.GL_FLOAT,
            false, 0, texCoordBuffer);
        
        // Enable the Vertex and Texture arrays
        GLES30.glEnableVertexAttribArray(vertexHandle);
        GLES30.glEnableVertexAttribArray(textureCoordHandle);
        
        // Send the color value to the shader
        GLES30.glUniform4fv(colorHandle, 1, color.getData(), 0);
        
        // Depending on if we are in portrait or landsacape mode,
        // choose the proper viewfinder texture
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
        
        // Draw the viewfinder
        Buffer indicesBuffer = fillBuffer(frameIndices);
        GLES30.glDrawElements(GLES30.GL_TRIANGLE_STRIP, NUM_FRAME_INDEX,
                GLES30.GL_UNSIGNED_SHORT, indicesBuffer);

        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        
    }
    
}
