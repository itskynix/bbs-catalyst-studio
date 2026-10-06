/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.GameModeSelectionScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.ingame.AnvilScreen
 *  net.minecraft.client.gui.screen.ingame.BeaconScreen
 *  net.minecraft.client.gui.screen.ingame.BlastFurnaceScreen
 *  net.minecraft.client.gui.screen.ingame.BookEditScreen
 *  net.minecraft.client.gui.screen.ingame.BookScreen
 *  net.minecraft.client.gui.screen.ingame.BrewingStandScreen
 *  net.minecraft.client.gui.screen.ingame.CartographyTableScreen
 *  net.minecraft.client.gui.screen.ingame.CrafterScreen
 *  net.minecraft.client.gui.screen.ingame.CraftingScreen
 *  net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
 *  net.minecraft.client.gui.screen.ingame.EnchantmentScreen
 *  net.minecraft.client.gui.screen.ingame.FurnaceScreen
 *  net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen
 *  net.minecraft.client.gui.screen.ingame.GenericContainerScreen
 *  net.minecraft.client.gui.screen.ingame.GrindstoneScreen
 *  net.minecraft.client.gui.screen.ingame.HandledScreen
 *  net.minecraft.client.gui.screen.ingame.HopperScreen
 *  net.minecraft.client.gui.screen.ingame.HorseScreen
 *  net.minecraft.client.gui.screen.ingame.InventoryScreen
 *  net.minecraft.client.gui.screen.ingame.LoomScreen
 *  net.minecraft.client.gui.screen.ingame.MerchantScreen
 *  net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen
 *  net.minecraft.client.gui.screen.ingame.SmithingScreen
 *  net.minecraft.client.gui.screen.ingame.SmokerScreen
 *  net.minecraft.client.gui.screen.ingame.StonecutterScreen
 *  net.minecraft.entity.passive.AbstractHorseEntity
 *  net.minecraft.entity.passive.DonkeyEntity
 *  net.minecraft.entity.passive.MuleEntity
 *  net.minecraft.screen.GenericContainerScreenHandler
 */
package mchorse.bbs_mod.camera.pov.actions.gui.schema;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HorseScreenPovAccess;
import java.util.Locale;
import net.minecraft.client.gui.screen.GameModeSelectionScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.BlastFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.BrewingStandScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
//? if >=1.20.4 {
import net.minecraft.client.gui.screen.ingame.CrafterScreen;
//?}
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.FurnaceScreen;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.GrindstoneScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HopperScreen;
import net.minecraft.client.gui.screen.ingame.HorseScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.LoomScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.client.gui.screen.ingame.SmokerScreen;
import net.minecraft.client.gui.screen.ingame.StonecutterScreen;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.passive.MuleEntity;
import net.minecraft.screen.GenericContainerScreenHandler;

public final class GuiTypeResolver {
    private GuiTypeResolver() {
    }

    public static String resolve(Screen screen) {
        if (screen instanceof BookEditScreen || screen instanceof BookScreen) {
            return "book";
        }
        if (screen instanceof GameModeSelectionScreen) {
            return "gamemode_switcher";
        }
        if (!(screen instanceof HandledScreen)) {
            return null;
        }
        HandledScreen handled = (HandledScreen)screen;
        return GuiTypeResolver.resolveHandled(handled);
    }

    public static String resolveOrInventory(HandledScreen<?> handled) {
        String id = GuiTypeResolver.resolveHandled(handled);
        return id == null ? "inventory" : id;
    }

    private static String resolveHandled(HandledScreen<?> handled) {
        if (handled instanceof CreativeInventoryScreen) {
            return "creative_inventory";
        }
        if (handled instanceof InventoryScreen) {
            return "inventory";
        }
        if (handled instanceof CraftingScreen) {
            return "crafting_table";
        }
        if (handled instanceof AnvilScreen) {
            return "anvil";
        }
        if (handled instanceof ShulkerBoxScreen) {
            return "shulker_box";
        }
        if (handled instanceof Generic3x3ContainerScreen) {
            String title = handled.getTitle().getString().toLowerCase(Locale.ROOT);
            return title.contains("dropper") ? "dropper" : "dispenser";
        }
        //? if >=1.20.4 {
        if (handled instanceof CrafterScreen) {
            return "crafter";
        }
        //?}
        if (handled instanceof GenericContainerScreen) {
            GenericContainerScreenHandler handler;
            GenericContainerScreen containerScreen = (GenericContainerScreen)handled;
            String title = handled.getTitle().getString().toLowerCase(Locale.ROOT);
            if (title.contains("barrel")) {
                return "barrel";
            }
            if (title.contains("ender")) {
                return "ender_chest";
            }
            if (containerScreen.getScreenHandler() instanceof GenericContainerScreenHandler && (handler = (GenericContainerScreenHandler)containerScreen.getScreenHandler()).getRows() > 3) {
                return "large_chest";
            }
            return "chest";
        }
        if (handled instanceof FurnaceScreen) {
            return "furnace";
        }
        if (handled instanceof BlastFurnaceScreen) {
            return "blast_furnace";
        }
        if (handled instanceof SmokerScreen) {
            return "smoker";
        }
        if (handled instanceof EnchantmentScreen) {
            return "enchanting_table";
        }
        if (handled instanceof BrewingStandScreen) {
            return "brewing_stand";
        }
        if (handled instanceof SmithingScreen) {
            return "smithing_table";
        }
        if (handled instanceof GrindstoneScreen) {
            return "grindstone";
        }
        if (handled instanceof StonecutterScreen) {
            return "stonecutter";
        }
        if (handled instanceof CartographyTableScreen) {
            return "cartography_table";
        }
        if (handled instanceof LoomScreen) {
            return "loom";
        }
        if (handled instanceof HopperScreen) {
            return "hopper";
        }
        if (handled instanceof MerchantScreen) {
            return "villager";
        }
        if (handled instanceof HorseScreen) {
            AbstractHorseEntity abstractHorseEntity;
            if (handled instanceof HorseScreenPovAccess) {
                HorseScreenPovAccess horseScreen = (HorseScreenPovAccess)handled;
                abstractHorseEntity = horseScreen.bbsPov$getEntity();
            } else {
                abstractHorseEntity = null;
            }
            return GuiTypeResolver.resolveMount(abstractHorseEntity);
        }
        if (handled instanceof BeaconScreen) {
            return "beacon";
        }
        return null;
    }

    public static String resolveMount(AbstractHorseEntity mount) {
        if (mount instanceof DonkeyEntity || mount instanceof MuleEntity) {
            return "donkey";
        }
        return "horse";
    }
}

