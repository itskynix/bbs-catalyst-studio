package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.FormRenderLast;
import mchorse.bbs_mod.forms.forms.PortalForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Matrix4f;

/**
 * A portal form draws no geometry of its own: it tells {@link FormPortals} where its window is, and
 * the pass closing does the rest. Its body parts draw as usual, so a frame around the window is just
 * another form hung on it.
 */
public class PortalFormRenderer extends FormRenderer<PortalForm>
{
    public PortalFormRenderer(PortalForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.icon(PortalForm.ICON, Colors.WHITE, (x1 + x2) / 2F, (y1 + y2) / 2F, 0.5F, 0.5F);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        /* Picking would only find a thin window; it is picked from the outliner */
        if (context.isPicking())
        {
            return;
        }

        /* Not in the scene seen through a window: there the partner is not drawn at all */
        if (FormPortals.isRenderingPortal())
        {
            return;
        }

        Matrix4f matrix = context.stack.peek().getPositionMatrix();

        FormPortals.seen(this.form, context.world == null ? null : context.world.peek().getPositionMatrix());

        /* The window of a film's form or an entity's, inside a render scope, and of a model block, which
         * draws with the block entities after the scope has closed. Outside those (the editor preview, a
         * shader pack's shadow pass) there is nobody to draw through it. */
        if (FormRenderLast.isActive() || (context.type == FormRenderType.MODEL_BLOCK && !BBSRendering.isIrisShadowPass()))
        {
            FormPortals.addWindow(this.form, matrix);
        }

        if (this.form.showFrame.get())
        {
            FormPortals.drawFrame(this.form, matrix);
        }
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        /* The partner and everything hung on it stay out of the scene seen through a window */
        if (FormPortals.isRenderingPortal())
        {
            return;
        }

        super.renderBodyParts(context);
    }
}
