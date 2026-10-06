/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.suggestion.Suggestion
 *  com.mojang.brigadier.suggestion.Suggestions
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.ChatInputSuggestor
 *  net.minecraft.entity.player.PlayerEntity
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.chat.ChatMorphHelper;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ChatInputSuggestor.class})
public class ChatInputSuggestorPovMixin {
    @Shadow
    @Final
    MinecraftClient field_21597;
    @Shadow
    private CompletableFuture<Suggestions> field_21611;

    @Inject(method={"showCommandSuggestions"}, at={@At(value="HEAD")})
    private void bbsPov$filterAndReplacePlayerName(CallbackInfo ci) {
        if (this.field_21611 != null && this.field_21611.isDone()) {
            try {
                Suggestions original = this.field_21611.getNow(null);
                if (original != null && !original.isEmpty()) {
                    String playerName = this.field_21597.player != null && this.field_21597.player.getGameProfile() != null ? this.field_21597.player.getGameProfile().getName() : null;
                    String morphName = this.field_21597.player != null ? ChatMorphHelper.getPlayerMorphName((PlayerEntity)this.field_21597.player) : null;
                    String replayName = ChatMorphHelper.getActiveReplayName();
                    if (morphName != null && !morphName.isEmpty() || replayName != null && !replayName.isEmpty()) {
                        ArrayList<Suggestion> modified = new ArrayList<Suggestion>();
                        ArrayList<String> addedTexts = new ArrayList<String>();
                        String primaryName = replayName != null && !replayName.isEmpty() ? replayName : morphName;
                        for (Suggestion s2 : original.getList()) {
                            if (playerName != null && s2.getText().equalsIgnoreCase(playerName)) {
                                if (primaryName == null || addedTexts.contains(primaryName)) continue;
                                modified.add(new Suggestion(s2.getRange(), primaryName, s2.getTooltip()));
                                addedTexts.add(primaryName);
                                continue;
                            }
                            if (replayName != null && !replayName.isEmpty() && morphName != null && s2.getText().equalsIgnoreCase(morphName)) {
                                if (addedTexts.contains(replayName)) continue;
                                modified.add(new Suggestion(s2.getRange(), replayName, s2.getTooltip()));
                                addedTexts.add(replayName);
                                continue;
                            }
                            if (addedTexts.contains(s2.getText())) continue;
                            modified.add(s2);
                            addedTexts.add(s2.getText());
                        }
                        if (replayName != null && !replayName.isEmpty() && morphName != null && !morphName.equalsIgnoreCase(replayName)) {
                            modified.removeIf(s -> s.getText().equalsIgnoreCase(morphName));
                        }
                        this.field_21611 = CompletableFuture.completedFuture(new Suggestions(original.getRange(), modified));
                    }
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }
}

