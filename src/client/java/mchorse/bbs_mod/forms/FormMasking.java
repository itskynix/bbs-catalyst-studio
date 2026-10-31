package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.FormMask;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.forms.editors.utils.MaskEditing;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import mchorse.bbs_mod.client.BBSShaders;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a form through its {@link FormMask}. The shape is written into the stencil buffer in the
 * form's own space, and the form is then drawn only where the stencil holds that value - so any
 * model, however it is rendered, is clipped the same way.
 *
 * <p>The cut is hard: one stencil value, one draw of the form. Nothing is written to colour or
 * depth while the shape is cut, so the shape cannot punch holes into the form or fight its depth,
 * and the form is drawn once, so there is no second pass to stipple the pixels.</p>
 */
public class FormMasking
{
    /** How long a mask waits for the framebuffer's stencil before the form is drawn without it. */
    private static final long STENCIL_WAIT_MS = 400L;

    /** When the first mask found no stencil, 0 once there is one. */
    private static long waitingSince;

    /** Whether the missing stencil has been said once since it last was there. */
    private static boolean reported;

    /**
     * The stencil bit a mask uses inside another stencil user. The portal pass keeps its window
     * references below it ({@code FormPortals}), so the two never share a bit.
     */
    public static final int NESTED_BIT = 0x80;

    /** The stencil test of an outer user, taken before a mask works inside it and given back after. */
    private static class StencilState
    {
        int func;
        int ref;
        int valueMask;
        int writeMask;
        int fail;
        int depthFail;
        int depthPass;

