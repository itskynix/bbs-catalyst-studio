/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
 *  net.minecraft.client.gui.screen.recipebook.AnimatedResultButton
 *  net.minecraft.recipe.RecipeEntry
 *  net.minecraft.text.Text
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.recipebook.AnimatedResultButton;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={AnimatedResultButton.class})
public abstract class AnimatedResultButtonPovMixin {
    @Shadow
    protected abstract List<RecipeEntry<?>> method_2639();

    @Inject(method={"renderWidget"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$preventZeroDivideCrash(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        List<RecipeEntry<?>> results = this.method_2639();
        if (results == null || results.isEmpty()) {
            ((AnimatedResultButton)(Object)this).visible = false;
            info.cancel();
        }
    }

    @Inject(method={"currentRecipe"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$safeCurrentRecipe(CallbackInfoReturnable<RecipeEntry<?>> info) {
        List<RecipeEntry<?>> results = this.method_2639();
        if (results == null || results.isEmpty()) {
            info.setReturnValue(null);
        }
    }

    @Inject(method={"getTooltip"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$safeGetTooltip(CallbackInfoReturnable<List<Text>> info) {
        List<RecipeEntry<?>> results = this.method_2639();
        if (results == null || results.isEmpty()) {
            info.setReturnValue(List.of());
        }
    }

    @Inject(method={"appendClickableNarrations"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$safeAppendClickableNarrations(NarrationMessageBuilder builder, CallbackInfo info) {
        List<RecipeEntry<?>> results = this.method_2639();
        if (results == null || results.isEmpty()) {
            info.cancel();
        }
    }
}

