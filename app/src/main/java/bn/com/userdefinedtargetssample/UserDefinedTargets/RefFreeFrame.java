/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package bn.com.userdefinedtargetssample.UserDefinedTargets;

import android.util.Log;

import com.vuforia.ImageTargetBuilder;
import com.vuforia.ObjectTracker;
import com.vuforia.Renderer;
import com.vuforia.TrackableSource;
import com.vuforia.TrackerManager;
import com.vuforia.Vec2F;
import com.vuforia.VideoBackgroundConfig;

import bn.com.userdefinedtargetssample.BnUtils.ShaderUtil;
import bn.com.userdefinedtargetssample.SampleApplication.SampleApplicationSession;


public class RefFreeFrame
{
    
    private static final String LOGTAG = "RefFreeFrame";
    
    // 状态枚举
    enum STATUS
    {
        STATUS_IDLE, STATUS_SCANNING, STATUS_CREATING, STATUS_SUCCESS
    }
    
    STATUS curStatus;//当前状态

    // / 当前target finder的颜色.由框架中设置的图形质量决定
    float colorFrame[];

    // / 屏幕半尺寸，用于渲染管线
    Vec2F halfScreenSize;

    // / 持续追踪框架中颜色转换的时间
    long lastFrameTime;
    long lastSuccessTime;

    // 所有渲染方法都包含再这个类中
    RefFreeFrameGL frameGL;

    // 从Target Builder中提取出的最新的 可追踪图源（trackable source）
    TrackableSource trackableSource;
    
    UserDefinedTargets mActivity;
    
    SampleApplicationSession vuforiaAppSession;
    
    // 将v0+inc限制在a 和 b范围内
    // Function used to transition in the range [0, 1]
    float transition(float v0, float inc, float a, float b)
    {
        float vOut = v0 + inc;
        return (vOut < a ? a : (vOut > b ? b : vOut));
    }
    float transition(float v0, float inc)
    {
        return transition(v0, inc, 0.0f, 1.0f);
    }
    
    
    public RefFreeFrame(UserDefinedTargets activity,
        SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        colorFrame = new float[4];
        curStatus = STATUS.STATUS_IDLE;
        lastSuccessTime = 0;
        trackableSource = null;
        colorFrame[0] = 1.0f;
        colorFrame[1] = 0.0f;
        colorFrame[2] = 0.0f;
        colorFrame[3] = 0.75f;
        
        frameGL = new RefFreeFrameGL(mActivity, vuforiaAppSession);
        halfScreenSize = new Vec2F();
    }
    
    
    void init()
    {
        trackableSource = null;
    }
    
    
    void deInit()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
            .getTracker(ObjectTracker.getClassType()));
        if (objectTracker != null)
        {
            ImageTargetBuilder targetBuilder = objectTracker
                .getImageTargetBuilder();
            if (targetBuilder != null
                && (targetBuilder.getFrameQuality() != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE))
            {
                targetBuilder.stopScan();
            }
        }
    }
    
    
    void initGL(int screenWidth, int screenHeight)
    {
        frameGL.init(screenWidth, screenHeight);
        
        Renderer renderer = Renderer.getInstance();
        VideoBackgroundConfig vc = renderer.getVideoBackgroundConfig();
        int temp[] = vc.getSize().getData();
        float[] videoBackgroundConfigSize = new float[2];
        videoBackgroundConfigSize[0] = temp[0] * 0.5f;
        videoBackgroundConfigSize[1] = temp[1] * 0.5f;
        
        halfScreenSize.setData(videoBackgroundConfigSize);
        // 设置时间戳
        lastFrameTime = System.currentTimeMillis();
        
        reset();//重设为闲置状态
    }
    
    
    void reset()
    {
        curStatus = STATUS.STATUS_IDLE;
    }
    
    
    void setCreating()
    {
        curStatus = STATUS.STATUS_CREATING;
    }
    
    
    void updateUIState(ImageTargetBuilder targetBuilder, int frameQuality)
    {
        // ** 经过时长
        long elapsedTimeMS = System.currentTimeMillis() - lastFrameTime;
        lastFrameTime += elapsedTimeMS;

        // 根据时间变化的值 用作版秒内在[0,1]范围内过渡
        // This is a time-dependent value used for transitions in 
        // the range [0,1] over the period of half of a second.
        float transitionHalfSecond = elapsedTimeMS * 0.002f;
        
        STATUS newStatus = curStatus;
        
        switch (curStatus)
        {
            case STATUS_IDLE://闲置
                if (frameQuality != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE)
                    newStatus = STATUS.STATUS_SCANNING;
                break;
            
            case STATUS_SCANNING://扫描章台
                switch (frameQuality)//检查画面质量
                {
                    // target的质量过低（纹理过简单无法辨认），渲染frame为白色直到匹配，变为绿色
                    // bad target quality, render the frame white until a match is
                    // made, then go to green
                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_LOW:
                        colorFrame[0] = 1.0f;
                        colorFrame[1] = 1.0f;
                        colorFrame[2] = 1.0f;
                        
                        break;
                        //良好的target 半秒内换为绿色
                        // good target, switch to green over half a second
                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_HIGH:
                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_MEDIUM:
                        colorFrame[0] = transition(colorFrame[0],
                            -transitionHalfSecond);
                        colorFrame[1] = transition(colorFrame[1],
                            transitionHalfSecond);
                        colorFrame[2] = transition(colorFrame[2],
                            -transitionHalfSecond);
                        
                        break;
                }
                break;
            
            case STATUS_CREATING://创建状态
            {
                // 检索新的可追踪图源
                // 如果找到则设置为成功，记录时间
                // check for new result
                // if found, set to success, success time and:
                TrackableSource newTrackableSource = targetBuilder
                    .getTrackableSource();
                if (newTrackableSource != null)
                {
                    newStatus = STATUS.STATUS_SUCCESS;
                    lastSuccessTime = lastFrameTime;
                    trackableSource = newTrackableSource;
                    
                    mActivity.targetCreated();
                }
            }
            default:
                break;
        }
        
        curStatus = newStatus;
    }
    
    
    void render()
    {
        // 得到追踪器
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
            .getTracker(ObjectTracker.getClassType()));

        // 从target builder中得到画面质量
        ImageTargetBuilder targetBuilder = objectTracker.getImageTargetBuilder();
        int frameQuality = targetBuilder.getFrameQuality();

        // 更新UI内部状态变量
        updateUIState(targetBuilder, frameQuality);
        
        if (curStatus == STATUS.STATUS_SUCCESS)
        {
            curStatus = STATUS.STATUS_IDLE;
            
            Log.d(LOGTAG, "Built target, reactivating dataset with new target");
            mActivity.doStartTrackers();
        }

        // 渲染
        switch (curStatus)
        {
            case STATUS_SCANNING:
                renderScanningViewfinder(frameQuality);
                break;
            default:
                break;
        
        }

        ShaderUtil.checkGlError("RefFreeFrame render");
    }
    
    
    void renderScanningViewfinder(int quality)
    {
        frameGL.setModelViewScale(2.0f);
        frameGL.setColor(colorFrame);
        frameGL.renderViewfinder();
    }
    
    
    boolean hasNewTrackableSource()
    {
        return (trackableSource != null);
    }
    
    
    TrackableSource getNewTrackableSource()
    {
        TrackableSource result = trackableSource;
        trackableSource = null;
        return result;
    }
}
