package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.lights.Light;
import mchorse.bbs_mod.graphics.lights.LightManager;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;

/**
 * A light form draws nothing the film shows. It hands its light to the {@link LightManager} for the
 * frame, and, in the editor only, a small marker where it stands.
 */
public class LightFormRenderer extends FormRenderer<LightForm>
{
    public LightFormRenderer(LightForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Color color = this.form.color.get();
        int rgb = Colors.A100 | ((int) (color.r * 255F) << 16) | ((int) (color.g * 255F) << 8) | (int) (color.b * 255F);

        context.batcher.icon(LightForm.ICON, rgb, (x1 + x2) / 2, (y1 + y2) / 2, 0.5F, 0.5F);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        if (context.ui || context.isPicking() || BBSRendering.isIrisShadowPass())
        {
            return;
        }

        LightManager.submit(Light.of(this.form, context.stack.peek().getPositionMatrix()));

        /* The marker is part of the editor, not of the picture: not in F1, not while a film plays or exports */
        if (!this.form.marker.get() || MinecraftClient.getInstance().options.hudHidden || AudioFormRenderer.isAnyFilmPlaying())
        {
            return;
        }

        Color color = this.form.color.get();
        float size = 0.12F;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        Draw.renderBox(context.stack, -size / 2, -size / 2, -size / 2, size, size, size, color.r, color.g, color.b, 0.9F);

        /* A spot light and a directional light also show the way they point */
        if (this.form.lightType.get() != LightForm.POINT)
        {
            Draw.renderBox(context.stack, -0.015, -0.015, size / 2, 0.03, 0.03, 0.35, color.r, color.g, color.b, 0.9F);
        }

        RenderSystem.enableCull();
    }
}
