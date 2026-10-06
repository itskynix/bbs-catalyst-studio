/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.widget.TextFieldWidget
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={TextFieldWidget.class})
public interface TextFieldWidgetPovAccessor {
    @Accessor(value="selectionStart")
    public int bbsPov$getSelectionStart();

    @Accessor(value="selectionEnd")
    public int bbsPov$getSelectionEnd();
}

