package mchorse.bbs_mod.film.eye;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The block outline of the replay looked out of. The game outlines what the player is looking at,
 * which through an actor's eyes is the wrong block (or none), so while a Point of View clip has the
 * camera that outline is held back, and the one drawn instead is of the block the actor looks at,
 * within the game's reach, when the clip lets it be seen from the eyes.
 */
public final class PovBlockOutline
{
    /** How far the game lets a player reach for a block */
    private static final double REACH = 4.5D;

    private PovBlockOutline()
    {}

    /** Whether the game's own outline should be drawn: not while the camera looks out of an actor */
    public static boolean allowVanilla()
    {
        return ActorEyeState.current() == null;
    }

    public static void render(WorldRenderContext context)
    {
        ActorEyeState.Report report = ActorEyeState.current();

        if (report == null || !report.firstPerson() || !report.clip().blockOutline.get())
        {
            return;
        }

        World world = context.world();
        Camera camera = context.camera();

        if (world == null || camera == null)
        {
            return;
        }

        Vec3d eye = camera.getPos();
        Vec3d end = eye.add(Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(REACH));
        BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE,
            //? if >=1.20.4 {
            ShapeContext.absent()
            //?} else {
            /*null
            *///?}
        ));

        if (hit == null || hit.getType() != HitResult.Type.BLOCK)
        {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.absent());

        if (state.isAir() || shape.isEmpty())
        {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        double ox = pos.getX() - eye.x;
        double oy = pos.getY() - eye.y;
        double oz = pos.getZ() - eye.z;

        /* The game's outline: thin, black, a little see-through */
        shape.forEachBox((x1, y1, z1, x2, y2, z2) ->
        {
            Box box = new Box(x1, y1, z1, x2, y2, z2).offset(ox, oy, oz);

            WorldRenderer.drawBox(matrices, lines, box, 0F, 0F, 0F, 0.4F);
        });

        consumers.draw(RenderLayer.getLines());
    }
}
