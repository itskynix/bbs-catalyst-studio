package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValueExpression;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;

/**
 * Shake modifier
 *
 * Professional camera shake modifier supporting Sine, Cosine, Perlin Noise (handheld organic camera),
 * and custom Math Expressions.
 */
public class ShakeClip extends ComponentClip
{
    public static final int MODE_SINE = 0;
    public static final int MODE_COSINE = 1;
    public static final int MODE_PERLIN = 2;
    public static final int MODE_MATH = 3;

    public final ValueInt mode = new ValueInt("mode", 0, 0, 3);
    public final ValueFloat frequency = new ValueFloat("frequency", 1F);
    public final ValueFloat amplitude = new ValueFloat("amplitude", 1F);
    public final ValueFloat rotationalWeight = new ValueFloat("rotationalWeight", 1F);
    public final ValueFloat positionalWeight = new ValueFloat("positionalWeight", 1F);

    public final MathBuilder builder = new MathBuilder();
    public final ValueExpression expression;

    public Variable varTicks;
    public Variable varOffset;
    public Variable varTransition;
    public Variable varDuration;
    public Variable varProgress;
    public Variable varVelocity;
    public Variable varDistance;
    public Variable varX;
    public Variable varY;
    public Variable varZ;
    public Variable varYaw;
    public Variable varPitch;
    public Variable varRoll;
    public Variable varFov;

    /* Legacy fields for backward compatibility */
    public final ValueFloat shake = new ValueFloat("shake", 0F);
    public final ValueFloat shakeAmount = new ValueFloat("shakeAmount", 0F);
    public final ValueBoolean noise = new ValueBoolean("noise", false);

