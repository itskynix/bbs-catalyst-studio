/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  net.minecraft.client.option.KeyBinding
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.BBSModClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={BBSModClient.class}, remap=false)
public interface BBSModClientAccessor {
    @Accessor(value="keyPlayFilm")
    public static KeyBinding bbsPov$getKeyPlayFilm() {
        throw new AssertionError();
    }

    @Accessor(value="keyRecordVideo")
    public static KeyBinding bbsPov$getKeyRecordVideo() {
        throw new AssertionError();
    }
}

