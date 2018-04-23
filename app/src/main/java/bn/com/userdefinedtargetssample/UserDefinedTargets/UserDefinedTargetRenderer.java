/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package bn.com.userdefinedtargetssample.UserDefinedTargets;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Device;
import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.Vuforia;

import bn.com.userdefinedtargetssample.BnUtils.LoadUtil;
import bn.com.userdefinedtargetssample.BnUtils.LoadedObjectVertexNormalTexture;
import bn.com.userdefinedtargetssample.BnUtils.ShaderUtil;
import bn.com.userdefinedtargetssample.SampleApplication.SampleAppRenderer;
import bn.com.userdefinedtargetssample.SampleApplication.SampleAppRendererControl;
import bn.com.userdefinedtargetssample.SampleApplication.SampleApplicationSession;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


// The renderer class for the ImageTargetsBuilder sample. 
public class UserDefinedTargetRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl
{
    private static final String LOGTAG = "UDTRenderer";

    private SampleApplicationSession vuforiaAppSession;
    private SampleAppRenderer mSampleAppRenderer;

    private boolean mIsActive = false;
    
////    private Vector<Texture> mTextures;
//    private int shaderProgramID;
//    private int vertexHandle;
//    private int textureCoordHandle;
//    private int mvpMatrixHandle;
//    private int texSampler2DHandle;
//    String mVertexShader;//顶点着色器代码脚本
//    String mFragmentShader;//片元着色器代码脚本
    
    // Constants:
    static final float kObjectScale = 3.f;
    
    private LoadedObjectVertexNormalTexture mTeapot;
    private int mTeapotTextureID;
    
    // Reference to main activity
    private UserDefinedTargets mActivity;
    
    
    public UserDefinedTargetRenderer(UserDefinedTargets activity,
                                     SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 10f, 5000f);
    }
    
    
    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        // 第一次使用或者在OpenGL ES coontext对象丢失后调用Vuforia渲染初始化函数
        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();
    }
    
    // 当画面改变尺寸时调用
    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // 调用函数更新渲染参数
        // Call function to update rendering when render surface
        // parameters have changed:
        mActivity.updateRendering();

        // 调用Vuforia中函数适应画面尺寸变化
        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives也需做出改变
        // RenderingPrimitives to be updated when some rendering change is done
        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        // 调用初始化渲染方法
        // Call function to initialize rendering:
        initRendering();
    }


    public void setActive(boolean active)
    {
        mIsActive = active;

        if(mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }


    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;

        // 渲染
        // Call our function to render content from SampleAppRenderer class
        mSampleAppRenderer.render();
    }


    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {
        //渲染图像背景
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();
        
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);

        // 根据当前状态渲染RefFree UI中元素
        // Render the RefFree UI elements depending on the current state
        mActivity.refFreeFrame.render();
        
        // Did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
        {
            // Get the trackable:
            TrackableResult trackableResult = state.getTrackableResult(tIdx);
            Matrix44F modelViewMatrix_Vuforia = Tool
                .convertPose2GLMatrix(trackableResult.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();
            
            float[] modelViewProjection = new float[16];
            Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f, kObjectScale);
            Matrix.scaleM(modelViewMatrix, 0, kObjectScale, kObjectScale,
                kObjectScale);
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);
            
//            GLES30.glUseProgram(shaderProgramID);
//
//            GLES30.glVertexAttribPointer(vertexHandle, 3, GLES30.GL_FLOAT,
//                false, 0, mTeapot.getVertices());
//            GLES30.glVertexAttribPointer(textureCoordHandle, 2,
//                GLES30.GL_FLOAT, false, 0, mTeapot.getTexCoords());
//
//            GLES30.glEnableVertexAttribArray(vertexHandle);
//            GLES30.glEnableVertexAttribArray(textureCoordHandle);
//
//            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
//            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,
//                mTextures.get(0).mTextureID[0]);
//            GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
//                modelViewProjection, 0);
//            GLES30.glUniform1i(texSampler2DHandle, 0);
//            GLES30.glDrawElements(GLES30.GL_TRIANGLES,
//                mTeapot.getNumObjectIndex(), GLES30.GL_UNSIGNED_SHORT,
//                mTeapot.getIndices());
//
//            GLES30.glDisableVertexAttribArray(vertexHandle);
//            GLES30.glDisableVertexAttribArray(textureCoordHandle);

            mTeapot.drawSelf(mTeapotTextureID,modelViewProjection);

            ShaderUtil.checkGlError("UserDefinedTargets renderFrame");
        }
        
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        
        Renderer.getInstance().end();
    }
    
    
    private void initRendering()
    {
        Log.d(LOGTAG, "initRendering");
        
        mTeapot = LoadUtil.loadFromFile("ch_t.obj", mActivity.getResources(),mActivity.mGlView);
        mTeapotTextureID = LoadUtil.initTexture(bn.com.userdefinedtargetssample.R.drawable.ghxp,mActivity.getResources());

        
        // Define clear color
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
            : 1.0f);
        
        // Now generate the OpenGL texture objects and add settings
//        for (Texture t : mTextures)
//        {
//            GLES30.glGenTextures(1, t.mTextureID, 0);
//            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.mTextureID[0]);
//            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
//                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
//            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
//                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
//            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
//                t.mWidth, t.mHeight, 0, GLES30.GL_RGBA,
//                GLES30.GL_UNSIGNED_BYTE, t.mData);
//        }

//        //加载顶点着色器的脚本内容
//        mVertexShader= ShaderUtil.loadFromAssetsFile("shader/vertex.sh", mActivity.getResources());
//        //加载片元着色器的脚本内容
//        mFragmentShader=ShaderUtil.loadFromAssetsFile("shader/frag.sh", mActivity.getResources());
//        //基于顶点着色器与片元着色器创建程序
//        shaderProgramID =  ShaderUtil.createProgram(mVertexShader, mFragmentShader);
//
//        vertexHandle = GLES30.glGetAttribLocation(shaderProgramID,
//            "vertexPosition");
//        textureCoordHandle = GLES30.glGetAttribLocation(shaderProgramID,
//            "vertexTexCoord");
//        mvpMatrixHandle = GLES30.glGetUniformLocation(shaderProgramID,
//            "modelViewProjectionMatrix");
//        texSampler2DHandle = GLES30.glGetUniformLocation(shaderProgramID,
//            "texSampler2D");
    }


    public void updateRenderingPrimitives()
    {
        mSampleAppRenderer.updateRenderingPrimitives();
    }
    
    
//    public void setTextures(Vector<Texture> textures)
//    {
//        mTextures = textures;
//
//    }
    
}
