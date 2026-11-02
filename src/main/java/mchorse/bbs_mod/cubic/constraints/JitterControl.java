package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;

import java.util.Objects;

/**
 * Procedural jitter / shaking parameters (tremors, shivering, panic, horror, bone chattering)
 * at the form level. Keyframeable directly on the Replay / Film timeline as a global track
 * and configurable on the model form without requiring individual bone constraints locks.
 */
public class JitterControl implements IMapSerializable
{
    public static final int TARGET_ALL = 0;
    public static final int TARGET_ARMS = 1;
    public static final int TARGET_HEAD = 2;
    public static final int TARGET_ARMS_AND_HEAD = 3;

    public static final float DEFAULT_INTENSITY = 0.0F;
    public static final float DEFAULT_FREQUENCY = 1.0F;

    public static final JitterControl DEFAULT = new JitterControl();

    public boolean enabled = false;
    public float intensity = DEFAULT_INTENSITY;
    public float frequency = DEFAULT_FREQUENCY;
    public int target = TARGET_ALL;

    public boolean isEnabled()
    {
        return (this.enabled || this.intensity > 0.0001F) && this.intensity > 0.0001F;
    }

    public boolean matchesTarget(String boneName)
    {
        if (this.target == TARGET_ALL)
        {
            return true;
        }

        if (boneName == null)
        {
            return false;
        }

        String lower = boneName.toLowerCase();
        boolean isArm = lower.contains("arm") || lower.contains("hand");
        boolean isHead = lower.contains("head");

        if (this.target == TARGET_ARMS)
        {
            return isArm;
        }
        else if (this.target == TARGET_HEAD)
        {
            return isHead;
        }
        else if (this.target == TARGET_ARMS_AND_HEAD)
        {
            return isArm || isHead;
        }

        return true;
    }

    public void identity()
    {
        this.enabled = false;
        this.intensity = DEFAULT_INTENSITY;
        this.frequency = DEFAULT_FREQUENCY;
        this.target = TARGET_ALL;
    }

    public void lerp(JitterControl preA, JitterControl a, JitterControl b, JitterControl postB, IInterp interp, float x)
    {
        this.intensity = (float) interp.interpolate(IInterp.context.set(preA.intensity, a.intensity, b.intensity, postB.intensity, x));
        this.frequency = (float) interp.interpolate(IInterp.context.set(preA.frequency, a.frequency, b.frequency, postB.frequency, x));
        this.enabled = a.enabled;
        this.target = a.target;
    }

    public void autoLerp(JitterControl preA, JitterControl a, JitterControl b, JitterControl postB, float pt, float at, float bt, float qt, boolean clamped, float x)
    {
        this.intensity = (float) AutoBezier.get(preA.intensity, a.intensity, b.intensity, postB.intensity, pt, at, bt, qt, clamped, x);
        this.frequency = (float) AutoBezier.get(preA.frequency, a.frequency, b.frequency, postB.frequency, pt, at, bt, qt, clamped, x);
        this.enabled = a.enabled;
        this.target = a.target;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }

        if (o instanceof JitterControl other)
        {
            return this.enabled == other.enabled
                && Float.compare(other.intensity, this.intensity) == 0
                && Float.compare(other.frequency, this.frequency) == 0
                && this.target == other.target;
        }

        return false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.enabled, this.intensity, this.frequency, this.target);
    }

    public JitterControl copy()
    {
        JitterControl copy = new JitterControl();

        copy.enabled = this.enabled;
        copy.intensity = this.intensity;
        copy.frequency = this.frequency;
        copy.target = this.target;

        return copy;
    }

    @Override
    public void fromData(MapType data)
    {
        this.enabled = data.getBool("enabled", this.enabled);
        this.intensity = data.getFloat("intensity", this.intensity);
        this.frequency = data.getFloat("frequency", this.frequency);
        this.target = data.getInt("target", this.target);
    }

    @Override
    public void toData(MapType map)
    {
        map.putBool("enabled", this.enabled);
        map.putFloat("intensity", this.intensity);
        map.putFloat("frequency", this.frequency);
        map.putInt("target", this.target);
    }
}

