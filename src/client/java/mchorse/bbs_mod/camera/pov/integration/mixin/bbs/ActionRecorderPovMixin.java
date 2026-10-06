/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.actions.ActionRecorder
 *  mchorse.bbs_mod.actions.types.ActionClip
 *  mchorse.bbs_mod.actions.types.chat.ChatActionClip
 *  mchorse.bbs_mod.actions.types.chat.CommandActionClip
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.actions.ActionRecorder;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.chat.ChatActionClip;
import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ActionRecorder.class}, remap=false)
public class ActionRecorderPovMixin {
    @Inject(method={"add"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$preventBakingChatActions(ActionClip clip, CallbackInfo ci) {
        if (clip instanceof ChatActionClip || clip instanceof CommandActionClip) {
            ci.cancel();
        }
    }
}

