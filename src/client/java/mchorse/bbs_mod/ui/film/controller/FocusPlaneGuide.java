package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.overwrite.ProCameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The focus plane of a pro camera clip, drawn in the world while it is edited: a translucent purple
 * sheet square to the lens at the focus distance, as wide and tall as the picture is there, with an
 * outline. It is depth tested, so where an actor or a wall pokes through it is where they are sharp.
 *
 * <p>Only the clip being edited draws one, only while the cursor is inside it and the clip asks for it,
 * and never into a recording or a shadow map. The distance is the one the clip settled on this frame
 * (the channel, the crosshair or the actor followed), read from the camera's position.</p>
 */
public final class FocusPlaneGuide
{
    private static final float R = 155 / 255F;
    private static final float G = 89 / 255F;
    private static final float B = 182 / 255F;
    private static final float FILL_ALPHA = 0.35F;

    private FocusPlaneGuide()
    {}

    public static void render(UIFilmPanel panel, WorldRenderContext context)
    {
        if (panel == null || context.camera() == null || BBSRendering.isIrisShadowPass() || BBSModClient.getVideoRecorder().isRecording())
        {
            return;
        }

        Clip clip = panel.cameraEditor == null ? null : panel.cameraEditor.getClip();

        if (!(clip instanceof ProCameraClip pro) || !pro.showFocusPlane.get() || !pro.enabled.get())
        {
            return;
        }

        int cursor = panel.getCursor();
        int start = pro.tick.get();

        if (cursor < start || cursor >= start + pro.duration.get())
        {
            return;
        }

        Position position = panel.getRunner().getPosition();
        float distance = position.focusDistance;

        if (!(distance > 0.01F) || !Float.isFinite(distance))
        {
            return;
        }

        /* The picture's size at the focus distance: H = 2 D tan(fov / 2) with the vertical field of view,
         * W = H times the video's aspect, which is 2 D tan(fovx / 2) */
        float halfHeight = distance * (float) Math.tan(MathUtils.toRad(position.angle.fov) / 2F);
        float halfWidth = halfHeight * BBSRendering.getVideoWidth() / (float) Math.max(BBSRendering.getVideoHeight(), 1);

        /* The camera's own axes. Forward is the look direction the clip measures the focus along (the
         * same one Camera#getLookDirection gives); right and up are square to it, with the world's up
         * as the reference, then turned by the roll the way the view is: the game rolls the view by
         * POSITIVE_Z, so the world directions that land on the screen's right and up are
         * right' = cos r right - sin r up and up' = sin r right + cos r up. */
        Vector3f forward = new Vector3f(Matrices.rotation(MathUtils.toRad(position.angle.pitch), MathUtils.toRad(-position.angle.yaw - 180))).normalize();
        Vector3f right = new Vector3f(forward).cross(0F, 1F, 0F);

        if (right.lengthSquared() < 1.0E-6F)
        {
            /* Straight up or down: any horizontal right will do, the yaw picks it */
            float yaw = MathUtils.toRad(position.angle.yaw);

            right.set(-(float) Math.cos(yaw), 0F, -(float) Math.sin(yaw));
        }

        right.normalize();

        Vector3f up = new Vector3f(right).cross(forward).normalize();
        float roll = MathUtils.toRad(position.angle.roll);
        float cos = (float) Math.cos(roll);
        float sin = (float) Math.sin(roll);
        Vector3f screenRight = new Vector3f(right).mul(cos).sub(new Vector3f(up).mul(sin));
        Vector3f screenUp = new Vector3f(right).mul(sin).add(new Vector3f(up).mul(cos));

        /* Centre: P_cam + forward D, relative to the render camera; corners: +- right W/2 +- up H/2 */
        Camera camera = context.camera();
        Vector3f centre = new Vector3f(forward).mul(distance).add(
            (float) (position.point.x - camera.getPos().x),
            (float) (position.point.y - camera.getPos().y),
            (float) (position.point.z - camera.getPos().z));
        Vector3f[] corners = new Vector3f[4];

        for (int i = 0; i < 4; i++)
        {
            float sx = (i == 0 || i == 3) ? -halfWidth : halfWidth;
            float sy = i < 2 ? -halfHeight : halfHeight;

            corners[i] = new Vector3f(centre).add(new Vector3f(screenRight).mul(sx)).add(new Vector3f(screenUp).mul(sy));
        }

        MatrixStack stack = context.matrixStack();
        Matrix4f matrix = stack.peek().getPositionMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        /* The sheet, seen from both sides */
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        vertex(builder, matrix, corners[0]);
        vertex(builder, matrix, corners[1]);
        vertex(builder, matrix, corners[2]);
        vertex(builder, matrix, corners[0]);
        vertex(builder, matrix, corners[2]);
        vertex(builder, matrix, corners[3]);

        Bufs.draw(builder);

        /* The outline, a little thicker the further it is so it stays a line on the screen */
        float thickness = Math.max(0.01F, distance * 0.004F);

        builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < 4; i++)
        {
            Vector3f a = corners[i];
            Vector3f b = corners[(i + 1) % 4];

            /* A flat band in the plane itself, not Draw.fillBoxTo: that one turns its bar by
             * Angle.angle's yaw, which sends anything with a z component the wrong way */
            Vector3f side = new Vector3f(b).sub(a).cross(forward).normalize(thickness / 2F);

            builder.vertex(matrix, a.x - side.x, a.y - side.y, a.z - side.z).color(R, G, B, 1F)/*$ nx*/.next()/**/;
            builder.vertex(matrix, b.x - side.x, b.y - side.y, b.z - side.z).color(R, G, B, 1F)/*$ nx*/.next()/**/;
            builder.vertex(matrix, b.x + side.x, b.y + side.y, b.z + side.z).color(R, G, B, 1F)/*$ nx*/.next()/**/;
            builder.vertex(matrix, a.x - side.x, a.y - side.y, a.z - side.z).color(R, G, B, 1F)/*$ nx*/.next()/**/;
            builder.vertex(matrix, b.x + side.x, b.y + side.y, b.z + side.z).color(R, G, B, 1F)/*$ nx*/.next()/**/;
            builder.vertex(matrix, a.x + side.x, a.y + side.y, a.z + side.z).color(R, G, B, 1F)/*$ nx*/.next()/**/;
        }

        Bufs.draw(builder);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vector3f p)
    {
        builder.vertex(matrix, p.x, p.y, p.z).color(R, G, B, FILL_ALPHA)/*$ nx*/.next()/**/;
    }
}
