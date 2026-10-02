package mchorse.bbs_mod.camera.pov;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.camera.clips.overwrite.POVClip;
import mchorse.bbs_mod.camera.clips.overwrite.POVClientState;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.mixin.client.HeldItemRendererAccessor;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;

/**
 * First-person hand rendering and arm bobbing dynamics for PoV Cam clips.
 *
 * <p>Bridges the target replay actor's custom model, skin texture, slim/classic arm
 * dimensions, held items, and walking sway into the first-person camera pipeline with
 * independent left/right hand visibility controls and proper view-space alignment.</p>
 */
public class POVHandRenderer
{
    private static boolean isRendering = false;

    public static boolean isRendering()
    {
        return isRendering;
    }

    public static boolean isPovActive()
    {
        POVClip clip = POVClientState.getActiveClip();
        IEntity actor = POVClientState.getActiveActor();

        if (clip == null || actor == null)
        {
            return false;
        }

        return clip.isFirstPerson() && clip.povOutput.get() && clip.renderHands.get();
    }

    public static boolean shouldRenderArm(Arm arm)
    {
        POVClip clip = POVClientState.getActiveClip();

        if (clip == null)
        {
            return true;
        }

        return arm == Arm.RIGHT ? clip.rightHandVisible.get() : clip.leftHandVisible.get();
    }

    public static boolean shouldRenderHand(Hand hand)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        Arm mainArm = client.player != null ? client.player.getMainArm() : Arm.RIGHT;
        Arm arm = hand == Hand.MAIN_HAND ? mainArm : mainArm.getOpposite();

