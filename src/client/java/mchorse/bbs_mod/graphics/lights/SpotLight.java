/*
 * BBS CS lighting layer. Original code written for BBS Catalyst Studio; it carries no irlight code.
 * See Light.java and THIRD_PARTY_LICENSES.md.
 */
package mchorse.bbs_mod.graphics.lights;

import mchorse.bbs_mod.forms.forms.LightForm;
import org.joml.Matrix4f;

/** Shines in a cone along its front, as far as its radius */
public class SpotLight extends Light
{
    /** The full opening of the cone, in radians */
    public final float coneAngle;

    public SpotLight(LightForm form, Matrix4f transform)
    {
        super(LightType.SPOT, form, transform);

        this.coneAngle = (float) Math.toRadians(form.spotAngle.get());
    }
}
