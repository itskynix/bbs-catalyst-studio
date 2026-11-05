package mchorse.bbs_mod.ui.film.replays.pov;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.film.eye.PovHands;
import mchorse.bbs_mod.film.replays.PovRig;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The POV model editor's view: the replay's first-person hands, drawn by {@link PovHands} like the
 * film draws them, inside a 16:9 frame that is the game's picture.
 *
 * <p>Locked (the default), the camera is the eye: the hand lens of the game, 70 degrees across the
 * frame's height, so what lands inside the frame is what lands on screen. Free, it is the usual
 * orbit, to see the arms from outside while placing them; the eye is then marked by its axes.</p>
 *
 * <p>A click on a bone's place picks it; the picked bone carries a gizmo with a handle per axis of
 * the space it is moved in (its parent's), which drag it along that axis or, in the turning mode,
 * turn it about the axis. A drag is one undo step.</p>
 */
public class UIPovViewport extends UIModelRenderer
{
    private static final float HAND_FOV = 70F;
    private static final float EYE_NEAR = 0.05F;
    private static final float FRAME_MARGIN = 10F;

    /** How long a gizmo handle is, in blocks, and how near the cursor must be to grab it or a bone, in pixels */
    private static final float HANDLE = 0.15F;
    private static final float GRAB = 6F;
    private static final float PICK = 12F;

    /* Where the free camera starts, and goes back to whenever it is freed */
    private static final float FREE_YAW = 150F;
    private static final float FREE_PITCH = 20F;
    private static final float FREE_DISTANCE = 2F;

    private static final int[] AXIS_COLORS = {0xffff4444, 0xff44ff44, 0xff4488ff};

    private final Supplier<Replay> replay;
    private final Supplier<IEntity> actor;
    private final Supplier<Float> cursor;

    private boolean free;
    private boolean rotating;
    private String selected = PovRig.RIGHT_ARM;
    private final Map<String, Matrix4f> frames = new HashMap<>();
    private final Matrix4f projection = new Matrix4f();

    /* The bone picked in the view goes to the editor; an edit made by dragging is told to it too */
    private Consumer<String> onPick;
    private Runnable onEdit;

    /* The drag of a handle: which axis, and where the cursor was */
    private int dragAxis = -1;
    private float lastX;
    private float lastY;

    /* The 16:9 frame inside the view, in screen pixels */
    private float frameX;
    private float frameY;
    private float frameW;
    private float frameH;

    /**
     * @param actor the replay's entity in the editor, for its items, armor and swing; null leaves the
     *              view a stand-in wearing the replay's form
     * @param cursor the film tick the channels are read at
     */
    public UIPovViewport(Supplier<Replay> replay, Supplier<IEntity> actor, Supplier<Float> cursor)
    {
        this.replay = replay;
        this.actor = actor;
        this.cursor = cursor;
        this.grid = false;

        this.resetFreeCamera();
    }

    public UIPovViewport callbacks(Consumer<String> onPick, Runnable onEdit)
    {
        this.onPick = onPick;
        this.onEdit = onEdit;

        return this;
    }

    /** Out in front of the hands, looking back at them and a little down; set at once, not slid to */
    private void resetFreeCamera()
    {
        this.setPosition(0F, -0.45F, -0.7F);
        this.setDistance(FREE_DISTANCE);
        this.setRotation(FREE_YAW, FREE_PITCH);
    }

    public boolean isFree()
    {
        return this.free;
    }

    public void setFree(boolean free)
    {
        this.free = free;
        this.dragging = 0;
        this.endDrag();

        /* Locked, the eye has overwritten the orbit's angles every frame: freed, the orbit would
         * slide back from there; it starts where it is meant to instead */
        if (free)
        {
            this.resetFreeCamera();
        }
    }

    public boolean isRotating()
    {
        return this.rotating;
    }

    public void setRotating(boolean rotating)
    {
        this.rotating = rotating;
    }

    public void setSelected(String bone)
    {
        this.selected = bone;
    }

    @Override
    protected void setupViewport(UIContext context)
    {
        this.updateFrame();

        if (this.free)
        {
            this.camera.setFov(50F);
            this.camera.near = 0.01F;
        }
        else
        {
            /* The lens is fitted to the frame, not to the view: the frame's height spans the hand lens */
            float half = (float) Math.tan(MathUtils.toRad(HAND_FOV / 2F)) * this.area.h / Math.max(this.frameH, 1F);

            this.camera.fov = 2F * (float) Math.atan(half);
            this.camera.near = EYE_NEAR;
            this.camera.position.set(0, 0, 0);
            this.camera.rotation.set(0, 0, 0);
        }

        super.setupViewport(context);
        this.projection.set(this.camera.projection);
    }

    private void updateFrame()
    {
        float w = Math.max(this.area.w - FRAME_MARGIN * 2F, 1F);
        float h = Math.max(this.area.h - FRAME_MARGIN * 2F, 1F);

        if (w * 9F > h * 16F)
        {
            w = h * 16F / 9F;
        }
        else
        {
            h = w * 9F / 16F;
        }

        this.frameW = w;
        this.frameH = h;
        this.frameX = this.area.x + (this.area.w - w) / 2F;
        this.frameY = this.area.y + (this.area.h - h) / 2F;
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        Replay replay = this.replay.get();

        if (replay == null || this.area.w <= 0 || this.area.h <= 0)
        {
            return;
        }

        IEntity entity = this.actor.get();

        if (entity == null)
        {
            entity = this.getEntity();
            entity.setForm(replay.form.get());
        }

        float cursor = this.cursor.get();
        float tick = replay.getTick((int) cursor) + (cursor - (int) cursor);
        MatrixStack stack = context.batcher.getContext().getMatrices();
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        this.frames.clear();
        PovHands.render(stack, consumers, LightmapTextureManager.pack(15, 15), replay, entity, tick, context.getTransition(), this.frames);

        RenderSystem.disableDepthTest();

        /* The picked bone's gizmo: the axes it is moved along, at where it is now */
        Matrix4f gizmo = this.gizmoMatrix();

        if (gizmo != null)
        {
            stack.push();
            stack.peek().getPositionMatrix().set(gizmo);
            Draw.coolerAxes(stack, HANDLE, 0.004F);
            stack.pop();
        }

        /* Seen from outside, the eye is somewhere too */
        if (this.free)
        {
            Draw.coolerAxes(stack, 0.12F, 0.006F);
        }

        RenderSystem.enableDepthTest();
    }

    /** The picked bone's place with its parent's axes: what its handles move it in */
    private Matrix4f gizmoMatrix()
    {
        Matrix4f bone = this.frames.get(this.selected);
        Matrix4f parent = this.frames.get(PovHands.parentFrame(this.selected));

        if (bone == null)
        {
            return null;
        }

        Matrix4f matrix = new Matrix4f(parent == null ? bone : parent);
        Vector3f origin = bone.getTranslation(new Vector3f());

        matrix.setTranslation(origin);

        /* The axes at unit length whatever the scale on the way, so the handles keep their size */
        Vector3f x = matrix.getColumn(0, new Vector3f()).normalize();
        Vector3f y = matrix.getColumn(1, new Vector3f()).normalize();
        Vector3f z = matrix.getColumn(2, new Vector3f()).normalize();

        matrix.setColumn(0, new Vector4f(x, 0F));
        matrix.setColumn(1, new Vector4f(y, 0F));
        matrix.setColumn(2, new Vector4f(z, 0F));

        return matrix;
    }

    /** A point of a frame on the screen, or null behind the eye */
    private Vector2f project(Matrix4f frame, float x, float y, float z)
    {
        Vector4f point = new Vector4f(x, y, z, 1F);

        frame.transform(point);
        this.projection.transform(point);

        if (point.w <= 0.0001F)
        {
            return null;
        }

        float nx = point.x / point.w;
        float ny = point.y / point.w;

        return new Vector2f(this.area.x + (nx + 1F) / 2F * this.area.w, this.area.y + (1F - ny) / 2F * this.area.h);
    }

    /** The handle under the cursor (0, 1, 2 for X, Y, Z), or -1 */
    private int handleAt(float mouseX, float mouseY)
    {
        Matrix4f gizmo = this.gizmoMatrix();

        if (gizmo == null)
        {
            return -1;
        }

        Vector2f origin = this.project(gizmo, 0F, 0F, 0F);

        if (origin == null)
        {
            return -1;
        }

        int best = -1;
        float bestDistance = GRAB;

        for (int axis = 0; axis < 3; axis++)
        {
            Vector2f end = this.project(gizmo, axis == 0 ? HANDLE : 0F, axis == 1 ? HANDLE : 0F, axis == 2 ? HANDLE : 0F);

            if (end == null)
            {
                continue;
            }

            float distance = distanceToSegment(mouseX, mouseY, origin, end);

            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = axis;
            }
        }

        return best;
    }

    private static float distanceToSegment(float px, float py, Vector2f a, Vector2f b)
    {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float length = dx * dx + dy * dy;
        float t = length <= 0F ? 0F : Math.max(0F, Math.min(1F, ((px - a.x) * dx + (py - a.y) * dy) / length));
        float x = a.x + dx * t - px;
        float y = a.y + dy * t - py;

        return (float) Math.sqrt(x * x + y * y);
    }

    /** The rig bone whose place is nearest the cursor, close enough to mean it, or null */
    private String boneAt(float mouseX, float mouseY)
    {
        String best = null;
        float bestDistance = PICK;

        for (String bone : PovRig.BONES)
        {
            Matrix4f frame = this.frames.get(bone);
            Vector2f point = frame == null ? null : this.project(frame, 0F, 0F, 0F);

            if (point == null)
            {
                continue;
            }

            float distance = point.distance(mouseX, mouseY);

            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = bone;
            }
        }

        return best;
    }

    private PovRig.Bone getBone()
    {
        Replay replay = this.replay.get();

        return replay == null ? null : replay.pov.rig.getBone(this.selected);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int axis = this.handleAt(context.mouseX, context.mouseY);
            PovRig.Bone bone = this.getBone();

            if (axis >= 0 && bone != null)
            {
                this.dragAxis = axis;
                this.lastX = context.mouseX;
                this.lastY = context.mouseY;
                bone.transform.preNotify();

                return true;
            }

            String picked = this.boneAt(context.mouseX, context.mouseY);

            if (picked != null)
            {
                this.selected = picked;

                if (this.onPick != null)
                {
                    this.onPick.accept(picked);
                }

                return true;
            }
        }

        /* Locked to the eye there is nothing to orbit: the lens is the game's */
        if (!this.free)
        {
            return this.area.isInside(context) && (context.mouseButton == 0 || context.mouseButton == 2);
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragAxis >= 0)
        {
            this.endDrag();

            return true;
        }

        return super.subMouseReleased(context);
    }

    private void endDrag()
    {
        if (this.dragAxis < 0)
        {
            return;
        }

        this.dragAxis = -1;

        PovRig.Bone bone = this.getBone();

        if (bone != null)
        {
            bone.transform.postNotify();
        }

        if (this.onEdit != null)
        {
            this.onEdit.run();
        }
    }

    /**
     * Follow the cursor with the handle being dragged: along its axis by as much as the cursor moved
     * along the handle's line on the screen, or turned about it by how far the cursor went across it.
     */
    private void drag(UIContext context)
    {
        PovRig.Bone bone = this.getBone();
        Matrix4f gizmo = this.gizmoMatrix();

        if (bone == null || gizmo == null)
        {
            return;
        }

        float dx = context.mouseX - this.lastX;
        float dy = context.mouseY - this.lastY;

        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        if (dx == 0F && dy == 0F)
        {
            return;
        }

        int axis = this.dragAxis;
        Vector2f origin = this.project(gizmo, 0F, 0F, 0F);
        Vector2f end = this.project(gizmo, axis == 0 ? HANDLE : 0F, axis == 1 ? HANDLE : 0F, axis == 2 ? HANDLE : 0F);

        if (origin == null || end == null)
        {
            return;
        }

        float sx = end.x - origin.x;
        float sy = end.y - origin.y;
        float length = sx * sx + sy * sy;

        if (length < 1F)
        {
            return;
        }

        Transform transform = bone.transform.get();

        if (this.rotating)
        {
            /* Across the handle's line: half a degree a pixel */
            float across = (dx * -sy + dy * sx) / (float) Math.sqrt(length);
            float angle = MathUtils.toRad(across * 0.5F);

            if (transform.rotationMode == Transform.RotationMode.QUATERNION)
            {
                Vector3f about = new Vector3f(axis == 0 ? 1F : 0F, axis == 1 ? 1F : 0F, axis == 2 ? 1F : 0F);

                transform.quat.set(new Quaternionf().rotateAxis(angle, about).mul(transform.quat));
            }
            else if (axis == 0)
            {
                transform.rotate.x += angle;
            }
            else if (axis == 1)
            {
                transform.rotate.y += angle;
            }
            else
            {
                transform.rotate.z += angle;
            }
        }
        else
        {
            /* Along the handle's line, scaled so the handle stays under the cursor */
            float along = (dx * sx + dy * sy) / length * HANDLE;

            if (axis == 0)
            {
                transform.translate.x += along;
            }
            else if (axis == 1)
            {
                transform.translate.y += along;
            }
            else
            {
                transform.translate.z += along;
            }
        }

        if (this.onEdit != null)
        {
            this.onEdit.run();
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragAxis >= 0)
        {
            this.drag(context);
        }

        super.render(context);

        this.renderHandles(context);

        if (this.free)
        {
            return;
        }

        int x1 = (int) this.frameX;
        int y1 = (int) this.frameY;
        int x2 = (int) (this.frameX + this.frameW);
        int y2 = (int) (this.frameY + this.frameH);
        int shade = Colors.setA(0, 0.45F);

        /* What falls outside the picture is dimmed, the picture itself outlined */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), y1, shade);
        context.batcher.box(this.area.x, y2, this.area.ex(), this.area.ey(), shade);
        context.batcher.box(this.area.x, y1, x1, y2, shade);
        context.batcher.box(x2, y1, this.area.ex(), y2, shade);
        context.batcher.outline(x1, y1, x2, y2, Colors.setA(Colors.WHITE, 0.6F));
    }

    /** The handles' grips and the bones' places, drawn flat over the view so they can be aimed at */
    private void renderHandles(UIContext context)
    {
        for (String bone : PovRig.BONES)
        {
            Matrix4f frame = this.frames.get(bone);
            Vector2f point = frame == null ? null : this.project(frame, 0F, 0F, 0F);

            if (point != null && this.area.isInside((int) point.x, (int) point.y))
            {
                int color = bone.equals(this.selected) ? Colors.WHITE : 0xffaaaaaa;

                context.batcher.box(point.x - 2, point.y - 2, point.x + 2, point.y + 2, 0xff000000);
                context.batcher.box(point.x - 1, point.y - 1, point.x + 1, point.y + 1, color);
            }
        }

        Matrix4f gizmo = this.gizmoMatrix();

        if (gizmo == null)
        {
            return;
        }

        int hovered = this.dragAxis >= 0 ? this.dragAxis : this.handleAt(context.mouseX, context.mouseY);

        for (int axis = 0; axis < 3; axis++)
        {
            Vector2f end = this.project(gizmo, axis == 0 ? HANDLE : 0F, axis == 1 ? HANDLE : 0F, axis == 2 ? HANDLE : 0F);

            if (end == null)
            {
                continue;
            }

            int size = axis == hovered ? 4 : 3;
            int color = axis == hovered ? Colors.WHITE : AXIS_COLORS[axis];

            /* Turning grips are hollow, moving grips are full */
            if (this.rotating)
            {
                context.batcher.outline(end.x - size, end.y - size, end.x + size, end.y + size, color);
            }
            else
            {
                context.batcher.box(end.x - size, end.y - size, end.x + size, end.y + size, color);
            }
        }
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (!this.free)
        {
            return this.area.isInside(context);
        }

        return super.subMouseScrolled(context);
    }
}
