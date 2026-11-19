package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;

/**
 * Set Material Value: the form's keyed 0 to 1 value, laid over the bone being drawn. A glow and a
 * colour blend are plain colour and light changes; a fill is measured by the model shader against the
 * bone's own extent, so it needs the program that has the {@code MaterialMask} uniform (no shader
 * pack's).
 */
public class FormMaterialValue
{
    /** What one bone's draw gets: its colour multipliers and how far its light is lifted to full */
    public static class Result
    {
        public float r;
        public float g;
        public float b;
        public float glow;
        /** 0 for no fill, 1 for a vertical one, 2 for a horizontal one */
        public int fill;
        public float value;
    }

    private static final Result RESULT = new Result();

    /** Whether the form drives this bone at all */
    public static boolean applies(ModelForm form, String bone)
    {
        if (form == null || form.materialMode.get() == ModelForm.MATERIAL_OFF)
        {
            return false;
        }

        String target = form.materialTargetBone.get();

        return target == null || target.isEmpty() || target.equals(bone);
    }

    /**
     * The look of a bone for the form's current value, or null when the form does not drive it. The
     * result is reused between calls: read it before asking again.
     */
    public static Result resolve(ModelForm form, String bone, float r, float g, float b)
    {
        if (!applies(form, bone))
        {
            return null;
        }

        float value = MathUtils.clamp(form.materialValue.get(), 0F, 1F);
        Color color = form.materialColor.get();
        Result result = RESULT;

        result.r = r;
        result.g = g;
        result.b = b;
        result.glow = 0F;
        result.fill = 0;
        result.value = value;

        switch (form.materialMode.get())
        {
            case ModelForm.MATERIAL_EMISSIVE ->
            {
                /* Brighter, tinted by the colour, and lit as if by a block light at full */
                float boost = value * 2F;

                result.r = r * (1F + boost * color.r);
                result.g = g * (1F + boost * color.g);
                result.b = b * (1F + boost * color.b);
                result.glow = value;
            }
            case ModelForm.MATERIAL_FILL_VERTICAL -> result.fill = 1;
            case ModelForm.MATERIAL_FILL_HORIZONTAL -> result.fill = 2;
            case ModelForm.MATERIAL_COLOR ->
            {
                result.r = r + (color.r - r) * value;
                result.g = g + (color.g - g) * value;
                result.b = b + (color.b - b) * value;
            }
            default -> result = null;
        }

        return result;
    }

    /** Hands a fill to the shader for the next draw; {@code bounds} is a ModelVAO's minX, maxX, minY, maxY */
    public static void setMask(ShaderProgram program, int fill, float value, float[] bounds)
    {
        GlUniform uniform = program == null ? null : program.getUniform("MaterialMask");

        if (uniform == null)
        {
            return;
        }

        if (fill == 0)
        {
            uniform.set(0F, 1F, 0F, 1F);
        }
        else
        {
            boolean vertical = fill == 1;

            uniform.set((float) fill, value, vertical ? bounds[2] : bounds[0], vertical ? bounds[3] : bounds[1]);
        }
    }
}
