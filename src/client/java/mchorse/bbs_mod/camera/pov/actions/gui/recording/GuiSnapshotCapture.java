/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.ingame.BookScreen$Contents
 *  net.minecraft.client.gui.screen.ingame.HandledScreen
 *  net.minecraft.client.gui.screen.ingame.MerchantScreen
 *  net.minecraft.client.gui.screen.recipebook.RecipeBookProvider
 *  net.minecraft.client.gui.screen.recipebook.RecipeBookWidget
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.recipebook.ClientRecipeBook
 *  net.minecraft.client.util.SelectionManager
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffects
 *  net.minecraft.entity.passive.AbstractDonkeyEntity
 *  net.minecraft.entity.passive.AbstractHorseEntity
 *  net.minecraft.entity.passive.HorseEntity
 *  net.minecraft.entity.passive.VillagerEntity
 *  net.minecraft.item.ItemGroup
 *  net.minecraft.item.ItemGroup$Type
 *  net.minecraft.item.ItemStack
 *  net.minecraft.recipe.book.RecipeBookCategory
 *  net.minecraft.registry.Registries
 *  net.minecraft.screen.AbstractFurnaceScreenHandler
 *  net.minecraft.screen.AbstractRecipeScreenHandler
 *  net.minecraft.screen.BeaconScreenHandler
 *  net.minecraft.screen.BrewingStandScreenHandler
 *  net.minecraft.screen.MerchantScreenHandler
 *  net.minecraft.screen.ScreenHandler
 *  net.minecraft.screen.slot.Slot
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Text
 *  net.minecraft.text.Text$Serialization
 *  net.minecraft.village.TradeOfferList
 *  net.minecraft.world.GameMode
 */
package mchorse.bbs_mod.camera.pov.actions.gui.recording;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.CreativeInventoryTabs;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiRecipeBook;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiSlotSchema;
import mchorse.bbs_mod.camera.pov.actions.gui.data.AnvilSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.BeaconSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.BookSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.BrewingSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.CreativeSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.EnchantmentSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.FurnaceSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GamemodeSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GuiCapture;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GuiSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.LoomSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.MerchantSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.MountSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.RecipeBookSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.StonecutterSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSlotDragPreview;
import mchorse.bbs_mod.camera.pov.actions.gui.schema.GuiTypeResolver;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BeaconScreenPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BookEditScreenPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BookScreenPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.GameModeSelectionScreenPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HandledScreenPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HorseScreenPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.MerchantScreenPovAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.GameMode;

public final class GuiSnapshotCapture {
    private static AnvilSnapshot anvilCache;
    private static CreativeSnapshot creativeCache;
    private static LoomSnapshot loomCache;
    private static StonecutterSnapshot stonecutterCache;
    private static EnchantmentSnapshot enchantmentCache;
    private static RecipeBookSnapshot recipeBookCache;

    private GuiSnapshotCapture() {
    }

    public static void updateAnvil(String name, boolean focused, int selStart, int selEnd, boolean error) {
        anvilCache = new AnvilSnapshot(name, focused, selStart, selEnd, error);
    }

    public static void updateCreative(ItemGroup selected, float scrollPosition, String searchText, int currentPage, boolean focused, int selStart, int selEnd) {
        boolean inventoryTab;
        List<ItemGroup> groups = CreativeInventoryTabs.groups();
        int index = groups.indexOf(selected);
        int tab = index < 0 ? 0 : index;
        float scroll = Math.max(0.0f, Math.min(1.0f, scrollPosition));
        String search = searchText == null ? "" : searchText;
        int page = Math.max(0, currentPage);
        boolean bl = inventoryTab = selected != null && selected.getType() == ItemGroup.Type.INVENTORY;
        int itemCount = selected == null ? 0 : (selected.getType() == ItemGroup.Type.SEARCH ? selected.getSearchTabStacks().size() : selected.getDisplayStacks().size());
        int rows = Math.max(5, (itemCount + 8) / 9);
        int row = Math.round(scroll * (float)Math.max(0, rows - 5));
        creativeCache = new CreativeSnapshot(tab, scroll, search, page, inventoryTab, row, focused, selStart, selEnd);
    }

    public static void resetCreative() {
        creativeCache = null;
    }

