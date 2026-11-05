package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The slot under the cursor of a game screen drawn by a POV interface clip, whose tooltip the clip draws itself */
@Mixin(HandledScreen.class)
public interface PovHandledScreenAccessor
{
    @Accessor("focusedSlot")
    Slot bbs$getFocusedSlot();
}
