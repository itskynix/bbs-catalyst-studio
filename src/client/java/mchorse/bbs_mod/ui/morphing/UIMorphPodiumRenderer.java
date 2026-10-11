package mchorse.bbs_mod.ui.morphing;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * Live 3D Podium preview renderer for the Modern Morphing Studio.
 * Features an illuminated 3D pedestal scene, concentric glowing rings,
 * idle turntable rotation, and smooth scale pop entrance transition.
 */
public class UIMorphPodiumRenderer extends UIModelRenderer
{
    public Form form;

    private float idleYaw = 0F;
    private float scalePop = 1F;
    private long lastTime = System.currentTimeMillis();

    public UIMorphPodiumRenderer()
    {
        super();

        this.setDistance(2.4F);
        this.setPosition(0, 0.9F, 0);
        this.setRotation(0, 10F);
        this.grid = true;
    }

    public void setForm(Form form)
    {
        if (this.form != form)
        {
            this.form = form;
            this.scalePop = 0.95F;
        }
    }

    @Override
    public void render(UIContext context)
    {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (now - this.lastTime) / 1000F);
        this.lastTime = now;

        /* Podyum Turntable (Idle Rotation): Yavaş ve akıcı kendi ekseni etrafında dönüş */
        if (!this.isDragging())
        {
            this.idleYaw += dt * 25F;

            if (this.idleYaw >= 360F)
            {
                this.idleYaw -= 360F;
            }

            this.setRotation(this.idleYaw, 10F);
        }
        else
        {
            this.idleYaw = (float) Math.toDegrees(this.camera.rotation.y);
        }

        /* Scale Pop (Giriş Animasyonu): 0.95 -> 1.0 yumuşak lerp geçişi */
        this.scalePop = Lerps.lerp(this.scalePop, 1.0F, 0.15F);

        super.render(context);

        /* Form seçilmediğinde merkezde şık bilgilendirme etiketi */
        if (this.form == null)
        {
            Area a = this.area;
            FontRenderer font = context.batcher.getFont();
            String hint = "Form Seçilmedi / No Form Selected";
            int w = font.getWidth(hint);

            context.batcher.text(hint, a.mx() - w / 2, a.my() - 4, Colors.setA(Colors.WHITE, 0.45F));
        }
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        Matrix4f matrix4f = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        int primary = BBSSettings.primaryColor.get();
        float r = ((primary >> 16) & 0xFF) / 255F;
        float g = ((primary >> 8) & 0xFF) / 255F;
        float b = (primary & 0xFF) / 255F;

        int segments = 36;
        float r1 = 0.85F; /* İç parlayan podyum halkası */
        float r2 = 1.35F; /* Dış zemin çemberi */

        for (int i = 0; i < segments; i++)
        {
            double a1 = (i * 2 * Math.PI) / segments;
            double a2 = ((i + 1) * 2 * Math.PI) / segments;

            float x1_1 = (float) (Math.cos(a1) * r1);
            float z1_1 = (float) (Math.sin(a1) * r1);
            float x1_2 = (float) (Math.cos(a2) * r1);
            float z1_2 = (float) (Math.sin(a2) * r1);

            builder.vertex(matrix4f, x1_1, 0, z1_1).color(r, g, b, 0.70F).next();
            builder.vertex(matrix4f, x1_2, 0, z1_2).color(r, g, b, 0.70F).next();

            float x2_1 = (float) (Math.cos(a1) * r2);
            float z2_1 = (float) (Math.sin(a1) * r2);
            float x2_2 = (float) (Math.cos(a2) * r2);
            float z2_2 = (float) (Math.sin(a2) * r2);

            builder.vertex(matrix4f, x2_1, 0, z2_1).color(r, g, b, 0.28F).next();
            builder.vertex(matrix4f, x2_2, 0, z2_2).color(r, g, b, 0.28F).next();

            /* Radyal bağ çizgileri (8 yönde) */
            if (i % 4 == 0)
            {
                builder.vertex(matrix4f, x1_1, 0, z1_1).color(r, g, b, 0.50F).next();
                builder.vertex(matrix4f, x2_1, 0, z2_1).color(r, g, b, 0.15F).next();
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    @Override
    protected void renderUserModelOverlay(UIContext context)
    {
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        MatrixStack matrices = context.batcher.getContext().getMatrices();

        matrices.push();

        if (this.scalePop != 1F)
        {
            matrices.scale(this.scalePop, this.scalePop, this.scalePop);
        }

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, matrices, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer(context.getTick());

        FormUtilsClient.render(this.form, formContext);

        matrices.pop();
    }
}
