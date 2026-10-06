/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSSettings
 *  mchorse.bbs_mod.settings.SettingsBuilder
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.integration.mixin.bbs.BaseValueNumberAccessor;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.SettingsBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BBSSettings.class}, remap=false)
public class BBSSettingsPovMixin {
    @Inject(method={"register"}, at={@At(value="FIELD", target="Lmchorse/bbs_mod/BBSSettings;recordingTeleport:Lmchorse/bbs_mod/settings/values/numeric/ValueBoolean;", shift=At.Shift.AFTER)})
    private static void bbsPov$registerPovCategory(SettingsBuilder builder, CallbackInfo info) {
        PovSettings.register(builder);
    }

    @Inject(method={"register"}, at={@At(value="RETURN")})
    private static void bbsPov$allowPovCameraMode(SettingsBuilder builder, CallbackInfo info) {
        ((BaseValueNumberAccessor)BBSSettings.editorCameraMode).bbsPov$setMaximum(6);
    }
}

