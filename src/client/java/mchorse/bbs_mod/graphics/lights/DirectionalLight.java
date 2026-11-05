/*
 * BBS CS lighting layer. Original code written for BBS Catalyst Studio; it carries no irlight code.
 * See Light.java and THIRD_PARTY_LICENSES.md.
 */
package mchorse.bbs_mod.graphics.lights;

import mchorse.bbs_mod.forms.forms.LightForm;
import org.joml.Matrix4f;

/** Shines along its front everywhere, like the sun: where it stands does not matter, how it points does */
public class DirectionalLight extends Light
{
    public DirectionalLight(LightForm form, Matrix4f transform)
    {
        super(LightType.DIRECTIONAL, form, transform);
    }
}
