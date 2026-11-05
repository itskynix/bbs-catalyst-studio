/*
 * BBS CS lighting layer. Original code written for BBS Catalyst Studio; it carries no irlight code.
 * It is the seam the irlight dynamic lighting back end by qualett (MIT License) is to be brought in
 * behind - see THIRD_PARTY_LICENSES.md. Files that are ported from irlight take its notice:
 *
 *     Based on irlight by qualett (c) 2026
 *     Licensed under the MIT License.
 */
package mchorse.bbs_mod.graphics.lights;

import mchorse.bbs_mod.forms.forms.LightForm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One light of one frame: what a {@link LightForm} says now, and where it stands. The transform is
 * the form's frame as the world pass drew it (camera-relative, view rotation included), so the
 * position is {@link #position()} and the way a spot or a directional light points is {@link #direction()}.
 */
public abstract class Light
{
    public final LightType type;
    public final float red;
    public final float green;
    public final float blue;
    public final float intensity;
    public final float radius;
    public final Matrix4f transform;

    protected Light(LightType type, LightForm form, Matrix4f transform)
    {
        this.type = type;
        this.red = form.color.get().r;
        this.green = form.color.get().g;
        this.blue = form.color.get().b;
        this.intensity = form.intensity.get();
        this.radius = form.radius.get();
        this.transform = new Matrix4f(transform);
    }

    /** The light a form is right now: its type picks the class */
    public static Light of(LightForm form, Matrix4f transform)
    {
        return switch (LightType.of(form.lightType.get()))
        {
            case SPOT -> new SpotLight(form, transform);
            case DIRECTIONAL -> new DirectionalLight(form, transform);
            default -> new PointLight(form, transform);
        };
    }

    /** Where it stands, in the space of the transform */
    public Vector3f position()
    {
        return this.transform.getTranslation(new Vector3f());
    }

    /** The way it points: the form's front (+Z), in the space of the transform, unit length */
    public Vector3f direction()
    {
        return this.transform.transformDirection(new Vector3f(0F, 0F, 1F)).normalize();
    }
}
