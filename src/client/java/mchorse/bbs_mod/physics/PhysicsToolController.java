package mchorse.bbs_mod.physics;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.items.PhysicsToolItem;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The physics studio's two hand tools in the film editor's preview.
 *
 * <p>The physics gun grabs a body under the mouse with the left button and carries it at the same distance
 * along the mouse ray; the wheel brings it nearer or pushes it away, Shift with the mouse turns it, and the
 * right button freezes it where it is. The gravity gun pulls a body to a spot in front of the camera with the
 * right button and launches it with the left.</p>
 *
 * <p>Everything a tool does ends up as keyframes on the cursor tick, so the simulation stays one honest
 * timeline: letting go keys the pose where it was dropped and releases it on the next tick with the speed it
 * was moving at, a freeze keys it and keeps holding it, a launch keys a push. While a body is in hand, it is
 * drawn where the tool holds it.</p>
 */
public final class PhysicsToolController
{
    public enum Tool
    {
        NONE, PHYSICS, GRAVITY
    }

    private static final int PHYSICS_COLOR = 0x3AD7FF;
    private static final int GRAVITY_COLOR = 0xFF8C1C;
    private static final float HOLD_DISTANCE = 2.5F;
    private static final float LAUNCH_SPEED = 24F;
    private static final float MIN_DISTANCE = 1F;
    private static final float MAX_DISTANCE = 64F;

    private static Tool picked = Tool.NONE;

    /* The physics gun's grab; a limb of a ragdoll is held live, the rest by the pose the tool gives it */
    private static Replay grabbed;
    private static boolean limb;
    private static long liveAt;
    private static float distance;
    private static float centerY;
    /* What is drawn chases where the mouse puts it on every frame, eased like a spring, so carrying and turning
     * are smooth at any frame rate and never jump with the mouse's steps */
    private static final Vector3f point = new Vector3f();
    private static final Quaternionf turn = new Quaternionf();
    private static final Vector3f targetPoint = new Vector3f();
    private static final Quaternionf targetTurn = new Quaternionf();
    private static long smoothedAt;
    private static final Vector3f velocity = new Vector3f();
    private static long movedAt;
    private static int lastMouseX;
    private static int lastMouseY;

    /** When the last launch went off, for the flash */
    private static long flashAt;
    private static final Vector3f flash = new Vector3f();

    private PhysicsToolController()
    {}

    /** The tool picked in the studio, or the one the player holds when none is */
    public static Tool tool()
    {
        if (picked != Tool.NONE)
        {
            return picked;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ItemStack stack = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack();

        if (stack.getItem() instanceof PhysicsToolItem item)
        {
            return item.kind == PhysicsToolItem.Kind.PHYSICS ? Tool.PHYSICS : Tool.GRAVITY;
        }

        return Tool.NONE;
    }

    public static Tool picked()
    {
        return picked;
    }

    public static void pick(Tool tool)
    {
        picked = picked == tool ? Tool.NONE : tool;
        drop();
    }

    /** Puts the picked tool away, e.g. when the Physics tab is left; a tool held in hand stays up */
    public static void reset()
    {
        picked = Tool.NONE;
        drop();
    }

    private static void drop()
    {
        if (grabbed != null && limb)
        {
            PhysicsStudio.endLive();
        }

        grabbed = null;
        limb = false;
    }

    /**
     * Whether a tool wants this click before anything else in the editor does: a tool is up and the mouse is over
     * the film preview. The orbit camera asks this before it takes the button.
     */
    public static boolean capturesMouse(UIContext context)
    {
        UIFilmPanel panel = filmPanel();

        return panel != null && tool() != Tool.NONE && panel.preview != null && panel.preview.isVisible() && panel.preview.area.isInside(context);
    }

    private static UIFilmPanel filmPanel()
    {
        if (mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu() instanceof mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard
            && dashboard.getPanels().panel instanceof UIFilmPanel panel)
        {
            return panel;
        }

        return null;
    }

    /** Where the tool aims: the mouse, or the middle of the picture while the mouse is locked into looking around */
    private static int[] aim(UIContext context, UIFilmPanel panel, Area viewport)
    {
        if (panel.dashboard.orbitUI.isFreeLook())
        {
            return new int[] {viewport.mx(), viewport.my()};
        }

        return new int[] {context.mouseX, context.mouseY};
    }

    /** Where a body in hand is drawn: its centre and its turn, or null when it is not in hand */
    public static float[] heldPose(Replay replay)
    {
        if (replay == null || replay != grabbed || limb)
        {
            return null;
        }

        return new float[] {point.x, point.y, point.z, turn.x, turn.y, turn.z, turn.w};
    }

    /** Gives the player one of the tools: straight into a free slot in creative, through the give command otherwise */
    public static boolean give(PhysicsToolItem.Kind kind)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.interactionManager == null)
        {
            return false;
        }

