package mchorse.bbs_mod.mixin.client;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The entity flags (gliding among them), recorded and lent back by Recap Studio. */
@Mixin(Entity.class)
public interface RecapEntityFlagInvoker
{
    @Invoker("getFlag")
    boolean bbs$getFlag(int index);

    @Invoker("setFlag")
    void bbs$setFlag(int index, boolean value);
}
