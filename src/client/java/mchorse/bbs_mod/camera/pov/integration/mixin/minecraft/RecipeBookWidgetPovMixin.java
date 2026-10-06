/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.recipebook.RecipeBookGhostSlots
 *  net.minecraft.client.gui.screen.recipebook.RecipeBookResults
 *  net.minecraft.client.gui.screen.recipebook.RecipeBookWidget
 *  net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget
 *  net.minecraft.client.gui.widget.TextFieldWidget
 *  net.minecraft.client.recipebook.ClientRecipeBook
 *  net.minecraft.recipe.RecipeEntry
 *  net.minecraft.screen.AbstractRecipeScreenHandler
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.RecipeBookResultsPovAccess;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.TextFieldWidgetPovAccessor;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.recipebook.RecipeBookGhostSlots;
import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.recipebook.ClientRecipeBook;
//? if >=1.20.4 {
import net.minecraft.recipe.RecipeEntry;
//?}
import net.minecraft.screen.AbstractRecipeScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={RecipeBookWidget.class})
public abstract class RecipeBookWidgetPovMixin {
    @Shadow
    @Final
    private RecipeBookResults field_3086;
    @Shadow
    private boolean field_33679;
    @Shadow
    private TextFieldWidget field_3089;
    @Shadow
    private RecipeGroupButtonWidget field_3098;
    @Shadow
    @Final
    private List<RecipeGroupButtonWidget> field_3094;
    @Shadow
    @Final
    protected RecipeBookGhostSlots field_3092;
    @Shadow
    private ClientRecipeBook field_3096;
    @Shadow
    protected AbstractRecipeScreenHandler<?> field_3095;

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void bbsPov$captureRecipeBook(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        int tab = 0;
        int visible = 0;
        if (this.field_3094 != null) {
            for (RecipeGroupButtonWidget button : this.field_3094) {
                if (!button.visible) continue;
                if (button == this.field_3098) {
                    tab = visible;
                    break;
                }
                ++visible;
            }
        }
        //? if >=1.20.4 {
        RecipeEntry ghost = this.field_3092 == null ? null : this.field_3092.getRecipe();
        String ghostId = ghost == null ? "" : ghost.id().toString();
        //?} else {
        /*net.minecraft.recipe.Recipe<?> ghost = this.field_3092 == null ? null : this.field_3092.getRecipe();
        String ghostId = ghost == null ? "" : ghost.getId().toString();
        *///?}
        boolean filtering = this.field_3096 != null && this.field_3095 != null && this.field_3096.isFilteringCraftable(this.field_3095);
        int selStart = 0;
        int selEnd = 0;
        if (this.field_3089 != null) {
            TextFieldWidgetPovAccessor access = (TextFieldWidgetPovAccessor)this.field_3089;
            selStart = access.bbsPov$getSelectionStart();
            selEnd = access.bbsPov$getSelectionEnd();
        }
        GuiSnapshotCapture.updateRecipeBook(this.field_33679, this.field_3089 == null ? "" : this.field_3089.getText(), filtering, tab, ghostId, this.field_3089 != null && this.field_3089.isFocused(), selStart, selEnd);
    }

    @Inject(method={"render"}, at={@At(value="RETURN")})
    private void bbsPov$captureRecipePageAfterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        this.bbsPov$captureRecipePage();
    }

    @Inject(method={"mouseClicked"}, at={@At(value="RETURN")})
    private void bbsPov$unfocusRecipeButton(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> info) {
        this.bbsPov$captureRecipePage();
        if (Boolean.TRUE.equals(info.getReturnValue())) {
            GuiSnapshotCapture.updateRecipeButton(false);
        }
    }

    @Unique
    private void bbsPov$captureRecipePage() {
        RecipeBookResults recipeBookResults = this.field_3086;
        if (recipeBookResults instanceof RecipeBookResultsPovAccess) {
            RecipeBookResultsPovAccess access = (RecipeBookResultsPovAccess)recipeBookResults;
            GuiSnapshotCapture.updateRecipePage(access.bbsPov$getCurrentPage());
        }
    }
}

