package mchorse.bbs_mod.film.eye;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.camera.clips.misc.ActorEyeClientClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * The first-person hands of an actor being looked out of: in the game's hand pass, instead of the
 * player's, the hands of the replay looked out of, as its point of view animates and places them
 * ({@link PovHands}), through the hand lens and the clip's own arm offset. The arms come from the
 * model's first-person slots, the same ones a morphed player's arms use; a form with none shows its
 * items only.
 */
public final class ActorEyeHands
{
    private ActorEyeHands()
    {}

    /**
     * Draw the hands if the picture is first person through an actor. True when it is (the game's own
     * hand pass should then not draw the player's), whether or not the clip wants hands at all.
     */
    public static boolean render(Camera camera, float tickDelta)
    {
        ActorEyeState.Report report = ActorEyeState.current(camera.getPos());

        if (report == null)
        {
            return false;
        }

        ActorEyeClientClip clip = report.clip();
        IEntity entity = report.entity();
        Form form = entity.getForm();

        /* Out of the eyes there are no first-person hands, the player's included */
        if (!report.firstPerson() || !clip.hands.get() || form == null)
        {
            return true;
        }

        /* A spectator has no hands, as the game draws none */
        if (report.replay() != null && !report.replay().pov.gameMode.isEmpty() && report.replay().pov.gameMode.interpolate(report.tick(), 0) == 3)
        {
            return true;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorting = RenderSystem.getVertexSorting();
        MatrixStack matrices = new MatrixStack();
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        int light = mc.world == null ? LightmapTextureManager.MAX_LIGHT_COORDINATE : WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(camera.getPos()));

        /* The hand's own lens, as the game draws hands: a fixed field of view, apart from the scene's */
        RenderSystem.setProjectionMatrix(mc.gameRenderer.getBasicProjectionMatrix(70D), VertexSorter.BY_DISTANCE);

        try
        {
            Point offset = clip.handOffset.get();

            matrices.translate(offset.x, offset.y, offset.z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(clip.handPitch.get()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(clip.handYaw.get()));

            /* The replay looked out of, so its point of view (channels, rig, POV body parts) drives the hands */
            PovHands.render(matrices, consumers, light, report.replay(), entity, report.tick(), tickDelta, null);
        }
        finally
        {
            RenderSystem.setProjectionMatrix(projection, sorting);
        }

        return true;
    }
}
