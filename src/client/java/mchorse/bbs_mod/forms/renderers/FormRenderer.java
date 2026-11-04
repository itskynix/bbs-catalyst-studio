package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.api.client.events.FormPoseEvents;
import mchorse.bbs_mod.api.client.render.RenderAttachment;
import java.util.IdentityHashMap;
import java.util.Map;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.forms.utils.FormIllusionValues;
import mchorse.bbs_mod.forms.renderers.utils.FormIllusions;
import org.joml.Vector3f;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forms.FormMasking;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class FormRenderer <T extends Form>
{
    protected T form;
    private Map<RenderAttachment<?>, Object> attachments;

    @SuppressWarnings("unchecked")
    public <V> V getAttachment(RenderAttachment<V> key)
    {
        return this.attachments == null ? null : (V) this.attachments.get(key);
    }

    public <V> void setAttachment(RenderAttachment<V> key, V value)
    {
        if (value == null)
        {
            if (this.attachments != null) this.attachments.remove(key);
        }
        else
        {
            if (this.attachments == null) this.attachments = new IdentityHashMap<>();
            this.attachments.put(key, value);
        }
    }

    public FormRenderer(T form)
    {
        this.form = form;
    }

    public T getForm()
    {
        return this.form;
    }

    public List<String> getBones()
    {
        return Collections.emptyList();
    }

    /**
     * The shape of this form's skeleton, or null when it has none. The one question the bone
     * widgets ask a form - the tree list, the pose editor's bone column, the bone picker menus -
     * so they no longer have to know whether they are looking at a cubic model, a BOBJ armature or
     * a vanilla entity model.
     */
    public IBoneHierarchy getBoneHierarchy()
    {
        return null;
    }

    public final void renderUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.renderInUI(context, x1, y1, x2, y2);

        FontRenderer font = context.batcher.getFont();
        String name = this.form.name.get();

        if (!name.isEmpty())
        {
            name = font.limitToWidth(name, x2 - x1 - 3);

            int w = font.getWidth(name);

            context.batcher.textCard(name, (x2 + x1 - w) / 2, y1 + 6, Colors.WHITE, Colors.ACTIVE | Colors.A50);
        }

        int keybind = this.form.hotkey.get();

        if (keybind > 0)
        {
            name = KeyCodes.getName(keybind);
            name = font.limitToWidth(name, x2 - x1 - 3);

            int w = font.getWidth(name);

            context.batcher.textCard(name, (x2 + x1 - w) / 2, y2 - 6 - font.getHeight(), Colors.WHITE, Colors.A50);
        }
    }

    /**
     * The form alone, without the name and hotkey cards {@link #renderUI} lays over it — for a
     * host that draws its own captions around the picture.
     */
    public final void renderPreview(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.renderInUI(context, x1, y1, x2, y2);
    }

    protected abstract void renderInUI(UIContext context, int x1, int y1, int x2, int y2);

    public boolean renderArm(MatrixStack matrices, int light, AbstractClientPlayerEntity player, Hand hand)
    {
        return false;
    }

    public final void render(FormRenderingContext context)
    {
        if (!this.form.shaderShadow.get() && BBSRendering.isIrisShadowPass())
        {
            return;
        }

        BBSProfiler.count(BBSProfiler.Section.FORM_RENDER);

        this.form.applyStates(context.transition);

        int light = context.light;
        boolean visible = this.form.visible.get();
        boolean isPicking = context.isPicking();

        if (!visible || (isPicking && !this.form.pickable.get()))
        {
            this.form.unapplyStates();

            return;
        }

        context.stack.push();
        if (context.world != null)
        {
            context.world.push();
        }
        this.applyTransforms(context.stack, false, context.getTransition());
        if (context.world != null)
        {
            this.applyTransforms(context.world, false, context.getTransition());
        }

        float lf = 1F - MathUtils.clamp(this.form.lighting.get(), 0F, 1F);
        int u = context.light & '\uffff';
        int v = context.light >> 16 & '\uffff';

        u = (int) Lerps.lerp(u, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, lf);
        context.light = u | v << 16;

        FormMasking.render(context, this.form, () ->
        {
            this.render3D(context);

            if (isPicking)
            {
                this.updateStencilMap(context);
            }

            this.renderBodyParts(context);

            if (!isPicking)
            {
                this.renderIllusions(context);
            }
        });

        context.stack.pop();
        if (context.world != null)
        {
            context.world.pop();
        }

        context.light = light;

        this.form.unapplyStates();
    }

    /**
     * The form's illusions: the form drawn again {@code illusions_count} times, each copy one place
     * further along the chosen pattern and one more of the offset transform on, fainter than the last.
     * With a delay set, the copies are the film's business (they trail the actor in time), so none
     * are laid out here.
     */
    private void renderIllusions(FormRenderingContext context)
    {
        FormIllusionValues illusions = this.form.illusions;

        if (!illusions.isActive() || illusions.delay.get() > 0 || FormIllusions.isDrawing())
        {
            return;
        }

        List<FormIllusionValues.Placement> layout = illusions.layout();
        Transform step = illusions.offset.get();
        boolean cumulative = illusions.cumulative.get();
        float jitter = illusions.jitter.get();
        int color = context.color;
        long moment = System.nanoTime() / 45_000_000L;

        FormIllusions.begin();

        try
        {
            for (int n = 1; n <= layout.size(); n++)
            {
                float opacity = illusions.opacityOf(n);

                if (opacity <= 0.003F)
                {
                    continue;
                }

                FormIllusionValues.Placement place = layout.get(n - 1);

                context.stack.push();

                if (context.world != null)
                {
                    context.world.push();
                }

                this.illusionStep(context.stack, place, step, cumulative, jitter, n, moment);

                if (context.world != null)
                {
                    this.illusionStep(context.world, place, step, cumulative, jitter, n, moment);
                }

                context.color = Colors.mulA(color, opacity);

                this.render3D(context);
                this.renderBodyParts(context);

                context.stack.pop();

                if (context.world != null)
                {
                    context.world.pop();
                }
            }
        }
        finally
        {
            FormIllusions.end();
            context.color = color;
        }
    }

    /** One copy's place: n steps along the direction, n of the offset transform, and a shiver off both */
    private void illusionStep(MatrixStack stack, FormIllusionValues.Placement place, Transform step, boolean cumulative, float jitter, int n, long moment)
    {
        stack.translate(place.position().x, place.position().y, place.position().z);

        /* A copy on a ring turned to look at the form, or away from it */
        if (place.turned())
        {
            Vector3f axis = place.axis() == 0 ? new Vector3f(1F, 0F, 0F) : place.axis() == 2 ? new Vector3f(0F, 0F, 1F) : new Vector3f(0F, 1F, 0F);

            stack.multiply(new org.joml.Quaternionf().fromAxisAngleRad(axis, place.angle()));
        }

        if (jitter > 0F)
        {
            stack.translate(FormIllusions.noise(n, 1, moment) * jitter, FormIllusions.noise(n, 2, moment) * jitter, FormIllusions.noise(n, 3, moment) * jitter);
        }

        /* The same on every copy, or growing with the copy's number; a copy with no transform is the form's own pose, untouched */
        Transform total = new Transform();
        int times = cumulative ? n : 1;

        total.translate.set(step.translate).mul(times);
        total.rotate.set(step.rotate).mul(times);
        total.scale.set((float) Math.pow(step.scale.x, times), (float) Math.pow(step.scale.y, times), (float) Math.pow(step.scale.z, times));

        MatrixStackUtils.applyTransform(stack, total);
    }

    protected void applyTransforms(MatrixStack stack, boolean origin, float transition)
    {
        Transform transform = this.createEvaluatedTransform(transition);

        if (origin)
        {
            stack.translate(transform.translate.x, transform.translate.y, transform.translate.z);
        }
        else
        {
            MatrixStackUtils.applyTransform(stack, transform);
        }
    }

    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        matrix.mul(this.createEvaluatedTransform(transition).createMatrix());
    }

    /**
     * The form's own transform as it is actually rendered: its transform, its overlay and
     * whatever else was hung on it. Public because the film's orbit camera attaches to this
     * frame - what the camera follows has to be what the eye sees, not just where the replay
     * stands.
     */
    /** Saved animation plus overlays and external pose contributions. */
    public Transform createEvaluatedTransform(float transition)
    {
        Transform transform = this.createTransform();
        FormPoseEvents.TRANSFORM.invoker().apply(this.form, transform, transition);
        return transform;
    }

    public Transform createTransform()
    {
        this.form.syncOverlayTracks();
        Transform transform = new Transform();

        transform.copy(this.form.transform.get());
        this.applyTransform(transform, this.form.transformOverlay.get());

        for (ValueTransform t : this.form.additionalTransforms)
        {
            this.applyTransform(transform, t.get());
        }

        return transform;
    }

    private void applyTransform(Transform transform, Transform overlay)
    {
        transform.translate.add(overlay.translate);
        transform.scale.add(overlay.scale).sub(1, 1, 1);
        transform.addRotation(overlay);
    }

    /**
     * Form-local displacement of the form's origin from its rest pose this frame, used to drag the
     * entity's shadow under the form's perceived position. The base form simply reports its own
     * transform's translation (so transform keyframes shift the shadow); subclasses can override to
     * add their own motion (e.g. {@link ModelFormRenderer} folds in anchor-bone root motion). The
     * caller maps the result to world axes with the render target.
     */
    public Vector3f getShadowDisplacement(IEntity entity, float transition)
    {
        MatrixStack stack = new MatrixStack();

        stack.push();
        this.applyTransforms(stack, false, transition);

        Vector3f displacement = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

        stack.pop();

        return displacement;
    }

    protected Supplier<ShaderProgram> getShader(FormRenderingContext context, Supplier<ShaderProgram> normal, Supplier<ShaderProgram> picking)
    {
        if (context.isPicking())
        {
            this.setupTarget(context, picking.get());

            return picking;
        }

        return normal;
    }

    protected void setupTarget(FormRenderingContext context, ShaderProgram program)
    {
        GlUniform target = program.getUniform("Target");

        if (target != null)
        {
            int pickingIndex = context.getPickingIndex();

            target.set(pickingIndex);
        }
    }

    protected void updateStencilMap(FormRenderingContext context)
    {
        context.stencilMap.addPicking(this.form);
    }

    protected void render3D(FormRenderingContext context)
    {}

    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            this.renderBodyPart(part, context);
        }
    }

    protected void renderBodyPart(BodyPart part, FormRenderingContext context)
    {
        IEntity oldEntity = context.entity;

        context.entity = part.getRenderEntity(oldEntity);

        if (part.getForm() != null)
        {
            context.stack.push();
            if (context.world != null)
            {
                context.world.push();
            }
            MatrixStackUtils.applyTransform(context.stack, part.transform.get());
            if (context.world != null)
            {
                MatrixStackUtils.applyTransform(context.world, part.transform.get());
            }

            FormUtilsClient.render(part.getForm(), context);

            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }
        }

        context.entity = oldEntity;
    }

    public MatrixCache collectMatrices(IEntity entity, float transition)
    {
        BBSProfiler.count(BBSProfiler.Section.COLLECT_MATRICES);

        MatrixCache map = new MatrixCache();
        MatrixStack stack = new MatrixStack();

        this.collectMatrices(entity, stack, map, "", transition);

        return map;
    }

    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        FormPoseEvents.PARENT_FRAME.invoker().capture(this.form, entity, stack.peek().getPositionMatrix(), prefix, transition);

        Matrix4f mm = new Matrix4f();
        Matrix4f oo = new Matrix4f();

        stack.push();
        this.applyTransforms(stack, true, transition);
        oo.set(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        mm.set(stack.peek().getPositionMatrix());

        matrices.put(prefix, mm, oo);

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                stack.push();
                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, part.getId()), transition);

                stack.pop();
            }
        }

        stack.pop();
    }
}
