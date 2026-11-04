package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.minecraft.client.util.math.MatrixStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The lattice's bent geometry, baked into VAOs of its own: the bones it reaches are built once with
 * every vertex moved through it in the model's rest space, and drawn from the GPU like any bone, the
 * pose moving them as it does an unbent one. So a still lattice costs nothing per frame - the bend
 * is redone only when the deformation changes (a point dragged, a keyframe moving it, the bulge on
 * its way), told by {@link LatticeDeform#key()}.
 *
 * <p>Baked on the render thread, a form at a time; the least recently drawn forms give theirs up
 * (and free them) past {@link #FORMS}.</p>
 */
public class LatticeBake
{
    /** How many forms keep a baked lattice at once */
    private static final int FORMS = 6;

    private static final Map<ModelForm, Baked> CACHE = new LinkedHashMap<>(8, 0.75F, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ModelForm, Baked> eldest)
        {
            if (this.size() > FORMS)
            {
                eldest.getValue().free();

                return true;
            }

            return false;
        }
    };

    private static class Baked
    {
        public Object model;
        public long key;
        public Map<ModelGroup, Map<String, ModelVAO>> vaos = new HashMap<>();

        public void free()
        {
            for (Map<String, ModelVAO> group : this.vaos.values())
            {
                for (ModelVAO vao : group.values())
                {
                    vao.delete();
                }
            }

            this.vaos.clear();
        }
    }

    /** The bent VAOs of the bones the deformation reaches, baked now if the ones held are of another shape */
    public static Map<ModelGroup, Map<String, ModelVAO>> get(ModelForm form, ModelInstance instance, LatticeDeform deform)
    {
        if (!(instance.model instanceof Model model))
        {
            return null;
        }

        Baked entry = CACHE.get(form);

        if (entry != null && entry.model == model && entry.key == deform.key())
        {
            return entry.vaos;
        }

        if (entry != null)
        {
            entry.free();
        }
        else
        {
            entry = new Baked();
            CACHE.put(form, entry);
        }

        entry.model = model;
        entry.key = deform.key();
        entry.vaos = new HashMap<>();

        CubicRenderer.processRenderModel(new CubicVAOBuilderRenderer(entry.vaos, deform), null, new MatrixStack(), model);

        return entry.vaos;
    }
}
