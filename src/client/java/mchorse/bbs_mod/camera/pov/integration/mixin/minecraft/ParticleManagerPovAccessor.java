/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.particle.Particle
 *  net.minecraft.client.particle.ParticleManager
 *  net.minecraft.particle.ParticleEffect
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.ParticleManagerPovAccess;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={ParticleManager.class})
public interface ParticleManagerPovAccessor
extends ParticleManagerPovAccess {
    @Override
    @Invoker(value="createParticle")
    public Particle bbsPov$createParticle(ParticleEffect var1, double var2, double var4, double var6, double var8, double var10, double var12);
}

