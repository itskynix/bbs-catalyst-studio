/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ingame.HandledScreen
 *  net.minecraft.screen.slot.Slot
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HandledScreenPovAccess;
import java.util.Set;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={HandledScreen.class})
public interface HandledScreenPovAccessor
extends HandledScreenPovAccess {
    @Override
    @Accessor(value="cursorDragSlots")
    public Set<Slot> bbsPov$getCursorDragSlots();

    @Override
    @Accessor(value="cursorDragging")
    public boolean bbsPov$isCursorDragging();

    @Override
    @Accessor(value="draggedStackRemainder")
    public int bbsPov$getDraggedStackRemainder();

    @Override
    @Accessor(value="heldButtonType")
    public int bbsPov$getHeldButtonType();
}

