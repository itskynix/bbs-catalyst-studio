/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.clip;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import java.util.Map;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.item.ItemStack;

public final class GuiClipNormalizer {
    private GuiClipNormalizer() {
    }

    public static void apply(GuiPovActionClip clip) {
        PovActionClip.constant(clip.state);
        PovActionClip.constant(clip.mouseButtons);
        PovActionClip.constant(clip.mouseScroll);
        PovActionClip.constant(clip.cursorVisible);
        PovActionClip.constant(clip.creativeTab);
        PovActionClip.constant(clip.creativePage);
        PovActionClip.constant(clip.creativeSearch);
        PovActionClip.constant(clip.creativeSearchFocus);
        PovActionClip.constant(clip.loomRow);
        PovActionClip.constant(clip.stonecutterRow);
        PovActionClip.constant(clip.creativeSearchSelStart);
        PovActionClip.constant(clip.creativeSearchSelEnd);
        PovActionClip.constant(clip.recipeSearchSelStart);
        PovActionClip.constant(clip.recipeSearchSelEnd);
        PovActionClip.constant(clip.recipeOpen);
        PovActionClip.constant(clip.recipeSearch);
        PovActionClip.constant(clip.recipeSearchFocus);
        PovActionClip.constant(clip.recipeShowing);
        PovActionClip.constant(clip.recipeCategory);
        PovActionClip.constant(clip.recipeSelected);
        PovActionClip.constant(clip.recipePage);
        PovActionClip.constant(clip.recipeButton);
        PovActionClip.constant(clip.anvilName);
        PovActionClip.constant(clip.anvilNameFocus);
        PovActionClip.constant(clip.anvilNameSelStart);
        PovActionClip.constant(clip.anvilNameSelEnd);
        PovActionClip.constant(clip.anvilError);
        PovActionClip.constant(clip.enchantOffers);
        PovActionClip.constant(clip.enchantSeed);
        PovActionClip.constant(clip.enchantPlayerLevel);
        PovActionClip.constant(clip.enchantCreative);
        PovActionClip.constant(clip.gamemodeSelection);
        PovActionClip.clamp(clip.gamemodeSelection, 0, 3);
        PovActionClip.constant(clip.beaconPrimary);
        PovActionClip.constant(clip.beaconSecondary);
        PovActionClip.constant(clip.beaconLevel);
        PovActionClip.clamp(clip.beaconPrimary, 0, 5);
        PovActionClip.clamp(clip.beaconSecondary, 0, 2);
        PovActionClip.clamp(clip.beaconLevel, 0, 4);
        PovActionClip.clamp(clip.creativeScroll, 0.0f, 1.0f);
        PovActionClip.clamp(clip.opacity, 0.0f, 1.0f);
        PovActionClip.clamp(clip.darknessOpacity, 0.0f, 1.0f);
        for (KeyframeChannel<ItemStack> keyframeChannel : clip.namedSlots.values()) {
            PovActionClip.constant(keyframeChannel);
        }
        for (Map<String, KeyframeChannel<ItemStack>> map : clip.guiSlots.values()) {
            for (KeyframeChannel<ItemStack> channel : map.values()) {
                PovActionClip.constant(channel);
            }
        }
        for (String string : clip.guiLayouts.keySet()) {
            PovActionClip.constant(clip.guiCursorVisibilities.get(string));
            PovActionClip.constant(clip.guiMouseButtons.get(string));
            PovActionClip.constant(clip.guiMouseScrolls.get(string));
            PovActionClip.constant(clip.guiDragSlots.get(string));
            PovActionClip.constant(clip.guiCursorItems.get(string));
            PovActionClip.constant(clip.guiPrimarySlotAnchors.get(string));
            PovActionClip.constant(clip.guiCraftingSlotAnchors.get(string));
            PovActionClip.constant(clip.guiRecipeOpens.get(string));
            PovActionClip.constant(clip.guiRecipeSearches.get(string));
            PovActionClip.constant(clip.guiRecipeSearchFocuses.get(string));
            PovActionClip.constant(clip.guiRecipeShowings.get(string));
            PovActionClip.constant(clip.guiRecipeCategories.get(string));
            PovActionClip.constant(clip.guiRecipeSelecteds.get(string));
            PovActionClip.constant(clip.guiRecipePages.get(string));
            PovActionClip.constant(clip.guiRecipeButtons.get(string));
            PovActionClip.constant(clip.guiRecipeSearchSelStarts.get(string));
            PovActionClip.constant(clip.guiRecipeSearchSelEnds.get(string));
            if (clip.guiFurnaceLit.get(string) != null) {
                PovActionClip.clamp(clip.guiFurnaceLit.get(string), 0.0f, 1.0f);
                PovActionClip.clamp(clip.guiFurnaceCook.get(string), 0.0f, 1.0f);
            }
            if (clip.guiBrewProgress.get(string) != null) {
                PovActionClip.clamp(clip.guiBrewProgress.get(string), 0.0f, 1.0f);
                PovActionClip.clamp(clip.guiBrewFuel.get(string), 0.0f, 1.0f);
                PovActionClip.constant(clip.guiBrewBubbles.get(string));
            }
            if (clip.guiHorseVariants.get(string) != null) {
                PovActionClip.constant(clip.guiHorseVariants.get(string));
            }
            if (clip.guiMountChests.get(string) != null) {
                PovActionClip.constant(clip.guiMountChests.get(string));
            }
            if (clip.guiMerchantSelectedOffers.get(string) != null) {
                PovActionClip.constant(clip.guiMerchantOffers.get(string));
                PovActionClip.constant(clip.guiMerchantProfessions.get(string));
                PovActionClip.constant(clip.guiMerchantLevels.get(string));
                PovActionClip.constant(clip.guiMerchantExperiences.get(string));
                PovActionClip.constant(clip.guiMerchantSelectedOffers.get(string));
                PovActionClip.constant(clip.guiMerchantScrollOffsets.get(string));
                PovActionClip.constant(clip.guiMerchantTitles.get(string));
                PovActionClip.constant(clip.guiMerchantCanLevels.get(string));
            }
            if (clip.guiBookWritables.get(string) != null) {
                PovActionClip.constant(clip.guiBookWritables.get(string));
                PovActionClip.constant(clip.guiBookSignings.get(string));
                PovActionClip.constant(clip.guiBookPagesIndex.get(string));
                PovActionClip.constant(clip.guiBookPages.get(string));
                PovActionClip.constant(clip.guiBookTitles.get(string));
                PovActionClip.constant(clip.guiBookAuthors.get(string));
                PovActionClip.constant(clip.guiBookSelStarts.get(string));
                PovActionClip.constant(clip.guiBookSelEnds.get(string));
            }
            PovActionClip.clamp(clip.guiOpacities.get(string), 0.0f, 1.0f);
            PovActionClip.clamp(clip.guiDarknessOpacities.get(string), 0.0f, 1.0f);
        }
    }
}

