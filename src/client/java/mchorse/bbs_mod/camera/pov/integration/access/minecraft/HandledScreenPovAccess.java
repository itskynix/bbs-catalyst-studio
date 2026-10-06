/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.screen.slot.Slot
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import java.util.Set;
import net.minecraft.screen.slot.Slot;

public interface HandledScreenPovAccess {
    public Set<Slot> bbsPov$getCursorDragSlots();

    public boolean bbsPov$isCursorDragging();

    public int bbsPov$getDraggedStackRemainder();

    public int bbsPov$getHeldButtonType();
}

