package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ModelConstraintsRuntime
{
    private ModelConstraintsRuntime()
    {
    }

    public static void apply(ModelInstance instance)
    {
        apply(instance, null, 0F);
    }

    public static void apply(ModelInstance instance, IEntity target, float transition)
    {
        if (instance == null || instance.model == null)
        {
            return;
        }

        Map<String, BoneConstraint> bones = getBones(instance);
        ModelForm form = instance.form instanceof ModelForm modelForm ? modelForm : null;
        JitterControl globalJitter = form != null ? form.jitter.get() : null;
        boolean hasGlobalJitter = globalJitter != null && globalJitter.isEnabled();

        if (bones.isEmpty() && !hasGlobalJitter)
        {
            return;
        }

        applyToBones(instance.model, bones, globalJitter, target, transition);
    }

    /* One-slot per-frame memo - the walk used to run twice per form per frame (physics asks
     * once, the constraints stage once). Track overrides land before renders, so within a
     * frame the map cannot change; the epoch re-reads it next frame. */
    private static ModelInstance lastInstance;
    private static Object lastForm;
    private static long lastEpoch;
    private static Map<String, BoneConstraint> lastBones;

    /**
     * The form's enabled constraints by bone name, read fresh from the bone properties each
     * frame (a track's runtime override on the property is picked up for free that way).
     */
    public static Map<String, BoneConstraint> getBones(ModelInstance instance)
    {
        if (!(instance != null && instance.form instanceof ModelForm form))
        {
            return Collections.emptyMap();
        }

        if (RenderFrame.isEnabled() && lastInstance == instance && lastForm == form && lastEpoch == RenderFrame.getEpoch())
        {
            return lastBones;
        }

        Map<String, BoneConstraint> bones = collectBones(form);

        lastInstance = instance;
        lastForm = form;
        lastEpoch = RenderFrame.getEpoch();
        lastBones = bones;

        return bones;
    }

    private static Map<String, BoneConstraint> collectBones(ModelForm form)
    {
        Map<String, BoneConstraint> bones = null;

        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone)
            {
                BoneConstraint constraint = bone.constraints.get();

                if (constraint.isActive())
                {
                    if (bones == null)
                    {
                        bones = new HashMap<>();
                    }

                    bones.put(bone.getId(), constraint);
                }
            }
        }

        return bones == null ? Collections.emptyMap() : bones;
    }

    /**
     * Clamps a bone's EVALUATED rotation (the constraint-stack result so far — FK, IK, physics),
     * not its FK channels: the evaluated rotation is decomposed to the euler branch nearest the FK
     * channels (a per-frame-stable reference, no frame-to-frame stranding), clamped per axis, and
     * written back to {@code orient}. The channels stay read-only FK truth, and an IK/physics
     * result survives the limit instead of being discarded for the clamped FK pose (limits used to
     * null {@code orient}, visually destroying the solve on a constrained chain bone). Works on
     * quaternion-mode bones too — the clamp reads the evaluated rotation, never a stale euler.
     */
    /**
     * Clamps every constrained bone of the rig. The limits are authored in DEGREES whatever the
     * skeleton stores its channels in, which is what {@link RigBone#fromDegrees()} is for; the
     * decomposition goes against the bone's own channels so a clamp lands on angles the animator
     * would recognise.
     */
    private static void applyToBones(IModel model, Map<String, BoneConstraint> bones, JitterControl globalJitter, IEntity target, float transition)
    {
        float time = target != null ? (float) target.getAge() + transition : (float) (System.currentTimeMillis() / 50.0);

        for (RigBone bone : model.getRigBones())
        {
            if (bone == null)
            {
                continue;
            }

            BoneConstraint c = bones.get(bone.getBoneName());
            boolean boneHasConstraint = c != null && c.isActive();
            boolean applyGlobal = globalJitter != null && globalJitter.isEnabled() && globalJitter.matchesTarget(bone.getBoneName());

            if (!boneHasConstraint && !applyGlobal)
            {
                continue;
            }

            Vector3f euler = bone.toCompatibleEuler(new Vector3f());

            if (c != null && c.jitter && c.jitterIntensity > 0F)
            {
                applyJitter(bone, euler, c.jitterIntensity, c.jitterFrequency, time);
            }
            else if (applyGlobal)
            {
                applyJitter(bone, euler, globalJitter.intensity, globalJitter.frequency, time);
            }

            if (c != null)
            {
                clamp(euler, c, bone.fromDegrees());
            }

            bone.setOrient(bone.orientFromEuler(euler));
        }
    }

    private static void applyJitter(RigBone bone, Vector3f euler, float intensity, float frequency, float time)
    {
        int seed = Math.abs(bone.getBoneName().hashCode()) % 1000;
        float freq = frequency * 0.75F;
        float t = time * freq;

        /* Multi-octave natural jitter tremor (primary rapid vibration + secondary irregular twitch) */
        float nX = ShakeClip.perlinNoise(t * 1.0F, seed * 3 + 1) + ShakeClip.noise(t * 2.1F, seed * 3 + 10) * 0.35F;
        float nY = ShakeClip.perlinNoise(t * 1.1F, seed * 3 + 2) + ShakeClip.noise(t * 2.3F, seed * 3 + 20) * 0.35F;
        float nZ = ShakeClip.perlinNoise(t * 0.9F, seed * 3 + 3) + ShakeClip.noise(t * 1.9F, seed * 3 + 30) * 0.35F;

        float intensityRad = intensity * bone.fromDegrees();

        euler.x += nX * intensityRad;
        euler.y += nY * intensityRad;
        euler.z += nZ * intensityRad;
    }

    /**
     * Clamps euler angles to the constraint's limits, {@code scale} converting the
     * degree limits to the angles' unit. An axis whose switch is off is left alone
     * — free, not pinned to whatever its unused min/max happen to say.
     */
    private static void clamp(Vector3f euler, BoneConstraint c, float scale)
    {
        if (c.limitX)
        {
            euler.x = clampAxis(euler.x, c.minX * scale, c.maxX * scale);
        }

        if (c.limitY)
        {
            euler.y = clampAxis(euler.y, c.minY * scale, c.maxY * scale);
        }

        if (c.limitZ)
        {
            euler.z = clampAxis(euler.z, c.minZ * scale, c.maxZ * scale);
        }
    }

    private static float clampAxis(float value, float min, float max)
    {
        if (min > max)
        {
            float t = min;
            min = max;
            max = t;
        }

        return MathUtils.clamp(value, min, max);
    }
}
