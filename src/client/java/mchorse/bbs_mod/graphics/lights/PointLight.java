/*
 * BBS CS lighting layer. Original code written for BBS Catalyst Studio; it carries no irlight code.
 * See Light.java and THIRD_PARTY_LICENSES.md.
 */
package mchorse.bbs_mod.graphics.lights;

import mchorse.bbs_mod.forms.forms.LightForm;
import org.joml.Matrix4f;

/** Shines the same in every direction, as far as its radius */
public class PointLight extends Light
{
    public PointLight(LightForm form, Matrix4f transform)
    {
        super(LightType.POINT, form, transform);
    }
}
