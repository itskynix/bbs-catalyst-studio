/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.ingame.AnvilScreen
 *  net.minecraft.client.gui.widget.TextFieldWidget
 *  net.minecraft.screen.AnvilScreenHandler
 *  net.minecraft.screen.slot.Slot
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.TextFieldWidgetPovAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AnvilScreen.class})
public abstract class AnvilScreenPovMixin {
    @Shadow
    private TextFieldWidget field_2821;

    @Inject(method={"renderForeground"}, at={@At(value="HEAD")})
    private void bbsPov$captureAnvil(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        AnvilScreen screen = (AnvilScreen)(Object)this;
        AnvilScreenHandler handler = (AnvilScreenHandler)screen.getScreenHandler();
        Slot input = handler.getSlot(0);
        Slot addition = handler.getSlot(1);
        Slot result = handler.getSlot(handler.getResultSlotIndex());
        boolean error = (input.hasStack() || addition.hasStack()) && !result.hasStack();
        int selStart = 0;
        int selEnd = 0;
        if (this.field_2821 != null) {
            TextFieldWidgetPovAccessor access = (TextFieldWidgetPovAccessor)this.field_2821;
            selStart = access.bbsPov$getSelectionStart();
            selEnd = access.bbsPov$getSelectionEnd();
        }
        GuiSnapshotCapture.updateAnvil(this.field_2821 == null ? "" : this.field_2821.getText(), this.field_2821 != null && this.field_2821.isFocused(), selStart, selEnd, error);
    }
}

