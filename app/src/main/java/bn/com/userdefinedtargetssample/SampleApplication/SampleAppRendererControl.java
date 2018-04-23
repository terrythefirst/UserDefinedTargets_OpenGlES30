/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package bn.com.userdefinedtargetssample.SampleApplication;

import com.vuforia.State;

public interface SampleAppRendererControl {

    // 这个方法必须被Render类继承控制渲染的内容.这个函数会在SampleAppRendering 类中循环为每个view调用
    // This method has to be implemented by the Renderer class which handles the content rendering
    // of the sample, this one is called from SampleAppRendering class for each view inside a loop
    void renderFrame(State state, float[] projectionMatrix);

}
