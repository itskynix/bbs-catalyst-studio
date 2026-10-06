/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.util.Hand
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.ClientPlayerEntityPovAccess;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={ClientPlayerEntity.class})
public interface ClientPlayerEntityPovAccessor
extends ClientPlayerEntityPovAccess {
    @Override
    @Accessor(value="usingItem")
    public boolean bbsPov$isUsingItem();

    @Override
    @Accessor(value="usingItem")
    public void bbsPov$setUsingItem(boolean var1);

    @Override
    @Accessor(value="activeHand")
    public Hand bbsPov$getClientActiveHand();

    @Override
    @Accessor(value="activeHand")
    public void bbsPov$setClientActiveHand(Hand var1);
}

