/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.block.Block
 *  net.minecraft.block.Blocks
 *  net.minecraft.particle.BlockStateParticleEffect
 *  net.minecraft.particle.DefaultParticleType
 *  net.minecraft.particle.DustParticleEffect
 *  net.minecraft.particle.ItemStackParticleEffect
 *  net.minecraft.particle.ParticleEffect
 *  net.minecraft.particle.ParticleType
 *  net.minecraft.particle.ParticleTypes
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 *  org.joml.Vector3f
 */
package mchorse.bbs_mod.camera.pov.actions.particle;

import mchorse.bbs_mod.camera.pov.actions.clip.ParticleEffectPovActionClip;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

final class ParticleEffects {
    private ParticleEffects() {
    }

    static ParticleEffect fromClip(ParticleEffectPovActionClip clip) {
        ParticleType type;
        Identifier id = Identifier.tryParse((String)((String)clip.particle.get()));
        ParticleType particleType = type = id == null ? null : (ParticleType)Registries.PARTICLE_TYPE.get(id);
        if (type == null) {
            return ParticleTypes.POOF;
        }
        if (type instanceof DefaultParticleType) {
            DefaultParticleType simple = (DefaultParticleType)type;
            return simple;
        }
        if (type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_MARKER || type == ParticleTypes.FALLING_DUST) {
            ParticleType blockType = type;
            return new BlockStateParticleEffect(blockType, ParticleEffects.resolveBlock((String)clip.blockId.get()).getDefaultState());
        }
        if (type == ParticleTypes.ITEM) {
            return new ItemStackParticleEffect(ParticleTypes.ITEM, clip.extraItem());
        }
        if (type == ParticleTypes.DUST) {
            return new DustParticleEffect(new Vector3f(((Float)clip.dustR.get()).floatValue(), ((Float)clip.dustG.get()).floatValue(), ((Float)clip.dustB.get()).floatValue()), ((Float)clip.dustScale.get()).floatValue());
        }
        return ParticleTypes.POOF;
    }

    private static Block resolveBlock(String id) {
        Identifier identifier = Identifier.tryParse((String)id);
        Block block = identifier == null ? Blocks.STONE : (Block)Registries.BLOCK.get(identifier);
        return block == null ? Blocks.STONE : block;
    }
}

