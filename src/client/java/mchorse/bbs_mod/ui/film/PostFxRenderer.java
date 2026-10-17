package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(-1, -1, 0).texture(0F, 0F).color(1F, 1F, 1F, 1F).next();
        builder.vertex(1, -1, 0).texture(1F, 0F).color(1F, 1F, 1F, 1F).next();
        builder.vertex(1, 1, 0).texture(1F, 1F).color(1F, 1F, 1F, 1F).next();
        builder.vertex(-1, 1, 0).texture(0F, 1F).color(1F, 1F, 1F, 1F).next();

        BufferRenderer.drawWithGlobalProgram(builder.end());
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
