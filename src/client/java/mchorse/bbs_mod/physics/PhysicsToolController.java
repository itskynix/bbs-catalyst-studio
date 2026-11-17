package mchorse.bbs_mod.physics;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.items.PhysicsToolItem;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.physics.actions.ImpulseActionClip;
import mchorse.bbs_mod.physics.actions.TearActionClip;
import mchorse.bbs_mod.physics.client.scene.FilmScene;
import mchorse.bbs_mod.physics.client.scene.FilmScenes;
import mchorse.bbs_mod.physics.client.scene.PhysicsBake;
import mchorse.bbs_mod.physics.client.scene.SceneBody;
import mchorse.bbs_mod.physics.forms.IPhysicsForm;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

/**
 * The physics gun and the gravity gun in the film editor's preview, on top of the integrated
 * physics engine (BBS CS).
 *
 * <p>The engine records a film ahead of the cursor and plays the recording back, so a tool cannot
 * reach into a live world and drag a body about: whatever it does has to become part of the film,
 * or the next recording would not have it. Every action is therefore one ordinary, undoable edit
 * of the film, written with the engine's own vocabulary:</p>
 *
 * <ul>
 * <li><b>Grab</b> (physics gun, left button held): the beam locks onto the middle of the body or
 * ragdoll limb under the mouse, and a target follows the mouse at the same distance (the wheel
 * brings it nearer or pushes it away). Letting go yanks the body towards the target: an impulse
 * clip on the cursor tick, aimed from the middle of the body at the target, as hard as the target
 * was dragged away.</li>
 * <li><b>Freeze</b> (physics gun, right button): the body's simulated motion is baked to keys up to
 * the cursor tick and the animation handle goes to 1, so it stays exactly where it was on that
 * tick, in mid-air if that is where it was.</li>
 * <li><b>Tear</b> (physics gun, Alt with the left button on a ragdoll limb): a tear clip on the
 * cursor tick, kicking the limb the way the camera looks.</li>
 * <li><b>Launch</b> and <b>pull</b> (gravity gun, left and right button): an impulse clip that
 * throws the body the way the camera looks, or pulls it towards the camera.</li>
 * </ul>
 *
 * <p>A push only moves what physics owns, so an object still held by its animation (the handle at
 * 1) is let go on the same tick: the handle is keyed from 1 on the tick before to 0 on the tick of
 * the push.</p>
 */
public final class PhysicsToolController
{
    public enum Tool
    {
        NONE, PHYSICS, GRAVITY
    }

    private static final int PHYSICS_COLOR = 0x3AD7FF;
    private static final int GRAVITY_COLOR = 0xFF8C1C;
    private static final float LAUNCH_SPEED = 24F;
    private static final float PULL_SPEED = 12F;
    private static final float TEAR_SPEED = 6F;
    private static final float YANK_PER_BLOCK = 8F;
    private static final float MIN_YANK = 0.15F;
    private static final float MAX_SPEED = 40F;
    private static final float MIN_DISTANCE = 0.5F;
    private static final float MAX_DISTANCE = 64F;

    private static Tool picked = Tool.NONE;

    /* The physics gun's grab */
    private static Hit grabbed;
    private static float distance;
    private static final Vector3f target = new Vector3f();

    /* The last thing a tool did, flashed over the preview */
    private static long flashAt;
    private static final Vector3f flash = new Vector3f();
    private static String message;
    private static long messageAt;

    /**
     * What a ray met: one shape of one movable body, in world space.
     *
     * @param body   the engine's body
     * @param replay the replay of the actor the body belongs to
     * @param center the middle of the shape the ray met, in world coordinates
     * @param size   the shape's largest half extent, for how far a push on it should reach
     */
    private record Hit(SceneBody body, Replay replay, Vector3f center, float size, float distance)
    {}

    private PhysicsToolController()
    {}