        return shouldRenderArm(arm);
    }

    public static boolean render(MatrixStack matrices, Camera camera, float tickDelta, HeldItemRenderer heldItemRenderer)
    {
        if (isRendering || !isPovActive())
        {
            return false;
        }

        POVClip clip = POVClientState.getActiveClip();
        IEntity actor = POVClientState.getActiveActor();
        Replay replay = POVClientState.getActiveReplay();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        if (player == null || client.world == null || heldItemRenderer == null)
        {
            return false;
        }

        isRendering = true;

        /* 1. Resolve Target Replay Actor's Form (Model, Skin, Geometry) */
        Form actorForm = actor.getForm();

        if (actorForm == null && replay != null)
        {
            actorForm = replay.form.get();
        }

        if (actorForm != null)
        {
            actorForm = FormUtils.getRoot(actorForm);
        }
        else
        {
            ModelForm fallback = new ModelForm();
            fallback.model.set("player/steve");
            actorForm = fallback;
        }

        Morph morph = Morph.getMorph(player);
        Form origForm = morph != null ? morph.getForm() : null;

        ItemStack origMain = player.getMainHandStack();
        ItemStack origOff = player.getOffHandStack();

        HeldItemRendererAccessor heldAccessor = (HeldItemRendererAccessor) heldItemRenderer;
        ItemStack origHeldMain = heldAccessor.bbs$getMainHand();
        ItemStack origHeldOff = heldAccessor.bbs$getOffHand();
        float origEquipMain = heldAccessor.bbs$getEquipProgressMainHand();
        float origPrevEquipMain = heldAccessor.bbs$getPrevEquipProgressMainHand();
        float origEquipOff = heldAccessor.bbs$getEquipProgressOffHand();
        float origPrevEquipOff = heldAccessor.bbs$getPrevEquipProgressOffHand();

        float origSwing = player.handSwingProgress;
        float origLastSwing = player.lastHandSwingProgress;
        boolean origSwinging = player.handSwinging;

        Matrix4f origProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter origSorter = RenderSystem.getVertexSorting();

        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        float aspect = (float) width / (float) Math.max(1, height);
        float fov = clip.fov.get() > 0F ? clip.fov.get() : client.options.getFov().getValue().floatValue();

        Matrix4f handProj = new Matrix4f().setPerspective((float) Math.toRadians(fov), aspect, 0.05F, 100.0F);
        RenderSystem.setProjectionMatrix(handProj, VertexSorter.BY_DISTANCE);

        matrices.push();
        matrices.peek().getPositionMatrix().identity();
        matrices.peek().getNormalMatrix().identity();

        try
        {
            /* 2. Bind Actor Form into Morph for PlayerEntityRendererMixin */
            if (morph != null)
            {
                morph.setFormRaw(actorForm);
            }

            /* 3. Bind Actor Held Items and Force Full Equip Progress (1.0F) */
            ItemStack actorMain = actor.getEquipmentStack(EquipmentSlot.MAINHAND);
            ItemStack actorOff = actor.getEquipmentStack(EquipmentSlot.OFFHAND);

            player.setStackInHand(Hand.MAIN_HAND, actorMain);
            player.setStackInHand(Hand.OFF_HAND, actorOff);

            heldAccessor.bbs$setMainHand(actorMain);
            heldAccessor.bbs$setOffHand(actorOff);
            heldAccessor.bbs$setEquipProgressMainHand(1.0F);
            heldAccessor.bbs$setPrevEquipProgressMainHand(1.0F);
            heldAccessor.bbs$setEquipProgressOffHand(1.0F);
            heldAccessor.bbs$setPrevEquipProgressOffHand(1.0F);

            /* 4. Sync Actor Arm Swing Progress */
            float swing = actor.getHandSwingProgress(tickDelta);
            player.handSwingProgress = swing;
            player.lastHandSwingProgress = swing;
            player.handSwinging = swing > 0.001F;

            /* 5. Natural View-Space Walking Bobbing Sway */
            if (clip.bobbing.get())
            {
                float strength = clip.bobStrength.get();

                if (strength > 0.001F)
                {
                    float limbPos = actor.getLimbPos(tickDelta);
                    float limbSpeed = actor.getLimbSpeed(tickDelta);

                    if (limbSpeed > 0.001F)
                    {
                        float speed = Math.min(limbSpeed, 1.0F);
                        float smoothSpeed = speed * speed * (3.0F - 2.0F * speed);
                        float bobFactor = smoothSpeed * strength;

                        float stepCycle = limbPos * 0.6662F;
                        float rollSway = (float) Math.sin(stepCycle);
                        float pitchDip = (float) (Math.cos(stepCycle * 2.0F) * 0.5 + 0.5);

                        float bobX = rollSway * bobFactor * 0.015F;
                        float bobY = -pitchDip * bobFactor * 0.012F;

                        matrices.translate(bobX, bobY, 0F);
                        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollSway * bobFactor * 0.7F));
                        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDip * bobFactor * 0.5F));
                    }
                }
            }

            /* 6. Lighting at Actor Head Position */
            BlockPos actorPos = BlockPos.ofFloored(actor.getX(), actor.getY() + actor.getEyeHeight(), actor.getZ());
            int light = LightmapTextureManager.pack(
                client.world.getLightLevel(LightType.BLOCK, actorPos),
                client.world.getLightLevel(LightType.SKY, actorPos)
            );

            if (light == 0)
            {
                light = 15728880;
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(515);

            VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();

            heldItemRenderer.renderItem(tickDelta, matrices, consumers, player, light);
            consumers.draw();
        }
        catch (Exception ignored)
        {}
        finally
        {
            /* 7. Cleanly Restore Live Player State */
            if (morph != null)
            {
                morph.setFormRaw(origForm);
            }

            player.setStackInHand(Hand.MAIN_HAND, origMain);
            player.setStackInHand(Hand.OFF_HAND, origOff);

            heldAccessor.bbs$setMainHand(origHeldMain);
            heldAccessor.bbs$setOffHand(origHeldOff);
            heldAccessor.bbs$setEquipProgressMainHand(origEquipMain);
            heldAccessor.bbs$setPrevEquipProgressMainHand(origPrevEquipMain);
            heldAccessor.bbs$setEquipProgressOffHand(origEquipOff);
            heldAccessor.bbs$setPrevEquipProgressOffHand(origPrevEquipOff);

            player.handSwingProgress = origSwing;
            player.lastHandSwingProgress = origLastSwing;
            player.handSwinging = origSwinging;

            matrices.pop();
            RenderSystem.setProjectionMatrix(origProj, origSorter);
            isRendering = false;
        }

        return true;
    }
}
