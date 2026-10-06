/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.recipebook.RecipeBookResults
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.RecipeBookResultsPovAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={RecipeBookResults.class})
public abstract class RecipeBookResultsPovMixin
implements RecipeBookResultsPovAccess {
    @Shadow
    private int field_3135;

    @Override
    public int bbsPov$getCurrentPage() {
        return this.field_3135;
    }

    @Inject(method={"draw"}, at={@At(value="HEAD")})
    private void bbsPov$captureRecipePage(DrawContext context, int x, int y, int mouseX, int mouseY, float delta, CallbackInfo info) {
        GuiSnapshotCapture.updateRecipePage(this.field_3135);
    }
}