    public ShakeClip()
    {
        super();

        this.builder.functions.put("noise", NoiseFunction.class);

        this.varTicks = this.builder.register("t");
        this.varOffset = this.builder.register("o");
        this.varTransition = this.builder.register("pt");
        this.varDuration = this.builder.register("d");
        this.varProgress = this.builder.register("p");
        this.varVelocity = this.builder.register("v");
        this.varDistance = this.builder.register("dt");

        this.varX = this.builder.register("x");
        this.varY = this.builder.register("y");
        this.varZ = this.builder.register("z");
        this.varYaw = this.builder.register("yaw");
        this.varPitch = this.builder.register("pitch");
        this.varRoll = this.builder.register("roll");
        this.varFov = this.builder.register("fov");

        this.expression = new ValueExpression("expression", this.builder);

        this.add(this.mode);
        this.add(this.frequency);
        this.add(this.amplitude);
        this.add(this.rotationalWeight);
        this.add(this.positionalWeight);
        this.add(this.expression);

        this.add(this.shake);
        this.add(this.shakeAmount);
        this.add(this.noise);

        /* Yaw and pitch should be enabled by default */
        this.active.set(0b0011000);
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        int currentMode = this.mode.get();
        if (currentMode == MODE_SINE && this.noise.get())
        {
            currentMode = MODE_PERLIN;
        }

        float freq = this.frequency.get();
        if (this.shake.get() > 0F && this.frequency.get() == 1F)
        {
            freq = 1F / this.shake.get();
        }

        float amp = this.amplitude.get();
        if (this.shakeAmount.get() > 0F && this.amplitude.get() == 1F)
        {
            amp = this.shakeAmount.get();
        }

        float time = context.ticks + context.transition;
        float t = time * freq;
        float rotW = this.rotationalWeight.get();
        float posW = this.positionalWeight.get();

        boolean isX = this.isActive(0);
        boolean isY = this.isActive(1);
        boolean isZ = this.isActive(2);
        boolean isYaw = this.isActive(3);
        boolean isPitch = this.isActive(4);
        boolean isRoll = this.isActive(5);
        boolean isFov = this.isActive(6);

        if (currentMode == MODE_PERLIN)
        {
            float n = t * 0.15F;

            if (isX) position.point.x += perlinNoise(n, 0) * amp * posW;
            if (isY) position.point.y += perlinNoise(n, 1) * amp * posW;
            if (isZ) position.point.z += perlinNoise(n, 2) * amp * posW;
            if (isYaw) position.angle.yaw += perlinNoise(n, 3) * amp * rotW;
            if (isPitch) position.angle.pitch += perlinNoise(n, 4) * amp * rotW;
            if (isRoll) position.angle.roll += perlinNoise(n, 5) * amp * rotW;
            if (isFov) position.angle.fov += perlinNoise(n, 6) * amp * rotW;

            return;
        }

        if (currentMode == MODE_MATH)
        {
            IExpression expr = this.expression.get();
            if (expr != null)
            {
                int duration = this.duration.get();

                this.varVelocity.set(context.velocity);
                this.varDistance.set(context.distance);
                this.varTicks.set(context.ticks);
                this.varOffset.set(context.relativeTick);
                this.varTransition.set(context.transition);
                this.varDuration.set(duration);
                this.varProgress.set(context.relativeTick + context.transition);

                this.varX.set(position.point.x);
                this.varY.set(position.point.y);
                this.varZ.set(position.point.z);
                this.varYaw.set(position.angle.yaw);
                this.varPitch.set(position.angle.pitch);
                this.varRoll.set(position.angle.roll);
                this.varFov.set(position.angle.fov);

                double val = expr.get().doubleValue() * amp;

                if (isX) position.point.x += val * posW;
                if (isY) position.point.y += val * posW;
                if (isZ) position.point.z += val * posW;
                if (isYaw) position.angle.yaw += (float) (val * rotW);
                if (isPitch) position.angle.pitch += (float) (val * rotW);
                if (isRoll) position.angle.roll += (float) (val * rotW);
                if (isFov) position.angle.fov += (float) (val * rotW);
            }

            return;
        }

        if (currentMode == MODE_COSINE)
        {
            double cos = Math.cos(t);
            double sin = Math.sin(t);

            if (isYaw && isPitch && !isX && !isY && !isZ && !isRoll && !isFov)
            {
                float swingX = (float) (cos * cos * sin * Math.sin(t / 2));
                float swingY = (float) (sin * cos * cos);

                position.angle.yaw += swingX * amp * rotW;
                position.angle.pitch += swingY * amp * rotW;
            }
            else
            {
                if (isX) position.point.x += cos * amp * posW;
                if (isY) position.point.y += sin * amp * posW;
                if (isZ) position.point.z -= cos * amp * posW;
                if (isYaw) position.angle.yaw += cos * amp * rotW;
                if (isPitch) position.angle.pitch += sin * amp * rotW;
                if (isRoll) position.angle.roll += cos * amp * rotW;
                if (isFov) position.angle.fov += sin * amp * rotW;
            }

            return;
        }

        // MODE_SINE
        double sin = Math.sin(t);
        double cos = Math.cos(t);

        if (isYaw && isPitch && !isX && !isY && !isZ && !isRoll && !isFov)
        {
            float swingX = (float) (sin * sin * cos * Math.cos(t / 2));
            float swingY = (float) (cos * sin * sin);

            position.angle.yaw += swingX * amp * rotW;
            position.angle.pitch += swingY * amp * rotW;
        }
        else
        {
            if (isX) position.point.x += sin * amp * posW;
            if (isY) position.point.y -= sin * amp * posW;
            if (isZ) position.point.z += cos * amp * posW;
            if (isYaw) position.angle.yaw += sin * amp * rotW;
            if (isPitch) position.angle.pitch += cos * amp * rotW;
            if (isRoll) position.angle.roll += sin * amp * rotW;
            if (isFov) position.angle.fov += cos * amp * rotW;
        }
    }

    public static float perlinNoise(float x, int channel)
    {
        float total = 0F;
        float curAmp = 1F;
        float curFreq = 1F;
        float maxAmp = 0F;

        for (int i = 0; i < 3; i++)
        {
            total += noise(x * curFreq, channel * 10 + i) * curAmp;
            maxAmp += curAmp;
            curAmp *= 0.5F;
            curFreq *= 2.05F;
        }

        return total / maxAmp;
    }

    /**
     * Value noise: a smooth signal in [-1, 1] that wanders instead of repeating.
     */
    public static float noise(float x, int channel)
    {
        int cell = (int) Math.floor(x);
        float t = x - cell;

        /* Smoothstep, so the lattice points don't show up as kinks */
        t = t * t * (3F - 2F * t);

        return Lerps.lerp(hash(cell, channel), hash(cell + 1, channel), t);
    }

    /**
     * Hash a lattice point into [-1, 1). An integer avalanche, so that neighbouring cells —
     * and neighbouring channels — land nowhere near each other.
     */
    private static float hash(int cell, int channel)
    {
        int h = cell * 374761393 + channel * 668265263;

        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);

        return (h >>> 8) / (float) (1 << 24) * 2F - 1F;
    }

    @Override
    public Clip create()
    {
        return new ShakeClip();
    }
}