    public static void resetScreenCaches() {
        creativeCache = null;
        anvilCache = null;
        loomCache = null;
        stonecutterCache = null;
        enchantmentCache = null;
    }

    public static void updateLoom(int visibleTopRow) {
        loomCache = new LoomSnapshot(visibleTopRow);
    }

    public static void updateStonecutter(int scrollOffset) {
        stonecutterCache = new StonecutterSnapshot(Math.max(0, scrollOffset / 4));
    }

    public static void updateEnchantment(int[] enchantPower, int[] enchantId, int[] enchantLevel, int tableSeed, int experienceLevel, boolean creativeMode, float turningSpeed) {
        enchantmentCache = new EnchantmentSnapshot(EnchantmentSnapshot.pack(enchantPower, enchantId, enchantLevel), tableSeed, experienceLevel, creativeMode, turningSpeed);
    }

    public static void updateRecipeBook(boolean bookOpen, String searchText, boolean craftableOnly, int tab, String selectedRecipe, int currentPage, boolean focused, int selStart, int selEnd) {
        RecipeBookSnapshot previous = recipeBookCache;
        recipeBookCache = new RecipeBookSnapshot(bookOpen, searchText, craftableOnly, tab, selectedRecipe, currentPage, previous == null ? false : previous.buttonSelected, focused, selStart, selEnd);
    }

    public static void updateRecipeBook(boolean bookOpen, String searchText, boolean craftableOnly, int tab, String selectedRecipe, boolean focused, int selStart, int selEnd) {
        GuiSnapshotCapture.updateRecipeBook(bookOpen, searchText, craftableOnly, tab, selectedRecipe, recipeBookCache == null ? 0 : GuiSnapshotCapture.recipeBookCache.page, focused, selStart, selEnd);
    }

    public static void updateRecipePage(int currentPage) {
        RecipeBookSnapshot previous = recipeBookCache;
        if (previous == null) {
            recipeBookCache = new RecipeBookSnapshot(false, "", false, 0, "", Math.max(0, currentPage), false, false, 0, 0);
            return;
        }
        recipeBookCache = new RecipeBookSnapshot(previous.open, previous.search, previous.showing, previous.category, previous.selected, currentPage, previous.buttonSelected, previous.searchFocused, previous.searchSelStart, previous.searchSelEnd);
    }

    public static void updateRecipeButton(boolean selected) {
        RecipeBookSnapshot previous = recipeBookCache;
        if (previous == null) {
            recipeBookCache = new RecipeBookSnapshot(false, "", false, 0, "", 0, selected, false, 0, 0);
            return;
        }
        recipeBookCache = new RecipeBookSnapshot(previous.open, previous.search, previous.showing, previous.category, previous.selected, previous.page, selected, previous.searchFocused, previous.searchSelStart, previous.searchSelEnd);
    }

    public static GuiCapture capture(Screen screen, int screenWidth, int screenHeight) {
        String guiType = GuiTypeResolver.resolve(screen);
        if (guiType == null) {
            return null;
        }
        return GuiSnapshotCapture.captureTyped(screen, guiType, screenWidth, screenHeight);
    }

    public static GuiCapture captureOrInventory(HandledScreen<?> handled, int screenWidth, int screenHeight) {
        String guiType = GuiTypeResolver.resolveOrInventory(handled);
        return GuiSnapshotCapture.captureTyped(handled, guiType, screenWidth, screenHeight);
    }

