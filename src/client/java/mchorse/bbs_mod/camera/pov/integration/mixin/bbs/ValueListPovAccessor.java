/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.core.ValueList
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import java.util.List;
import mchorse.bbs_mod.settings.values.core.ValueList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={ValueList.class}, remap=false)
public interface ValueListPovAccessor {
    @Accessor(value="list")
    public List bbsPov$getList();
}

