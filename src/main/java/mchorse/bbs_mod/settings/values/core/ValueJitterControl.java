package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.cubic.constraints.JitterControl;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ValueJitterControl extends BaseKeyframeFactoryValue<JitterControl>
{
    public ValueJitterControl(String id, JitterControl value)
    {
        super(id, KeyframeFactories.JITTER, value);
    }
}