    private static GuiCapture captureTyped(Screen screen, String guiType, int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        double mouseX = client.mouse.getX() * (double)screenWidth / (double)client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * (double)screenHeight / (double)client.getWindow().getHeight();
        float curTx = (float)((mouseX - (double)screenWidth / 2.0) / 2.0);
        float curTy = (float)(((double)screenHeight / 2.0 - mouseY) / 2.0);
        LinkedHashMap<String, ItemStack> slots = new LinkedHashMap<String, ItemStack>();
        ItemStack cursorItem = ItemStack.EMPTY;
        boolean dragging = false;
        String dragEncoded = "";
        List<String> dragKeys = List.of();
        if (screen instanceof HandledScreen) {
            HandledScreen handled = (HandledScreen)screen;
            ScreenHandler handler = handled.getScreenHandler();
            if ("creative_inventory".equals(guiType) && creativeCache != null && GuiSnapshotCapture.creativeCache.inventoryTab && client.player != null) {
                handler = client.player.playerScreenHandler;
            }
            if (handler != null) {
                HandledScreenPovAccess screenDrag;
                ItemStack cursorStack;
                GuiSlotSchema schema = GuiSlotSchema.get(guiType);
                if (schema != null && schema.slots != null) {
                    for (GuiSlotSchema.Slot slot : schema.slots) {
                        if (slot.handlerIndex() < 0 || slot.handlerIndex() >= handler.slots.size()) continue;
                        ItemStack stack = ((Slot)handler.slots.get(slot.handlerIndex())).getStack();
                        slots.put(slot.id(), stack == null ? ItemStack.EMPTY : stack.copy());
                    }
                }
                cursorItem = (cursorStack = handler.getCursorStack()) == null ? ItemStack.EMPTY : cursorStack.copy();
                HandledScreenPovAccess dragAccess = handled instanceof HandledScreenPovAccess ? (screenDrag = (HandledScreenPovAccess)handled) : null;
                boolean bl = dragging = dragAccess != null && dragAccess.bbsPov$isCursorDragging() && dragAccess.bbsPov$getCursorDragSlots() != null && dragAccess.bbsPov$getCursorDragSlots().size() > 1;
                if (dragging) {
                    cursorItem = cursorItem.isEmpty() ? ItemStack.EMPTY : cursorItem.copyWithCount(Math.max(0, dragAccess.bbsPov$getDraggedStackRemainder()));
                    dragKeys = GuiSlotDragPreview.keys(dragAccess.bbsPov$getCursorDragSlots(), guiType);
                    dragEncoded = GuiSlotDragPreview.encode(dragAccess.bbsPov$getHeldButtonType(), cursorStack == null ? 0 : cursorStack.getCount(), cursorStack, dragKeys);
                }
            }
        }
        GuiSnapshot snapshot = new GuiSnapshot(guiType, curTx, curTy, true, cursorItem, dragging, dragEncoded, slots, dragKeys);
        return new GuiCapture(snapshot, "anvil".equals(guiType) ? anvilCache : null, "creative_inventory".equals(guiType) ? creativeCache : null, "loom".equals(guiType) ? loomCache : null, "stonecutter".equals(guiType) ? stonecutterCache : null, "enchanting_table".equals(guiType) ? enchantmentCache : null, GuiSnapshotCapture.captureBeacon(screen, guiType), GuiRecipeBook.supports(guiType) ? GuiSnapshotCapture.captureRecipeBook(screen, guiType) : null, GuiSnapshotCapture.captureMerchant(screen, guiType), GuiSnapshotCapture.captureBook(screen, guiType), GuiSnapshotCapture.captureMount(screen, guiType), GuiSnapshotCapture.captureGamemode(screen, guiType), GuiSnapshotCapture.captureFurnace(screen, guiType), GuiSnapshotCapture.captureBrewing(screen, guiType));
    }

