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

    public static void render(MatrixStack stack, Batcher2D batcher, ClipContext context)
    {
        if (failed)
        {
            return;
        }

        List<FxOverlay> overlays = FxClip.getOverlays(context);

        if (overlays.isEmpty())
        {
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
                scratch = new SimpleFramebuffer(main.textureWidth, main.textureHeight, false, MinecraftClient.IS_SYSTEM_MAC);
            }
            else if (scratch.textureWidth != main.textureWidth || scratch.textureHeight != main.textureHeight)
            {
                scratch.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            }

            resolveMotionBlur(overlays, context, main);

            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(false);

            for (FxOverlay overlay : overlays)
            {
                pass(program, main, overlay);
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

    private static void pass(ShaderProgram program, Framebuffer main, FxOverlay overlay)
    {
        int w = main.textureWidth;
        int h = main.textureHeight;

        /* The frame so far, copied aside */
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratch.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        /* ... and back to drawing on the original */
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.fbo);
        GlStateManager._viewport(0, 0, w, h);

        RenderSystem.setShader(() -> program);
        RenderSystem.setShaderTexture(0, scratch.getColorAttachment());

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

    private static void set(ShaderProgram program, String name, float... values)
    {
        GlUniform uniform = program.getUniform(name);

        if (uniform != null)
        {
            uniform.set(values);
        }
    }
}
