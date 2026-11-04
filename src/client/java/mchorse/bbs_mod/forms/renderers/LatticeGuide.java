package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.render.LatticeDeform;
import mchorse.bbs_mod.forms.forms.ILatticeForm;
import mchorse.bbs_mod.forms.forms.ValueLattice;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Bufs;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

/**
 * A lattice as a wireframe in the form editor's preview: orange lines between neighbouring control
 * points, a faint yellow box where the undeformed grid stands, and a small cube on every point - the
 * selected one larger and white. Drawn through the body (depth off). Positions are in the actor's
 * frame, the frame the form is previewed in.
 */
public class LatticeGuide
{
    private static final float LINE = 0.01F;

    /** Where the control points of a lattice stand now (offsets on top of the grid), in the actor's frame; null without a box */
    public static Vector3f[] points(ILatticeForm form)
    {
        Vector3f[] box = LatticeDeform.box(form);

        if (box == null)
        {
            return null;
        }

        ValueLattice lattice = form.getLattice();
        int d = lattice.dimensions();
        Vector3f[] points = new Vector3f[lattice.count()];

        for (int n = 0; n < points.length; n++)
        {
            int[] cell = lattice.cell(n);
            float[] offset = lattice.get(n);

            points[n] = LatticeDeform.grid(box, d, cell[0], cell[1], cell[2]).add(offset[0], offset[1], offset[2]);
        }

        return points;
    }

    /** Where a control point stands with no offset, in the actor's frame */
    public static Vector3f rest(ILatticeForm form, int n)
    {
        Vector3f[] box = LatticeDeform.box(form);

        if (box == null || n < 0 || n >= form.getLattice().count())
        {
            return null;
        }

        int[] cell = form.getLattice().cell(n);

        return LatticeDeform.grid(box, form.getLattice().dimensions(), cell[0], cell[1], cell[2]);
    }

    public static void render(MatrixStack stack, ILatticeForm form, int selected, boolean middle)
    {
        Vector3f[] points = points(form);
        Vector3f[] box = LatticeDeform.box(form);

        if (points == null || box == null)
        {
            return;
        }

        ValueLattice lattice = form.getLattice();
        int d = lattice.dimensions();
        float size = Math.max(0.03F, Math.min(Math.min(box[1].x - box[0].x, box[1].y - box[0].y), box[1].z - box[0].z) * 0.025F);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* The undeformed box, faint */
        for (int a = 0; a < 2; a++)
        {
            for (int b = 0; b < 2; b++)
            {
                VehicleCameraGuide.line(builder, stack, corner(box, a, b, 0), corner(box, a, b, 1), LINE * 0.6F, 1F, 0.9F, 0.3F);
                VehicleCameraGuide.line(builder, stack, corner(box, a, 0, b), corner(box, a, 1, b), LINE * 0.6F, 1F, 0.9F, 0.3F);
                VehicleCameraGuide.line(builder, stack, corner(box, 0, a, b), corner(box, 1, a, b), LINE * 0.6F, 1F, 0.9F, 0.3F);
            }
        }

        /* The control cage between neighbours */
        for (int i = 0; i < d; i++)
        {
            for (int j = 0; j < d; j++)
            {
                for (int k = 0; k < d; k++)
                {
                    int here = (i * d + j) * d + k;

                    if (i + 1 < d)
                    {
                        VehicleCameraGuide.line(builder, stack, points[here], points[((i + 1) * d + j) * d + k], LINE, 1F, 0.55F, 0.1F);
                    }

                    if (j + 1 < d)
                    {
                        VehicleCameraGuide.line(builder, stack, points[here], points[(i * d + j + 1) * d + k], LINE, 1F, 0.55F, 0.1F);
                    }

                    if (k + 1 < d)
                    {
                        VehicleCameraGuide.line(builder, stack, points[here], points[(i * d + j) * d + k + 1], LINE, 1F, 0.55F, 0.1F);
                    }
                }
            }
        }

        for (int n = 0; n < points.length; n++)
        {
            Vector3f p = points[n];
            boolean lit = n == selected;
            float h = lit ? size * 1.6F : size;

            if (lit)
            {
                Draw.fillBox(builder, stack, p.x - h, p.y - h, p.z - h, p.x + h, p.y + h, p.z + h, 1F, 1F, 1F, 1F);
            }
            else
            {
                Draw.fillBox(builder, stack, p.x - h, p.y - h, p.z - h, p.x + h, p.y + h, p.z + h, 1F, 0.8F, 0.1F, 1F);
            }
        }

        /* The middle of the whole cage, the handle that moves it as one */
        if (middle)
        {
            float h = size * 2F;
            float cx = (box[0].x + box[1].x) / 2F;
            float cy = (box[0].y + box[1].y) / 2F;
            float cz = (box[0].z + box[1].z) / 2F;

            Draw.fillBox(builder, stack, cx - h, cy - h, cz - h, cx + h, cy + h, cz + h, 0.3F, 0.9F, 1F, 1F);
        }

        /* The bulge sphere: three rings round its centre */
        if (lattice.bulgeActive() || lattice.bulgeEnabled.get())
        {
            float[] sphere = lattice.bulge.get();
            int segments = 32;

            for (int axis = 0; axis < 3; axis++)
            {
                Vector3f previous = null;

                for (int s = 0; s <= segments; s++)
                {
                    double angle = Math.PI * 2D * s / segments;
                    float a = (float) Math.cos(angle) * sphere[3];
                    float b = (float) Math.sin(angle) * sphere[3];
                    Vector3f point = new Vector3f(sphere[0] + (axis == 0 ? 0F : a), sphere[1] + (axis == 1 ? 0F : axis == 0 ? a : b), sphere[2] + (axis == 2 ? 0F : b));

                    if (previous != null)
                    {
                        VehicleCameraGuide.line(builder, stack, previous, point, LINE * 0.7F, 1F, 0.3F, 0.5F);
                    }

                    previous = point;
                }
            }
        }

        Bufs.draw(builder);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static Vector3f corner(Vector3f[] box, int x, int y, int z)
    {
        return new Vector3f(x == 0 ? box[0].x : box[1].x, y == 0 ? box[0].y : box[1].y, z == 0 ? box[0].z : box[1].z);
    }
}
