/*===============================================================================
Copyright (c) 2016-2017 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package bn.com.userdefinedtargetssample.SampleApplication;

import com.vuforia.State;


//  activity实现SampleApplication接口后 可以控制 SampleApplicationSession
//  Interface to be implemented by the activity which uses SampleApplicationSession
public interface SampleApplicationControl
{

    // 初始化追踪器(trackers)
    boolean doInitTrackers();
    
    // 加载追踪器数据
    boolean doLoadTrackersData();
    

    //开始追踪
    // To be called to start tracking with the initialized trackers and their
    // loaded data
    boolean doStartTrackers();
    
    //停止追踪
    // To be called to stop the trackers
    boolean doStopTrackers();
    
    //摧毁追踪器数据
    // To be called to destroy the trackers' data
    boolean doUnloadTrackersData();
    
    //逆初始化追踪器
    // To be called to deinitialize the trackers
    boolean doDeinitTrackers();
    
    // 当Vuforia初始化完成后调用
    // 此时追踪器已经完成初始化，数据已经载入，追踪器可以启动了
    // This callback is called after the Vuforia initialization is complete,
    // the trackers are initialized, their data loaded and
    // tracking is ready to start
    void onInitARDone(SampleApplicationException e);
    
    // 周期调用更新函数
    // This callback is called every cycle
    void onVuforiaUpdate(State state);

    //Vuforia恢复时调用
    void onVuforiaResumed();

    //Vuforia启动时调用
    void onVuforiaStarted();
    
}
