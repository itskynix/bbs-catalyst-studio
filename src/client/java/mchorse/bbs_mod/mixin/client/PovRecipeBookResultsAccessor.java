package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.screen.recipebook.RecipeBookResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The page the recipe book shows, for a live take's interface clips */
@Mixin(RecipeBookResults.class)
public interface PovRecipeBookResultsAccessor
{
    @Accessor("currentPage")
    int bbs$getCurrentPage();
}
