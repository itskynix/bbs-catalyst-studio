package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * The state interface is drawn in. Anything that draws a world into the middle of the interface
 * (the film preview, picking passes, post-production) hands back whatever it left behind, and
 * since 1.21 less of the interface sets this up for itself.
 */
public class GuiState
{
    /** The orthographic projection and model-view the game set up for the interface this frame. */
    private static final Matrix4f baseProjection = new Matrix4f();
    private static final Matrix4f baseModelView = new Matrix4f();
    private static boolean hasBase;

    /**
     * Remembers the matrices the game drew the interface with this frame: it builds them from the
     * window's framebuffer size and scale factor (the clip planes and the depth the interface sits
     * at differ between versions), so they are copied rather than rebuilt here.
     */
    public static void captureBase()
    {
        baseProjection.set(RenderSystem.getProjectionMatrix());
        baseModelView.set(RenderSystem.getModelViewMatrix());

        hasBase = true;
    }

    /** Puts back everything but the depth function, which the menu sets for its own span. */
    public static void restore()
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);

        /* A portal pass cuts its window into the stencil; none of it is the interface's */
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    /**
     * The window as the interface's coordinates see it: the viewport over the whole framebuffer,
     * and the projection and model-view the game drew the interface with. A 3D pass in the middle
     * of the interface moves all three to its own, and a panel drawn after it would be scaled
     * and placed by the scene's camera.
     */
    public static void restoreProjection()
    {
        Window window = MinecraftClient.getInstance().getWindow();

        RenderSystem.viewport(0, 0, window.getFramebufferWidth(), window.getFramebufferHeight());

        if (!hasBase)
        {
            return;
        }

        RenderSystem.setProjectionMatrix(baseProjection, VertexSorter.BY_Z);

        /* The applied matrix is not necessarily the stack's top, so it goes through a level of its own */
        ModelViewStack.push();
        ModelViewStack.loadIdentity();
        ModelViewStack.multiply(baseModelView);
        RenderSystem.applyModelViewMatrix();
        ModelViewStack.pop();
    }

    /** A draw context's matrices as a menu starts them: nothing pushed on, nothing carried over. */
    public static void resetMatrices(MatrixStack stack)
    {
        stack.peek().getPositionMatrix().identity();
        stack.peek().getNormalMatrix().identity();
    }

    /** {@link #restore()} for inside a menu, where every draw passes the depth test. */
    public static void restoreInMenu()
    {
        restore();
        restoreProjection();

        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }
}
