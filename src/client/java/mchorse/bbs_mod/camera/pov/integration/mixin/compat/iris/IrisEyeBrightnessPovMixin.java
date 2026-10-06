/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.Position
 *  net.minecraft.util.math.Vec3d
 *  net.minecraft.world.LightType
 *  org.joml.Vector2i
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Pseudo
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.compat.iris;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovBlindnessHelper;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovDarknessHelper;
import mchorse.bbs_mod.camera.pov.hand.playback.PovHandPlayback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"net.irisshaders.iris.uniforms.CommonUniforms"}, remap=false)
public class IrisEyeBrightnessPovMixin {
    @Inject(method={"getEyeBrightness"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$cameraEyeBrightness(CallbackInfoReturnable<Vector2i> info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && PovHandPlayback.isHandActive(client.getTickDelta())) {
            Vec3d camPos = client.gameRenderer.getCamera().getPos();
            BlockPos position = BlockPos.ofFloored((Position)camPos);
            if (!client.world.isAir(position) && client.world.isAir(position.up())) {
                position = position.up();
            }
            int blockLight = client.world.getLightLevel(LightType.BLOCK, position);
            int skyLight = client.world.getLightLevel(LightType.SKY, position);
            info.setReturnValue(new Vector2i(blockLight * 16, skyLight * 16));
        }
    }

    @Inject(method={"getDarknessFactor"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$darknessFactor(CallbackInfoReturnable<Float> info) {
        float delta = MinecraftClient.getInstance().getTickDelta();
        float factor = PovDarknessHelper.resolveDarknessFactor(delta);
        if (factor >= 0.0f) {
            info.setReturnValue(Float.valueOf(factor));
        }
    }

    @Inject(method={"getBlindness"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$blindnessUniform(CallbackInfoReturnable<Float> info) {
        float delta = MinecraftClient.getInstance().getTickDelta();
        float blind = PovBlindnessHelper.resolveBlindnessFactor(delta);
        if (blind >= 0.0f) {
            info.setReturnValue(Float.valueOf(blind));
            return;
        }
    }
}

