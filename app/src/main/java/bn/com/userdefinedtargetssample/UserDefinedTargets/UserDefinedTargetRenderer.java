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

// 渲染类
public class UserDefinedTargetRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl
{
    private static final String LOGTAG = "UDTRenderer";

    private SampleApplicationSession vuforiaAppSession;
    private SampleAppRenderer mSampleAppRenderer;

    private boolean mIsActive = false;

    static final float kObjectScale = 3.f;//缩放倍数
    
    private LoadedObjectVertexNormalTexture mTeapot;
    private int mTeapotTextureID;

    private UserDefinedTargets mActivity;
    
    
    public UserDefinedTargetRenderer(UserDefinedTargets activity,
                                     SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;

        // SampleAppRenderer用来封装RenderingPrimitives中的设置信息
        // AR/VR、立体模式
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 10f, 5000f);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        // Vuforia渲染初始化函数
        // 第一次使用或者在OpenGL ES coontext对象丢失后 调用
        vuforiaAppSession.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();
    }
    
    // 当画面改变尺寸时调用
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // 调用函数更新渲染参数
        mActivity.updateRendering();

        // 调用Vuforia中函数适应画面尺寸变化
        vuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives也需做出改变
        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        // 调用初始化渲染方法
        initRendering();
    }


    public void setActive(boolean active)
    {
        mIsActive = active;

        if(mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }


    // 绘制
    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;
        // 渲染
        mSampleAppRenderer.render();
    }

    // 渲染方法
    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {
        //渲染图像背景
        mSampleAppRenderer.renderVideoBackground();
        
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);

        // 根据当前状态渲染RefFree UI中元素
        mActivity.refFreeFrame.render();

        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
        {
            // 得到可追踪图源
            TrackableResult trackableResult = state.getTrackableResult(tIdx);
            Matrix44F modelViewMatrix_Vuforia = Tool
                .convertPose2GLMatrix(trackableResult.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();
            
            float[] modelViewProjection = new float[16];
            Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f, kObjectScale);
            Matrix.scaleM(modelViewMatrix, 0, kObjectScale, kObjectScale,
                kObjectScale);
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);

            mTeapot.drawSelf(mTeapotTextureID,modelViewProjection);//根据投影矩阵绘制

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

        // 设置清除颜色
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
            : 1.0f);
    }

    public void updateRenderingPrimitives()
    {
        mSampleAppRenderer.updateRenderingPrimitives();
    }
    
}
