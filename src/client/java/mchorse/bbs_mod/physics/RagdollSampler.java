package mchorse.bbs_mod.physics;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviours;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a replay's model the way the render poses it, out of sight, on a scratch entity: the bones of the
 * ragdoll and, for any tick, where each of their pivots stands in the world and how each is turned. That is
 * the pose the ragdoll's muscles pull towards.
 *
 * <p>The bones are the model's groups that have cubes of their own; each one's box is measured from its cubes,
 * in its own frame, so a body sits on its bone the way the bone's geometry does.</p>
 */
final class RagdollSampler
{
    /** One bone of the ragdoll: its name, the nearest ancestor that is also a body, and its box in its own frame */
    /**
     * One bone of the ragdoll: its name, the body it is jointed to, its box in its own frame and the joint the form
     * picked. {@code detached} marks a bone with no body anywhere above it in the model: its parent in the model
     * does not move with the ragdoll, so it is placed by a shift of its own instead of riding a parent body.
     */
    record Bone(String id, int parent, Vector3f center, Vector3f half, int joint, boolean detached)
    {}

    /**
     * The frame the render stands the model's bones in, from the frame the film stands the entity in: the form's
     * own transform, then the half turn about Y every model is drawn with. Bodies built in any other frame would
     * stand mirrored against the limbs they belong to.
     */
    static Matrix4f renderBase(Matrix4f entityWorld, Form form)
    {
        Matrix4f base = new Matrix4f(entityWorld);
        mchorse.bbs_mod.forms.renderers.FormRenderer renderer = form == null ? null : FormUtilsClient.getRenderer(form);

        if (renderer != null)
        {
            base.mul(renderer.createEvaluatedTransform(0F).createMatrix());
        }

        return base.rotateY((float) Math.PI);
    }

    /**
     * The ragdoll bones of a model: groups with cubes whose collision the form did not switch off, each with its
     * box in its own frame, its nearest parent body and the joint the form picked for it.
     */
    static List<Bone> bones(Model model, ModelForm form)
    {
        List<Bone> list = new ArrayList<>();

        for (ModelGroup group : model.topGroups)
        {
            collect(list, group, -1, form);
        }

        /* Bones under cubeless groups (an anchor, a low_body pivot) come out with no body above them. They are all
         * jointed to the main body, so the whole skeleton is one connected ragdoll instead of loose pieces */
        int main = -1;
        float biggest = -1F;

        for (int i = 0; i < list.size(); i++)
        {
            Bone bone = list.get(i);

            if (bone.parent() >= 0)
            {
                continue;
            }

            String id = bone.id().toLowerCase();
            boolean named = id.contains("body") || id.contains("torso") || id.contains("chest") || id.contains("spine") || id.contains("pelvis") || id.contains("hip") || id.contains("waist");
            float volume = bone.half().x * bone.half().y * bone.half().z + (named ? 1000F : 0F);

            if (volume > biggest)
            {
                biggest = volume;
                main = i;
            }
        }

        for (int i = 0; i < list.size(); i++)
        {
            Bone bone = list.get(i);

            if (bone.parent() < 0 && i != main && main >= 0)
            {
                list.set(i, new Bone(bone.id(), main, bone.center(), bone.half(), bone.joint(), true));
            }
        }

        return list;
    }

    private static void collect(List<Bone> list, ModelGroup group, int parent, ModelForm form)
    {
        int index = parent;
        mchorse.bbs_mod.forms.forms.utils.FormBone settings = form == null ? null : form.bones.getBone(group.id);
        boolean collides = settings == null || settings.ragdollCollision.get();

        if (!group.cubes.isEmpty() && collides)
        {
            Vector3f min = new Vector3f(Float.MAX_VALUE);
            Vector3f max = new Vector3f(-Float.MAX_VALUE);

            for (ModelCube cube : group.cubes)
            {
                min.min(cube.origin);
                max.max(new Vector3f(cube.origin).add(cube.size));
            }

            Vector3f pivot = group.initial.translate;
            Vector3f center = new Vector3f(min).add(max).mul(0.5F).sub(pivot).div(16F);
            Vector3f half = new Vector3f(max).sub(min).mul(0.5F / 16F);

            half.set(Math.max(0.03F, half.x), Math.max(0.03F, half.y), Math.max(0.03F, half.z));

            list.add(new Bone(group.id, parent, center, half, settings == null ? 0 : settings.ragdollJoint.get(), parent < 0));
            index = list.size() - 1;
        }

        for (ModelGroup child : group.children)
        {
            collect(list, child, index, form);
        }
    }