    /** The tool picked in the editor, or the one the player holds when none is */
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
        grabbed = null;
    }

    /** Puts the picked tool away; a tool held in hand stays up */
    public static void reset()
    {
        picked = Tool.NONE;
        grabbed = null;
    }

    /**
     * Whether a tool wants this click before anything else in the editor does: a tool is up and the
     * mouse is over the film preview. The orbit camera asks this before it takes the button.
     */
    public static boolean capturesMouse(UIContext context)
    {
        UIFilmPanel panel = PhysicsAim.filmPanel();

        return panel != null && tool() != Tool.NONE && panel.preview != null && panel.preview.isVisible() && panel.preview.area.isInside(context);
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

    /* Aiming */

    /** Where the tool aims: the mouse, or the middle of the picture while the mouse is locked into looking around */
    private static int[] aim(UIContext context, UIFilmPanel panel, Area viewport)
    {
        if (panel.dashboard.orbitUI.isFreeLook())
        {
            return new int[] {viewport.mx(), viewport.my()};
        }

        return new int[] {context.mouseX, context.mouseY};
    }

    /** The mouse ray: where it starts in the world, and the way it goes */
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

        return new float[] {viewport.x + (clip.x / clip.w + 1F) / 2F * viewport.w, viewport.y + (1F - clip.y / clip.w) / 2F * viewport.h};
    }

    /** The physics scene the preview draws, or null when there is none (physics off, no Jolt, nothing simulated) */
    private static FilmScene scene(UIFilmPanel panel)
    {
        return panel.getController() == null || panel.getController().editorController == null ? null : FilmScenes.get(panel.getController().editorController);
    }

    /**
     * The movable body nearest along the ray: every shape of every body the engine is simulating is
     * tested as the box it is drawn as in the debug overlay, where the body stands on the frame being drawn.
     */
    private static Hit pick(UIFilmPanel panel, Vector3f origin, Vector3f direction)
    {
        FilmScene scene = scene(panel);
        Film film = panel.getData();

        if (scene == null || film == null)
        {
            return null;
        }

        Map<String, IEntity> entities = panel.getController().editorController.getEntities();
        float transition = MinecraftClient.getInstance().getTickDelta();
        Vector3f local = new Vector3f(
            (float) (origin.x - scene.getOriginX()),
            (float) (origin.y - scene.getOriginY()),
            (float) (origin.z - scene.getOriginZ()));
        Hit best = null;

        for (SceneBody body : scene.getBodies())
        {
            if (!body.movable || body.owner == null || !body.isKnown())
            {
                continue;
            }

            Vector3f position = body.getPosition(transition, new Vector3f());
            Quaternionf rotation = body.getRotation(transition, new Quaternionf());

            for (SceneBody.Shape shape : body.getShapes())
            {
                Vector3f center = rotation.transform(new Vector3f(shape.offset())).add(position);
                Quaternionf turn = new Quaternionf(rotation).mul(shape.rotation());
                float t = rayBox(local, direction, center, turn, shape.half());

                if (t < 0F || (best != null && t >= best.distance()))
                {
                    continue;
                }

                Replay replay = replayOf(film, entities, body.owner);

                if (replay != null)
                {
                    Vector3f world = new Vector3f(center).add((float) scene.getOriginX(), (float) scene.getOriginY(), (float) scene.getOriginZ());
                    float size = Math.max(shape.half().x, Math.max(shape.half().y, shape.half().z));

                    best = new Hit(body, replay, world, size, t);
                }
            }
        }

        return best;
    }

    /** The distance along the ray to a turned box, or -1 when the ray misses it */
    private static float rayBox(Vector3f origin, Vector3f direction, Vector3f center, Quaternionf rotation, Vector3f half)
    {
        Quaternionf inverse = new Quaternionf(rotation).conjugate();
        Vector3f o = inverse.transform(new Vector3f(origin).sub(center));
        Vector3f d = inverse.transform(new Vector3f(direction));
        float near = Float.NEGATIVE_INFINITY;
        float far = Float.POSITIVE_INFINITY;

        for (int axis = 0; axis < 3; axis++)
        {
            float oa = o.get(axis);
            float da = d.get(axis);
            float ha = Math.max(half.get(axis), 0.05F);

            if (Math.abs(da) < 1.0e-6F)
            {
                if (oa < -ha || oa > ha)
                {
                    return -1F;
                }

                continue;
            }

            float t1 = (-ha - oa) / da;
            float t2 = (ha - oa) / da;

            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
        }

        if (near > far || far < 0F)
        {
            return -1F;
        }

        return Math.max(near, 0F);
    }

    private static Replay replayOf(Film film, Map<String, IEntity> entities, IEntity entity)
    {
        for (Map.Entry<String, IEntity> entry : entities.entrySet())
        {
            if (entry.getValue() == entity)
            {
                for (Replay replay : film.replays.getList())
                {
                    if (replay.getId().equals(entry.getKey()))
                    {
                        return replay;
                    }
                }
            }
        }

        return null;
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

        if (tool == Tool.PHYSICS && context.mouseButton == 1 && grabbed != null)
        {
            /* The right button while holding: the held body stays where it is */
            freeze(panel, grabbed, tick);
            grabbed = null;

            return true;
        }

        Hit hit = pick(panel, ray[0], ray[1]);

        if (hit == null)
        {
            say(scene(panel) == null ? UIKeys.FILM_PHYSICS_TOOL_NO_SCENE : UIKeys.FILM_PHYSICS_TOOL_MISSED);

            return true;
        }

        if (tool == Tool.PHYSICS)
        {
            if (context.mouseButton == 1)
            {
                freeze(panel, hit, tick);
            }
            else if (Window.isAltPressed() && hit.body().bone != null)
            {
                tear(panel, hit, tick, ray[1]);
            }
            else
            {
                grabbed = hit;
                distance = Math.max(MIN_DISTANCE, ray[0].distance(hit.center()));
                target.set(hit.center());
            }

            return true;
        }

        /* The gravity gun: the left button launches the way the camera looks, the right one pulls towards it */
        Vector3f way = context.mouseButton == 0
            ? new Vector3f(ray[1])
            : new Vector3f(ray[0]).sub(hit.center()).normalize();

        push(panel, hit, tick, way, context.mouseButton == 0 ? LAUNCH_SPEED : PULL_SPEED);

        return true;
    }

    /** Letting go of the left button yanks what the physics gun holds towards where it was dragged */
    public static boolean mouseReleased(UIContext context, UIFilmPanel panel)
    {
        if (grabbed == null || context.mouseButton != 0)
        {
            return false;
        }

        Hit hit = grabbed;
        Vector3f way = new Vector3f(target).sub(hit.center());
        float length = way.length();

        grabbed = null;

        if (length >= MIN_YANK)
        {
            push(panel, hit, panel.getCursor(), way.div(length), Math.min(MAX_SPEED, length * YANK_PER_BLOCK));
        }

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

    /* What the tools write */

    /**
     * An impulse clip on the cursor tick, aimed at the hit body alone: shoving along {@code way} at
     * {@code speed} blocks a second, reaching just past the shape that was hit (a whole crate, or the
     * one limb a ragdoll was taken by, which is what makes it spin).
     */
    private static void push(UIFilmPanel panel, Hit hit, int tick, Vector3f way, float speed)
    {
        Replay replay = hit.replay();
        int local = replay.getTick(tick);
        ImpulseActionClip clip = new ImpulseActionClip();

        clip.tick.set(local);
        clip.duration.set(1);
        clip.point.set(new Point(hit.center().x, hit.center().y, hit.center().z));
        clip.radial.set(false);
        clip.direction.set(new Point(way.x, way.y, way.z));
        clip.strength.set(speed);
        clip.radius.set(hit.size() + 0.75F);
        clip.onlyThisReplay.set(true);

        BaseValue.edit(replay, (r) ->
        {
            release(r, hit.body().path, local);
            clip.layer.set(r.actions.findFreeLayer(clip));
            r.actions.addClip(clip);
        });

        done(panel, hit.center(), UIKeys.FILM_PHYSICS_TOOL_LAUNCHED);
    }

    /** A tear clip on the cursor tick for the ragdoll limb that was hit, kicked the way the camera looks */
    private static void tear(UIFilmPanel panel, Hit hit, int tick, Vector3f way)
    {
        Replay replay = hit.replay();
        int local = replay.getTick(tick);
        TearActionClip clip = new TearActionClip();

        clip.tick.set(local);
        clip.duration.set(1);
        clip.bone.set(hit.body().bone);
        clip.strength.set(TEAR_SPEED);
        clip.direction.set(new Point(way.x, way.y, way.z));

        BaseValue.edit(replay, (r) ->
        {
            clip.layer.set(r.actions.findFreeLayer(clip));
            r.actions.addClip(clip);
        });

        done(panel, hit.center(), UIKeys.FILM_PHYSICS_TOOL_TORN.format(hit.body().bone));
    }

    /** The body's simulated motion baked to keys through the cursor tick, so it holds the pose of that tick */
    private static void freeze(UIFilmPanel panel, Hit hit, int tick)
    {
        FilmScene scene = scene(panel);
        PhysicsBake.Result result = scene == null ? null : scene.bake(hit.replay(), hit.body().path, tick);

        if (result == null)
        {
            say(UIKeys.FILM_PHYSICS_TOOL_NO_SCENE);

            return;
        }

        done(panel, hit.center(), UIKeys.FILM_PHYSICS_TOOL_FROZEN.format(result.keys()));
    }

    /**
     * Lets the form at {@code path} go to physics on {@code local}, when its animation still holds it:
     * the handle is keyed at its current value on the tick before and at 0 on the tick itself.
     */
    private static void release(Replay replay, String path, int local)
    {
        Form root = replay.form.get();
        Form form = root == null ? null : FormUtils.getForm(root, path);

        if (!(form instanceof IPhysicsForm physics))
        {
            return;
        }

        ValueFloat authority = physics.bbs_physics$getAuthority();
        KeyframeChannel channel = replay.properties.getOrCreate(root, FormUtils.getPropertyPath(authority));
        float now = channel == null || channel.isEmpty() ? authority.get() : ((Number) channel.interpolate(local)).floatValue();

        if (now <= 0F)
        {
            return;
        }

        if (channel == null)
        {
            authority.set(0F);

            return;
        }

        if (local > 0)
        {
            channel.insert(local - 1, now);
        }

        channel.insert(local, 0F);
    }

    private static void done(UIFilmPanel panel, Vector3f at, IKey text)
    {
        flash.set(at);
        flashAt = System.nanoTime();
        say(text);

        if (panel.replayEditor != null)
        {
            panel.replayEditor.updateChannelsList();
        }
    }

    private static void say(IKey text)
    {
        message = text.get();
        messageAt = System.nanoTime();
    }

    /* Every frame */

    /** Moves the grab's target after the mouse and draws the beam, the flash, the sight and the hint */
    public static void render(UIContext context, UIFilmPanel panel, Area viewport)
    {
        Tool tool = tool();

        if (tool == Tool.NONE)
        {
            grabbed = null;

            return;
        }

        int[] aim = aim(context, panel, viewport);
        int color = tool == Tool.PHYSICS ? PHYSICS_COLOR : GRAVITY_COLOR;
        float[] muzzle = {viewport.ex() - viewport.w * 0.18F, viewport.ey() - viewport.h * 0.1F};

        if (grabbed != null)
        {
            Vector3f[] ray = ray(panel, viewport, aim[0], aim[1]);

            target.set(new Vector3f(ray[0]).add(new Vector3f(ray[1]).mul(distance)));

            float[] start = project(panel, viewport, grabbed.center());
            float[] end = project(panel, viewport, target);

            if (start != null)
            {
                beam(context, muzzle, start, color);
                ring(context, start[0], start[1], 5F, Colors.setA(color, 0.9F));

                if (end != null)
                {
                    /* The pull: a dotted line from the middle of the body to where it will be yanked */
                    line(context, start, end, Colors.setA(0xFFFFFF, 0.6F));
                    ring(context, end[0], end[1], 3F, Colors.setA(0xFFFFFF, 0.9F));
                }
            }
        }

        float age = (System.nanoTime() - flashAt) / 1.0E9F;

        if (age < 0.35F)
        {
            float[] at = project(panel, viewport, flash);

            if (at != null)
            {
                beam(context, muzzle, at, color);
                ring(context, at[0], at[1], 6F + age * 80F, Colors.setA(color, 1F - age / 0.35F));
            }
        }

        /* With the mouse free the pointer itself aims; locked into looking around, a sight marks the middle */
        if (panel.dashboard.orbitUI.isFreeLook())
        {
            int sight = Colors.setA(color, 0.9F);
            int cx = aim[0];
            int cy = aim[1];

            context.batcher.box(cx - 6, cy, cx - 2, cy + 1, sight);
            context.batcher.box(cx + 3, cy, cx + 7, cy + 1, sight);
            context.batcher.box(cx, cy - 6, cx + 1, cy - 2, sight);
            context.batcher.box(cx, cy + 3, cx + 1, cy + 7, sight);
        }

        String hint = (tool == Tool.PHYSICS ? UIKeys.FILM_PHYSICS_TOOL_HINT_PHYSICS : UIKeys.FILM_PHYSICS_TOOL_HINT_GRAVITY).get();
        int width = context.batcher.getFont().getWidth(hint);

        context.batcher.textCard(hint, viewport.mx() - width / 2F, viewport.ey() - 20, Colors.WHITE, Colors.A50);

        if (message != null && (System.nanoTime() - messageAt) / 1.0E9F < 2.5F)
        {
            int messageWidth = context.batcher.getFont().getWidth(message);

            context.batcher.textCard(message, viewport.mx() - messageWidth / 2F, viewport.ey() - 38, Colors.setA(color, 1F), Colors.A50);
        }
    }

    private static void line(UIContext context, float[] a, float[] b, int color)
    {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        int steps = (int) Math.min(200, Math.max(Math.abs(dx), Math.abs(dy)) / 4F);

        for (int i = 0; i <= steps; i++)
        {
            float t = steps == 0 ? 0F : i / (float) steps;
            float x = a[0] + dx * t;
            float y = a[1] + dy * t;

            context.batcher.box(x, y, x + 2F, y + 2F, color);
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
