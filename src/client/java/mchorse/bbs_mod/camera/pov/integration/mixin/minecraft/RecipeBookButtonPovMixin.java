/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.ButtonTextures
 *  net.minecraft.client.gui.screen.recipebook.RecipeBookWidget
 *  net.minecraft.client.gui.widget.TexturedButtonWidget
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import net.minecraft.client.gui.DrawContext;
//? if >=1.20.4 {
import net.minecraft.client.gui.screen.ButtonTextures;
//?}
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={TexturedButtonWidget.class})
public abstract class RecipeBookButtonPovMixin {
    //? if >=1.20.4 {
    @Shadow
    @Final
    protected ButtonTextures field_45356;

    @Inject(method={"renderWidget"}, at={@At(value="HEAD")})
    private void bbsPov$captureRecipeButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        if (this.field_45356 == RecipeBookWidget.BUTTON_TEXTURES) {
            GuiSnapshotCapture.updateRecipeButton(((TexturedButtonWidget)(Object)this).isSelected());
        }
    }
    //?} else {
    /*@Inject(method={"renderButton"}, at={@At(value="HEAD")})
    private void bbsPov$captureRecipeButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        GuiSnapshotCapture.updateRecipeButton(((TexturedButtonWidget)(Object)this).isSelected());
    }
    *///?}
}

