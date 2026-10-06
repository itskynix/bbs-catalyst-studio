/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.StringReader
 *  net.minecraft.command.EntitySelectorReader
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import com.mojang.brigadier.StringReader;
import net.minecraft.command.EntitySelectorReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntitySelectorReader.class})
public class EntitySelectorReaderPovMixin {
    @Shadow
    @Final
    private StringReader field_10860;
    @Shadow
    private String field_10876;

    @Inject(method={"readRegular"}, at={@At(value="TAIL")})
    private void bbsPov$allowSlashAndActorNames(CallbackInfo ci) {
        if (this.field_10876 != null && this.field_10860.canRead() && !Character.isWhitespace(this.field_10860.peek())) {
            StringBuilder sb = new StringBuilder(this.field_10876);
            while (this.field_10860.canRead() && !Character.isWhitespace(this.field_10860.peek())) {
                sb.append(this.field_10860.read());
            }
            this.field_10876 = sb.toString();
        }
    }
}

