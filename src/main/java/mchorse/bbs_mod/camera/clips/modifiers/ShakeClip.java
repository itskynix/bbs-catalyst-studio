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
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * Shake modifier
 *
 * Professional camera shake modifier supporting Sine, Cosine, Perlin Noise (handheld organic camera),
 * custom Math Expressions, and Dope Sheet Keyframe Channels automation.
 */
public class ShakeClip extends ComponentClip
{
    public static final int MODE_SINE = 0;
    public static final int MODE_COSINE = 1;
    public static final int MODE_PERLIN = 2;
    public static final int MODE_MATH = 3;

    public static final int PRESET_CUSTOM = 0;
    public static final int PRESET_HANDHELD = 1;
    public static final int PRESET_PANIC = 2;
    public static final int PRESET_IMPACT = 3;

    public final ValueInt mode = new ValueInt("mode", 0, 0, 3);
    public final ValueInt preset = new ValueInt("preset", PRESET_CUSTOM, 0, 3);
    public final ValueFloat frequency = new ValueFloat("frequency", 1F);
    public final ValueFloat amplitude = new ValueFloat("amplitude", 1F);
    public final ValueFloat rotationalWeight = new ValueFloat("rotationalWeight", 1F);
    public final ValueFloat positionalWeight = new ValueFloat("positionalWeight", 1F);
    public final ValueFloat macroWobble = new ValueFloat("macroWobble", 0.6F, 0F, 5F);
    public final ValueFloat microJitter = new ValueFloat("microJitter", 0.4F, 0F, 5F);
    public final ValueFloat rollWeight = new ValueFloat("rollWeight", 0.5F, 0F, 5F);
    public final ValueFloat fovWeight = new ValueFloat("fovWeight", 0.25F, 0F, 5F);

