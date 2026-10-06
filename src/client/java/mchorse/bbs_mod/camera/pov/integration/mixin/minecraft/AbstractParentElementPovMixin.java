/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.AbstractParentElement
 *  net.minecraft.client.gui.Element
 *  net.minecraft.client.gui.screen.recipebook.RecipeBookWidget
 *  net.minecraft.client.gui.widget.TexturedButtonWidget
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.TexturedButtonWidgetPovAccessor;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AbstractParentElement.class})
public abstract class AbstractParentElementPovMixin {
    @Inject(method={"setFocused(Lnet/minecraft/client/gui/Element;)V"}, at={@At(value="RETURN")})
    private void bbsPov$clearRecipeButtonFocus(Element focused, CallbackInfo info) {
        //? if >=1.20.4 {
        TexturedButtonWidget button;
        if (focused instanceof TexturedButtonWidget && ((TexturedButtonWidgetPovAccessor)(button = (TexturedButtonWidget)focused)).bbsPov$getTextures() == RecipeBookWidget.BUTTON_TEXTURES) {
            return;
        }
        //?} else {
        /*if (focused instanceof TexturedButtonWidget) {
            return;
        }
        *///?}
        GuiSnapshotCapture.updateRecipeButton(false);
    }
}

