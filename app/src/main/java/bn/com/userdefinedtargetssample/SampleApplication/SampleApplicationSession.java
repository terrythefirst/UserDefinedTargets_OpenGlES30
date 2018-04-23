/*===============================================================================
Copyright (c) 2016-2017 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/


package bn.com.userdefinedtargetssample.SampleApplication;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.INIT_ERRORCODE;
import com.vuforia.INIT_FLAGS;
import com.vuforia.State;
import com.vuforia.Vuforia;
import com.vuforia.Vuforia.UpdateCallbackInterface;

import bn.com.userdefinedtargetssample.R;


public class SampleApplicationSession implements UpdateCallbackInterface
{
    
    private static final String LOGTAG = "SampleAppSession";
    
    // 当前Activity的引用
    private Activity mActivity;
    private SampleApplicationControl mSessionControl;
    
    // 标识
    private boolean mStarted = false;
    private boolean mCameraRunning = false;
    
    // 异步初始化Vuforia SDK:
    private InitVuforiaTask mInitVuforiaTask;
    private InitTrackerTask mInitTrackerTask;
    private LoadTrackerTask mLoadTrackerTask;
    private StartVuforiaTask mStartVuforiaTask;
    private ResumeVuforiaTask mResumeVuforiaTask;
    
    // 一个用来同步 初始化Vuforia，载入dataset和 生命周期事件onDestroy() 的锁。
    // 如果正在载入datadet时，application（应用）被destroyed，那么会先完成载入再关闭Vuforia
    private final Object mLifecycleLock = new Object();
    
    // Vuforia初始化标识:
    private int mVuforiaFlags = 0;
    
    // 摄像头配置信息：用来resume
    private int mCamera = CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT;
    

    public SampleApplicationSession(SampleApplicationControl sessionControl)
    {
        mSessionControl = sessionControl;
    }
    
    
    // 初始化 Vuforia ，设置偏好（preferences）.
    public void initAR(Activity activity, int screenOrientation)
    {
        //AR初始化用户自定义异常
        SampleApplicationException vuforiaException = null;
        mActivity = activity;

        //Build.VERSION_CODES.FROYO =>2.2 sdk版本大于2.2
        if ((screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
            && (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO))
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        
        //这里用OrientationChangeListener来捕捉所有屏幕方向(Orientation)改变.  Android
        //安卓在180度方向改变时是不会回调一个 Activity中的onConfigurationChanged()
        //也就是说左右改变不会有反应，而Vuforia需要做出响应，因为需要更新矩阵变换
        // Use an OrientationChangeListener here to capture all orientation changes.  Android
        // will not send an Activity.onConfigurationChanged() callback on a 180 degree rotation,
        // ie: Left Landscape to Right Landscape.  Vuforia needs to react to this change and the
        // SampleApplicationSession needs to update the Projection Matrix.
        OrientationEventListener orientationEventListener = new OrientationEventListener(mActivity) {
            @Override
            public void onOrientationChanged(int i) {
                int activityRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                if(mLastRotation != activityRotation)
                {
                    mLastRotation = activityRotation;
                }
            }

            int mLastRotation = -1;
        };
        //可以开启角度检测则开启
        if(orientationEventListener.canDetectOrientation())
            orientationEventListener.enable();

        // 适配屏幕方向 Apply screen orientation
        mActivity.setRequestedOrientation(screenOrientation);
        
        // 保持常亮
        mActivity.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        mVuforiaFlags = INIT_FLAGS.GL_30;

        //异步初始化Vuforia SDK，避免阻塞 主线程(UI线程)
        //注意：必须由UI线程触发，并且只能执行一次
        // Initialize Vuforia SDK asynchronously to avoid blocking the
        // main (UI) thread.
        //
        // NOTE: This task instance must be created and invoked on the
        // UI thread and it can be executed only once!
        if (mInitVuforiaTask != null)
        {
            String logMessage = "Cannot initialize SDK twice";
            vuforiaException = new SampleApplicationException(
                SampleApplicationException.VUFORIA_ALREADY_INITIALIZATED,
                logMessage);
            Log.e(LOGTAG, logMessage);
        }
        
        if (vuforiaException == null)
        {
            try {
                //Vuforia初始化任务
                mInitVuforiaTask = new InitVuforiaTask();
                mInitVuforiaTask.execute();
            }
            catch (Exception e)
            {
                String logMessage = "Initializing Vuforia SDK failed";
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
                Log.e(LOGTAG, logMessage);
            }
        }

        if (vuforiaException != null)
        {
            // 初始化有问题时停止初始化
            // Send Vuforia Exception to the application and call initDone
            // to stop initialization process
            mSessionControl.onInitARDone(vuforiaException);
        }
    }
    
    
    // 启动Vuforia， 初始化并启动摄像头和trackers（追踪器）
    // Starts Vuforia, initialize and starts the camera and start the trackers
    private void startCameraAndTrackers(int camera) throws SampleApplicationException
    {
        String error;
        if(mCameraRunning)
        {
        	error = "Camera already running, unable to open again";
        	Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        mCamera = camera;
        if (!CameraDevice.getInstance().init(camera))
        {
            error = "Unable to open camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
               
        if (!CameraDevice.getInstance().selectVideoMode(
            CameraDevice.MODE.MODE_DEFAULT))
        {
            error = "Unable to set video mode";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        if (!CameraDevice.getInstance().start())
        {
            error = "Unable to start camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        mSessionControl.doStartTrackers();
        
        mCameraRunning = true;
    }

    public void startAR(int camera)
    {
        mCamera = camera;
        SampleApplicationException vuforiaException = null;

        try {
            mStartVuforiaTask = new StartVuforiaTask();
            mStartVuforiaTask.execute();
        }
        catch (Exception e)
        {
            String logMessage = "Starting Vuforia failed";
            vuforiaException = new SampleApplicationException(
                    SampleApplicationException.CAMERA_INITIALIZATION_FAILURE,
                    logMessage);
            Log.e(LOGTAG, logMessage);
        }

        if (vuforiaException != null)
        {
            // 初始化有问题时停止初始化
            // Send Vuforia Exception to the application and call initDone
            // to stop initialization process
            mSessionControl.onInitARDone(vuforiaException);
        }
    }

    // 停止正在进行的任何初始化进程，关闭vuforia
    // Stops any ongoing initialization, stops Vuforia
    public void stopAR() throws SampleApplicationException
    {
        // 取消潜在运行的任务
        // Cancel potentially running tasks
        if (mInitVuforiaTask != null
            && mInitVuforiaTask.getStatus() != InitVuforiaTask.Status.FINISHED)
        {
            mInitVuforiaTask.cancel(true);
            mInitVuforiaTask = null;
        }
        
        if (mLoadTrackerTask != null
            && mLoadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
        {
            mLoadTrackerTask.cancel(true);
            mLoadTrackerTask = null;
        }
        
        mInitVuforiaTask = null;
        mLoadTrackerTask = null;
        
        mStarted = false;
        
        stopCamera();
        
        // Ensure that all asynchronous operations to initialize Vuforia
        // and loading the tracker datasets do not overlap:
        synchronized (mLifecycleLock)
        {
            
            boolean unloadTrackersResult;
            boolean deinitTrackersResult;
            
            // Destroy the tracking data set:
            unloadTrackersResult = mSessionControl.doUnloadTrackersData();
            
            // Deinitialize the trackers:
            deinitTrackersResult = mSessionControl.doDeinitTrackers();
            
            // Deinitialize Vuforia SDK:
            Vuforia.deinit();
            
            if (!unloadTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.UNLOADING_TRACKERS_FAILURE,
                    "Failed to unload trackers\' data");
            
            if (!deinitTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.TRACKERS_DEINITIALIZATION_FAILURE,
                    "Failed to deinitialize trackers");
            
        }
    }
    

    // Resumes Vuforia, restarts the trackers and the camera
    private void resumeAR()
    {
        SampleApplicationException vuforiaException = null;

        try {
            mResumeVuforiaTask = new ResumeVuforiaTask();
            mResumeVuforiaTask.execute();
        }
        catch (Exception e)
        {
            String logMessage = "Resuming Vuforia failed";
            vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
            Log.e(LOGTAG, logMessage);
        }

        if (vuforiaException != null)
        {
            // 初始化有问题时停止初始化
            // Send Vuforia Exception to the application and call initDone
            // to stop initialization process
            mSessionControl.onInitARDone(vuforiaException);
        }
    }


    // Pauses Vuforia and stops the camera
    public void pauseAR() throws SampleApplicationException
    {
        if (mStarted)
        {
            stopCamera();
        }
        
        Vuforia.onPause();
    }
    
    // 每个周期回调
    // Callback called every cycle
    @Override
    public void Vuforia_onUpdate(State s)
    {
        mSessionControl.onVuforiaUpdate(s);
    }
    
    // 管理配置变化
    // Manages the configuration changes
    public void onConfigurationChanged()
    {
        if (mStarted)
        {
            Device.getInstance().setConfigurationChanged();
        }
    }
    
    // 恢复时调用
    // Methods to be called to handle lifecycle
    public void onResume()
    {
        if (mResumeVuforiaTask == null
                || mResumeVuforiaTask.getStatus() == ResumeVuforiaTask.Status.FINISHED)
        {
            // onResume() will sometimes be called twice depending on the screen lock mode
            // This will prevent redundant AsyncTasks from being executed
            resumeAR();
        }
    }
    
    //暂停时调用
    public void onPause()
    {
        Vuforia.onPause();
    }
    
    
    public void onSurfaceChanged(int width, int height)
    {
        Vuforia.onSurfaceChanged(width, height);
    }
    
    
    public void onSurfaceCreated()
    {
        Vuforia.onSurfaceCreated();
    }

    // 异步初始化Vuforia
    // An async task to initialize Vuforia asynchronously.
    private class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean>
    {
        // 初始化为无效值
        // Initialize with invalid value:
        private int mProgressValue = -1;
        
        
        protected Boolean doInBackground(Void... params)
        {
            //防止onDestroyed方法和初始化同时运行
            // Prevent the onDestroy() method to overlap with initialization:
            synchronized (mLifecycleLock)
            {
                //填写Vuforia Key
                String keyTemp= "AYZ/b6b/////AAABmXCwQAysd01xszlRSA6+ktYDm97t1lt/bk72UtH7JKlURVkcV7puTbiOOjmBuQeIk28vbFh2lFpVAG0fEBfH4Zom7Lf700/CVTNnkIveARR1+a+6gD/HOeGcAAK6lTPkVejKnqCJD2NSnZgGgYnmcEoa3t2HJ6a2AkLuzoHfFer9uAGLRyUdrs+SOp5YXbm4DGtPwQv2mBy9FfbwRu/oMgfhm4PHrJC01zFARZeH6mrOZBuntozuy3YsahcCeoEpGGboNX2hpHUwomdZJaQMt/tZu8KQAWYYkq8SA5+Ot7dSfPKqCWOvd/sq6gRrvEzHN6hSEmP3uZeSnFktKz3oLksQgakIkDxkq/W4+JpbSt9e";
                Vuforia.setInitParameters(mActivity, mVuforiaFlags, keyTemp);
                
                do
                {
                    // 当初始化完成一步前Vuforia.init()会一直阻塞，通过百分比形式给出进度，然后继续下一步
                    // 当Vuforia.init()返回-1时表示有错误
                    // 当进度百分比为100%时初始化完成
                    // Vuforia.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%).
                    // If Vuforia.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    mProgressValue = Vuforia.init();

                    // 展示进度
                    // Publish the progress value:
                    publishProgress(mProgressValue);

                    // 同时我们要通过调用 AsyncTask.cancel(true)来确定是否任务被取消
                    // 如果被取消则停止线程.因为AsyncTask 会一直运行到结束无论开始时
                    // component的状态.
                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true)).
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                } while (!isCancelled() && mProgressValue >= 0
                    && mProgressValue < 100);

                return (mProgressValue > 0);
            }
        }
        
        
        protected void onProgressUpdate(Integer... values)
        {
            // 进度条更新 这里没有实现
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }
        
        
        protected void onPostExecute(Boolean result)
        {
            // 完成Vuforia初始化.继续下一个应用
            // Done initializing Vuforia, proceed to next application
            // initialization status:

            Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));
            
            SampleApplicationException vuforiaException = null;
            
            if (result)
            {
                try {
                    mInitTrackerTask = new InitTrackerTask();
                    mInitTrackerTask.execute();
                }
                catch (Exception e)
                {
                    String logMessage = "Failed to initialize tracker.";
                    vuforiaException = new SampleApplicationException(
                            SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                            logMessage);
                    Log.e(LOGTAG, logMessage);
                }
            } else
            {
                String logMessage;
                
                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                logMessage = getInitializationErrorString(mProgressValue);
                
                // Log error:
                Log.e(LOGTAG, "InitVuforiaTask.onPostExecute: " + logMessage
                    + " Exiting.");

                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
            }

            if (vuforiaException != null)
            {
                // Send Vuforia Exception to the application and call initDone
                // to stop initialization process
                mSessionControl.onInitARDone(vuforiaException);
            }
        }
    }

    // An async task to resume Vuforia asynchronously
    private class ResumeVuforiaTask extends AsyncTask<Void, Void, Void>
    {
        protected Void doInBackground(Void... params)
        {
            // Prevent the concurrent lifecycle operations:
            synchronized (mLifecycleLock)
            {
                Vuforia.onResume();
            }

            return null;
        }

        protected void onPostExecute(Void result)
        {
            Log.d(LOGTAG, "ResumeVuforiaTask.onPostExecute");

            // We may start the camera only if the Vuforia SDK has already been initialized
            if (mStarted && !mCameraRunning)
            {
                startAR(mCamera);
                mSessionControl.onVuforiaResumed();
            }
        }
    }

    // An async task to initialize trackers asynchronously
    private class InitTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {
        protected Boolean doInBackground(Void... params)
        {
            synchronized (mLifecycleLock)
            {
                // Load the tracker data set:
                return mSessionControl.doInitTrackers();
            }
        }

        protected void onPostExecute(Boolean result)
        {

            SampleApplicationException vuforiaException = null;
            Log.d(LOGTAG, "InitTrackerTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));

            if (result)
            {
                try {
                    mLoadTrackerTask = new LoadTrackerTask();
                    mLoadTrackerTask.execute();
                }
                catch (Exception e)
                {
                    String logMessage = "Failed to load tracker data.";
                    Log.e(LOGTAG, logMessage);

                    vuforiaException = new SampleApplicationException(
                            SampleApplicationException.LOADING_TRACKERS_FAILURE,
                            logMessage);
                }
            }
            else
            {
                String logMessage = "Failed to load tracker data.";
                Log.e(LOGTAG, logMessage);

                // Error loading dataset
                vuforiaException = new SampleApplicationException(
                        SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                        logMessage);
            }

            if (vuforiaException != null)
            {
                // 初始化有问题时停止初始化
                // Send Vuforia Exception to the application and call initDone
                // to stop initialization process
                mSessionControl.onInitARDone(vuforiaException);
            }
        }
    }

    // 异步载入追踪器数据
    // An async task to load the tracker data asynchronously.
    private class LoadTrackerTask extends AsyncTask<Void, Void, Boolean>
    {
        protected Boolean doInBackground(Void... params)
        {
            // 加锁
            // Prevent the concurrent lifecycle operations:
            synchronized (mLifecycleLock)
            {
                //加载追踪器dataset
                // Load the tracker data set:
                return mSessionControl.doLoadTrackersData();
            }
        }
        
        protected void onPostExecute(Boolean result)
        {
            
            SampleApplicationException vuforiaException = null;
            
            Log.d(LOGTAG, "LoadTrackerTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));
            
            if (!result)
            {
                String logMessage = "Failed to load tracker data.";
                // Error loading dataset
                Log.e(LOGTAG, logMessage);
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.LOADING_TRACKERS_FAILURE,
                    logMessage);
            } else
            {
                // 通知系统垃圾回收
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();
                
                Vuforia.registerCallback(SampleApplicationSession.this);

                mStarted = true;
            }

            // 完成追踪器载入，更新应用状态，传递exception检查是否有错误
            // Done loading the tracker, update application status, send the
            // exception to check errors
            mSessionControl.onInitARDone(vuforiaException);
        }
    }

    // 异步启动摄像机和追踪器
    // An async task to start the camera and trackers
    private class StartVuforiaTask extends AsyncTask<Void, Void, Boolean>
    {
        SampleApplicationException vuforiaException = null;
        protected Boolean doInBackground(Void... params)
        {
            // 防止同时有生命周期操作
            // Prevent the concurrent lifecycle operations:
            synchronized (mLifecycleLock)
            {
                try {
                    startCameraAndTrackers(mCamera);
                }
                catch (SampleApplicationException e)
                {
                    Log.e(LOGTAG, "StartVuforiaTask.doInBackground: Could not start AR with exception: " + e);
                    vuforiaException = e;
                }
            }

            return true;
        }

        protected void onPostExecute(Boolean result)
        {
            Log.d(LOGTAG, "StartVuforiaTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));

            mSessionControl.onVuforiaStarted();

            if (vuforiaException != null)
            {
                // 如果出现问题 停止初始化
                // Send Vuforia Exception to the application and call initDone
                // to stop initialization process
                mSessionControl.onInitARDone(vuforiaException);
            }
        }
    }
    

    // 初始化错误信息表述串
    // Returns the error message for each error code
    private String getInitializationErrorString(int code)
    {
        if (code == INIT_ERRORCODE.INIT_DEVICE_NOT_SUPPORTED)
            return mActivity.getString(R.string.INIT_ERROR_DEVICE_NOT_SUPPORTED);
        if (code == INIT_ERRORCODE.INIT_NO_CAMERA_ACCESS)
            return mActivity.getString(R.string.INIT_ERROR_NO_CAMERA_ACCESS);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_MISSING_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_MISSING_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_INVALID_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_INVALID_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_CANCELED_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_CANCELED_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH);
        else
        {
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_UNKNOWN_ERROR);
        }
    }
    
    // 关闭摄像机
    public void stopCamera()
    {
        if (mCameraRunning)
        {
            mSessionControl.doStopTrackers();
            mCameraRunning = false;
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
    }
    }
    

    // AR是否启动
    // Returns true if Vuforia is initialized, the trackers started and the
    // tracker data loaded
    private boolean isARRunning()
    {
        return mStarted;
    }
    
}
