package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.utils.VertexUtils;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.utils.ModelViewStack;
import mchorse.bbs_mod.utils.Bufs;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormRenderLast;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FramebufferDebug;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.FramebufferPool;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.Map;
import java.util.function.Supplier;

public class FramebufferFormRenderer extends FormRenderer<FramebufferForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    private IEntity entity = new StubEntity();

    public FramebufferFormRenderer(FramebufferForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        if (this.form.parts.getAll().isEmpty())
        {
            /* Nothing in it yet, so there is no picture to show - stand a figure in the cell
             * instead, at the size the video form draws its own placeholder at. */
            int size = 32;

            context.batcher.scaledIcon(Icons.PLAYER, Colors.WHITE, (x1 + x2 - size) / 2F, (y1 + y2 - size) / 2F, size);
        }
        else
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();
            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            stack.push();

            this.applyTransforms(uiMatrix, context.getTransition());
            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(180F));
            stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            this.renderBodyParts(new FormRenderingContext()
                .set(FormRenderType.ENTITY, this.entity, stack, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
                .inUI());

            stack.pop();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }

    /**
     * How deep in nested framebuffer forms the render currently is. The profiler's timer keeps
     * a single start per subsystem, so only the outermost framebuffer runs it - an inner one
     * would restart the clock and the outer one's remainder would be lost.
     */
    private static int renderDepth;

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        FramebufferPool pool = BBSModClient.getFramebuffers().getFormFramebuffers();
        int width = MathUtils.clamp(this.form.width.get(), 2, 4096);
        int height = MathUtils.clamp(this.form.height.get(), 2, 4096);
        boolean outermost = renderDepth == 0;

        BBSProfiler.count(BBSProfiler.Section.FRAMEBUFFER_RENDERS);

        if (outermost)
        {
            BBSProfiler.begin(BBSProfiler.Timer.FRAMEBUFFER_FORMS);
        }

        renderDepth += 1;

        try
        {
            this.updateGazeTracking(context);

            boolean glowActive = this.form.glow.get();
            boolean hasEyes = false;
            boolean hasNonEyes = false;

            if (glowActive)
            {
                for (BodyPart part : this.form.parts.getAllTyped())
                {
                    if (isEyesPart(part))
                    {
                        hasEyes = true;
                    }
                    else
                    {
                        hasNonEyes = true;
                    }
                }
            }

            if (glowActive && hasEyes && hasNonEyes)
            {
                /* 1. Pass: Sahnenin normal Minecraft ışıklandırmasıyla gölgeli yüz / ten dokusu */
                Framebuffer faceFb = pool.get(width, height);
                FramebufferDebug.beginRender(this.form, context, faceFb);

                try
                {
                    this.renderFramebuffer(context, faceFb, false, false);
                }
                finally
                {
                    FramebufferDebug.endRender();
                    pool.release(faceFb);
                }

                /* 2. Pass: Tam parlak parlayan göz modelleri (player/eyes, player/eyes_1px vb.) */
                Framebuffer eyesFb = pool.get(width, height);
                FramebufferDebug.beginRender(this.form, context, eyesFb);

                try
                {
                    this.renderFramebuffer(context, eyesFb, true, true);
                }
                finally
                {
                    FramebufferDebug.endRender();
                    pool.release(eyesFb);
                }
            }
            else
            {
                /* Tek pass: Glow kapalıysa normal sahne ışığı, yalnızca göz varsa parlak */
                boolean glowThisPass = glowActive && hasEyes;
                Framebuffer framebuffer = pool.get(width, height);
                FramebufferDebug.beginRender(this.form, context, framebuffer);

                try
                {
                    this.renderFramebuffer(context, framebuffer, glowThisPass, null);
                }
                finally
                {
                    FramebufferDebug.endRender();
                    pool.release(framebuffer);
                }
            }
        }
        finally
        {
            renderDepth -= 1;

            if (outermost)
            {
                BBSProfiler.end(BBSProfiler.Timer.FRAMEBUFFER_FORMS);
            }
        }
    }

    /** Diagnostic (see {@link FramebufferDebug}): every nested part reports its bindings and what it left in the buffer. */
    @Override
    protected void renderBodyPart(BodyPart part, FormRenderingContext context)
    {
        if (!FramebufferDebug.inside())
        {
            super.renderBodyPart(part, context);

            return;
        }

        String name = part.getForm() == null ? "null" : part.getForm().getClass().getSimpleName();

        FramebufferDebug.log("part", "begin " + name + " id=" + part.getId() + " | " + FramebufferDebug.bindings());

        super.renderBodyPart(part, context);

        FramebufferDebug.log("part", "end " + name + " | " + FramebufferDebug.bindings());
        FramebufferDebug.log("part", "end " + name + " | " + FramebufferDebug.glState());
        FramebufferDebug.readViewport("part end " + name);
    }

    public static boolean isEyesPart(BodyPart part)
    {
        if (part == null || part.getForm() == null)
        {
            return false;
        }

        Form form = part.getForm();

        if (form instanceof ModelForm modelForm)
        {
            String model = modelForm.model.get();

            if (model != null)
            {
                String lower = model.toLowerCase();

                if (lower.startsWith("player/eyes") || lower.contains("eyes"))
                {
                    return true;
                }
            }
        }

        String partId = part.getId();

        if (partId != null)
        {
            String lower = partId.toLowerCase();

            if (lower.contains("eye") || lower.contains("pupil"))
            {
                return true;
            }
        }

        return false;
    }

    private void renderFilteredParts(FormRenderingContext context, Boolean filterEyes)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            if (filterEyes != null)
            {
                boolean isEyes = isEyesPart(part);

                if (filterEyes && !isEyes)
                {
                    continue;
                }

                if (!filterEyes && isEyes)
                {
                    continue;
                }
            }

            this.renderBodyPart(part, context);
        }
    }

    private void renderFramebuffer(FormRenderingContext context, Framebuffer framebuffer, boolean glowing, Boolean filterEyes)
    {
        int x;
        int y;
        int width;
        int height;

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer viewport = stack.mallocInt(4);

            GL30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);

            x = viewport.get(0);
            y = viewport.get(1);
            width = viewport.get(2);
            height = viewport.get(3);
        }

        int prevDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];
        float[] clearColor = new float[4];

        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);

        Vector3f light0 = RenderSystem.shaderLightDirections[0];
        Vector3f light1 = RenderSystem.shaderLightDirections[1];
        Matrix4f projectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter vertexSorter = RenderSystem.getVertexSorting();
        int cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);

        FramebufferDebug.state("entry", context);

        GL30.glCullFace(GL30.GL_FRONT);
        /* Both lights along Z, one each way. The picture in here is meant to be flat, and the
         * two vanilla lights are what a flat one is made of - but pointing both at the camera
         * lights only the faces that happen to look back at it. The framebuffer renders under a
         * Y-flipped ortho with front faces culled, so a two-sided quad (a billboard draws both
         * of its sides) keeps the side whose normal points away, and that side came out at
         * MINECRAFT_AMBIENT_LIGHT alone - 40% - while a one-sided model next to it stayed lit. */
        RenderSystem.setShaderLights(new Vector3f(0F, 0F, 1F), new Vector3f(0F, 0F, -1F));
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1F, 1F, 1F, -1F, -500F, 500F), VertexSorter.BY_Z);
        ModelViewStack.push();
        ModelViewStack.identityTop();

        /* Pushing the identity is not enough - the programs read the APPLIED matrix, and in the
         * interface that is the GUI's translate(0, 0, -11000): with our ortho reaching only
         * 500 units deep, every vertex of the parts landed outside it and the buffer stayed
         * empty. In the world the applied matrix is the identity already, so nothing changes. */
        RenderSystem.applyModelViewMatrix();

        framebuffer.apply();

        /* Whoever was drawing before us may have left a scissor box — the UI clips its
         * viewport that way — and it would clip this framebuffer's own pixels too. */
        RenderSystem.disableScissor();

        /* Transparent clear: whatever was drawn before us may have left an opaque clear colour,
         * and clearing this buffer with it would give the finished picture a solid background. */
        RenderSystem.clearColor(0F, 0F, 0F, 0F);
        FramebufferDebug.clearState("clear");
        framebuffer.clear();

        context.stack.push();
        context.stack.peek().getPositionMatrix().identity();
        context.stack.peek().getNormalMatrix().identity();

        /* The nested forms render under an ortho projection into this framebuffer — deferring
         * their translucent pixels into the world's queue would replay them with the wrong
         * projection, so they render single-pass as before. Render-last is off here for the
         * same reason: a part postponed out of this buffer would come back in the world. */
        boolean queueWasActive = FormTranslucentQueue.suspend();
        boolean renderLastWasActive = FormRenderLast.suspend();

        /* Full bright on the way in: the quad that draws the finished picture applies the
         * caller's lightmap once, so letting it shade the parts inside the buffer too would
         * land the very same shading on them twice. */
        int light = context.light;

        context.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        /* Blending as GL really holds it, not as GlStateManager's cache believes. A shader pack's
         * per-draw-buffer blend modes are set by Iris with indexed GL calls the cache never sees,
         * and put back through the cache - which skips the real call when it already thinks the
         * default is in place. So after a pack's entity program the world runs with the alpha
         * factors ZERO/ONE (keep what the target already holds) on draw buffer 0, while the cache
         * says ONE/ZERO. This buffer is cleared to alpha 0, and every part's defaultBlendFunc()
         * was a no-op against that cache: the parts painted their colours, alpha stayed 0, and the
         * quad drew a fully transparent picture - only in the world pass, only under a pack (the
         * shadow pass and the UI carry no such override, and there the same buffer filled fine).
         * A raw reset puts GL at the default, the tracked calls put the cache there too, and from
         * here the parts' own blend calls mean what they say. Nothing is put back afterwards: the
         * pack re-applies its overrides on its next program bind, and a cache that agrees with GL
         * is the state everything else assumes. */
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glEnable(GL11.GL_BLEND);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        try
        {
            BBSRendering.renderOffscreen(() -> this.renderFilteredParts(context, filterEyes));
        }
        finally
        {
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            context.light = light;

            FormTranslucentQueue.restore(queueWasActive);
            FormRenderLast.restore(renderLastWasActive);
        }

        FramebufferDebug.readBuffer("after parts", framebuffer);
        FramebufferDebug.state("after parts", context);

        context.stack.pop();

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        /* All four: whoever called us may have placed the viewport off the origin - the preview
         * cache does, sliding it so a list cell's on-screen box lands at its own framebuffer's
         * corner. Putting it back at (0, 0) drew the quad off that framebuffer and the cell
         * showed nothing. */
        GL30.glViewport(x, y, width, height);

        if (scissorEnabled)
        {
            RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        }
        else
        {
            RenderSystem.disableScissor();
        }

        RenderSystem.setShaderLights(light0, light1);
        ModelViewStack.pop();
        RenderSystem.applyModelViewMatrix();

        /* As they were, not as they usually are: the world sorts by distance from the camera,
         * and leaving BY_Z behind would mis-order its translucency for the rest of the frame. */
        RenderSystem.setProjectionMatrix(projectionMatrix, vertexSorter);
        GL11.glCullFace(cullFace);

        boolean shading = !context.isPicking() && !glowing;
        VertexFormat format = glowing
            ? VertexFormats.POSITION_TEXTURE_COLOR
            : (shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_LIGHT_COLOR);
        Supplier<ShaderProgram> shader = glowing
            ? GameRenderer::getPositionTexColorProgram
            : (shading ? GameRenderer::getRenderTypeEntityTranslucentProgram : /*$ ptl*/GameRenderer::getPositionTexLightmapColorProgram/**/);

        if (FramebufferDebug.logging)
        {
            FramebufferDebug.log("quad", "shader=" + FramebufferDebug.shader(shader.get()) + " shading=" + shading
                + " texId=" + framebuffer.getMainTexture().id + " restoredTo draw=" + prevDraw + " read=" + prevRead
                + " viewport=[" + x + "," + y + "," + width + "," + height + "]");
        }

        FramebufferDebug.state("before quad", context);

        int actualLight = glowing ? LightmapTextureManager.MAX_LIGHT_COORDINATE : context.light;

        this.renderModel(framebuffer.getMainTexture(), format, shader, context.stack, context.overlay, actualLight, context.color, context.getTransition(), !context.isPicking());

        FramebufferDebug.state("after quad", context);
    }

    private double getEntityEyeHeight(IEntity entity)
    {
        if (entity == null)
        {
            return 1.62D;
        }

        Form form = entity.getForm();

        if (form != null)
        {
            return form.hitboxHeight.get() * form.hitboxEyeHeight.get();
        }

        return 1.62D;
    }

    private Vec3d findTargetActorPosition(String actorName)
    {
        if (actorName == null || actorName.trim().isEmpty())
        {
            return null;
        }

        actorName = actorName.trim();

        /* 0. The actors the film is drawing this very frame: works with the GUI closed
         * (world preview) and in exports, where there is no film panel to ask. */
        IEntity actor = mchorse.bbs_mod.film.FilmEntityRenderer.findActor(actorName);

        if (actor != null)
        {
            return new Vec3d(actor.getX(), actor.getY() + this.getEntityEyeHeight(actor), actor.getZ());
        }

        /* 1. Film / Replay sahnesindeki aktörleri kontrol et */
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        if (dashboard != null)
        {
            UIFilmPanel filmPanel = dashboard.getPanel(UIFilmPanel.class);

            if (filmPanel != null)
            {
                /* A. Canlı IEntity haritasından kontrol et */
                Map<String, IEntity> entities = filmPanel.getController() != null ? filmPanel.getController().getEntities() : null;

                if (entities != null && !entities.isEmpty())
                {
                    for (Map.Entry<String, IEntity> entry : entities.entrySet())
                    {
                        if (entry.getKey().equalsIgnoreCase(actorName))
                        {
                            IEntity e = entry.getValue();

                            if (e != null)
                            {
                                return new Vec3d(e.getX(), e.getY() + this.getEntityEyeHeight(e), e.getZ());
                            }
                        }
                    }
                }

                /* B. Film verisindeki Replay listesinden isim ve keyframe enterpolasyonu ile kontrol et */
                if (filmPanel.getData() != null)
                {
                    for (Replay replay : filmPanel.getData().replays.getList())
                    {
                        if (actorName.equalsIgnoreCase(replay.getName()) || actorName.equalsIgnoreCase(replay.getId()))
                        {
                            if (entities != null)
                            {
                                IEntity e = entities.get(replay.getId());

                                if (e != null)
                                {
                                    return new Vec3d(e.getX(), e.getY() + this.getEntityEyeHeight(e), e.getZ());
                                }
                            }

                            int cursor = filmPanel.getCursor();
                            float at = replay.getTick(cursor);
                            Form form = replay.form.get();
                            double eye = form == null ? 1.62D : form.hitboxHeight.get() * form.hitboxEyeHeight.get();

                            double x = replay.keyframes.x.isEmpty() ? 0 : replay.keyframes.x.interpolate(at);
                            double y = (replay.keyframes.y.isEmpty() ? 0 : replay.keyframes.y.interpolate(at)) + eye;
                            double z = replay.keyframes.z.isEmpty() ? 0 : replay.keyframes.z.interpolate(at);

                            return new Vec3d(x, y, z);
                        }
                    }
                }
            }
        }

        /* 2. Minecraft dünyasındaki canlı entity'leri kontrol et */
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null)
        {
            for (net.minecraft.entity.Entity e : mc.world.getEntities())
            {
                if (e.getName().getString().equalsIgnoreCase(actorName)
                    || (e.getCustomName() != null && e.getCustomName().getString().equalsIgnoreCase(actorName)))
                {
                    return new Vec3d(e.getX(), e.getY() + e.getStandingEyeHeight(), e.getZ());
                }
            }
        }

        return null;
    }

    /** Sets (or, with NaN, clears) the transient gaze offset on every model part; it is never written into the saved pose. */
    private void setGaze(float x, float y)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            if (part.getForm() instanceof ModelForm modelForm)
            {
                boolean changed = Float.compare(modelForm.gazeX, x) != 0 || Float.compare(modelForm.gazeY, y) != 0;

                modelForm.gazeX = x;
                modelForm.gazeY = y;

                if (changed)
                {
                    modelForm.bumpPoseVersion();
                }
            }
        }
    }

    /** Older saves may carry exploded pupil values in the stored poses; make them harmless. */
    private static void sanitizePupil(ModelForm modelForm, Pose pose)
    {
        if (pose == null)
        {
            return;
        }

        for (String bone : new String[] {"pupil_l", "pupil_r"})
        {
            PoseTransform transform = pose.get(bone);

            if (transform == null)
            {
                continue;
            }

            float x = transform.translate.x;
            float y = transform.translate.y;
            float z = transform.translate.z;

            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || Math.abs(x) > 64F || Math.abs(y) > 64F || Math.abs(z) > 64F)
            {
                transform.translate.set(0F, 0F, 0F);
                modelForm.bumpPoseVersion();
            }
        }
    }

    private void updateGazeTracking(FormRenderingContext context)
    {
        int target = this.form.targetType.get();

        /* Previews and the GUI have no scene to look at; with no target the pupils are plainly
         * free: nothing is written, so the form editor and the dope sheet own the bones. */
        if (context.ui || target == FramebufferForm.TARGET_NONE)
        {
            this.setGaze(Float.NaN, 0F);

            for (BodyPart part : this.form.parts.getAllTyped())
            {
                if (part.getForm() instanceof ModelForm modelForm)
                {
                    sanitizePupil(modelForm, modelForm.pose.get());
                    sanitizePupil(modelForm, modelForm.poseOverlay.get());
                }
            }

            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d targetWorldPos = null;

        if (target == FramebufferForm.TARGET_CAMERA)
        {
            if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null)
            {
                targetWorldPos = mc.gameRenderer.getCamera().getPos();
            }
        }
        else if (target == FramebufferForm.TARGET_ACTOR)
        {
            targetWorldPos = this.findTargetActorPosition(this.form.targetActor.get());
        }

        if (targetWorldPos == null || context.world == null)
        {
            this.setGaze(Float.NaN, 0F);

            return;
        }

        /* The head's matrix in world space (the entity's placement and every transform on the
         * way here), and the target in the very same space: bringing the target into the head's
         * local space is then one inverse, with no camera involved. */
        Matrix4f headWorld = new Matrix4f(context.world.peek().getPositionMatrix());

        if (!headWorld.isFinite() || Math.abs(headWorld.determinant()) < 1.0E-8F)
        {
            this.setGaze(Float.NaN, 0F);

            return;
        }

        Vector3f targetLocal = new Matrix4f(headWorld).invert().transformPosition(
            new Vector3f((float) targetWorldPos.x, (float) targetWorldPos.y, (float) targetWorldPos.z)
        );
        float len = targetLocal.length();

        if (!Float.isFinite(len) || len <= 0.001F)
        {
            this.setGaze(Float.NaN, 0F);

            return;
        }

        float maxOffset = this.form.maxLookOffset.get();
        float saccadeAmp = this.form.saccadeAmplitude.get();
        float saccadeFreq = this.form.saccadeFrequency.get();
        float lookX = MathUtils.clamp(targetLocal.x / len, -1F, 1F) * maxOffset;
        float lookY = MathUtils.clamp(targetLocal.y / len, -1F, 1F) * (maxOffset * 0.75F);

        /* Micro-saccades & physiological jitter, only while a valid target is tracked */
        if (saccadeFreq > 0F && saccadeAmp > 0F)
        {
            double time = (System.currentTimeMillis() / 1000.0) * saccadeFreq;
            long step = (long) time;
            float hash1 = (float) Math.sin(step * 12.9898 + 78.233);
            float hash2 = (float) Math.cos(step * 43.1231 + 93.987);

            lookX += hash1 * saccadeAmp * 0.15F + (float) Math.sin(time * 8.0) * saccadeAmp * 0.02F;
            lookY += hash2 * saccadeAmp * 0.1F;
        }

        lookX = MathUtils.clamp(lookX, -maxOffset, maxOffset);
        lookY = MathUtils.clamp(lookY, -maxOffset, maxOffset);

        if (!Float.isFinite(lookX) || !Float.isFinite(lookY))
        {
            this.setGaze(Float.NaN, 0F);

            return;
        }

        this.setGaze(lookX, lookY);
    }

    private void renderModel(Texture texture, VertexFormat format, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        /* The glowing eyes layer sits a hair in front of the face layer so the two coplanar quads do not z-fight. */
        float insetZ = -this.form.inset.get() * 0.0625F + (format == VertexFormats.POSITION_TEXTURE_COLOR ? 0.0004F : 0F);

        matrices.push();
        matrices.translate(0F, 0F, insetZ);

        float w = texture.width;
        float h = texture.height;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = new Vector4f(0, 0, 0, 0);
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

        /* Calculate quad's size (vertices, not UV). The scale sizes the quad the framebuffer is
         * shown on, not what is drawn into it — the body parts always fill the whole texture,
         * so raising it can't push them past the framebuffer's own edges. */
        float scale = this.form.scale.get() * 2F;
        float ratioX = (w > h ? h / w : 1F) * scale;
        float ratioY = (h > w ? w / h : 1F) * scale;
        float TLx = (uvTLx - 0.5F) * ratioY;
        float TLy = -(uvTLy - 0.5F) * ratioX;
        float BRx = (uvBRx - 0.5F) * ratioY;
        float BRy = -(uvBRy - 0.5F) * ratioX;

        quad.p1.set(TLx, TLy, 0);
        quad.p2.set(BRx, TLy, 0);
        quad.p3.set(TLx, BRy, 0);
        quad.p4.set(BRx, BRy, 0);

        FramebufferDebug.quad("quad corners", matrices, quad);

        this.renderQuad(format, texture, shader, matrices, overlay, light, overlayColor, transition, defer);

        matrices.pop();
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        BufferBuilder builder;
        Color color = Color.white();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();

        color.mul(overlayColor);

        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        BBSModClient.getTextures().bindTexture(texture);
        RenderSystem.setShader(shader);

        /* No raw bind here: the draw binds its own samplers, and a bind on whatever unit is
         * active would land behind GlStateManager's back - see BillboardFormRenderer. */
        builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, format);

        /* Front */
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, 1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, 1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, normal, 1F)/*$ nx*/.next()/**/;

        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, 1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, normal, 1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, 1F)/*$ nx*/.next()/**/;

        /* Back */
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, normal, -1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, -1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, -1F)/*$ nx*/.next()/**/;

        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, -1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, normal, -1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, -1F)/*$ nx*/.next()/**/;

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        if (defer && FormTranslucentQueue.isActive())
        {
            /* The framebuffer's content is transparent-background by nature, so the whole quad
             * defers into the sorted translucent pass. The command binds the framebuffer's live
             * texture at flush — and the pool hands the same buffer to the next form of the
             * same size, so several deferred quads would all show the last-rendered content;
             * a known trade-off of the pooled framebuffer scheme. */
            ShaderProgram finalShader = RenderSystem.getShader();
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

            buffer.bind();
            Bufs.upload(buffer, builder);
            VertexBuffer.unbind();

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));
            Vector3f planeNormal = FormTranslucentQueue.quadPlaneNormal(modelView, matrix);

            /* The quad's opaque texels also draw right here, writing depth, because the sort
             * alone cannot order this quad against a model it sits inside: a semi-transparent
             * layer of the parent model (a skin's hat layer) sorts by its group's pivot, which
             * is always further than the quad's own plane, so it replays first. With depth in
             * the buffer that layer lands over the quad by the depth test, pixel by pixel,
             * instead of the two fighting over who overwrites whom. */
            ShaderProgram cutout = this.form.glow.get() ? finalShader : GameRenderer.getRenderTypeEntityCutoutProgram();

            if (cutout != null)
            {
                /* The world pass draws with depth writes on; this only re-asserts it. */
                RenderSystem.depthMask(true);

                buffer.bind();
                buffer.draw(modelView, RenderSystem.getProjectionMatrix(), cutout);
                VertexBuffer.unbind();
            }

            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
                buffer, () -> finalShader, texture, modelView, null, origin, planeNormal, true, null, null
            ));
        }
        else
        {
            Bufs.draw(builder);
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    private VertexConsumer fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, Color color, float u, float v, int overlay, int light, Matrix3f normal, float nz)
    {
        if (format == VertexFormats.POSITION_TEXTURE_COLOR)
        {
            return consumer.vertex(matrix, x, y, 0F).texture(u, v).color(color.r, color.g, color.b, color.a);
        }

        if (format == VertexFormats.POSITION_TEXTURE_LIGHT_COLOR)
        {
            return consumer.vertex(matrix, x, y, 0F).texture(u, v).light(light).color(color.r, color.g, color.b, color.a);
        }

        return VertexUtils.normal(consumer.vertex(matrix, x, y, 0F).color(color.r, color.g, color.b, color.a).texture(u, v).overlay(overlay).light(light), normal, 0F, 0F, nz);
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        stack.push();
        this.applyTransforms(stack, true, transition);
        Matrix4f origin = new Matrix4f(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        matrices.put(prefix, new Matrix4f(stack.peek().getPositionMatrix()), origin);

        float width = MathUtils.clamp(this.form.width.get(), 2, 4096);
        float height = MathUtils.clamp(this.form.height.get(), 2, 4096);
        float scale = this.form.scale.get();

        Matrix4f parent = new Matrix4f(stack.peek().getPositionMatrix());
        MatrixStack childStack = new MatrixStack();
        MatrixCache children = new MatrixCache();

        /* The body parts live in the framebuffer's ortho box (-1..1 across the whole texture),
         * and the quad that shows it is that box times the scale and the aspect ratio. */
        float scaleX = scale * (height > width ? width / height : 1F);
        float scaleY = scale * (width > height ? height / width : 1F);

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                childStack.push();
                MatrixStackUtils.applyTransform(childStack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, childStack, children, StringUtils.combinePaths(prefix, part.getId()), transition);

                childStack.pop();
            }
        }

        stack.pop();

        for (Map.Entry<String, MatrixCacheEntry> entry : children.entrySet())
        {
            MatrixCacheEntry child = entry.getValue();

            matrices.put(entry.getKey(), this.projectOrigin(parent, child.matrix(), scaleX, scaleY), this.projectOrigin(parent, child.origin(), scaleX, scaleY));
        }
    }

    private Matrix4f projectOrigin(Matrix4f parent, Matrix4f child, float scaleX, float scaleY)
    {
        if (child == null)
        {
            return null;
        }

        /* Flatten positions only: gizmo orientation and rotation sampling need a full basis. */
        Matrix4f projected = new Matrix4f(child).setTranslation(child.m30() * scaleX, child.m31() * scaleY, 0F);

        return new Matrix4f(parent).mul(projected);
    }
}
