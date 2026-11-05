package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far each first-person hand is raised (1 up, 0 down out of view), for a live take's equip channels */
@Mixin(HeldItemRenderer.class)
public interface PovHeldItemRendererAccessor
{
    @Accessor("equipProgressMainHand")
    float bbs$getEquipProgressMainHand();

    @Accessor("equipProgressOffHand")
    float bbs$getEquipProgressOffHand();
}
