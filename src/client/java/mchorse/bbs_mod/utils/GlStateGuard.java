package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.GameRenderer;

import java.nio.ByteBuffer;

/**
 * What a pass drawn into the middle of the interface (the picking pass of the film preview) must
 * hand back: the framebuffer and viewport it was started with, the scissor, the depth, colour and
 * blend state, and the program and vertex array - those two not by their raw ids but by unbinding
 * them through the game's own calls.
 *
 * <p>The game remembers which program and which vertex array it bound last ({@code ShaderProgram}'s
 * active program, {@code BufferRenderer}'s current vertex buffer) and skips a bind that would change
 * nothing; Sodium keeps the same kind of memory. Putting the raw ids of the moment before the pass
 * back left GL running one program while those caches named the one the pass used last, so the
 * next draw of the interface that wanted that one skipped the bind and ran with the wrong program,
 * its projection and its vertex layout: panels drawn on top of each other and at the wrong scale,
 * from the moment the mouse came over the preview (that is when this pass runs). Unbinding through
 * the game resets the caches together with GL, and every next draw binds what it needs.</p>
 */
public class GlStateGuard
{
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final byte[] colorMask = new byte[4];
    private boolean scissor;
    private boolean depthTest;
    private boolean depthMask;
    private boolean blend;
    private boolean cull;
    private int depthFunc;
    private int framebuffer;
    private int activeTexture;

    public static GlStateGuard capture()
    {
        GlStateGuard guard = new GlStateGuard();

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, guard.viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, guard.scissorBox);

        guard.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        guard.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        guard.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        guard.blend = GL11.glIsEnabled(GL11.GL_BLEND);
        guard.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        guard.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        guard.framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        guard.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        /* On the stack: this runs every frame the mouse is over the preview */
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer mask = stack.malloc(4);

            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);

            for (int i = 0; i < 4; i++)
            {
                guard.colorMask[i] = mask.get(i);
            }
        }

        return guard;
    }

    public void restore()
    {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        RenderSystem.viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);

        if (this.scissor)
        {
            RenderSystem.enableScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
        }
        else
        {
            RenderSystem.disableScissor();
        }

        RenderSystem.colorMask(this.colorMask[0] != 0, this.colorMask[1] != 0, this.colorMask[2] != 0, this.colorMask[3] != 0);
        RenderSystem.depthMask(this.depthMask);
        RenderSystem.depthFunc(this.depthFunc);

        if (this.depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (this.blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (this.cull) RenderSystem.enableCull(); else RenderSystem.disableCull();

        /* No program and no vertex array, with the game's memory of them cleared along (see above) */
        ShaderProgram any = GameRenderer.getPositionColorProgram();

        if (any != null)
        {
            any.unbind();
        }
        else
        {
            GlStateManager._glUseProgram(0);
        }

        VertexBuffer.unbind();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        RenderSystem.activeTexture(this.activeTexture);
    }
}
