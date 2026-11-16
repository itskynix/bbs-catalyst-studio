package mchorse.bbs_mod.physics;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import org.joml.Vector3f;

/**
 * Works out the collision of a form without anyone marking anything up: a box that wraps its geometry.
 *
 * <p>A model form is measured from the cubes of its bones, scaled by the form's own transform; a form
 * that has a hitbox of its own uses that; anything else gets the size of a person. The box stands on the
 * replay's position, which is where a form's feet are, so the body's centre is half its height above it.</p>
 */
public final class AutoCollisionBuilder
{
    private static final float MIN_HALF = 0.05F;
    private static final float MAX_HALF = 8F;

    /** Half extents of the box and the height of its centre above the replay's position */
    public record Box(float halfX, float halfY, float halfZ, float centerY)
    {
        public float volume()
        {
            return 8F * this.halfX * this.halfY * this.halfZ;
        }
    }

    private AutoCollisionBuilder()
    {}

    public static Box build(Form form)
    {
        if (form == null)
        {
            return person();
        }

        Box model = form instanceof ModelForm modelForm ? fromModel(modelForm) : null;

        if (model != null)
        {
            return model;
        }

        if (form.hitbox.get())
        {
            float w = clamp(form.hitboxWidth.get() / 2F);
            float h = clamp(form.hitboxHeight.get() / 2F);

            return new Box(w, h, w, h);
        }

        return person();
    }

    private static Box person()
    {
        return new Box(0.3F, 0.9F, 0.3F, 0.9F);
    }

    /** The cubes of every bone, at rest, as one box; null when the model has nothing to measure */
    private static Box fromModel(ModelForm form)
    {
        ModelInstance instance = ModelFormRenderer.getModel(form);

        if (instance == null || instance.model == null)
        {
            return null;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        boolean any = false;

        for (ModelGroup group : instance.model.getAllGroups())
        {
            for (ModelCube cube : group.cubes)
            {
                /* A cube is a corner and a size in sixteenths of a block */
                minX = Math.min(minX, cube.origin.x);
                minY = Math.min(minY, cube.origin.y);
                minZ = Math.min(minZ, cube.origin.z);
                maxX = Math.max(maxX, cube.origin.x + cube.size.x);
                maxY = Math.max(maxY, cube.origin.y + cube.size.y);
                maxZ = Math.max(maxZ, cube.origin.z + cube.size.z);
                any = true;
            }
        }

        if (!any)
        {
            return null;
        }

        Vector3f scale = form.transform.get().scale;
        float sx = Math.abs(scale.x);
        float sy = Math.abs(scale.y);
        float sz = Math.abs(scale.z);
        float width = (maxX - minX) / 16F * sx;
        float height = (maxY - minY) / 16F * sy;
        float depth = (maxZ - minZ) / 16F * sz;

        if (width <= 0F || height <= 0F || depth <= 0F)
        {
            return null;
        }

        float hy = clamp(height / 2F);

        return new Box(clamp(width / 2F), hy, clamp(depth / 2F), Math.max(hy, (minY + maxY) / 32F * sy));
    }

    private static float clamp(float half)
    {
        return Math.max(MIN_HALF, Math.min(MAX_HALF, half));
    }
}
