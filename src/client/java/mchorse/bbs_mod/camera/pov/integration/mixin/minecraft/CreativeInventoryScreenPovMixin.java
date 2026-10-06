/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.impl.client.itemgroup.CreativeGuiExtensions
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
 *  net.minecraft.client.gui.widget.TextFieldWidget
 *  net.minecraft.item.ItemGroup
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.TextFieldWidgetPovAccessor;
import net.fabricmc.fabric.impl.client.itemgroup.CreativeGuiExtensions;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={CreativeInventoryScreen.class})
public abstract class CreativeInventoryScreenPovMixin {
    @Shadow
    private static ItemGroup field_2896;
    @Shadow
    private float field_2890;
    @Shadow
    private TextFieldWidget field_2894;

    @Inject(method={"handledScreenTick"}, at={@At(value="HEAD")})
    private void bbsPov$captureCreativeState(CallbackInfo info) {
        TextFieldWidget box = this.field_2894;
        int selStart = 0;
        int selEnd = 0;
        if (box != null) {
            TextFieldWidgetPovAccessor access = (TextFieldWidgetPovAccessor)box;
            selStart = access.bbsPov$getSelectionStart();
            selEnd = access.bbsPov$getSelectionEnd();
        }
        int currentPage = 0;
        try {
            currentPage = ((CreativeGuiExtensions)(Object)this).fabric_currentPage();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        GuiSnapshotCapture.updateCreative(field_2896, this.field_2890, box == null ? "" : box.getText(), currentPage, box != null && box.isVisible() && box.isFocused(), selStart, selEnd);
    }

    @Inject(method={"init"}, at={@At(value="RETURN")})
    private void bbsPov$captureOnInit(CallbackInfo info) {
        this.bbsPov$captureCreativeState(info);
    }

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void bbsPov$captureOnRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        this.bbsPov$captureCreativeState(info);
    }

    @Inject(method={"removed"}, at={@At(value="HEAD")})
    private void bbsPov$resetOnClose(CallbackInfo info) {
        GuiSnapshotCapture.resetCreative();
    }
}

