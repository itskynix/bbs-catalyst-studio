package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.utils.Bufs;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.fx.FxClip;
import mchorse.bbs_mod.camera.clips.fx.FxOverlay;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Draws the passes of the post-production clips ({@link FxClip}) over the finished frame.
 *
 * <p>A pass reads the frame and writes over it, which a texture cannot do to itself, so the frame is
 * copied into a scratch buffer first and the pass is drawn from that copy. The renderer asks nothing
 * of the game's own framebuffer setup: it works on whatever is the main framebuffer at that moment
 * and puts the bindings back as it found them.</p>
 *
 * <p>It must never take the timeline down. Every pass is guarded; the first failure switches the
 * effects off for the rest of the session instead of throwing every frame, and a missing program
 * (a driver that would not compile it) simply means nothing is drawn.</p>
 */
public class PostFxRenderer
{
    private static SimpleFramebuffer scratch;
    private static boolean failed;

    /* The world's depth, copied the moment the world is done: the hand is drawn after a depth clear, so by
     * the time the post passes run the main buffer holds nothing but the hand. */
    private static SimpleFramebuffer worldDepth;
    private static boolean depthCaptured;

    /* Camera turning, measured between two frames of the film: where it was, when, and how fast it is
     * turning now in frame fractions per second (x, y). */
    private static boolean hasTurn;
    private static float turnTime;
    private static float turnYaw;
    private static float turnPitch;
    private static long turnStamp;
    private static double turnX;
    private static double turnY;
    /* Angular speed of the camera in radians per second, smoothed across frames */
    private static double turnSpeed;

    /* A blur never smears further than this much of the frame, whatever the camera did */
    private static final double MAX_BLUR = 0.2D;

    /**
     * Frees every buffer of the post passes (scratch, world depth, the depth of field's three) and
     * forgets that anything failed: the next frame that needs one allocates it clean. Render thread only.
     */
    public static void purge()
    {
        for (SimpleFramebuffer buffer : new SimpleFramebuffer[] {scratch, worldDepth, dofCoc, dofUp, dofLeft})
        {
            if (buffer != null)
            {
                buffer.delete();
            }
        }

        scratch = null;
        worldDepth = null;
        dofCoc = null;
        dofUp = null;
        dofLeft = null;
        depthCaptured = false;
        failed = false;
        dofFailed = false;
    }

    /** Whether any of the frame's clips asks for the world wobble, and so wants the depth kept. */
    public static boolean wantsDepth(ClipContext context)
    {
        if (context == null)
        {
            return false;
        }

        for (FxOverlay overlay : FxClip.getOverlays(context))
        {
            if (overlay.kind == FxOverlay.WORLD_WOBBLE || overlay.kind == FxOverlay.DEPTH_OF_FIELD)
            {
                return true;
            }
        }

        return false;
    }

    /** Call when the world is drawn and before the hand: keeps its depth for the wobble pass. */
    public static void captureDepth()
    {
        if (failed)
        {
            return;
        }

        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();

        if (main == null || main.textureWidth <= 0 || main.textureHeight <= 0)
        {
            return;
        }

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        try
        {
            if (worldDepth == null)
            {
                worldDepth = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
            }
            else if (worldDepth.textureWidth != main.textureWidth || worldDepth.textureHeight != main.textureHeight)
            {
                worldDepth.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            }

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, worldDepth.fbo);
            GL30.glBlitFramebuffer(0, 0, main.textureWidth, main.textureHeight, 0, 0, main.textureWidth, main.textureHeight, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

            depthCaptured = true;
        }
        catch (Throwable e)
        {
            depthCaptured = false;
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        }
    }

