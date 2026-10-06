/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
 *  net.minecraft.block.BlockState
 *  net.minecraft.block.ShapeContext
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.render.Camera
 *  net.minecraft.client.render.RenderLayer
 *  net.minecraft.client.render.VertexConsumer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.client.util.math.MatrixStack$Entry
 *  net.minecraft.client.world.ClientWorld
 *  net.minecraft.entity.Entity
 *  net.minecraft.util.hit.BlockHitResult
 *  net.minecraft.util.hit.HitResult$Type
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.math.Vec3d
 *  net.minecraft.util.shape.VoxelShape
 *  net.minecraft.world.BlockView
 *  net.minecraft.world.RaycastContext
 *  net.minecraft.world.RaycastContext$FluidHandling
 *  net.minecraft.world.RaycastContext$ShapeType
 */
package mchorse.bbs_mod.camera.pov.render;

import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

public final class PovBlockOutlineRenderer {
    private static final double BLOCK_REACH = 4.5;

    private PovBlockOutlineRenderer() {
    }

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(PovBlockOutlineRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        BlockPos blockPos;
        BlockState state;
        if (!PovBlockOutlineRenderer.shouldRenderOutline(context.tickDelta())) {
            return;
        }
        Camera camera = context.camera();
        ClientWorld world = context.world();
        MinecraftClient client = MinecraftClient.getInstance();
        if (camera == null || world == null || client == null) {
            return;
        }
        Vec3d cameraPos = camera.getPos();
        Vec3d dir = Vec3d.fromPolar((float)camera.getPitch(), (float)camera.getYaw());
        double reach = 4.5;
        Entity cameraEntity = client.cameraEntity != null ? client.cameraEntity : client.player;
        BlockHitResult hit = world.raycast(new RaycastContext(cameraPos, cameraPos.add(dir.multiply(reach)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, cameraEntity));
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && !(state = world.getBlockState(blockPos = hit.getBlockPos())).isAir() && world.getWorldBorder().contains(blockPos)) {
            MatrixStack matrices = context.matrixStack();
            VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLines());
            VoxelShape shape = state.getOutlineShape((BlockView)world, blockPos, ShapeContext.of((Entity)cameraEntity));
            PovBlockOutlineRenderer.drawUnifiedShapeOutline(matrices, consumer, shape, (double)blockPos.getX() - cameraPos.x, (double)blockPos.getY() - cameraPos.y, (double)blockPos.getZ() - cameraPos.z, 0.0f, 0.0f, 0.0f, 0.4f);
        }
    }

    private static void drawUnifiedShapeOutline(MatrixStack matrices, VertexConsumer vertexConsumer, VoxelShape shape, double offsetX, double offsetY, double offsetZ, float r, float g, float b, float a) {
        MatrixStack.Entry entry = matrices.peek();
        shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float)(x2 - x1);
            float dy = (float)(y2 - y1);
            float dz = (float)(z2 - z1);
            float len = MathHelper.sqrt((float)(dx * dx + dy * dy + dz * dz));
            vertexConsumer.vertex(entry.getPositionMatrix(), (float)(x1 + offsetX), (float)(y1 + offsetY), (float)(z1 + offsetZ)).color(r, g, b, a).normal(entry.getNormalMatrix(), dx /= len, dy /= len, dz /= len).next();
            vertexConsumer.vertex(entry.getPositionMatrix(), (float)(x2 + offsetX), (float)(y2 + offsetY), (float)(z2 + offsetZ)).color(r, g, b, a).normal(entry.getNormalMatrix(), dx, dy, dz).next();
        });
    }

    private static boolean shouldRenderOutline(float tickDelta) {
        PovPlaybackContext.Frame frame;
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel != null && panel.getController() != null) {
            float filmTick;
            int povMode = panel.getController().getPovMode();
            if (povMode == 6) {
                return true;
            }
            Film film = (Film)panel.getData();
            PovCameraClip clip = PovCameraClips.resolve(film, filmTick = panel.getRunner() != null && panel.getRunner().isRunning() ? (float)panel.getRunner().ticks + tickDelta : (float)panel.getCursor());
            if (clip != null && ((Boolean)clip.headLook.get()).booleanValue() && ((Boolean)clip.blockOutline.get()).booleanValue()) {
                return true;
            }
        }
        if ((frame = PovPlaybackContext.getActive(tickDelta)) != null) {
            if (frame.clip() != null) {
                return (Boolean)frame.clip().headLook.get() != false && (Boolean)frame.clip().blockOutline.get() != false;
            }
            return true;
        }
        return false;
    }
}

