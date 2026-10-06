/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Keyframe.class}, remap=false)
public abstract class KeyframePovMixin {
    @Inject(method={"setTick(FZ)V"}, at={@At(value="HEAD")})
    private void bbsPov$moveGroupedSlotKeyframes(float tick, boolean notify, CallbackInfo info) {
        GuiPovActionClip clip;
        KeyframeChannel channel;
        BaseValue baseValue;
        Keyframe keyframe = (Keyframe)(Object)this;
        if (Math.abs(keyframe.getTick() - tick) >= 1.0E-4f && (baseValue = keyframe.getParent()) instanceof KeyframeChannel && (baseValue = (channel = (KeyframeChannel)baseValue).getParent()) instanceof GuiPovActionClip && (clip = (GuiPovActionClip)baseValue).isSlotAnchor(channel)) {
            clip.moveSlotKeyframes(channel, keyframe.getTick(), tick);
        }
    }
}

