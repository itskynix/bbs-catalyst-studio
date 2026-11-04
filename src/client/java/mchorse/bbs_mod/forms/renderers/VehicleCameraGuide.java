package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The driver's camera of a vehicle as a wireframe pyramid in the form editor: its tip is where the
 * eyes are, its base is where they look. Drawn through the body (depth off), since it sits inside
 * the cabin. Positions are in the vehicle's frame, the frame the form is previewed in.
 *
 * The pyramid is built once in the camera's own space - the tip at the origin, looking down +Z -
 * and the stack carries it to the eye and turns it, so its edges keep their shape at any angle.
 */
public class VehicleCameraGuide
{
    private static final float THICKNESS = 0.012F;
    private static final float LENGTH = 0.45F;
    private static final float HALF_WIDTH = 0.24F;
    private static final float HALF_HEIGHT = 0.14F;

    /** Ry(-yaw)·Rx(pitch): +Z becomes Minecraft's look for that yaw and pitch. The gizmo turns the same frame. */
    public static Quaternionf rotation(ModelForm form)
    {
        return Matrices.toQuaternionZYXRadians(MathUtils.toRad(form.driverCameraPitch.get()), -MathUtils.toRad(form.driverCameraYaw.get()), 0F);
    }

    public static void render(MatrixStack stack, ModelForm form)
    {
        Vector3f eye = new Vector3f();
        Vector3f center = new Vector3f(0F, 0F, LENGTH);
        Vector3f[] corners = {
            new Vector3f(-HALF_WIDTH, HALF_HEIGHT, LENGTH),
            new Vector3f(HALF_WIDTH, HALF_HEIGHT, LENGTH),
            new Vector3f(HALF_WIDTH, -HALF_HEIGHT, LENGTH),
            new Vector3f(-HALF_WIDTH, -HALF_HEIGHT, LENGTH)
        };
        Vector3f mark = new Vector3f(center).add(0F, HALF_HEIGHT * 1.8F, 0F);

        stack.push();
        stack.translate(form.driverCameraX.get(), form.driverCameraY.get(), form.driverCameraZ.get());
        stack.multiply(rotation(form));

        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* Yellow edges from the eye, a blue frame where it looks, and a blue mark on its top side */
        for (Vector3f corner : corners)
        {
            line(builder, stack, eye, corner, THICKNESS, 1F, 0.85F, 0.1F);
        }

        for (int i = 0; i < 4; i++)
        {
            line(builder, stack, corners[i], corners[(i + 1) % 4], THICKNESS, 0.2F, 0.55F, 1F);
        }

        line(builder, stack, corners[0], mark, THICKNESS, 0.2F, 0.55F, 1F);
        line(builder, stack, mark, corners[1], THICKNESS, 0.2F, 0.55F, 1F);

        Bufs.draw(builder);
        RenderSystem.enableDepthTest();
        stack.pop();
    }

    /**
     * A thin box from a to b. Turned by the shortest arc from +Z onto the edge, not by
     * {@code Draw#fillBoxTo}'s yaw and pitch, which mirror an edge along Z onto the other side.
     */
    static void line(BufferBuilder builder, MatrixStack stack, Vector3f a, Vector3f b, float thickness, float r, float g, float bl)
    {
        Vector3f d = new Vector3f(b).sub(a);
        float length = d.length();

        if (length < 1.0E-5F)
        {
            return;
        }

        float h = thickness / 2F;

        stack.push();
        stack.translate(a.x, a.y, a.z);
        stack.multiply(new Quaternionf().rotationTo(0F, 0F, 1F, d.x / length, d.y / length, d.z / length));
        Draw.fillBox(builder, stack, -h, -h, 0F, h, h, length, r, g, bl, 1F);
        stack.pop();
    }
}
