/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ingame.HorseScreen
 *  net.minecraft.entity.passive.AbstractHorseEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HorseScreenPovAccess;
import net.minecraft.client.gui.screen.ingame.HorseScreen;
import net.minecraft.entity.passive.AbstractHorseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={HorseScreen.class})
public interface HorseScreenPovAccessor
extends HorseScreenPovAccess {
    @Override
    @Accessor(value="entity")
    public AbstractHorseEntity bbsPov$getEntity();
}

