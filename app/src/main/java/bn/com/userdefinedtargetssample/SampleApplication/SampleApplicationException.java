/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package bn.com.userdefinedtargetssample.SampleApplication;

// 用来发送使用vuforia中发生的错误给activity
public class SampleApplicationException extends Exception
{
    
    private static final long serialVersionUID = 2L;
    
    public static final int INITIALIZATION_FAILURE = 0;//初始化失败
    public static final int VUFORIA_ALREADY_INITIALIZATED = 1;//vuforia已经初始化
    public static final int TRACKERS_INITIALIZATION_FAILURE = 2;//追踪器初始化失败
    public static final int LOADING_TRACKERS_FAILURE = 3;//追踪器载入失败
    public static final int UNLOADING_TRACKERS_FAILURE = 4;//停用追踪器数据失败
    public static final int TRACKERS_DEINITIALIZATION_FAILURE = 5;//关闭追踪器失败
    public static final int CAMERA_INITIALIZATION_FAILURE = 6;//摄像机初始化失败
    public static final int SET_FOCUS_MODE_FAILURE = 7;//设置对焦模式失败
    public static final int ACTIVATE_FLASH_FAILURE = 8;//闪光灯激活失败
    
    private int mCode = -1;
    private String mString = "";
    
    
    public SampleApplicationException(int code, String description)
    {
        super(description);
        mCode = code;
        mString = description;
    }
    
    
    public int getCode()
    {
        return mCode;
    }
    
    
    public String getString()
    {
        return mString;
    }
}
