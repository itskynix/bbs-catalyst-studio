package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.functions.NNFunction;

public class NoiseFunction extends NNFunction
{
    public NoiseFunction(MathBuilder builder, IExpression[] expressions, String name) throws Exception
    {
        super(builder, expressions, name);
    }

    @Override
    public int getRequiredArguments()
    {
        return 1;
    }

    @Override
    public double doubleValue()
    {
        float x = (float) this.getArg(0).doubleValue();
        int channel = this.args.length > 1 ? (int) this.getArg(1).doubleValue() : 0;

        return ShakeClip.noise(x, channel);
    }
}