    private static RecipeBookSnapshot captureRecipeBook(Screen screen, String guiType) {
        RecipeBookSnapshot live;
        RecipeBookProvider provider;
        RecipeBookWidget widget;
        RecipeBookSnapshot cached = recipeBookCache == null ? new RecipeBookSnapshot(false, "", false, 0, "", 0, false, false, 0, 0) : recipeBookCache;
        boolean open = cached.open;
        boolean showing = cached.showing;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            HandledScreen handled;
            ScreenHandler screenHandler;
            ClientRecipeBook book = client.player.getRecipeBook();
            RecipeBookCategory category = GuiRecipeBook.category(guiType);
            open = book.isGuiOpen(category);
            showing = book.isFilteringCraftable(category);
            if (screen instanceof HandledScreen && (screenHandler = (handled = (HandledScreen)screen).getScreenHandler()) instanceof AbstractRecipeScreenHandler) {
                AbstractRecipeScreenHandler recipeHandler = (AbstractRecipeScreenHandler)screenHandler;
                showing = book.isFilteringCraftable(recipeHandler);
            }
        }
        if (screen instanceof RecipeBookProvider && (widget = (provider = (RecipeBookProvider)screen).getRecipeBookWidget()) != null) {
            open = widget.isOpen();
        }
        recipeBookCache = live = new RecipeBookSnapshot(open, cached.search, showing, cached.category, cached.selected, cached.page, cached.buttonSelected, cached.searchFocused, cached.searchSelStart, cached.searchSelEnd);
        return live;
    }

    private static BeaconSnapshot captureBeacon(Screen screen, String guiType) {
        if ("beacon".equals(guiType) && screen instanceof HandledScreen<?> handled && handled.getScreenHandler() instanceof BeaconScreenHandler beacon) {
            StatusEffect primary = handled instanceof BeaconScreenPovAccess accessor ? accessor.bbsPov$getPrimaryEffect() : beacon.getPrimaryEffect();
            StatusEffect secondary = handled instanceof BeaconScreenPovAccess accessorx ? accessorx.bbsPov$getSecondaryEffect() : beacon.getSecondaryEffect();
            int secondaryIndex = secondary == StatusEffects.REGENERATION ? 1 : (secondary != null && secondary == primary ? 2 : 0);
            return new BeaconSnapshot(GuiSnapshotCapture.beaconPrimaryIndex(primary), secondaryIndex, beacon.getProperties());
        }
        return null;
    }

    private static MerchantSnapshot captureMerchant(Screen screen, String guiType) {
        if ("villager".equals(guiType) && screen instanceof MerchantScreen merchantScreen) {
            MerchantScreenHandler merchantHandler = (MerchantScreenHandler)merchantScreen.getScreenHandler();
            int scrollIndex = merchantScreen instanceof MerchantScreenPovAccess acc ? acc.bbsPov$getIndexStartOffset() : 0;
            int selectedIndex = merchantScreen instanceof MerchantScreenPovAccess accx ? accx.bbsPov$getSelectedIndex() : 0;
            TradeOfferList recipes = merchantHandler.getRecipes();
            int level = merchantHandler.getLevelProgress();
            int xp = merchantHandler.getExperience();
            boolean canLevel = merchantHandler.isLeveled();
            String title = merchantScreen.getTitle() != null ? merchantScreen.getTitle().getString() : "";
            int profession = 1;
            MinecraftClient client = MinecraftClient.getInstance();
            Entity entity = client.targetedEntity;
            if (entity instanceof VillagerEntity) {
                VillagerEntity villager = (VillagerEntity)entity;
                profession = Registries.VILLAGER_PROFESSION.getRawId(villager.getVillagerData().getProfession());
            }
            return MerchantSnapshot.fromOffers(recipes, profession, level, xp, selectedIndex, scrollIndex, title, canLevel);
        }
        return null;
    }

    private static BookSnapshot captureBook(Screen screen, String guiType) {
        String author;
        if (!"book".equals(guiType)) {
            return null;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        String string = author = player != null ? player.getName().getString() : "";
        if (screen instanceof BookEditScreenPovAccess) {
            BookEditScreenPovAccess edit = (BookEditScreenPovAccess)screen;
            boolean signing = edit.bbsPov$isSigning();
            SelectionManager selection = signing ? edit.bbsPov$getTitleSelection() : edit.bbsPov$getPageSelection();
            return new BookSnapshot(true, signing, edit.bbsPov$getCurrentPage(), BookSnapshot.pack(edit.bbsPov$getPages()), edit.bbsPov$getTitle(), author, selection == null ? 0 : selection.getSelectionStart(), selection == null ? 0 : selection.getSelectionEnd());
        }
        if (screen instanceof BookScreenPovAccess) {
            BookScreenPovAccess book = (BookScreenPovAccess)screen;
            BookScreen.Contents contents = book.bbsPov$getContents();
            ArrayList<String> pages = new ArrayList<String>();
            int count = contents == null ? 0 : contents.getPageCount();
            for (int i = 0; i < count; ++i) {
                StringVisitable visitable = contents.getPage(i);
                if (visitable instanceof Text) {
                    Text text = (Text)visitable;
                    pages.add(Text.Serialization.toJsonString((Text)text));
                    continue;
                }
                pages.add(visitable == null ? "" : visitable.getString());
            }
            return new BookSnapshot(false, false, book.bbsPov$getPageIndex(), BookSnapshot.pack(pages), "", author, 0, 0);
        }
        return null;
    }

    private static MountSnapshot captureMount(Screen screen, String guiType) {
        AbstractHorseEntity abstractHorseEntity;
        if (!"horse".equals(guiType) && !"donkey".equals(guiType) || !(screen instanceof HandledScreen)) {
            return null;
        }
        HandledScreen handled = (HandledScreen)screen;
        if (handled instanceof HorseScreenPovAccess) {
            HorseScreenPovAccess horseScreen = (HorseScreenPovAccess)handled;
            abstractHorseEntity = horseScreen.bbsPov$getEntity();
        } else {
            abstractHorseEntity = null;
        }
        AbstractHorseEntity mount = abstractHorseEntity;
        int variant = 0;
        boolean chest = false;
        if ("horse".equals(guiType) && mount instanceof HorseEntity) {
            HorseEntity horse = (HorseEntity)mount;
            variant = GuiPovActionClip.unpackHorseVariant(horse.getVariant().getId(), horse.getMarking().getId());
        }
        if ("donkey".equals(guiType) && mount instanceof AbstractDonkeyEntity) {
            AbstractDonkeyEntity donkey = (AbstractDonkeyEntity)mount;
            chest = donkey.hasChest();
        }
        return new MountSnapshot(variant, chest);
    }

    private static GamemodeSnapshot captureGamemode(Screen screen, String guiType) {
        if (!"gamemode_switcher".equals(guiType)) {
            return null;
        }
        int selected = 0;
        if (screen instanceof GameModeSelectionScreenPovAccess) {
            GameModeSelectionScreenPovAccess acc = (GameModeSelectionScreenPovAccess)screen;
            Object modeObj = acc.bbsPov$getGameMode();
            if (modeObj != null) {
                String modeName = modeObj.toString();
                if ("CREATIVE".equals(modeName)) {
                    selected = 1;
                } else if ("ADVENTURE".equals(modeName)) {
                    selected = 2;
                } else if ("SPECTATOR".equals(modeName)) {
                    selected = 3;
                }
            }
        } else if (MinecraftClient.getInstance().interactionManager != null) {
            GameMode gm = MinecraftClient.getInstance().interactionManager.getCurrentGameMode();
            if (gm == GameMode.CREATIVE) {
                selected = 1;
            } else if (gm == GameMode.ADVENTURE) {
                selected = 2;
            } else if (gm == GameMode.SPECTATOR) {
                selected = 3;
            }
        }
        return new GamemodeSnapshot(selected);
    }

    private static FurnaceSnapshot captureFurnace(Screen screen, String guiType) {
        HandledScreen handled;
        ScreenHandler screenHandler;
        if (!(GuiRecipeBook.isFurnace(guiType) && screen instanceof HandledScreen && (screenHandler = (handled = (HandledScreen)screen).getScreenHandler()) instanceof AbstractFurnaceScreenHandler)) {
            return null;
        }
        AbstractFurnaceScreenHandler furnace = (AbstractFurnaceScreenHandler)screenHandler;
        return new FurnaceSnapshot(furnace.isBurning() ? furnace.getFuelProgress() : 0.0f, furnace.getCookProgress());
    }

    private static BrewingSnapshot captureBrewing(Screen screen, String guiType) {
        HandledScreen handled;
        ScreenHandler screenHandler;
        if (!("brewing_stand".equals(guiType) && screen instanceof HandledScreen && (screenHandler = (handled = (HandledScreen)screen).getScreenHandler()) instanceof BrewingStandScreenHandler)) {
            return null;
        }
        BrewingStandScreenHandler brewing = (BrewingStandScreenHandler)screenHandler;
        int brewTime = brewing.getBrewTime();
        return new BrewingSnapshot(brewTime > 0 ? 1.0f - (float)brewTime / 400.0f : 0.0f, (float)brewing.getFuel() / 20.0f, brewTime > 0);
    }

    private static int beaconPrimaryIndex(StatusEffect effect) {
        if (effect == StatusEffects.SPEED) {
            return 1;
        }
        if (effect == StatusEffects.HASTE) {
            return 2;
        }
        if (effect == StatusEffects.RESISTANCE) {
            return 3;
        }
        if (effect == StatusEffects.JUMP_BOOST) {
            return 4;
        }
        if (effect == StatusEffects.STRENGTH) {
            return 5;
        }
        return 0;
    }

    static {
        recipeBookCache = new RecipeBookSnapshot(false, "", false, 0, "", 0, false, false, 0, 0);
    }
}

