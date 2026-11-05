package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The recipe book's page, tab and search, for a live take's interface clips */
@Mixin(RecipeBookWidget.class)
public interface PovRecipeBookWidgetAccessor
{
    @Accessor("recipesArea")
    RecipeBookResults bbs$getRecipesArea();

    @Accessor("currentTab")
    RecipeGroupButtonWidget bbs$getCurrentTab();

    @Accessor("searchField")
    TextFieldWidget bbs$getSearchField();
}