    final Replay replay;
    final List<Bone> bones = new ArrayList<>();
    final Set<String> ids = new LinkedHashSet<>();

    private final StubEntity entity;
    private final ModelFormRenderer renderer;
    private final Map<String, IEntity> entities = new HashMap<>();

    private RagdollSampler(Replay replay, StubEntity entity, ModelFormRenderer renderer)
    {
        this.replay = replay;
        this.entity = entity;
        this.renderer = renderer;
        this.entities.put(replay.getId(), entity);
    }

    /** Null when the replay's form is not a cubic model, or the model has fewer than two bones with cubes */
    static RagdollSampler create(Replay replay, World world)
    {
        if (!(replay.form.get() instanceof ModelForm))
        {
            return null;
        }

        StubEntity entity = new StubEntity(world);

        entity.setForm(FormUtils.copy(replay.form.get()));

        if (!(FormUtilsClient.getRenderer(entity.getForm()) instanceof ModelFormRenderer renderer))
        {
            return null;
        }

        RagdollSampler sampler = new RagdollSampler(replay, entity, renderer);
        ModelInstance instance = renderer.evaluateChannels(entity, 0F);

        if (instance == null || !(instance.model instanceof Model model))
        {
            return null;
        }

        for (Bone bone : bones(model, (ModelForm) replay.form.get()))
        {
            sampler.bones.add(bone);
            sampler.ids.add(bone.id());
        }

        return sampler.bones.size() >= 2 ? sampler : null;
    }

    /** The world frame the film stands the entity in at a tick */
    private Matrix4f world()
    {
        return renderBase(FilmMatrices.getMatrixForRenderWithRotation(this.entity, 0D, 0D, 0D, 0F), this.entity.getForm());
    }

    /** Every ragdoll bone's pivot frame in the world at a tick, as the channels pose it */
    Map<String, PivotFrame> sample(int tick)
    {
        Map<String, PivotFrame> frames = new HashMap<>();
        Form root = this.entity.getForm();
        int ticks = this.replay.getTick(tick);

        RenderFrame.invalidate();

        this.replay.keyframes.apply(ticks, this.entity);
        TrackBehaviours.clearOverrides(root);
        this.replay.properties.apply(TrackContext.frame(root, 0F, (anchor, transition) -> BaseFilmController.resolveAnchor(this.entities, anchor, transition)), ticks, 1F);
        root.applyStates(0F);

        try
        {
            ModelInstance model = this.renderer.evaluateChannels(this.entity, 0F);

            if (model != null)
            {
                ModelPivotFrames.collect(model.model, this.ids, frames, this.world());
            }
        }
        finally
        {
            root.unapplyStates();
            RenderFrame.invalidate();
        }

        return frames;
    }

    /** Where a body stands for a bone frame: its centre, then its turn as a quaternion */
    static float[] bodyPose(Bone bone, PivotFrame frame)
    {
        Quaternionf rotation = new Quaternionf(frame.worldRotation()).normalize();
        Vector3f scale = frame.scale();
        Vector3f offset = rotation.transform(new Vector3f(bone.center()).mul(scale));
        Vector3f position = new Vector3f(frame.position()).add(offset);

        return new float[] {position.x, position.y, position.z, rotation.x, rotation.y, rotation.z, rotation.w};
    }
}
