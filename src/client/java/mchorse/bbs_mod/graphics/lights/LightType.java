/*
 * BBS CS lighting layer. Original code written for BBS Catalyst Studio; it carries no irlight code.
 * See Light.java and THIRD_PARTY_LICENSES.md.
 */
package mchorse.bbs_mod.graphics.lights;

public enum LightType
{
    POINT, SPOT, DIRECTIONAL;

    public static LightType of(int index)
    {
        LightType[] values = values();

        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
