/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.film.Films
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.client.gui.screen.GameMenuScreen
 *  net.minecraft.client.gui.screen.GameModeSelectionScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.SleepingChatScreen
 *  net.minecraft.client.gui.screen.ingame.BookEditScreen
 *  net.minecraft.client.gui.screen.ingame.BookScreen
 *  net.minecraft.client.gui.screen.ingame.HandledScreen
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.Hand
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.ClientPlayerEntityPovAccessor;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.LivingEntityPovAccessor;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackInput;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Films;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.GameModeSelectionScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={MinecraftClient.class})
public abstract class MinecraftClientPovInputMixin {
    @Inject(method={"tick"}, at={@At(value="HEAD")})
    private void bbsPov$detachLiveUseState(CallbackInfo info) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        if (!PovPlaybackInput.isFirstPersonPlayback()) {
            return;
        }
        if (client.currentScreen instanceof HandledScreen || client.currentScreen instanceof BookScreen || client.currentScreen instanceof BookEditScreen || client.currentScreen instanceof GameModeSelectionScreen || client.currentScreen instanceof ChatScreen) {
            client.setScreen(null);
        }
        ClientPlayerEntity player = client.player;
        client.options.useKey.setPressed(false);
        client.options.attackKey.setPressed(false);
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.inventoryKey.setPressed(false);
        client.options.chatKey.setPressed(false);
        client.options.commandKey.setPressed(false);
        client.options.pickItemKey.setPressed(false);
        client.options.dropKey.setPressed(false);
        client.options.swapHandsKey.setPressed(false);
        if (player != null) {
            ((ClientPlayerEntityPovAccessor)player).bbsPov$setUsingItem(false);
            ((ClientPlayerEntityPovAccessor)player).bbsPov$setClientActiveHand(Hand.MAIN_HAND);
            ((LivingEntityPovAccessor)player).bbsPov$setActiveItemStack(ItemStack.EMPTY);
            ((LivingEntityPovAccessor)player).bbsPov$setItemUseTimeLeft(0);
        }
    }

    @Inject(method={"tick"}, at={@At(value="TAIL")})
    private void bbsPov$keepRecordingOpenGuis(CallbackInfo info) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        if (!client.isPaused()) {
            return;
        }
        Screen screen = client.currentScreen;
        if (!(screen instanceof BookScreen || screen instanceof BookEditScreen || screen instanceof HandledScreen || screen instanceof GameModeSelectionScreen || screen instanceof GameMenuScreen || screen instanceof SleepingChatScreen)) {
            return;
        }
        Films films = BBSModClient.getFilms();
        if (films != null && films.getRecorder() != null) {
            films.update();
        }
    }

    @Inject(method={"doAttack"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$blockAttack(CallbackInfoReturnable<Boolean> info) {
        if (PovPlaybackInput.isFirstPersonPlayback()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method={"doItemUse"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$blockUse(CallbackInfo info) {
        if (PovPlaybackInput.isFirstPersonPlayback()) {
            info.cancel();
        }
    }
}

