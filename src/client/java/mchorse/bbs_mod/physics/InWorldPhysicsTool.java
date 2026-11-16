package mchorse.bbs_mod.physics;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.items.PhysicsToolItem;
import mchorse.bbs_mod.items.PhysicsToolServer;
import mchorse.bbs_mod.network.BBSClientNetworking;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.Bufs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The physics and gravity guns in the player's hand, in the world.
 *
 * <p>Physics gun: hold the left button on an entity, a block or a model block to carry it along the look, the
 * wheel changes the distance, Shift turns it with the mouse instead of the view, the right button freezes it
 * where it is, and letting go throws it with the speed it was being moved at. Gravity gun: the right button pulls
 * the target in front of the player and holds it, the left button launches what is held or what is looked at.</p>
 *
 * <p>The client aims and says where the hold point is each tick; the server does the moving. The clicks are
 * taken before the game turns them into breaking, placing or hitting.</p>
 */
public final class InWorldPhysicsTool
{
    private static final double REACH = 48D;
    private static final double GRAVITY_HOLD = 2.5D;
    private static final float LAUNCH_SPEED = 2.5F;

    private static boolean holding;
    private static boolean gravityHold;
    private static double distance;
    private static Vec3d target;
    private static Vec3d previous;
    private static boolean attackWasDown;
    /** The game repeats the right button while it is held; one press is one action */
    private static boolean useLatched;
    /* The turn the mouse gave while Shift was down, sent with the next tick */
    private static float pendingYaw;
    private static float pendingPitch;

    /* What is drawn and predicted every frame: the hold point eased towards where the look puts it */
    private static Vec3d shown;
    private static long shownAt;
    private static BlockPos heldModel;
    private static final Vector3f predictedRotate = new Vector3f();
    private static Vec3d flash;
    private static int flashTicks;
    private static boolean flashGravity;

    private InWorldPhysicsTool()
    {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register((mc) -> tick());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register((context) -> frame(context./*$ wtd*/tickDelta()/**/));
        ModelBlockEntity.CLIENT_HELD = (pos) -> holding && pos.equals(heldModel);
    }

    /** The tool in the player's main hand, or null */
    private static PhysicsToolItem.Kind kind()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemStack stack = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack();

