package bn.com.userdefinedtargetssample.SampleApplication.utils;
import java.io.IOException;
import java.io.InputStream;

import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.support.v7.widget.LinearLayoutCompat;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MySampleApplicationGLView extends GLSurfaceView
{
	public MySampleApplicationGLView(Context context, boolean translucent, int depth, int stencil) {
        super(context);
        this.setEGLContextClientVersion(3); //设置使用OPENGL ES 3.0
//        mRenderer = new SceneRenderer();	//创建场景渲染器
//        setRenderer(mRenderer);				//设置渲染器
//        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);//设置渲染模式为主动渲染

        if(translucent)
            this.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        // 选择一个刚好符合我们所需界面的EGLConfig配置
        // 需要用到ConfigChooser类 定义在底下
        // We need to choose an EGLConfig that matches the format of
        // our surface exactly. This is going to be done in our
        // custom config chooser. See ConfigChooser class definition
        // below.
//        setEGLConfigChooser(translucent ? new ConfigChooser(8, 8, 8, 8, depth,
//                stencil) : new ConfigChooser(5, 6, 5, 0, depth, stencil));
    }

//    // 配置选择器
//    // The config chooser.
//    private static class ConfigChooser implements
//            GLSurfaceView.EGLConfigChooser
//    {
//        public ConfigChooser(int r, int g, int b, int a, int depth, int stencil)
//        {
//            mRedSize = r;
//            mGreenSize = g;
//            mBlueSize = b;
//            mAlphaSize = a;
//            mDepthSize = depth;
//            mStencilSize = stencil;
//        }
//
//
//        private EGLConfig getMatchingConfig(EGL10 egl, EGLDisplay display,
//                                            int[] configAttribs)
//        {
//            // Get the number of minimally matching EGL configurations
//            int[] num_config = new int[1];
//            egl.eglChooseConfig(display, configAttribs, null, 0, num_config);
//
//            int numConfigs = num_config[0];
//            if (numConfigs <= 0)
//                throw new IllegalArgumentException("No matching EGL configs");
//
//            // Allocate then read the array of minimally matching EGL configs
//            EGLConfig[] configs = new EGLConfig[numConfigs];
//            egl.eglChooseConfig(display, configAttribs, configs, numConfigs,
//                    num_config);
//
//            // Now return the "best" one
//            return chooseConfig(egl, display, configs);
//        }
//
//
//        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display)
//        {
//            // This EGL config specification is used to specify 2.0
//            // rendering. We use a minimum size of 4 bits for
//            // red/green/blue, but will perform actual matching in
//            // chooseConfig() below.
//            final int EGL_OPENGL_ES2_BIT = 0x0004;
//            final int[] s_configAttribs_gl20 = { EGL10.EGL_RED_SIZE, 4,
//                    EGL10.EGL_GREEN_SIZE, 4, EGL10.EGL_BLUE_SIZE, 4,
//                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
//                    EGL10.EGL_NONE };
//
//            return getMatchingConfig(egl, display, s_configAttribs_gl20);
//        }
//
//
//        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display,
//                                      EGLConfig[] configs)
//        {
//            for (EGLConfig config : configs)
//            {
//                int d = findConfigAttrib(egl, display, config,
//                        EGL10.EGL_DEPTH_SIZE, 0);
//                int s = findConfigAttrib(egl, display, config,
//                        EGL10.EGL_STENCIL_SIZE, 0);
//
//                // We need at least mDepthSize and mStencilSize bits
//                if (d < mDepthSize || s < mStencilSize)
//                    continue;
//
//                // We want an *exact* match for red/green/blue/alpha
//                int r = findConfigAttrib(egl, display, config,
//                        EGL10.EGL_RED_SIZE, 0);
//                int g = findConfigAttrib(egl, display, config,
//                        EGL10.EGL_GREEN_SIZE, 0);
//                int b = findConfigAttrib(egl, display, config,
//                        EGL10.EGL_BLUE_SIZE, 0);
//                int a = findConfigAttrib(egl, display, config,
//                        EGL10.EGL_ALPHA_SIZE, 0);
//
//                if (r == mRedSize && g == mGreenSize && b == mBlueSize
//                        && a == mAlphaSize)
//                    return config;
//            }
//
//            return null;
//        }
//
//        private int findConfigAttrib(EGL10 egl, EGLDisplay display,
//                                     EGLConfig config, int attribute, int defaultValue)
//        {
//
//            if (egl.eglGetConfigAttrib(display, config, attribute, mValue))
//                return mValue[0];
//
//            return defaultValue;
//        }
//
//        // Subclasses can adjust these values:
//        protected int mRedSize;
//        protected int mGreenSize;
//        protected int mBlueSize;
//        protected int mAlphaSize;
//        protected int mDepthSize;
//        protected int mStencilSize;
//        private int[] mValue = new int[1];
//    }
}
