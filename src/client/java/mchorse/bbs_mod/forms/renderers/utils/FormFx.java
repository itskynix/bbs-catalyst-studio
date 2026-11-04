package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The glue between a form's dissolve and shatter values and the draws that show them.
 *
 * <p>Dissolve is the model shader's own: it measures every fragment against a threshold in the form's
 * frame, drops the ones the progress has passed and lights the ones it has just reached. The shader is
 * shared, so what is set here stays set until {@link #endDissolve} - a draw that starts a dissolve
 * must end it, or the next model on screen burns too. Shader packs replace the program outright, so
 * under one there is no dissolve ({@link #supportsDissolve}); shattering is geometry and works
 * everywhere.</p>
 */
public final class FormFx
{
    /* The inverse of the model-view the form's own frame sits at, while a dissolve measures vertex
     * positions that are local to whatever bone is drawing; null otherwise */
    private static Matrix4f relativeRoot;
    private static final Matrix4f TEMP = new Matrix4f();

    private FormFx()
    {}

    /** Whether the BBS model shader can burn a form here: it exists, and no shader pack replaces it. */
    public static boolean supportsDissolve()
    {
        return BBSShaders.getModel() != null && !BBSRendering.isIrisShadersEnabled();
    }

    /* Dissolve */

    private static boolean params(FormFxValues fx, float minY, float height)
    {
        ShaderProgram program = BBSShaders.getModel();

        if (program == null)
        {
            return false;
        }

        Color burn = fx.dissolveBurnColor.get();
        GlUniform uniform = program.getUniform("DissolveParams");

        if (uniform != null)
        {
            uniform.set(fx.dissolveProgress.get(), fx.dissolveEdge.get(), (float) fx.dissolveMode.get(), 3F / Math.max(height, 0.01F));
        }

        uniform = program.getUniform("DissolveBurn");

        if (uniform != null)
        {
            uniform.set(burn.r, burn.g, burn.b, 1F);
        }

        uniform = program.getUniform("DissolveRange");

        if (uniform != null)
        {
            uniform.set(minY, height);
        }

        return true;
    }

    /**
     * Start a dissolve for vertices written in a space of their own - what vanilla's consumers and the
     * billboard's buffer hold - rather than a model's local one.
     *
     * @param toLocal takes a vertex position to the form's frame, where the range is measured
     * @param minY the lowest point of the form in that frame
     * @param height how tall it is
     */
    public static boolean beginDissolve(FormFxValues fx, Matrix4f toLocal, float minY, float height)
    {
        if (!params(fx, minY, height))
        {
            return false;
        }

        ShaderProgram program = BBSShaders.getModel();
        GlUniform matrix = program.getUniform("DissolveMat");

        relativeRoot = null;

        if (matrix != null)
        {
            matrix.set(toLocal);
        }

        /* These vertices have their normals baked in already */
        GlUniform normal = program.getUniform("NormalMat");

        if (normal != null)
        {
            normal.set(new Matrix3f());
        }

        return true;
    }

    /**
     * Start a dissolve for vertex arrays that are drawn per bone: each draw's position is local to its
     * bone, and {@link #onVaoDraw} walks it back to the model's frame.
     *
     * @param rootModelView the model-view of the form's frame, as captured before any bone moved it
     */
    public static boolean beginDissolveVao(FormFxValues fx, Matrix4f rootModelView, float minY, float height)
    {
        if (!params(fx, minY, height))
        {
            return false;
        }

        relativeRoot = new Matrix4f(rootModelView).invert();

        return true;
    }

    /** Called by every draw of a vertex array, with the model-view that draw is made at. */
    public static void onVaoDraw(ShaderProgram shader, Matrix4f modelView)
    {
        if (relativeRoot == null)
        {
            return;
        }

        GlUniform matrix = shader.getUniform("DissolveMat");

        if (matrix != null)
        {
            matrix.set(TEMP.set(relativeRoot).mul(modelView));
        }
    }

    public static void endDissolve()
    {
        ShaderProgram program = BBSShaders.getModel();

        relativeRoot = null;

        if (program == null)
        {
            return;
        }

        GlUniform uniform = program.getUniform("DissolveParams");

        if (uniform != null)
        {
            uniform.set(0F, 0.1F, 1F, 1F);
        }

        uniform = program.getUniform("DissolveMat");

        if (uniform != null)
        {
            uniform.set(new Matrix4f());
        }
    }

    /**
     * For the per-layer hook of the consumer provider: draw the layer with the model shader, so it
     * dissolves. Layers in some other vertex layout - the enchantment glint, say - are left alone.
     */
    public static void useDissolveProgram(RenderLayer layer)
    {
        if (layer.getVertexFormat() == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
        {
            RenderSystem.setShader(BBSShaders::getModel);
        }
    }

    /**
     * The per-layer hook of a block or an item that dissolves: blending on, whatever the draw needs
     * for itself (the color overlay), and then the model shader over the layer's own.
     */
    public static Consumer<RenderLayer> withDissolve(Consumer<RenderLayer> also)
    {
        return (layer) ->
        {
            RenderSystem.enableBlend();

            if (also != null)
            {
                also.accept(layer);
            }

            useDissolveProgram(layer);
        };
    }

    /* Shatter */

    /** The substitute that bends what is drawn through it by a lattice, over the ones below it. */
    public static Function<VertexConsumer, VertexConsumer> lattice(Function<VertexConsumer, VertexConsumer> inner, Matrix4f forward, mchorse.bbs_mod.cubic.render.LatticeDeform deform)
    {
        Matrix4f frame = new Matrix4f(forward);

        return (buffer) -> new LatticeVertexConsumer(inner == null ? buffer : inner.apply(buffer), frame, deform);
    }

    /** The substitute that breaks what is drawn through it into pieces, over the one that tints it. */
    public static Function<VertexConsumer, VertexConsumer> shatter(Function<VertexConsumer, VertexConsumer> inner, ShatterField field)
    {
        return (buffer) -> new ShatterVertexConsumer(inner == null ? buffer : inner.apply(buffer), field);
    }
}
