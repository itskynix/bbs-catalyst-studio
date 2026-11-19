package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.forms.NavigationVolumeForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.nav.NavGrid;
import mchorse.bbs_mod.nav.NavGrids;
import mchorse.bbs_mod.nav.NavPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.Bufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.qualet.irl.light.shadow.ShadowBakeState;
import qualet.irlite.client.light.IRLightPositionResolver;

/**
 * The navigation volume draws its box as a guide and, while the P key has the meshes shown, the mesh
 * itself: the floors an agent can stand on as a green translucent cover, the edges and obstacles as
 * a red one. In the editor preview there is no world to scan, so only the box is drawn.
 */
public class NavigationVolumeFormRenderer extends FormRenderer<NavigationVolumeForm>
{
    /** The most floors drawn at once, so a huge volume does not cost a frame */
    private static final int MAX_DRAWN = 30000;

    public NavigationVolumeFormRenderer(NavigationVolumeForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        int r = Math.max(4, Math.min(x2 - x1, y2 - y1) / 3);

        context.batcher.gradientVBox(x1 + 4, y1 + 4, x2 - 4, y2 - 4, 0x1030e070, 0x5030e070);
        context.batcher.outline(cx - r, cy - r, cx + r, cy + r, 0xff30e070);

        for (int i = -r + 3; i < r; i += 6)
        {
            context.batcher.box(cx + i, cy - 1, cx + i + 3, cy + 1, 0xff30e070);
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        if (context.isPicking() || BBSRendering.isIrisShadowPass() || ShadowBakeState.isBaking() || FormPortals.isRenderingPortal())
        {
            return;
        }

        FormRenderType type = context.type;
        boolean editor = context.ui || context.modelRenderer || type == FormRenderType.PREVIEW;
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!editor)
        {
            if (type != FormRenderType.ENTITY && type != FormRenderType.MODEL_BLOCK)
            {
                return;
            }

            if (mc.options.hudHidden || BBSModClient.getVideoRecorder().isRecording())
            {
                return;
            }
        }

        boolean guides = this.form.showGuides.get() && (editor || type == FormRenderType.MODEL_BLOCK || mc.currentScreen instanceof UIScreen || NavPreview.isVisible());
        boolean mesh = !editor && NavPreview.isVisible();

        if (!guides && !mesh)
        {
            return;
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        if (guides)
        {
            this.box(builder, context.stack);
        }

        if (mesh)
        {
            this.mesh(builder, context);
        }

        Bufs.draw(builder);
        RenderSystem.disableBlend();
    }

    private void box(BufferBuilder builder, MatrixStack stack)
    {
        float hw = this.form.width.get() / 2F;
        float hl = this.form.length.get() / 2F;
        float top = this.form.height.get();
        float t = 0.03F;

        for (int sy = 0; sy <= 1; sy++)
        {
            for (int sz = -1; sz <= 1; sz += 2)
            {
                Draw.fillBox(builder, stack, -hw, sy * top - t, sz * hl - t, hw, sy * top + t, sz * hl + t, 0.19F, 0.88F, 0.44F, 0.9F);
            }

            for (int sx = -1; sx <= 1; sx += 2)
            {
                Draw.fillBox(builder, stack, sx * hw - t, sy * top - t, -hl, sx * hw + t, sy * top + t, hl, 0.19F, 0.88F, 0.44F, 0.9F);
            }
        }

        for (int sx = -1; sx <= 1; sx += 2)
        {
            for (int sz = -1; sz <= 1; sz += 2)
            {
                Draw.fillBox(builder, stack, sx * hw - t, 0F, sz * hl - t, sx * hw + t, top, sz * hl + t, 0.19F, 0.88F, 0.44F, 0.9F);
            }
        }
    }

    /** The floors, relative to the form: the form's world place is taken off, which is all it is to a mesh scanned in world blocks */
    private void mesh(BufferBuilder builder, FormRenderingContext context)
    {
        Matrix4f world;

        if (context.world != null && context.entity != null)
        {
            world = new Matrix4f(context.world.peek().getPositionMatrix());
        }
        else
        {
            org.joml.Vector3d origin = IRLightPositionResolver.resolve(context);

            world = new Matrix4f().translation((float) origin.x, (float) origin.y, (float) origin.z);
        }

        Vector3f at = world.getTranslation(new Vector3f());
        NavGrid grid = NavGrids.get(this.form, at.x, at.y, at.z);

        if (grid == null)
        {
            return;
        }

        float alpha = this.form.previewOpacity.get();
        boolean borders = this.form.showBorders.get();
        int drawn = 0;
        MatrixStack stack = context.stack;

        for (NavGrid.Node node : grid.getNodes())
        {
            if (drawn++ >= MAX_DRAWN)
            {
                break;
            }

            float x1 = node.x - at.x + 0.04F;
            float z1 = node.z - at.z + 0.04F;
            float y = (float) node.y - at.y + 0.02F;
            float r = 0.2F;
            float g = 0.85F;
            float b = 0.3F;
            float a = alpha;

            if (node.edge && borders)
            {
                r = 0.95F;
                g = 0.15F;
                b = 0.15F;
                a = Math.min(1F, alpha + 0.15F);
            }

            Draw.fillQuad(builder, stack, x1, y, z1, x1, y, z1 + 0.92F, x1 + 0.92F, y, z1 + 0.92F, x1 + 0.92F, y, z1, r, g, b, a);
        }
    }
}
