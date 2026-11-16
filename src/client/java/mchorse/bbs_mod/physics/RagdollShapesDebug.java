package mchorse.bbs_mod.physics;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.Bufs;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The form editor's live collision preview: the box of every ragdoll bone as a green wireframe and every joint as
 * a yellow cross, drawn over the model being edited in the form editor and nowhere else.
 */
public final class RagdollShapesDebug
{
    /** The switch in the form editor's Physics tab */
    public static boolean enabled = true;

    private static ModelForm edited;
    private static long shownAt;

    private RagdollShapesDebug()
    {}

    /** Called while the Physics tab of the form editor is on screen, with the form it edits */
    public static void editing(ModelForm form)
    {
        edited = form;
        shownAt = System.currentTimeMillis();
    }

    public static void render(MatrixStack stack, IModel model, ModelForm form)
    {
        if (!enabled || form == null || form != edited || System.currentTimeMillis() - shownAt > 500L || form.physicsMode.get() == 0 || !(model instanceof Model cubic))
        {
            return;
        }

        List<RagdollSampler.Bone> bones = RagdollSampler.bones(cubic, form);
        Set<String> ids = new LinkedHashSet<>();

        for (RagdollSampler.Bone bone : bones)
        {
            ids.add(bone.id());
        }

        Map<String, PivotFrame> frames = new HashMap<>();

        ModelPivotFrames.collect(cubic, ids, frames);

        Matrix4f matrix = new Matrix4f(stack.peek().getPositionMatrix());
        BufferBuilder lines = Bufs.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

        for (RagdollSampler.Bone bone : bones)
        {
            PivotFrame frame = frames.get(bone.id());

            if (frame == null)
            {
                continue;
            }

            Quaternionf q = new Quaternionf(frame.worldRotation()).normalize();
            Vector3f[] corners = new Vector3f[8];

            for (int i = 0; i < 8; i++)
            {
                Vector3f local = new Vector3f(bone.center()).add((i & 1) == 0 ? -bone.half().x : bone.half().x, (i & 2) == 0 ? -bone.half().y : bone.half().y, (i & 4) == 0 ? -bone.half().z : bone.half().z);

                corners[i] = q.transform(local).add(frame.position());
            }

            for (int[] e : edges)
            {
                vertex(lines, matrix, corners[e[0]], 0.25F, 0.88F, 0.63F);
                vertex(lines, matrix, corners[e[1]], 0.25F, 0.88F, 0.63F);
            }

            if (bone.parent() >= 0)
            {
                Vector3f p = frame.position();
                float s = 0.05F;

                for (Vector3f axis : new Vector3f[] {new Vector3f(s, 0F, 0F), new Vector3f(0F, s, 0F), new Vector3f(0F, 0F, s)})
                {
                    vertex(lines, matrix, new Vector3f(p).sub(axis), 1F, 0.82F, 0.25F);
                    vertex(lines, matrix, new Vector3f(p).add(axis), 1F, 0.82F, 0.25F);
                }
            }
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        Bufs.draw(lines);
        RenderSystem.enableDepthTest();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vector3f p, float r, float g, float b)
    {
        builder.vertex(matrix, p.x, p.y, p.z).color(r, g, b, 0.9F)/*$ nx*/.next()/**/;
    }
}
