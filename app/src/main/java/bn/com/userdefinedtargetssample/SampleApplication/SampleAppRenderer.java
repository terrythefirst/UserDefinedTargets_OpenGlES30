/*===============================================================================
Copyright (c) 2016-2017 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package bn.com.userdefinedtargetssample.SampleApplication;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Point;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.COORDINATE_SYSTEM_TYPE;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.GLTextureUnit;
import com.vuforia.Matrix34F;
import com.vuforia.Mesh;
import com.vuforia.Renderer;
import com.vuforia.RenderingPrimitives;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackerManager;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.VIEW;
import com.vuforia.Vec2F;
import com.vuforia.Vec2I;
import com.vuforia.Vec4I;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.ViewList;

import bn.com.userdefinedtargetssample.BnUtils.ShaderUtil;

public class SampleAppRenderer {

    private static final String LOGTAG = "SampleAppRenderer";

    private RenderingPrimitives mRenderingPrimitives = null;
    private SampleAppRendererControl mRenderingInterface = null;
    private Activity mActivity = null;

    private Renderer mRenderer = null;
    private int currentView = VIEW.VIEW_SINGULAR;
    private float mNearPlane = -1.0f;
    private float mFarPlane = -1.0f;

    private GLTextureUnit videoBackgroundTex = null;

    // 渲染AR模式背景的着色器
    // Shader user to render the video background on AR mode
    private int vbShaderProgramID = 0;
    private int vbTexSampler2DHandle = 0;
    private int vbVertexHandle = 0;
    private int vbTexCoordHandle = 0;
    private int vbProjectionMatrixHandle = 0;
    String mVertexShader;//顶点着色器代码脚本
    String mFragmentShader;//片元着色器代码脚本

    // 显示尺寸
    // Display size of the device:
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    // 是否为竖屏模式
    // Stores orientation
    private boolean mIsPortrait = false;

    public SampleAppRenderer(SampleAppRendererControl renderingInterface, Activity activity, int deviceMode,
                             boolean stereo, float nearPlane, float farPlane)
    {
        mActivity = activity;

        mRenderingInterface = renderingInterface;
        mRenderer = Renderer.getInstance();

        if(farPlane < nearPlane)
        {
            Log.e(LOGTAG, "Far plane should be greater than near plane");
            throw new IllegalArgumentException();
        }

        setNearFarPlanes(nearPlane, farPlane);

        if(deviceMode != Device.MODE.MODE_AR && deviceMode != Device.MODE.MODE_VR)
        {
            Log.e(LOGTAG, "Device mode should be Device.MODE.MODE_AR or Device.MODE.MODE_VR");
            throw new IllegalArgumentException();
        }

        Device device = Device.getInstance();
        device.setViewerActive(stereo); // 根据是否使用viewer，立体模式 初始化渲染参数 Indicates if the app will be using a viewer, stereo mode and initializes the rendering primitives
        device.setMode(deviceMode); // 选择AR模式或者VR模式 Select if we will be in AR or VR mode
    }

    public void onSurfaceCreated()
    {
        initRendering();
    }

    public void onConfigurationChanged(boolean isARActive)
    {
        updateActivityOrientation();
        storeScreenDimensions();

        if(isARActive)
            configureVideoBackground();

        updateRenderingPrimitives();
    }


    public synchronized void updateRenderingPrimitives()
    {
        mRenderingPrimitives = Device.getInstance().getRenderingPrimitives();
    }


    void initRendering()
    {
        //加载顶点着色器的脚本内容
        mVertexShader= ShaderUtil.loadFromAssetsFile("shader/vb_vertex.sh", mActivity.getResources());
        //加载片元着色器的脚本内容
        mFragmentShader=ShaderUtil.loadFromAssetsFile("shader/vb_frag.sh", mActivity.getResources());
        //基于顶点着色器与片元着色器创建程序
        vbShaderProgramID = ShaderUtil.createProgram(mVertexShader, mFragmentShader);
//        vbShaderProgramID = SampleUtils.createProgramFromShaderSrc(VideoBackgroundShader.VB_VERTEX_SHADER,
//                VideoBackgroundShader.VB_FRAGMENT_SHADER);

        // 配置背景渲染参数
        // Rendering configuration for video background
        if (vbShaderProgramID > 0)
        {
            vbTexSampler2DHandle = GLES30.glGetUniformLocation(vbShaderProgramID, "texSampler2D");
            // 取得投影矩阵的引用
            // Retrieve handler for projection matrix shader uniform variable:
            vbProjectionMatrixHandle = GLES30.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");

            vbVertexHandle = GLES30.glGetAttribLocation(vbShaderProgramID, "vertexPosition");
            vbTexCoordHandle = GLES30.glGetAttribLocation(vbShaderProgramID, "vertexTexCoord");
            vbProjectionMatrixHandle = GLES30.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");
            vbTexSampler2DHandle = GLES30.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

        }

        videoBackgroundTex = new GLTextureUnit();
    }

    // 主渲染方法
    // 为渲染作准备，设置AR虚拟物体的3D变换并调用具体的渲染方法
    // Main rendering method
    // The method setup state for rendering, setup 3D transformations required for AR augmentation
    // and call any specific rendering method
    public void render()
    {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        State state;

        // 得到当前渲染模式
        // Get our current state
        state = TrackerManager.getInstance().getStateUpdater().updateState();
        mRenderer.begin(state);

        // 需要检测背景反射是否开启然后调整卷绕方向
        // 如果背景反射开启了，表面当前矩阵已经被反射了
        // 因此标准逆时针卷绕会导致内外颠倒的模型
        // We must detect if background reflection is active and adjust the
        // culling direction.
        // If the reflection is active, this means the post matrix has been
        // reflected as well,
        // therefore standard counter clockwise face culling will result in
        // "inside out" models.
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES30.glFrontFace(GLES30.GL_CW);// 前置摄像头Front camera
        else
            GLES30.glFrontFace(GLES30.GL_CCW);// 后置摄像头Back camera

        // 获取正在操作的图像的列表.当单个模式时只会有一个view，三维模式时会有 左、右、后处理（postprocess） 三个view
        // We get a list of views which depend on the mode we are working on, for mono we have
        // only one view, in stereo we have three: left, right and postprocess
        ViewList viewList = mRenderingPrimitives.getRenderingViews();

        // 遍历图像列表
        for (int v = 0; v < viewList.getNumViews(); v++)
        {
            // 得到view的id
            int viewID = viewList.getView(v);

            Vec4I viewport;
            // 得到该view的 视口（viewport）
            viewport = mRenderingPrimitives.getViewport(viewID);

            // 设该视口为当前
            // Set viewport for current view
            GLES30.glViewport(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // 设置剪裁
            // Set scissor
            GLES30.glScissor(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // 得到投影矩阵
            // Get projection matrix for the current view. COORDINATE_SYSTEM_CAMERA used for AR and
            // COORDINATE_SYSTEM_WORLD for VR
            Matrix34F projMatrix = mRenderingPrimitives.getProjectionMatrix(viewID, COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA,
                                                                            state.getCameraCalibration());

            // 为近平面和远平面创建矩阵
            // Create GL matrix setting up the near and far planes
            float rawProjectionMatrixGL[] = Tool.convertPerspectiveProjection2GLMatrix(
                    projMatrix,
                    mNearPlane,
                    mFarPlane)
                    .getData();


            // 对投影矩阵作出调整使眼睛更舒服
            // Apply the appropriate eye adjustment to the raw projection matrix, and assign to the global variable
            float eyeAdjustmentGL[] = Tool.convert2GLMatrix(mRenderingPrimitives
                    .getEyeDisplayAdjustmentMatrix(viewID)).getData();

            float projectionMatrix[] = new float[16];
            // 对投影矩阵作出调整
            // Apply the adjustment to the projection matrix
            Matrix.multiplyMM(projectionMatrix, 0, rawProjectionMatrixGL, 0, eyeAdjustmentGL, 0);
            //MatrixState.setProjMatrix(projectionMatrix);

            currentView = viewID;

            // 调用 renderFrame渲染
            // 只会在单个模式以及左右模式中调用，在后操作（postprocess）模式中不会渲染
            // Call renderFrame from the app renderer class which implements SampleAppRendererControl
            // This will be called for MONO, LEFT and RIGHT views, POSTPROCESS will not render the
            // frame
            if(currentView != VIEW.VIEW_POSTPROCESS)
                mRenderingInterface.renderFrame(state, projectionMatrix);
        }

        mRenderer.end();
    }

    public void setNearFarPlanes(float near, float far)
    {
        mNearPlane = near;
        mFarPlane = far;
    }

    public void renderVideoBackground()
    {
        if(currentView == VIEW.VIEW_POSTPROCESS)
            return;

        int vbVideoTextureUnit = 0;
        // 从Vuforia中绑定背景的纹理ID
        // Bind the video bg texture and get the Texture ID from Vuforia
        videoBackgroundTex.setTextureUnit(vbVideoTextureUnit);
        if (!mRenderer.updateVideoBackgroundTexture(videoBackgroundTex))
        {
            Log.e(LOGTAG, "Unable to update video background texture");
            return;
        }

        float[] vbProjectionMatrix = Tool.convert2GLMatrix(
                mRenderingPrimitives.getVideoBackgroundProjectionMatrix(currentView, COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA)).getData();

        // 将屏幕缩放到图像透视眼镜，缩放背景和增加的虚拟物体和现实世界匹配
        // 不能应用在光学透视设备上，因为没有视频背景
        // 校准保证增加的虚拟物体融入现实世界
        // Apply the scene scale on video see-through eyewear, to scale the video background and augmentation
        // so that the display lines up with the real world
        // This should not be applied on optical see-through devices, as there is no video background,
        // and the calibration ensures that the augmentation matches the real world
        if (Device.getInstance().isViewerActive()) {
            float sceneScaleFactor = (float)getSceneScaleFactor();
            Matrix.scaleM(vbProjectionMatrix, 0, sceneScaleFactor, sceneScaleFactor, 1.0f);
        }

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST);

        Mesh vbMesh = mRenderingPrimitives.getVideoBackgroundMesh(currentView);
        // 载入着色器并上传顶点坐标数据
        // Load the shader and upload the vertex/texcoord/index data
        GLES30.glUseProgram(vbShaderProgramID);
        GLES30.glVertexAttribPointer(vbVertexHandle, 3, GLES30.GL_FLOAT, false, 0, vbMesh.getPositions().asFloatBuffer());
        GLES30.glVertexAttribPointer(vbTexCoordHandle, 2, GLES30.GL_FLOAT, false, 0, vbMesh.getUVs().asFloatBuffer());

        GLES30.glUniform1i(vbTexSampler2DHandle, vbVideoTextureUnit);

        // 渲染背景
        // Render the video background with the custom shader
        // First, we enable the vertex arrays
        GLES30.glEnableVertexAttribArray(vbVertexHandle);
        GLES30.glEnableVertexAttribArray(vbTexCoordHandle);

        // 传入投影矩阵
        // Pass the projection matrix to OpenGL
        GLES30.glUniformMatrix4fv(vbProjectionMatrixHandle, 1, false, vbProjectionMatrix, 0);

        // 绘制
        // Then, we issue the render call
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, vbMesh.getNumTriangles() * 3, GLES30.GL_UNSIGNED_SHORT,
                vbMesh.getTriangles().asShortBuffer());

        // 关闭顶点数组
        // Finally, we disable the vertex arrays
        GLES30.glDisableVertexAttribArray(vbVertexHandle);
        GLES30.glDisableVertexAttribArray(vbTexCoordHandle);

        ShaderUtil.checkGlError("Rendering of the video background failed");
    }


    static final float VIRTUAL_FOV_Y_DEGS = 85.0f;
    static final float M_PI = 3.14159f;

    double getSceneScaleFactor()
    {
        // 得到物理世界相机y轴的view
        // Get the y-dimension of the physical camera field of view
        Vec2F fovVector = CameraDevice.getInstance().getCameraCalibration().getFieldOfViewRads();
        float cameraFovYRads = fovVector.getData()[1];

        // 得到虚拟世界相机y轴的view
        // Get the y-dimension of the virtual camera field of view
        float virtualFovYRads = VIRTUAL_FOV_Y_DEGS * M_PI / 180;

        // scene-scale factor表示图像背景被投影到同一平面时视口的比例.
        // 计算时：
        //        d 为摄像机和平面的距离
        //        h 为投影图像的高
        // 在此平面可计算为 tan(fov/2) = h/2d =》 2d = h/tan(fov/2)
        // 因为d在两个摄像机中时相等的，联立两个摄像机产生的方程
        //           hPhysical/tan(fovPhysical/2) = hVirtual/tan(fovVirtual/2)
        //              =》hPhysical/hVirtual = tan(fovPhysical/2)/tan(fovVirtual/2)
        //      即为 scene-scale factor

        // The scene-scale factor represents the proportion of the viewport that is filled by
        // the video background when projected onto the same plane.
        // In order to calculate this, let 'd' be the distance between the cameras and the plane.
        // The height of the projected image 'h' on this plane can then be calculated:
        //   tan(fov/2) = h/2d
        // which rearranges to:
        //   2d = h/tan(fov/2)
        // Since 'd' is the same for both cameras, we can combine the equations for the two cameras:
        //   hPhysical/tan(fovPhysical/2) = hVirtual/tan(fovVirtual/2)
        // Which rearranges to:
        //   hPhysical/hVirtual = tan(fovPhysical/2)/tan(fovVirtual/2)
        // ... which is the scene-scale factor
        return Math.tan(cameraFovYRads / 2) / Math.tan(virtualFovYRads / 2);
    }

    // 设置图像的模式和摄像机的图像
    // Configures the video mode and sets offsets for the camera's image
    public void configureVideoBackground()
    {
        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(CameraDevice.MODE.MODE_DEFAULT);

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setEnabled(true);
        config.setPosition(new Vec2I(0, 0));

        int xSize = 0, ySize = 0;
        // 保持高宽比确保渲染正确.
        // 如果时竖屏模式，保持高度不变，缩放宽度;
        // 横屏模式时则相反,保持宽度不变，检测所选的值是否能填充整个屏幕，如果不能，倒置选择（保持高度而改变宽度）
        //
        // We keep the aspect ratio to keep the video correctly rendered. If it is portrait we
        // preserve the height and scale width and vice versa if it is landscape, we preserve
        // the width and we check if the selected values fill the screen, otherwise we invert
        // the selection
        if (mIsPortrait)
        {
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm
                    .getWidth()));
            ySize = mScreenHeight;

            if (xSize < mScreenWidth)
            {
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm
                        .getHeight()));
            }
        } else
        {
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm
                    .getWidth()));

            if (ySize < mScreenHeight)
            {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm
                        .getHeight()));
                ySize = mScreenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        Renderer.getInstance().setVideoBackgroundConfig(config);

    }


    // 储存屏幕尺寸
    // Stores screen dimensions
    private void storeScreenDimensions()
    {
        //查询显示尺寸
        // Query display dimensions:
        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getRealSize(size);
        mScreenWidth = size.x;
        mScreenHeight = size.y;
    }


    // 根据当前图源的配置信息储存屏幕方向
    // Stores the orientation depending on the current resources configuration
    private void updateActivityOrientation()
    {
        Configuration config = mActivity.getResources().getConfiguration();

        switch (config.orientation)
        {
            case Configuration.ORIENTATION_PORTRAIT://竖屏
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE://横屏
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }

        Log.i(LOGTAG, "Activity is in "
                + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }
}
