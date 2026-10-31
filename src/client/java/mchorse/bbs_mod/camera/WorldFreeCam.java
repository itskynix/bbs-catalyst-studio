package mchorse.bbs_mod.camera;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.utils.MathUtils;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

/**
 * A free flying camera in the 3D world, independent of the editor's preview window.
 *
 * <p>With it on, the vanilla mouse lock does the pointer's part (hidden, no button held), WASD flies
 * the camera, jump and sneak go up and down, sprint is fast and the wheel changes the speed. The
 * player is left standing: {@code KeyboardInputMixin} and {@code MouseMixin} hand their input to
 * this instead. When it ends, its pose can be handed to the editor's flight camera, from where it
 * becomes a keyframe like any other flown position.</p>
 */
public class WorldFreeCam implements ICameraController
{
    public static final WorldFreeCam INSTANCE = new WorldFreeCam();

    private static final float MIN_SPEED = 0.25F;
    private static final float MAX_SPEED = 64F;
    private static final float FAST_FACTOR = 4F;
    private static final double MAX_FRAME_SECONDS = 0.1D;

    private boolean active;
    private final Vector3d position = new Vector3d();
    /** Vanilla's yaw and pitch, in degrees. */
    private float yaw;
    private float pitch;
    private float fov = MathUtils.toRad(70F);
    /** Blocks per second. */
    private float speed = 6F;
    private long lastNanos;

    public static boolean isActive()
    {
        return INSTANCE.active;
    }

    public static void toggle()
    {
        if (INSTANCE.active)
        {
            INSTANCE.disable(true);
        }
        else
        {
            INSTANCE.enable();
        }
    }

    /** Whatever is on screen right now is where the free camera starts. */
    public void enable()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        CameraController controller = BBSModClient.getCameraController();

        if (this.active || mc.world == null)
        {
            return;
        }

        Entity entity = mc.getCameraEntity();

        if (controller.getCurrent() != null)
        {
            this.position.set(controller.getPosition());
            this.yaw = controller.getYaw();
            this.pitch = controller.getPitch();
            this.fov = controller.camera.fov;
        }
        else if (entity != null)
        {
            Vec3d eye = entity.getEyePos();

            this.position.set(eye.x, eye.y, eye.z);
            this.yaw = entity.getYaw();
            this.pitch = entity.getPitch();
            this.fov = MathUtils.toRad(mc.options.getFov().getValue().floatValue());
        }
        else
        {
            return;
        }

        this.active = true;
        this.lastNanos = System.nanoTime();

        controller.add(this);

        /* In the world the pointer is the game's: closing the screen is what hides and locks it */
        if (mc.currentScreen != null)
        {
            mc.setScreen(null);
        }

        mc.inGameHud.setOverlayMessage(Text.translatable("bbs.free_cam.on"), false);
    }

    /**
     * @param sync hand the pose to the editor's flight camera, so the next flight, and the
     *             keyframes inserted from it, start where the free camera stopped
     */
    public void disable(boolean sync)
    {
        if (!this.active)
        {
            return;
        }

        this.active = false;

        BBSModClient.getCameraController().remove(this);

        if (sync)
        {
            this.syncToEditor();
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.inGameHud != null)
        {
            mc.inGameHud.setOverlayMessage(Text.translatable("bbs.free_cam.off"), false);
        }
    }

    /** The world went away: nothing to sync, nowhere to fly. */
    public static void reset()
    {
        INSTANCE.disable(false);
    }

    private void syncToEditor()
    {
        Camera camera = new Camera();

        this.setup(camera, 0F);
        BBSModClient.getDashboard().orbit.setup(camera);
    }

    /** Write the current pose into a film position (a keyframe's camera data). */
    public void applyTo(Position position)
    {
        position.point.set(this.position.x, this.position.y, this.position.z);
        position.angle.set(this.yaw - 180F, this.pitch, 0F, MathUtils.toDeg(this.fov));
    }

    /** Mouse look, in the same units vanilla turns the player by. */
    public void look(double dx, double dy)
    {
        this.yaw += (float) dx * 0.15F;
        this.pitch = MathUtils.clamp(this.pitch + (float) dy * 0.15F, -90F, 90F);
    }

    /** One wheel notch changes the speed. */
    public void scroll(double vertical)
    {
        this.speed = MathUtils.clamp(this.speed * (vertical > 0 ? 1.25F : 0.8F), MIN_SPEED, MAX_SPEED);
    }

    private static boolean down(KeyBinding binding)
    {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);

        if (key.getCode() == GLFW.GLFW_KEY_UNKNOWN)
        {
            return false;
        }

        if (key.getCategory() == InputUtil.Type.MOUSE)
        {
            return GLFW.glfwGetMouseButton(Window.getWindow(), key.getCode()) == GLFW.GLFW_PRESS;
        }

        return key.getCategory() == InputUtil.Type.KEYSYM && Window.isKeyPressed(key.getCode());
    }

    /** Flies by the time the frame took, so the speed does not depend on the frame rate. */
    private void fly()
    {
        long now = System.nanoTime();
        double seconds = Math.min((now - this.lastNanos) / 1_000_000_000D, MAX_FRAME_SECONDS);

        this.lastNanos = now;

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.currentScreen != null || seconds <= 0D)
        {
            return;
        }

        GameOptions options = mc.options;
        double forward = (down(options.forwardKey) ? 1D : 0D) - (down(options.backKey) ? 1D : 0D);
        double strafe = (down(options.leftKey) ? 1D : 0D) - (down(options.rightKey) ? 1D : 0D);
        double vertical = (down(options.jumpKey) ? 1D : 0D) - (down(options.sneakKey) ? 1D : 0D);

        if (forward == 0D && strafe == 0D && vertical == 0D)
        {
            return;
        }

        double distance = this.speed * (down(options.sprintKey) ? FAST_FACTOR : 1D) * seconds;
        double yawRad = Math.toRadians(this.yaw);
        double pitchRad = Math.toRadians(this.pitch);
        double cos = Math.cos(pitchRad);

        /* Forward follows the gaze, strafing stays level */
        this.position.x += (-Math.sin(yawRad) * cos * forward + Math.cos(yawRad) * strafe) * distance;
        this.position.y += (-Math.sin(pitchRad) * forward + vertical) * distance;
        this.position.z += (Math.cos(yawRad) * cos * forward + Math.sin(yawRad) * strafe) * distance;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        if (this.active)
        {
            this.fly();
        }

        camera.position.set(this.position);
        camera.rotation.set(MathUtils.toRad(this.pitch), MathUtils.toRad(this.yaw - 180F), 0F);
        camera.fov = this.fov;
    }

    @Override
    public int getPriority()
    {
        return 1000;
    }
}