        static StencilState capture()
        {
            StencilState state = new StencilState();

            state.func = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
            state.ref = GL11.glGetInteger(GL11.GL_STENCIL_REF);
            state.valueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK) & 0xFF;
            state.writeMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK) & 0xFF;
            state.fail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
            state.depthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
            state.depthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);

            return state;
        }

        void restore()
        {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(this.func, this.ref, this.valueMask);
            GL11.glStencilOp(this.fail, this.depthFail, this.depthPass);
            GL11.glStencilMask(this.writeMask);
        }
    }

    /**
     * Run {@code draw} - the form and its body parts - through the form's mask. With no mask
     * it is simply run.
     */
    public static void render(FormRenderingContext context, Form form, Runnable draw)
    {
        FormMask mask = form.mask;

        if (mask.isSlicing() && !context.isPicking())
        {
            slice(context, form, draw);

            return;
        }

        if (!mask.isActive() || context.isPicking())
        {
            draw.run();

            return;
        }

        if (!FormPortals.ensureStencil(false))
        {
            /* The framebuffer gets its stencil as the next frame begins; until then drawing the
             * form unmasked would show what the mask is there to hide. A buffer that never gets
             * one (an offscreen target built without) must not make the form vanish for good,
             * so after a short wait it is drawn whole. */
            long now = System.currentTimeMillis();

            if (waitingSince == 0L)
            {
                waitingSince = now;
            }

            FormPortals.requestStencil();

            if (now - waitingSince > STENCIL_WAIT_MS)
            {
                /* Only drawn whole, never given up on: the next frame asks again */
                if (!reported)
                {
                    reported = true;

                    System.err.println("[BBS] Form mask: the framebuffer being drawn into (" + GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) + ") has no stencil buffer; the form is drawn without its mask.");
                }

                draw.run();
            }

            return;
        }

        waitingSince = 0L;
        reported = false;

        Matrix4f matrix = context.stack.peek().getPositionMatrix();
        List<Vector3f> outline = mask.outline();
        List<int[]> triangles = triangulate(mask.localOutline());
        boolean invert = mask.invert.get();
        boolean wasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean wasCulling = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean wasDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean wasDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int wasDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        /* Draws deferred to the end of the frame would run without the stencil: draw them now */
        boolean wasQueued = FormTranslucentQueue.suspend();

        /* Inside another stencil user - a portal pass holds its window in the stencil - the mask
         * keeps to a bit of its own, and the outer test is kept and given back exactly: a mask
         * that wrote over the window, or left the test open behind it, let everything drawn after
         * it in the pass out of the window and all over the world. */
        StencilState outer = wasEnabled ? StencilState.capture() : null;
        int bit = wasEnabled ? NESTED_BIT : 0xFF;
        int cutRef = wasEnabled ? NESTED_BIT : 1;

        try
        {
            GL11.glStencilMask(0xFF);

            /* Whatever an earlier frame (a mask dragged fast leaves a new footprint every frame)
             * or another draw left in the stencil is wiped first. Inside another stencil user
             * only the mask's own bit is touched. */
            if (!wasEnabled)
            {
                GL11.glClearStencil(0);
                GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            }

            GL11.glEnable(GL11.GL_STENCIL_TEST);

            /* Cut: the shape is written into the mask's bit, whatever the depth buffer holds,
             * touching neither colour nor depth */
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            GL11.glStencilMask(bit);
            GL11.glStencilFunc(GL11.GL_ALWAYS, cutRef, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);

            fill(matrix, outline, triangles);

            /* Draw: the form once, where the stencil is 1 (or, inverted, where it is not) */
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(wasDepthMask);

            if (wasDepthTest)
            {
                RenderSystem.enableDepthTest();
            }

            if (wasCulling)
            {
                RenderSystem.enableCull();
            }

            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            GL11.glStencilMask(0x00);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            if (outer == null)
            {
                GL11.glStencilFunc(invert ? GL11.GL_NOTEQUAL : GL11.GL_EQUAL, 1, 0xFF);
            }
            else if (outer.func == GL11.GL_EQUAL)
            {
                /* Both at once: the outer value on the bits it tests, and the mask's bit set (or,
                 * inverted, clear) */
                int outerMask = outer.valueMask & ~NESTED_BIT;
                int outerRef = outer.ref & outerMask;

                GL11.glStencilFunc(GL11.GL_EQUAL, outerRef | (invert ? 0 : NESTED_BIT), outerMask | NESTED_BIT);
            }
            else
            {
                /* An outer test that is not a plain match cannot be folded in; the mask's bit
                 * alone decides */
                GL11.glStencilFunc(GL11.GL_EQUAL, invert ? 0 : NESTED_BIT, NESTED_BIT);
            }

            draw.run();
        }
        finally
        {
            FormTranslucentQueue.restore(wasQueued);

            RenderSystem.depthFunc(wasDepthFunc);

            /* Give the stencil back as it was found */
            GL11.glStencilMask(0xFF);

            if (!wasEnabled)
            {
                /* Nobody else was using it: all of it is cleared, and the test goes off */
                GL11.glClearStencil(0);
                GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
                GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            else
            {
                /* Only the mask's bit of its footprint goes back to zero; the outer user's bits
                 * under it were never written */
                GL11.glStencilMask(NESTED_BIT);
                GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.depthMask(false);
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();

                fill(matrix, outline, triangles);
            }

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(wasDepthMask);

            if (wasDepthTest)
            {
                RenderSystem.enableDepthTest();
            }

            if (wasCulling)
            {
                RenderSystem.enableCull();
            }

            if (outer != null)
            {
                /* The outer user's test, exactly as it was: its function, value, masks, ops */
                outer.restore();
            }
            else
            {
                /* Left closed: no later draw may write into the stencil by accident */
                GL11.glStencilMask(0x00);
            }
        }
    }

    /** The eye space plane the model shader keeps the positive side of, while a sliced form draws; null when none. */
    private static Vector4f currentSlice;

    /**
     * The form's slice plane in its own space: through {@code normal * offset}, the normal being up
     * turned by yaw, pitch and roll. Its fourth component makes {@code dot(plane, point) >= 0} the kept side.
     */
    public static Vector4f slicePlane(Form form)
    {
        Vector3f normal = sliceRotation(form).transform(new Vector3f(0F, 1F, 0F));
        Vector4f plane = new Vector4f(normal, -normal.dot(sliceCenter(form)));

        return form.sliceInvert.get() ? plane.mul(-1F) : plane;
    }

    /** Pitch, yaw and roll composed the way a transform's euler angles are (ZYX), the gizmo's own order. */
    public static Matrix3f sliceRotation(Form form)
    {
        return new Matrix3f().rotation(Matrices.toQuaternionZYXRadians(
            (float) Math.toRadians(form.slicePitch.get()),
            (float) Math.toRadians(form.sliceYaw.get()),
            (float) Math.toRadians(form.sliceRoll.get())
        ));
    }

    /** The middle of the slice plane, in the form's space. */
    public static Vector3f sliceCenter(Form form)
    {
        return new Vector3f(form.slicePosX.get(), form.slicePosY.get(), form.slicePosZ.get());
    }

    /**
     * The plane of the slice being drawn right now, in eye space (what the model shader is given),
     * or null with no slice. For the forms drawn by other programs, which cut by it on the CPU.
     */
    public static Vector4f getSlicePlane()
    {
        return currentSlice == null ? null : new Vector4f(currentSlice);
    }

    /** Whether a form is being drawn through a 3D slice right now: blocks and items take the model shader then. */
    public static boolean isSliceActive()
    {
        return currentSlice != null;
    }

    /**
     * A 3D slice: the plane is taken from the form's space to the eye's (through the form's matrix,
     * the inverse transposed carries planes) and handed to the model shader, which drops every
     * fragment on its far side. Depth is untouched, so the cut form stands in the world like any other.
     * Only the BBS model shader knows the plane: forms drawn by other programs (vanilla layers, a
     * shader pack's) are drawn whole.
     */
    private static void slice(FormRenderingContext context, Form form, Runnable draw)
    {
        ShaderProgram program = BBSShaders.getModel();
        GlUniform uniform = program == null ? null : program.getUniform("SlicePlane");

        if (uniform == null)
        {
            draw.run();

            return;
        }

        /* Eye space is what the shader multiplies Position by: the applied model-view, then the stack */
        Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(context.stack.peek().getPositionMatrix());
        Vector4f plane = new Matrix4f(matrix).invert().transpose().transform(slicePlane(form));
        Vector4f previous = currentSlice;

        /* Draws deferred to the end of the frame would replay after the plane is gone: draw them now */
        boolean wasQueued = FormTranslucentQueue.suspend();

        currentSlice = plane;
        uniform.set(plane.x, plane.y, plane.z, plane.w);

        try
        {
            draw.run();
        }
        finally
        {
            FormTranslucentQueue.restore(wasQueued);

            /* A slice inside a slice gives the outer one back; the last one gives back the whole form */
            currentSlice = previous;

            if (previous == null)
            {
                uniform.set(0F, 0F, 0F, 1F);
            }
            else
            {
                uniform.set(previous.x, previous.y, previous.z, previous.w);
            }
        }
    }

    /**
     * The editor's guide of a slice: a square of the plane, two units across, with a stalk along the
     * side it keeps. Drawn in the form's own space, like the mask's guide.
     */
    public static void renderSliceGuide(MatrixStack stack, Form form, float handleSize)
    {
        Matrix3f rotation = sliceRotation(form);
        Vector3f normal = rotation.transform(new Vector3f(0F, 1F, 0F));
        Vector3f center = sliceCenter(form);
        Vector3f[] corners = new Vector3f[4];
        float line = handleSize * 0.4F;
        Matrix4f matrix = stack.peek().getPositionMatrix();

        for (int i = 0; i < 4; i++)
        {
            corners[i] = rotation.transform(new Vector3f(i == 1 || i == 2 ? 1F : -1F, 0F, i >= 2 ? 1F : -1F)).add(center);
        }

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < 4; i++)
        {
            segment(builder, matrix, corners[i], corners[(i + 1) % 4], line, 1F, 0.35F, 0.6F);
        }

        Vector3f kept = new Vector3f(normal).mul(form.sliceInvert.get() ? -0.5F : 0.5F).add(center);

        segment(builder, matrix, center, kept, line * 0.6F, 1F, 0.85F, 0.1F);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        Bufs.draw(builder);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /**
     * The editor's guide of a mask: its outline as a coloured wireframe, and a handle on every
     * point that defines it, the one being dragged or hovered lit. Drawn in the form's own space
     * (the stack stands at the form's frame), on top of the scene. Only the form editor's
     * viewport calls it - never a film that plays or is exported.
     */
    public static void renderGuide(MatrixStack stack, FormMask mask, float handleSize)
    {
        List<Vector3f> outline = mask.outline();
        List<MaskEditing.Handle> handles = MaskEditing.handles(mask);
        float line = handleSize * 0.4F;
        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        if (outline.isEmpty())
        {
            /* A custom shape still short of three points is drawn as the open line it is */
            for (int i = 1; i < handles.size(); i++)
            {
                Vector3f a = handles.get(i - 1).position;
                Vector3f b = handles.get(i).position;

                segment(builder, matrix, a, b, line, 0.15F, 0.85F, 1F);
            }
        }

        for (int i = 0; i < outline.size(); i++)
        {
            Vector3f a = outline.get(i);
            Vector3f b = outline.get((i + 1) % outline.size());

            segment(builder, matrix, a, b, line, 0.15F, 0.85F, 1F);
        }

        for (MaskEditing.Handle handle : handles)
        {
            Vector3f p = handle.position;
            float size = handleSize;
            float g = 0.85F;
            float b = 0.1F;

            if (handle.link != null)
            {
                /* The rotation handle hangs off the shape by a stalk */
                segment(builder, matrix, handle.link, p, line * 0.6F, 0.8F, 0.8F, 0.8F);
            }

            if (MaskEditing.isLit(handle))
            {
                size *= 1.5F;
                g = 0.4F;
            }

            Draw.fillBox(builder, stack, p.x - size, p.y - size, p.z - size, p.x + size, p.y + size, p.z + size, 1F, g, b, 1F);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        Bufs.draw(builder);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /**
     * One edge of the guide: a thin bar from a to b, a straight run between the two points whichever
     * way they lie. Its sides are found from the edge itself (two perpendiculars), not from angles,
     * so a bar tipped in any direction stays on its two points and the bars of a loop meet.
     */
    private static void segment(BufferBuilder builder, Matrix4f matrix, Vector3f a, Vector3f b, float half, float r, float g, float blue)
    {
        Vector3f direction = b.sub(a, new Vector3f());

        if (direction.lengthSquared() < 1.0E-12F)
        {
            return;
        }

        direction.normalize();

        /* Any axis the edge is not along gives a perpendicular */
        Vector3f reference = Math.abs(direction.y) < 0.9F ? new Vector3f(0F, 1F, 0F) : new Vector3f(1F, 0F, 0F);
        Vector3f u = direction.cross(reference, new Vector3f()).normalize(half);
        Vector3f v = direction.cross(u, new Vector3f()).normalize(half);
        Vector3f[] corners = new Vector3f[8];

        for (int i = 0; i < 8; i++)
        {
            Vector3f end = (i & 4) == 0 ? a : b;
            float su = (i & 1) == 0 ? -1F : 1F;
            float sv = (i & 2) == 0 ? -1F : 1F;

            corners[i] = new Vector3f(end).fma(su, u).fma(sv, v);
        }

        /* Four sides of the bar, as pairs of triangles over its corner indices; the ends are left open */
        int[][] quads = {{0, 1, 5, 4}, {1, 3, 7, 5}, {3, 2, 6, 7}, {2, 0, 4, 6}};

        for (int[] quad : quads)
        {
            int[] order = {0, 1, 2, 0, 2, 3};

            for (int index : order)
            {
                Vector3f c = corners[quad[index]];

                builder.vertex(matrix, c.x, c.y, c.z).color(r, g, blue, 1F)/*$ nx*/.next()/**/;
            }
        }
    }

    /** The polygon, in the form's space, as the triangles the shape's own plane was cut into. */
    private static void fill(Matrix4f matrix, List<Vector3f> outline, List<int[]> triangles)
    {
        if (outline.size() < 3)
        {
            return;
        }

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int[] triangle : triangles)
        {
            for (int index : triangle)
            {
                Vector3f p = outline.get(index);

                builder.vertex(matrix, p.x, p.y, p.z).color(1F, 1F, 1F, 1F)/*$ nx*/.next()/**/;
            }
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Bufs.draw(builder);
    }

    /**
     * Ear clipping on the polygon's XY: any simple polygon, whichever way it winds, convex or not.
     * A polygon whose edges cross has no ears left at some point; what is left of it is then
     * fanned, so a bad shape draws something instead of nothing.
     */
    private static List<int[]> triangulate(List<Vector3f> points)
    {
        List<int[]> triangles = new ArrayList<>();
        List<Integer> rest = new ArrayList<>();
        float area = 0F;

        for (int i = 0; i < points.size(); i++)
        {
            Vector3f a = points.get(i);
            Vector3f b = points.get((i + 1) % points.size());

            area += a.x * b.y - b.x * a.y;
            rest.add(i);
        }

        float winding = area >= 0F ? 1F : -1F;

        while (rest.size() > 3)
        {
            boolean clipped = false;

            for (int i = 0; i < rest.size(); i++)
            {
                int prev = rest.get((i + rest.size() - 1) % rest.size());
                int current = rest.get(i);
                int next = rest.get((i + 1) % rest.size());

                if (!isEar(points, rest, prev, current, next, winding))
                {
                    continue;
                }

                triangles.add(new int[] {prev, current, next});
                rest.remove(i);

                clipped = true;

                break;
            }

            if (!clipped)
            {
                break;
            }
        }

        for (int i = 1; i + 1 < rest.size(); i++)
        {
            triangles.add(new int[] {rest.get(0), rest.get(i), rest.get(i + 1)});
        }

        return triangles;
    }

    private static boolean isEar(List<Vector3f> points, List<Integer> rest, int prev, int current, int next, float winding)
    {
        Vector3f a = points.get(prev);
        Vector3f b = points.get(current);
        Vector3f c = points.get(next);

        if (cross(a, b, c) * winding <= 0F)
        {
            return false;
        }

        for (int index : rest)
        {
            if (index == prev || index == current || index == next)
            {
                continue;
            }

            Vector3f p = points.get(index);

            if (cross(a, b, p) * winding >= 0F && cross(b, c, p) * winding >= 0F && cross(c, a, p) * winding >= 0F)
            {
                return false;
            }
        }

        return true;
    }

    private static float cross(Vector3f a, Vector3f b, Vector3f c)
    {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
}