        return stack.getItem() instanceof PhysicsToolItem item ? item.kind : null;
    }

    private static boolean active()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.player != null && mc.currentScreen == null && kind() != null;
    }

    private static boolean permitted()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (PhysicsToolServer.allowed(mc.player))
        {
            return true;
        }

        mc.player.sendMessage(net.minecraft.text.Text.literal(UIKeys.PHYSICS_TOOL_CREATIVE.get()), true);

        return false;
    }

    /* Input, called from the client's click handling before the game decides what a click means */

    /** Left button. True when the click was the tool's */
    public static boolean onAttack()
    {
        if (!active())
        {
            return false;
        }

        if (!permitted())
        {
            return true;
        }

        if (kind() == PhysicsToolItem.Kind.PHYSICS)
        {
            if (!holding)
            {
                grab(false);
            }
        }
        else
        {
            launch();
        }

        return true;
    }

    /** Right button. True when the click was the tool's */
    public static boolean onUse()
    {
        if (!active())
        {
            return false;
        }

        if (useLatched)
        {
            return true;
        }

        useLatched = true;

        if (!permitted())
        {
            return true;
        }

        if (kind() == PhysicsToolItem.Kind.PHYSICS)
        {
            if (holding)
            {
                send(PhysicsToolServer.FREEZE, (buf) -> {});
                stop();
            }
        }
        else if (holding)
        {
            release(Vec3d.ZERO);
        }
        else
        {
            grab(true);
        }

        return true;
    }

    /** The wheel brings what is held nearer or pushes it away, instead of turning the hotbar */
    public static boolean onScroll(double vertical)
    {
        if (!active() || !holding || vertical == 0D)
        {
            return false;
        }

        distance = Math.max(1.5D, Math.min(REACH, distance * Math.pow(1.12D, vertical)));

        return true;
    }

    /** Whether the held attack key must not break blocks this tick */
    public static boolean blocksBreaking()
    {
        return active();
    }

    /* Aiming */

    private record Aim(byte kind, int entity, BlockPos pos, Vec3d point, double distance)
    {}

    private static Aim aim()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        Vec3d eye = player.getCameraPosVec(1F);
        Vec3d look = player.getRotationVec(1F);
        Vec3d end = eye.add(look.multiply(REACH));
        BlockHitResult block = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? REACH : block.getPos().distanceTo(eye);
        Box box = player.getBoundingBox().stretch(look.multiply(reach)).expand(1D);
        EntityHitResult entity = ProjectileUtil.raycast(player, eye, eye.add(look.multiply(reach)), box,
            (e) -> !e.isSpectator() && e != player && !(e instanceof PlayerEntity), reach * reach);

        if (entity != null)
        {
            Entity hit = entity.getEntity();

            return new Aim(PhysicsToolServer.KIND_ENTITY, hit.getId(), null, hit.getPos().add(0D, hit.getHeight() / 2D, 0D), entity.getPos().distanceTo(eye));
        }

        if (block.getType() == HitResult.Type.BLOCK)
        {
            BlockPos pos = block.getBlockPos();
            byte kind = mc.world.getBlockEntity(pos) instanceof ModelBlockEntity ? PhysicsToolServer.KIND_MODEL_BLOCK : PhysicsToolServer.KIND_BLOCK;

            return new Aim(kind, 0, pos, Vec3d.ofCenter(pos), block.getPos().distanceTo(eye));
        }

        return null;
    }

    private static void write(PacketByteBuf buf, Aim aim)
    {
        buf.writeByte(aim.kind());

        if (aim.kind() == PhysicsToolServer.KIND_ENTITY)
        {
            buf.writeInt(aim.entity());
        }
        else
        {
            buf.writeBlockPos(aim.pos());
        }
    }

    private static void grab(boolean pull)
    {
        Aim aim = aim();

        if (aim == null)
        {
            return;
        }

        send(PhysicsToolServer.GRAB, (buf) -> write(buf, aim));

        holding = true;
        gravityHold = pull;
        distance = pull ? GRAVITY_HOLD : Math.max(1.5D, aim.distance());
        target = aim.point();
        previous = target;
        shown = target;
        shownAt = System.nanoTime();
        attackWasDown = true;
        pendingYaw = 0F;
        pendingPitch = 0F;
        heldModel = null;

        if (aim.kind() == PhysicsToolServer.KIND_MODEL_BLOCK && MinecraftClient.getInstance().world.getBlockEntity(aim.pos()) instanceof ModelBlockEntity model)
        {
            heldModel = aim.pos();
            predictedRotate.set(model.getProperties().getTransform().rotate);
        }
    }

    private static void launch()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d look = mc.player.getRotationVec(1F).multiply(LAUNCH_SPEED);

        if (holding)
        {
            flash(target, true);
            release(look);

            return;
        }

        Aim aim = aim();

        if (aim == null || aim.kind() == PhysicsToolServer.KIND_MODEL_BLOCK)
        {
            return;
        }

        send(PhysicsToolServer.LAUNCH, (buf) ->
        {
            write(buf, aim);
            buf.writeFloat((float) look.x);
            buf.writeFloat((float) look.y);
            buf.writeFloat((float) look.z);
        });
        flash(aim.point(), true);
    }

    private static void release(Vec3d velocity)
    {
        send(PhysicsToolServer.RELEASE, (buf) ->
        {
            buf.writeFloat((float) velocity.x);
            buf.writeFloat((float) velocity.y);
            buf.writeFloat((float) velocity.z);
        });
        stop();
    }

    private static void stop()
    {
        holding = false;
        heldModel = null;
        pendingYaw = 0F;
        pendingPitch = 0F;
    }

    /**
     * The mouse while Shift is down and something is held: it turns the held thing, not the view. The turn is
     * predicted on a held model block at once, and sent to the server with the next tick.
     */
    public static boolean consumeLook(double dx, double dy)
    {
        if (!holding || !active() || !MinecraftClient.getInstance().options.sneakKey.isPressed())
        {
            return false;
        }

        float yaw = (float) dx * 0.15F;
        float pitch = (float) dy * 0.15F;

        pendingYaw += yaw;
        pendingPitch += pitch;

        if (heldModel != null)
        {
            predictedRotate.y += (float) Math.toRadians(-yaw);
            predictedRotate.x += (float) Math.toRadians(pitch);
        }

        return true;
    }

    /** Every frame, before the world is drawn: the hold point eased towards the look, and a held model block moved there */
    private static void frame(float delta)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!holding || mc.player == null || shown == null)
        {
            return;
        }

        long now = System.nanoTime();
        float seconds = Math.min(0.1F, Math.max(0F, (now - shownAt) / 1.0E9F));
        double ease = 1D - Math.exp(-seconds * 24D);
        Vec3d wanted = mc.player.getCameraPosVec(delta).add(mc.player.getRotationVec(delta).multiply(distance));

        shownAt = now;
        shown = shown.add(wanted.subtract(shown).multiply(ease));

        if (heldModel != null && mc.world.getBlockEntity(heldModel) instanceof ModelBlockEntity model)
        {
            mchorse.bbs_mod.utils.pose.Transform transform = model.getProperties().getTransform();
            Vec3d base = new Vec3d(heldModel.getX() + 0.5D, heldModel.getY(), heldModel.getZ() + 0.5D);
            Vec3d offset = shown.subtract(base);

            transform.translate.set((float) offset.x, (float) offset.y, (float) offset.z);
            transform.rotate.set(predictedRotate);
        }
    }

    private static void flash(Vec3d point, boolean gravity)
    {
        flash = point;
        flashTicks = 6;
        flashGravity = gravity;
    }

    private static void send(byte action, java.util.function.Consumer<PacketByteBuf> body)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeByte(action);
        body.accept(buf);
        BBSClientNetworking.send(ServerNetwork.SERVER_PHYSICS_TOOL, buf);
    }

    /* Every tick */

    private static void tick()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (flashTicks > 0)
        {
            flashTicks -= 1;
        }

        if (!mc.options.useKey.isPressed())
        {
            useLatched = false;
        }

        if (!holding)
        {
            attackWasDown = false;

            return;
        }

        if (mc.player == null || kind() == null)
        {
            release(Vec3d.ZERO);

            return;
        }

        ClientPlayerEntity player = mc.player;
        boolean attackDown = mc.options.attackKey.isPressed();

        /* The physics gun holds while the button is down; letting go throws with the hand's speed */
        if (!gravityHold && attackWasDown && !attackDown)
        {
            Vec3d swing = target.subtract(previous);

            release(swing.length() > 0.05D ? swing : Vec3d.ZERO);

            return;
        }

        attackWasDown = attackDown;

        /* The turn the mouse gave since the last tick (see consumeLook) */
        float yaw = pendingYaw;
        float pitch = pendingPitch;

        pendingYaw = 0F;
        pendingPitch = 0F;

        Vec3d eye = player.getCameraPosVec(1F);

        previous = target;
        target = eye.add(player.getRotationVec(1F).multiply(distance));

        Vec3d point = target;
        float turnYaw = yaw;
        float turnPitch = pitch;

        send(PhysicsToolServer.MOVE, (buf) ->
        {
            buf.writeDouble(point.x);
            buf.writeDouble(point.y);
            buf.writeDouble(point.z);
            buf.writeFloat(turnYaw);
            buf.writeFloat(turnPitch);
        });
    }

    /* Drawing */

    /** The beam from the muzzle to what is held, curving and pulsing, in the world's own space */
    public static void renderWorld(WorldRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        PhysicsToolItem.Kind kind = kind();

        if (mc.player == null || kind == null)
        {
            return;
        }

        Vec3d end = holding ? (shown != null ? shown : target) : flashTicks > 0 ? flash : null;

        if (end == null)
        {
            return;
        }

        float delta = context./*$ wtd*/tickDelta()/**/;
        Vec3d camera = context.camera().getPos();
        Vec3d look = mc.player.getRotationVec(delta);
        Vec3d up = new Vec3d(0D, 1D, 0D);
        Vec3d right = look.crossProduct(up);

        right = right.lengthSquared() < 1e-6D ? new Vec3d(1D, 0D, 0D) : right.normalize();

        Vec3d down = right.crossProduct(look).normalize().multiply(-1D);
        Vec3d muzzle = mc.options.getPerspective().isFirstPerson()
            ? camera.add(look.multiply(0.7D)).add(right.multiply(0.32D)).add(down.multiply(0.24D))
            : mc.player.getCameraPosVec(delta).add(look.multiply(0.7D)).add(right.multiply(0.4D)).add(down.multiply(0.45D));

        boolean physics = kind == PhysicsToolItem.Kind.PHYSICS && (holding ? !gravityHold : !flashGravity);
        float r = physics ? 0.23F : 1F;
        float g = physics ? 0.84F : 0.55F;
        float b = physics ? 1F : 0.11F;
        float time = (mc.player.age + delta) / 20F;
        Matrix4f matrix = new Matrix4f(context.matrixStack().peek().getPositionMatrix());
        Vec3d middle = muzzle.add(end).multiply(0.5D);
        Vec3d bend = right.multiply(Math.sin(time * 3D) * 0.25D).add(up.multiply(0.2D + Math.cos(time * 2.3D) * 0.1D));
        Vec3d control = middle.add(bend.multiply(muzzle.distanceTo(end) * 0.15D));
        int steps = 24;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int pass = 0; pass < 2; pass++)
        {
            float width = pass == 0 ? 0.09F : 0.025F;
            float alpha = pass == 0 ? 0.35F : 0.95F;
            Vec3d last = muzzle;

            for (int i = 1; i <= steps; i++)
            {
                double t = i / (double) steps;
                double u = 1D - t;
                Vec3d point = muzzle.multiply(u * u).add(control.multiply(2D * u * t)).add(end.multiply(t * t));
                Vec3d direction = point.subtract(last);
                Vec3d toCamera = camera.subtract(point);
                Vec3d side = direction.crossProduct(toCamera);

                if (side.lengthSquared() > 1e-9D)
                {
                    side = side.normalize().multiply(width);

                    float pulse = alpha * (0.6F + 0.4F * (float) Math.sin(time * 16D - t * 10D));
                    float cr = pass == 1 ? Math.min(1F, r + 0.5F) : r;
                    float cg = pass == 1 ? Math.min(1F, g + 0.4F) : g;
                    float cb = pass == 1 ? Math.min(1F, b + 0.4F) : b;

                    quad(builder, matrix, camera, last.add(side), last.subtract(side), point.subtract(side), point.add(side), cr, cg, cb, pulse);
                }

                last = point;
            }
        }

        Bufs.draw(builder);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, Vec3d camera, Vec3d a, Vec3d b, Vec3d c, Vec3d d, float r, float g, float bl, float alpha)
    {
        Vec3d[] corners = {a, b, c, a, c, d};

        for (Vec3d p : corners)
        {
            builder.vertex(matrix, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z)).color(r, g, bl, alpha)/*$ nx*/.next()/**/;
        }
    }

    /** The hint over the hotbar while a tool is in hand */
    public static void renderHud(Batcher2D batcher)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        PhysicsToolItem.Kind kind = active() && !mc.options.hudHidden ? kind() : null;

        if (kind == null)
        {
            return;
        }

        String line = kind == PhysicsToolItem.Kind.PHYSICS
            ? (holding ? UIKeys.PHYSICS_TOOL_HINT_HOLD.get() : UIKeys.PHYSICS_TOOL_HINT_GRAB.get())
            : (holding ? UIKeys.PHYSICS_TOOL_HINT_GRAVITY_HOLD.get() : UIKeys.PHYSICS_TOOL_HINT_GRAVITY.get());
        int width = batcher.getFont().getWidth(line);
        int x = (mc.getWindow().getScaledWidth() - width) / 2;
        int y = mc.getWindow().getScaledHeight() - 72;
        int color = kind == PhysicsToolItem.Kind.PHYSICS ? 0xFF3AD7FF : 0xFFFF8C1C;

        batcher.textShadow(line, x, y, color);
    }
}