    /**
     * Bend the air around sounds (the audio form's vibration waves): one pass per source, drawn over
     * the world once it is done and before the hand. Needs no film clip, so it runs in the world, in
     * the editor's preview and in exports alike. Each overlay comes filled in except for the depth,
     * which is taken here.
     */
    public static void renderRipples(List<FxOverlay> ripples)
    {
        if (failed || ripples.isEmpty())
        {
            return;
        }

        ShaderProgram program = BBSShaders.getPostFxProgram();
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();

        if (program == null || main == null || main.textureWidth <= 0 || main.textureHeight <= 0)
        {
            return;
        }

        captureDepth();

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        try
        {
            if (scratch == null)
            {
                scratch = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
            }
            else if (scratch.textureWidth != main.textureWidth || scratch.textureHeight != main.textureHeight)
            {
                scratch.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            }

            /* Copied a moment ago, before the hand clears it: no need to read it back to know it holds the world */
            boolean depth = depthCaptured;
            float far = MinecraftClient.getInstance().gameRenderer.getFarPlaneDistance();

            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(false);

            for (FxOverlay ripple : ripples)
            {
                ripple.params[13] = 0.05F;
                ripple.params[14] = far;
                ripple.params[15] = depth ? 1F : 0F;

                pass(program, main, ripple);
            }
        }
        catch (Throwable e)
        {
            failed = true;

            System.err.println("[BBS] Post-production effects failed and are switched off for this session:");
            e.printStackTrace();
        }
        finally
        {
            /* depthCaptured stays: the copy is this frame's, and a world wobble clip later in the frame reads it too */

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    public static void render(MatrixStack stack, Batcher2D batcher, ClipContext context)
    {
        if (failed)
        {
            return;
        }

        List<FxOverlay> overlays = FxClip.getOverlays(context);

        if (overlays.isEmpty())
        {
            depthCaptured = false;

            return;
        }

        ShaderProgram program = BBSShaders.getPostFxProgram();
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();

        if (program == null || main == null || main.textureWidth <= 0 || main.textureHeight <= 0)
        {
            return;
        }

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        try
        {
            if (scratch == null)
            {
                scratch = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
            }
            else if (scratch.textureWidth != main.textureWidth || scratch.textureHeight != main.textureHeight)
            {
                scratch.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            }

            resolveMotionBlur(overlays, context, main);

            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(false);

            /* The lens comes first: the grain, the vignette and the bars are laid over what it made. Two
             * pro camera clips at once leave one lens, the last. */
            FxOverlay lens = null;

            for (FxOverlay overlay : overlays)
            {
                if (overlay.kind == FxOverlay.DEPTH_OF_FIELD)
                {
                    lens = overlay;
                }
            }

            if (lens != null)
            {
                depthOfField(program, main, lens);
            }

            for (FxOverlay overlay : overlays)
            {
                if (overlay.kind != FxOverlay.DEPTH_OF_FIELD && overlay.kind != FxOverlay.TRANSFORM)
                {
                    pass(program, main, overlay);
                }
            }

            /* The 2D transform moves the finished picture, so it goes last; neutral ones never get here */
            for (FxOverlay overlay : overlays)
            {
                if (overlay.kind == FxOverlay.TRANSFORM)
                {
                    resolveTransform(overlay, main.textureWidth, main.textureHeight);
                    pass(program, main, overlay);
                }
            }
        }
        catch (Throwable e)
        {
            failed = true;

            System.err.println("[BBS] Post-production effects failed and are switched off for this session:");
            e.printStackTrace();
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            depthCaptured = false;

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

/**
     * Fill in the direction and length of every motion blur pass from how the camera has turned
     * since the frame before. Time is the film's, not the clock's: a paused film or a scrub has
     * no velocity and so no blur, and a frame rendered for export blurs by the same amount however
     * long it took to render.
     */
    private static void resolveMotionBlur(List<FxOverlay> overlays, ClipContext context, Framebuffer main)
    {
        boolean any = false;

        for (FxOverlay overlay : overlays)
        {
            any |= overlay.kind == FxOverlay.MOTION_BLUR;
        }

        if (!any)
        {
            hasTurn = false;

            return;
        }

        Camera camera = BBSModClient.getCameraController().camera;
        float time = context.ticks + context.transition;
        float yaw = camera.rotation.y;
        float pitch = camera.rotation.x;
        long now = System.nanoTime();

        if (hasTurn)
        {
            float seconds = (time - turnTime) / 20F;

            if (seconds > 0.0001F && seconds <= 0.2F)
            {
                float dYaw = yaw - turnYaw;

                /* The short way round, so crossing a full turn is not a whip pan */
                dYaw -= (float) (Math.PI * 2D) * Math.round(dYaw / (float) (Math.PI * 2D));

                double tanHalf = Math.tan(Math.max(camera.fov, 0.01F) / 2D);
                double aspect = main.textureWidth / (double) Math.max(main.textureHeight, 1);

                /* Turning right slides the picture left, turning up slides it up */
                turnX = -Math.tan(dYaw) / (2D * tanHalf * aspect) / seconds;
                turnY = Math.tan(pitch - turnPitch) / (2D * tanHalf) / seconds;

                /* Angular speed from the raw yaw and pitch change. A snap spikes for a single frame, so
                 * it is eased in: a sudden jump is blended with the speed before it, which keeps the
                 * smear from flickering while the direction of the pan is kept. */
                double speed = Math.sqrt(dYaw * (double) dYaw + (pitch - turnPitch) * (double) (pitch - turnPitch)) / seconds;

                turnSpeed += (speed - turnSpeed) * 0.6D;
            }
            else if (seconds != 0F || now - turnStamp > 2_000_000L)
            {
                /* Paused, seeked, or looped around: nothing is moving */
                turnX = 0D;
                turnY = 0D;
                turnSpeed = 0D;
            }
            /* else: a second pass within the same frame, which reads what the first one found */
        }

        if (!hasTurn || time != turnTime)
        {
            turnTime = time;
            turnYaw = yaw;
            turnPitch = pitch;
            turnStamp = now;
        }

        hasTurn = true;

        double fps = Math.max(BBSSettings.videoFrameRate.get(), 1);

        for (FxOverlay overlay : overlays)
        {
            if (overlay.kind != FxOverlay.MOTION_BLUR)
            {
                continue;
            }

            /* Whip pan: past the snap threshold the blur grows towards the multiplier, reaching it
             * at twice the threshold. A multiplier of 1 leaves the blur as it was. */
            double threshold = Math.toRadians(Math.max(overlay.params[7], 1F));
            double whip = Math.max(overlay.params[6], 1F);
            double excess = Math.min(Math.max((turnSpeed - threshold) / threshold, 0D), 1D);
            double dynamic = 1D + (whip - 1D) * excess * excess * (3D - 2D * excess);

            double reach = overlay.params[0] * overlay.params[1] * dynamic / fps;
            double x = turnX * reach + overlay.params[2];
            double y = turnY * reach;
            double length = Math.sqrt(x * x + y * y);

            if (length > MAX_BLUR)
            {
                x *= MAX_BLUR / length;
                y *= MAX_BLUR / length;
            }

            overlay.params[4] = (float) x;
            overlay.params[5] = (float) y;
        }
    }

    /**
     * Whether the depth copy holds a scene at all. The interface clears depth before it draws, and a
     * shader pack can keep it elsewhere, so the buffer can be a flat 1.0 - in which case the wobble
     * falls back to bending the whole picture by screen position instead of doing nothing.
     */
    private static boolean hasDepth(int w, int h)
    {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, worldDepth.fbo);

        try (MemoryStack memory = MemoryStack.stackPush())
        {
            FloatBuffer pixel = memory.mallocFloat(1);

            for (int i = 1; i < 6; i++)
            {
                for (int j = 1; j < 6; j++)
                {
                    GL11.glReadPixels(w * i / 6, h * j / 6, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixel);

                    if (pixel.get(0) < 0.99999F)
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * T(c + pos) * R(rot) * S(scale) * T(-c) around the frame centre c, handed to the shader as its
     * inverse: the pass asks, for every output pixel, which source pixel lands there.
     */
    private static void resolveTransform(FxOverlay overlay, int w, int h)
    {
        float[] p = overlay.params;

        /* The same overlays can be drawn twice in a frame; the numbers become the matrix only once */
        if (p[23] != 0F)
        {
            return;
        }

        double rot = Math.toRadians(p[2]);
        double cos = Math.cos(rot);
        double sin = Math.sin(rot);
        double sx = Math.max(Math.abs(p[3]), 1.0E-4D) * Math.signum(p[3] == 0F ? 1F : p[3]);
        double sy = Math.max(Math.abs(p[4]), 1.0E-4D) * Math.signum(p[4] == 0F ? 1F : p[4]);
        double cx = w / 2D;
        double cy = h / 2D;
        float posX = (float) (p[0] * w);
        float posY = (float) (p[1] * h);

        /* S^-1 * R^-1, with y pointing down so a positive angle turns clockwise */
        p[0] = (float) (cos / sx);
        p[1] = (float) (sin / sx);
        p[2] = (float) (-sin / sy);
        p[3] = (float) (cos / sy);
        p[4] = (float) (cx + posX);
        p[5] = (float) (cy + posY);
        p[6] = (float) cx;
        p[7] = (float) cy;
        p[23] = 1F;
    }

    private static void pass(ShaderProgram program, Framebuffer main, FxOverlay overlay)
    {
        int w = main.textureWidth;
        int h = main.textureHeight;

        /* The frame so far, copied aside */
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratch.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        boolean wobble = overlay.kind == FxOverlay.WORLD_WOBBLE;

        if (wobble)
        {
            overlay.params[5] = 0.05F;
            overlay.params[6] = MinecraftClient.getInstance().gameRenderer.getFarPlaneDistance();
            overlay.params[7] = depthCaptured && hasDepth(w, h) ? 1F : 0F;
        }

        /* ... and back to drawing on the original */
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.fbo);
        GlStateManager._viewport(0, 0, w, h);

        RenderSystem.setShader(() -> program);
        RenderSystem.setShaderTexture(0, scratch.getColorAttachment());
        boolean depthPass = wobble || overlay.kind == FxOverlay.RIPPLE;

        RenderSystem.setShaderTexture(1, depthPass && worldDepth != null ? worldDepth.getDepthAttachment() : scratch.getColorAttachment());

        set(program, "Mode", overlay.kind);
        set(program, "Size", w, h);

        float[] p = overlay.params;

        for (int i = 0; i < 6; i++)
        {
            set(program, "U" + i, p[i * 4], p[i * 4 + 1], p[i * 4 + 2], p[i * 4 + 3]);
        }

        BufferBuilder builder;

        builder = Bufs.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(-1, -1, 0).texture(0F, 0F).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;
        builder.vertex(1, -1, 0).texture(1F, 0F).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;
        builder.vertex(1, 1, 0).texture(1F, 1F).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;
        builder.vertex(-1, 1, 0).texture(0F, 1F).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;

        Bufs.draw(builder);
    }

    /* The depth of field's own buffers: the frame with its circle of confusion, and the two directional blurs */
    private static SimpleFramebuffer dofCoc;
    private static SimpleFramebuffer dofUp;
    private static SimpleFramebuffer dofLeft;
    private static boolean dofFailed;

    /** The largest blur radius, as a fraction of the frame's width */
    private static final float DOF_MAX_RADIUS = 0.15F;

    /** The general blur at full strength, as a fraction of the frame's height */
    private static final float DOF_GENERAL_RADIUS = 0.02F;

    /** The most taps one line of the blur takes (the shader takes one every 2.5 pixels of radius up to this) */
    private static final int DOF_TAPS = 128;

    private static SimpleFramebuffer dofBuffer(SimpleFramebuffer buffer, int w, int h)
    {
        boolean fresh = false;

        if (buffer == null)
        {
            buffer = new SimpleFramebuffer(w, h, false, MinecraftClient.IS_SYSTEM_MAC);
            fresh = true;
        }
        else if (buffer.textureWidth != w || buffer.textureHeight != h)
        {
            buffer.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
            fresh = true;
        }

        if (fresh)
        {
            /* Half floats: the circle of confusion rides in the alpha, and eight bits of it would be
             * steps of a couple of pixels at the largest blur. Linear filtering, so the taps between
             * pixels blend instead of snapping. The texture keeps its name, so the framebuffer keeps it. */
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

            GlStateManager._bindTexture(buffer.getColorAttachment());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager._bindTexture(previous);
        }

        return buffer;
    }

    /**
     * Depth of field with a hexagonal bokeh, in four passes: the frame with its circle of confusion
     * (from the depth the world left, in the alpha), a blur of it up, a blur of it down and to the left,
     * and the second blur of each of the three rhombi a hexagon is made of, summed into the frame. The
     * passes never write the frame before the last, so a failure leaves it as it was. It switches only
     * itself off, not the other effects.
     */
    private static void depthOfField(ShaderProgram program, Framebuffer main, FxOverlay overlay)
    {
        if (dofFailed)
        {
            return;
        }

        int w = main.textureWidth;
        int h = main.textureHeight;

        try
        {
            dofCoc = dofBuffer(dofCoc, w, h);
            dofUp = dofBuffer(dofUp, w, h);
            dofLeft = dofBuffer(dofLeft, w, h);

            boolean depth = depthCaptured && worldDepth != null && hasDepth(w, h);
            float[] p = overlay.params;
            float far = MinecraftClient.getInstance().gameRenderer.getFarPlaneDistance();
            float maxRadius = Math.max(DOF_MAX_RADIUS * w, 1F);
            float general = p[4] * DOF_GENERAL_RADIUS * h;

            /* The same numbers in every pass, but for the direction */
            float[] u0 = {p[0], p[1], p[2], maxRadius};
            float[] u1 = {0.05F, far, depth ? 1F : 0F, general};
            float[] u2 = {p[3], (float) mchorse.bbs_mod.camera.clips.overwrite.ProCameraClip.SENSOR_HEIGHT_MM, 0F, 0F};

            RenderSystem.setShader(() -> program);
            set(program, "Size", w, h);
            set(program, "U0", u0);
            set(program, "U1", u1);
            set(program, "U2", u2);

            /* 1: the frame and its circle of confusion */
            dofPass(program, dofCoc, 20, main.getColorAttachment(), depth ? worldDepth.getDepthAttachment() : main.getColorAttachment(), 0F, 0F);

            /* 2: up, and down-left, 120 degrees apart; the third direction is taken in the last pass */
            dofPass(program, dofUp, 21, dofCoc.getColorAttachment(), dofCoc.getColorAttachment(), 0F, 1F);
            dofPass(program, dofLeft, 21, dofCoc.getColorAttachment(), dofCoc.getColorAttachment(), -0.8660254F, -0.5F);

            /* 3: the rhombi, onto the frame */
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.fbo);
            dofPass(program, null, 22, dofUp.getColorAttachment(), dofLeft.getColorAttachment(), 0F, 0F);
        }
        catch (Throwable e)
        {
            dofFailed = true;

            System.err.println("[BBS] Depth of field failed and is switched off for this session:");
            e.printStackTrace();
        }
    }

    /** One of the depth of field's passes, into {@code target} or, with none, into whatever is bound. */
    private static void dofPass(ShaderProgram program, SimpleFramebuffer target, int mode, int first, int second, float dirX, float dirY)
    {
        Framebuffer size = MinecraftClient.getInstance().getFramebuffer();

        if (target != null)
        {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fbo);
        }

        GlStateManager._viewport(0, 0, size.textureWidth, size.textureHeight);

        RenderSystem.setShaderTexture(0, first);
        RenderSystem.setShaderTexture(1, second);

        set(program, "Mode", mode);
        set(program, "U3", dirX, dirY, DOF_TAPS, 0F);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        builder.vertex(-1, -1, 0).texture(0F, 0F).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;
        builder.vertex(1, -1, 0).texture(1F, 0F).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;
        builder.vertex(1, 1, 0).texture(1F, 1F).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;
        builder.vertex(-1, 1, 0).texture(0F, 1F).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;

        Bufs.draw(builder);
    }

    private static void set(ShaderProgram program, String name, float... values)
    {
        GlUniform uniform = program.getUniform(name);

        if (uniform != null)
        {
            uniform.set(values);
        }
    }
}
