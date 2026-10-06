/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.ingame.EnchantmentScreen
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.screen.EnchantmentScreenHandler
 *  net.minecraft.util.math.MathHelper
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EnchantmentScreen.class})
public abstract class EnchantmentScreenPovMixin {
    @Shadow
    private float field_2904;
    @Shadow
    private float field_2905;

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void bbsPov$captureEnchantment(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        EnchantmentScreen screen = (EnchantmentScreen)(Object)this;
        EnchantmentScreenHandler handler = (EnchantmentScreenHandler)screen.getScreenHandler();
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        float open = MathHelper.lerp((float)client.getTickDelta(), (float)this.field_2904, (float)this.field_2905);
        GuiSnapshotCapture.updateEnchantment(handler.enchantmentPower, handler.enchantmentId, handler.enchantmentLevel, handler.getSeed(), player == null ? 0 : player.experienceLevel, player != null && player.getAbilities().creativeMode, open);
    }
}