    /* Dope Sheet Keyframe Channels */
    public final KeyframeChannel<Double> intensityChannel = new KeyframeChannel<>("shake_intensity", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> frequencyChannel = new KeyframeChannel<>("shake_frequency", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> fovChannel = new KeyframeChannel<>("shake_fov", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> rollChannel = new KeyframeChannel<>("shake_roll", KeyframeFactories.DOUBLE);

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
        this.add(this.preset);
        this.add(this.frequency);
        this.add(this.amplitude);
        this.add(this.rotationalWeight);
        this.add(this.positionalWeight);
        this.add(this.macroWobble);
        this.add(this.microJitter);
        this.add(this.rollWeight);
        this.add(this.fovWeight);
        this.add(this.expression);

        this.add(this.intensityChannel);
        this.add(this.frequencyChannel);
        this.add(this.fovChannel);
        this.add(this.rollChannel);

        this.add(this.shake);
        this.add(this.shakeAmount);
        this.add(this.noise);

        /* Yaw and pitch should be enabled by default */
        this.active.set(0b0011000);
    }

    public void applyPreset(int presetId)
    {
        this.preset.set(presetId);

        if (presetId == PRESET_HANDHELD)
        {
            this.mode.set(MODE_PERLIN);
            this.frequency.set(0.85F);
            this.amplitude.set(0.65F);
            this.rotationalWeight.set(0.85F);
            this.positionalWeight.set(0.3F);
            this.macroWobble.set(0.85F);
            this.microJitter.set(0.2F);
            this.rollWeight.set(0.35F);
            this.fovWeight.set(0.1F);
            this.active.set(0b1111000); // Yaw, Pitch, Roll, FOV
        }
        else if (presetId == PRESET_PANIC)
        {
            this.mode.set(MODE_PERLIN);
            this.frequency.set(2.4F);
            this.amplitude.set(1.5F);
            this.rotationalWeight.set(1.3F);
            this.positionalWeight.set(0.8F);
            this.macroWobble.set(0.6F);
            this.microJitter.set(0.95F);
            this.rollWeight.set(0.85F);
            this.fovWeight.set(0.6F);
            this.active.set(0b1111111); // All active
        }
        else if (presetId == PRESET_IMPACT)
        {
            this.mode.set(MODE_PERLIN);
            this.frequency.set(4.2F);
            this.amplitude.set(2.4F);
            this.rotationalWeight.set(1.8F);
            this.positionalWeight.set(1.2F);
            this.macroWobble.set(0.3F);
            this.microJitter.set(1.2F);
            this.rollWeight.set(1.2F);
            this.fovWeight.set(0.9F);
            this.active.set(0b1111111); // All active
        }
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

        float relTick = context.relativeTick + context.transition;

        /* Dope Sheet Keyframe Channels Modulation */
        float intensityMod = this.intensityChannel.isEmpty() ? 1F : this.intensityChannel.interpolate(relTick, 1D).floatValue();
        float freqMod = this.frequencyChannel.isEmpty() ? 1F : this.frequencyChannel.interpolate(relTick, 1D).floatValue();
        float fovMod = this.fovChannel.isEmpty() ? 1F : this.fovChannel.interpolate(relTick, 1D).floatValue();
        float rollMod = this.rollChannel.isEmpty() ? 1F : this.rollChannel.interpolate(relTick, 1D).floatValue();

        amp *= intensityMod;
        freq *= freqMod;

        if (amp <= 1E-5F)
        {
            return;
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

        float rollW = this.rollWeight.get() * rollMod;
        float fovW = this.fovWeight.get() * fovMod;

        if (currentMode == MODE_PERLIN)
        {
            float macro = this.macroWobble.get();
            float micro = this.microJitter.get();

            if (isX) position.point.x += this.sampleMultiLayer(t, 0, macro, micro) * amp * posW;
            if (isY) position.point.y += this.sampleMultiLayer(t, 1, macro, micro) * amp * posW;
            if (isZ) position.point.z += this.sampleMultiLayer(t, 2, macro, micro) * amp * posW;
            if (isYaw) position.angle.yaw += this.sampleMultiLayer(t, 3, macro, micro) * amp * rotW;
            if (isPitch) position.angle.pitch += this.sampleMultiLayer(t, 4, macro, micro) * amp * rotW;
            if (isRoll) position.angle.roll += this.sampleMultiLayer(t, 5, macro, micro) * amp * rotW * rollW;
            if (isFov) position.angle.fov += this.sampleMultiLayer(t, 6, macro, micro) * amp * fovW * 2.5F;

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
                if (isRoll) position.angle.roll += (float) (val * rotW * rollW);
                if (isFov) position.angle.fov += (float) (val * fovW * 2.5F);
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
                if (isRoll) position.angle.roll += cos * amp * rotW * rollW;
                if (isFov) position.angle.fov += sin * amp * fovW * 2.5F;
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
            if (isRoll) position.angle.roll += sin * amp * rotW * rollW;
            if (isFov) position.angle.fov += cos * amp * fovW * 2.5F;
        }
    }

    /**
     * Multi-layer procedural noise for organic camera motion:
     * - Macro layer: broad, wandering handheld/breathing sway
     * - Mid layer: natural human body movement & steps
     * - Micro layer: rapid muscle micro-tremors and impact shudders
     */
    public float sampleMultiLayer(float time, int channel, float macroFactor, float microFactor)
    {
        /* 1. Macro Wobble: low-frequency, high-smoothness sway */
        float macro = noise(time * 0.04F, channel * 17 + 101) * macroFactor;

        /* 2. Mid fBm: standard organic handheld motion */
        float mid = perlinNoise(time * 0.15F, channel);

        /* 3. Micro Jitter: high-frequency shudder & tremor */
        float microTime = time * 0.65F;
        float micro = (noise(microTime, channel * 23 + 203) * 0.7F + noise(microTime * 2.3F, channel * 31 + 307) * 0.3F) * microFactor;

        float totalWeight = 0.45F + 0.4F + 0.3F;

        return (macro * 0.45F + mid * 0.4F + micro * 0.3F) / totalWeight;
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

        /* Quintic smoothstep for continuous acceleration (eliminates mechanical jerk) */
        t = t * t * t * (t * (t * 6F - 15F) + 10F);

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