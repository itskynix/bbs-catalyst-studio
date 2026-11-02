package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.cubic.constraints.JitterControl;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class JitterKeyframeFactory implements IKeyframeFactory<JitterControl>
{
    private JitterControl i = new JitterControl();

    @Override
    public JitterControl fromData(BaseType data)
    {
        JitterControl control = new JitterControl();

        if (data.isMap())
        {
            control.fromData(data.asMap());
        }

        return control;
    }

    @Override
    public BaseType toData(JitterControl value)
    {
        return value.toData();
    }

    @Override
    public JitterControl createEmpty()
    {
        return new JitterControl();
    }

    @Override
    public JitterControl copy(JitterControl value)
    {
        return value.copy();
    }

    @Override
    public JitterControl interpolate(Keyframe<JitterControl> preA, Keyframe<JitterControl> a, Keyframe<JitterControl> b, Keyframe<JitterControl> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);

            this.i.autoLerp(preA.getValue(), a.getValue(), b.getValue(), postB.getValue(), preA.getTick(), a.getTick(), b.getTick(), postB.getTick(), clamped, x);

            return this.i;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public JitterControl interpolate(JitterControl preA, JitterControl a, JitterControl b, JitterControl postB, IInterp interpolation, float x)
    {
        this.i.lerp(preA, a, b, postB, interpolation, x);

        return this.i;
    }
}