        ItemStack stack = new ItemStack(kind == PhysicsToolItem.Kind.PHYSICS ? BBSMod.PHYSICS_GUN_ITEM : BBSMod.GRAVITY_GUN_ITEM);

        if (mc.player.getAbilities().creativeMode)
        {
            int slot = mc.player.getInventory().getEmptySlot();

            if (slot < 0)
            {
                return false;
            }

            mc.player.getInventory().setStack(slot, stack);
            mc.interactionManager.clickCreativeStack(stack, slot < 9 ? 36 + slot : slot);

            return true;
        }

        mc.player.networkHandler.sendChatCommand("give @s bbs:" + kind.id);

        return true;
    }

    /* The mouse ray */

    private static Vector3f[] ray(UIFilmPanel panel, Area viewport, int mouseX, int mouseY)
    {
        Camera camera = panel.getCamera();
        float x = (mouseX - viewport.x) / (float) Math.max(1, viewport.w) * 2F - 1F;
        float y = 1F - (mouseY - viewport.y) / (float) Math.max(1, viewport.h) * 2F;
        Matrix4f inverse = new Matrix4f(panel.lastProjection).mul(panel.lastView).invert();
        Vector3f near = inverse.transformProject(new Vector3f(x, y, -1F));
        Vector3f far = inverse.transformProject(new Vector3f(x, y, 1F));
        Vector3f direction = far.sub(near, new Vector3f()).normalize();
        Vector3f origin = new Vector3f((float) camera.position.x, (float) camera.position.y, (float) camera.position.z).add(near);

        return new Vector3f[] {origin, direction};
    }

    /** Where a world point lands on the preview, or null when it is behind the camera */
    private static float[] project(UIFilmPanel panel, Area viewport, Vector3f world)
    {
        Camera camera = panel.getCamera();
        Vector4f clip = new Vector4f((float) (world.x - camera.position.x), (float) (world.y - camera.position.y), (float) (world.z - camera.position.z), 1F);

        new Matrix4f(panel.lastProjection).mul(panel.lastView).transform(clip);

        if (clip.w <= 0.01F)
        {
            return null;
        }

        float x = clip.x / clip.w;
        float y = clip.y / clip.w;

        return new float[] {viewport.x + (x + 1F) / 2F * viewport.w, viewport.y + (1F - y) / 2F * viewport.h};
    }

    /* Input */

    public static boolean mouseClicked(UIContext context, UIFilmPanel panel, Area viewport)
    {
        Tool tool = tool();
        Film film = panel.getData();

        int[] aim = aim(context, panel, viewport);

        if (tool == Tool.NONE || film == null || !viewport.isInside(aim[0], aim[1]) || context.mouseButton > 1)
        {
            return false;
        }

        Vector3f[] ray = ray(panel, viewport, aim[0], aim[1]);
        int tick = panel.getCursor();

        if (tool == Tool.PHYSICS)
        {
            if (context.mouseButton == 1 && grabbed != null)
            {
                if (limb)
                {
                    PhysicsStudio.freezeLive(film, grabbed, tick);
                    grabbed = null;
                    limb = false;
                    panel.replayEditor.updateChannelsList();
                }
                else
                {
                    freeze(panel, grabbed, tick);
                    grabbed = null;
                }

                return true;
            }

            if (context.mouseButton == 0)
            {
                PhysicsStudio.Hit hit = PhysicsStudio.pick(film, new Vector3d(ray[0].x, ray[0].y, ray[0].z), ray[1], tick);

                if (hit != null)
                {
                    grabbed = hit.replay();
                    distance = Math.max(MIN_DISTANCE, hit.distance() + 0.01F);
                    centerY = hit.centerY();
                    point.set(hit.pose()[0], hit.pose()[1], hit.pose()[2]);
                    turn.set(hit.pose()[3], hit.pose()[4], hit.pose()[5], hit.pose()[6]);
                    targetPoint.set(point);
                    targetTurn.set(turn);
                    smoothedAt = System.nanoTime();
                    velocity.zero();
                    movedAt = System.nanoTime();
                    lastMouseX = aim[0];
                    lastMouseY = aim[1];
                    distance = ray[0].distance(point);

                    /* A ragdoll is held by the limb the ray met, and the rest of it swings after the hand */
                    limb = hit.limb() && PhysicsStudio.beginLive(film, grabbed, hit.body(), tick);
                    liveAt = System.nanoTime();
                }

                return true;
            }

            return true;
        }

        /* The gravity gun: the right button pulls, the left one launches */
        Vector3f forward = new Vector3f(ray[1]);

        if (context.mouseButton == 1)
        {
            PhysicsStudio.Hit hit = PhysicsStudio.pick(film, new Vector3d(ray[0].x, ray[0].y, ray[0].z), ray[1], tick);

            if (hit != null)
            {
                Vector3f front = new Vector3f(ray[0]).add(new Vector3f(forward).mul(HOLD_DISTANCE));

                keyPose(hit.replay(), tick, front, new Quaternionf(hit.pose()[3], hit.pose()[4], hit.pose()[5], hit.pose()[6]), hit.centerY());
                keyAnimation(hit.replay(), tick, 1F);
                panel.replayEditor.updateChannelsList();
            }

            return true;
        }

        PhysicsStudio.Hit hit = PhysicsStudio.pick(film, new Vector3d(ray[0].x, ray[0].y, ray[0].z), ray[1], tick);

        if (hit != null)
        {
            Replay replay = hit.replay();
            Vector3f push = new Vector3f(forward).mul(LAUNCH_SPEED);

            keyAnimation(replay, tick + 1, 0F);
            PhysicsStudio.push(replay, tick + 1, push.x, push.y, push.z);
            flash.set(hit.pose()[0], hit.pose()[1], hit.pose()[2]);
            flashAt = System.nanoTime();
            panel.replayEditor.updateChannelsList();
        }

        return true;
    }

    /** Letting go of the left button drops what the physics gun holds, with the speed it was being moved at */
    public static boolean mouseReleased(UIContext context, UIFilmPanel panel)
    {
        if (grabbed == null || context.mouseButton != 0)
        {
            return false;
        }

        Replay replay = grabbed;
        int tick = panel.getCursor();

        grabbed = null;

        if (limb)
        {
            /* A limb let go without a freeze only swung for the look of it; the timeline is as it was */
            limb = false;
            PhysicsStudio.endLive();

            return true;
        }

        keyPose(replay, tick, point, turn, centerY);
        keyAnimation(replay, tick, 1F);
        keyAnimation(replay, tick + 1, 0F);

        if (velocity.lengthSquared() > 0.25F)
        {
            PhysicsStudio.push(replay, tick + 1, velocity.x, velocity.y, velocity.z);
        }

        panel.replayEditor.updateChannelsList();

        return true;
    }

    public static boolean mouseScrolled(UIContext context)
    {
        if (grabbed == null)
        {
            return false;
        }

        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance * (float) Math.pow(1.1D, context.mouseWheel)));

        return true;
    }

    /** Right button while holding: the body stays where it is, keyed and held there */
    private static void freeze(UIFilmPanel panel, Replay replay, int tick)
    {
        keyPose(replay, tick, point, turn, centerY);
        keyAnimation(replay, tick, 1F);
        panel.replayEditor.updateChannelsList();
    }

    private static void keyPose(Replay replay, int tick, Vector3f center, Quaternionf rotation, float centerY)
    {
        Vector3f offset = new Quaternionf(rotation).transform(new Vector3f(0F, centerY, 0F));
        Vector3f angles = new Quaternionf(rotation).getEulerAnglesYXZ(new Vector3f());
        double yaw = -Math.toDegrees(angles.y);

        BaseValue.edit(replay.keyframes, (keyframes) ->
        {
            keyframes.x.insert(tick, (double) (center.x - offset.x));
            keyframes.y.insert(tick, (double) (center.y - offset.y));
            keyframes.z.insert(tick, (double) (center.z - offset.z));
            keyframes.yaw.insert(tick, yaw);
            keyframes.bodyYaw.insert(tick, yaw);
        });
    }

    private static void keyAnimation(Replay replay, int tick, float value)
    {
        BaseValue.edit(replay.physics, (physics) ->
        {
            KeyframeChannel<Double> channel = physics.animation;

            if (tick > 0 && channel.isEmpty())
            {
                channel.insert(0, 1D);
            }

            channel.insert(tick, (double) value);
        });
    }

    /* Every frame */

    /** Moves what is in hand after the mouse and draws the beam and the flash */
    public static void render(UIContext context, UIFilmPanel panel, Area viewport)
    {
        Tool tool = tool();

        if (tool == Tool.NONE)
        {
            drop();

            return;
        }

        int[] aim = aim(context, panel, viewport);
        boolean locked = panel.dashboard.orbitUI.isFreeLook();

        int color = tool == Tool.PHYSICS ? PHYSICS_COLOR : GRAVITY_COLOR;
        float[] muzzle = {viewport.ex() - viewport.w * 0.18F, viewport.ey() - viewport.h * 0.1F};

        if (grabbed != null)
        {
            Vector3f[] ray = ray(panel, viewport, aim[0], aim[1]);

            if (Window.isShiftPressed())
            {
                /* Shift turns the body about the camera's up and right instead of moving it */
                float dx = (aim[0] - lastMouseX) * 0.02F;
                float dy = (aim[1] - lastMouseY) * 0.02F;

                targetTurn.premul(new Quaternionf().rotateY(dx)).premul(new Quaternionf().rotateX(dy)).normalize();
            }
            else
            {
                targetPoint.set(new Vector3f(ray[0]).add(new Vector3f(ray[1]).mul(distance)));
            }

            {
                /* The spring: the share of the gap closed this frame depends on the frame's length, not its count */
                long clock = System.nanoTime();
                float frame = Math.min(0.1F, Math.max(0F, (clock - smoothedAt) / 1.0E9F));
                float ease = 1F - (float) Math.exp(-frame * 22F);

                smoothedAt = clock;
                turn.slerp(targetTurn, ease).normalize();

                Vector3f next = new Vector3f(point).lerp(targetPoint, ease);
                long now = System.nanoTime();
                float seconds = Math.max(1e-3F, (now - movedAt) / 1.0E9F);

                if (seconds > 0.004F)
                {
                    velocity.set(next).sub(point).div(seconds).mul(0.5F).add(new Vector3f(velocity).mul(0.5F));

                    if (velocity.length() > 40F)
                    {
                        velocity.normalize(40F);
                    }

                    movedAt = now;
                }

                point.set(next);
            }

            lastMouseX = aim[0];
            lastMouseY = aim[1];

            if (limb)
            {
                /* The live world runs at the film's own pace, a tick every twentieth of a second */
                long now = System.nanoTime();
                int steps = (int) Math.min(3, (now - liveAt) / 50_000_000L);

                for (int i = 0; i < steps; i++)
                {
                    PhysicsStudio.stepLive(new float[] {point.x, point.y, point.z, turn.x, turn.y, turn.z, turn.w});
                }

                if (steps > 0)
                {
                    liveAt = now;
                }
            }

            float[] end = project(panel, viewport, point);

            if (end != null)
            {
                beam(context, muzzle, end, color);
            }
        }

        float age = (System.nanoTime() - flashAt) / 1.0E9F;

        if (age < 0.35F)
        {
            float[] at = project(panel, viewport, flash);

            if (at != null)
            {
                float radius = 6F + age * 80F;
                int alpha = Colors.setA(color, 1F - age / 0.35F);

                beam(context, muzzle, at, color);
                ring(context, at[0], at[1], radius, alpha);
            }
        }

        /* With the mouse free the pointer itself aims; locked into looking around, a sight marks the middle */
        if (locked)
        {
            int sight = Colors.setA(color, 0.9F);
            int cx = aim[0];
            int cy = aim[1];

            context.batcher.box(cx - 6, cy, cx - 2, cy + 1, sight);
            context.batcher.box(cx + 3, cy, cx + 7, cy + 1, sight);
            context.batcher.box(cx, cy - 6, cx + 1, cy - 2, sight);
            context.batcher.box(cx, cy + 3, cx + 1, cy + 7, sight);
        }
    }

    /** The collision boxes and the joints of the simulated bodies, see-through, over the picture */
    public static void renderShapes(UIContext context, UIFilmPanel panel, Area viewport)
    {
        if (!PhysicsStudio.showShapes || panel.getData() == null)
        {
            return;
        }

        java.util.List<float[]> joints = new java.util.ArrayList<>();
        int edge = Colors.setA(0x40E0A0, 0.7F);

        for (float[] shape : PhysicsStudio.shapes(panel.getData(), panel.getCursor(), joints))
        {
            Quaternionf q = new Quaternionf(shape[3], shape[4], shape[5], shape[6]);
            float[][] corners = new float[8][];

            for (int i = 0; i < 8; i++)
            {
                Vector3f local = new Vector3f((i & 1) == 0 ? -shape[7] : shape[7], (i & 2) == 0 ? -shape[8] : shape[8], (i & 4) == 0 ? -shape[9] : shape[9]);
                Vector3f world = q.transform(local).add(shape[0], shape[1], shape[2]);

                corners[i] = project(panel, viewport, world);
            }

            int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

            for (int[] e : edges)
            {
                if (corners[e[0]] != null && corners[e[1]] != null)
                {
                    line(context, corners[e[0]], corners[e[1]], edge);
                }
            }
        }

        for (float[] joint : joints)
        {
            float[] at = project(panel, viewport, new Vector3f(joint[0], joint[1], joint[2]));

            if (at != null)
            {
                context.batcher.box(at[0] - 2F, at[1] - 2F, at[0] + 2F, at[1] + 2F, Colors.setA(0xFFD040, 0.9F));
            }
        }
    }

    private static void line(UIContext context, float[] a, float[] b, int color)
    {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        int steps = (int) Math.min(400, Math.max(Math.abs(dx), Math.abs(dy)));

        for (int i = 0; i <= steps; i++)
        {
            float t = steps == 0 ? 0F : i / (float) steps;
            float x = a[0] + dx * t;
            float y = a[1] + dy * t;

            context.batcher.box(x, y, x + 1F, y + 1F, color);
        }
    }

    /** A curving beam of glowing beads from the muzzle to the target, swaying a little over time */
    private static void beam(UIContext context, float[] from, float[] to, int color)
    {
        float time = System.nanoTime() / 1.0E9F;
        float mx = (from[0] + to[0]) / 2F;
        float my = (from[1] + to[1]) / 2F;
        float nx = -(to[1] - from[1]);
        float ny = to[0] - from[0];
        float length = (float) Math.sqrt(nx * nx + ny * ny);

        if (length < 1F)
        {
            return;
        }

        float bend = (float) Math.sin(time * 3F) * 0.12F * length;
        float cx = mx + nx / length * bend;
        float cy = my + ny / length * bend;
        int steps = Math.max(8, (int) (length / 3F));

        for (int i = 0; i <= steps; i++)
        {
            float t = i / (float) steps;
            float u = 1F - t;
            float x = u * u * from[0] + 2F * u * t * cx + t * t * to[0];
            float y = u * u * from[1] + 2F * u * t * cy + t * t * to[1];
            float pulse = 0.55F + 0.45F * (float) Math.sin(time * 18F - t * 12F);

            context.batcher.box(x - 2F, y - 2F, x + 2F, y + 2F, Colors.setA(color, 0.25F * pulse));
            context.batcher.box(x - 1F, y - 1F, x + 1F, y + 1F, Colors.setA(0xFFFFFF, 0.8F * pulse));
        }
    }

    private static void ring(UIContext context, float x, float y, float radius, int color)
    {
        for (int i = 0; i < 32; i++)
        {
            double a = i / 32D * Math.PI * 2D;
            float px = x + (float) Math.cos(a) * radius;
            float py = y + (float) Math.sin(a) * radius;

            context.batcher.box(px - 1F, py - 1F, px + 1F, py + 1F, color);
        }
    }
}
