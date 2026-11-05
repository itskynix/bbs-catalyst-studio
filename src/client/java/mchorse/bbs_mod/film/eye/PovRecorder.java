package mchorse.bbs_mod.film.eye;

import mchorse.bbs_mod.film.pov.PovClip;
import mchorse.bbs_mod.film.pov.PovGuiClip;
import mchorse.bbs_mod.film.pov.PovSlotSet;
import mchorse.bbs_mod.film.pov.PovStatusEffectsClip;
import mchorse.bbs_mod.film.pov.PovToastClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayPov;
import mchorse.bbs_mod.mixin.client.PovAdvancementToastAccessor;
import mchorse.bbs_mod.mixin.client.PovBookEditScreenAccessor;
import mchorse.bbs_mod.mixin.client.PovBookScreenAccessor;
import mchorse.bbs_mod.mixin.client.PovCreativeScreenAccessor;
import mchorse.bbs_mod.mixin.client.PovDeathScreenAccessor;
import mchorse.bbs_mod.mixin.client.PovHeldItemRendererAccessor;
import mchorse.bbs_mod.mixin.client.PovRecipeBookResultsAccessor;
import mchorse.bbs_mod.mixin.client.PovRecipeBookWidgetAccessor;
import mchorse.bbs_mod.mixin.client.PovSystemToastAccessor;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.gui.screen.recipebook.RecipeGroupButtonWidget;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.BlastFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.BrewingStandScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.FurnaceScreen;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.GrindstoneScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HopperScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.LoomScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.client.gui.screen.ingame.SmokerScreen;
import net.minecraft.client.gui.screen.ingame.StonecutterScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.toast.AdvancementToast;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.util.Window;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The point of view of a live take, every tick: what the interface shows (the game mode, health,
 * food, armor, air, experience, the mount's health, whether it is hidden), the inventory above the
 * hotbar, how each hand swings and dips, where the cursor is and what it carries while a screen is
 * open; and as POV action clips, the screen open from the tick it opened to the tick it closed
 * (its slot groups and state as they changed), the status effects while any is on, and the toasts.
 *
 * <p>The hotbar, the selected slot, the off hand and the armor are not here: the replay's own
 * recording already takes them every tick (see {@code ReplayKeyframes#record}), and the POV draws
 * them from there.</p>
 *
 * <p>A channel is keyed where its value changes, and at the start of what it belongs to (the take,
 * the screen), so a scrub to before the first change finds what was there, not what came after.
 * The cursor is kept in interface units from the middle of the screen, which is where the game
 * centres its screens, so a take played back at another size still points at the same slot.</p>
 */
public class PovRecorder
{
    /** What the take writes into: the channels are copied over the replay's when it is done */
    public final ReplayPov pov = new ReplayPov("pov");
    /** The POV action clips of the take: the screens opened, the status effects, the toasts */
    public final List<PovClip> clips = new ArrayList<>();

    /** How long the game keeps a toast up, in ticks (five seconds) */
    private static final int TOAST_TICKS = 100;

    /** The last value keyed on each channel, so the next is keyed only if it differs */
    private final Map<KeyframeChannel<?>, Object> last = new HashMap<>();

    private PovGuiClip open;

    private PovStatusEffectsClip effects;
    private final Map<String, PovStatusEffectsClip.Effect> effectEntries = new HashMap<>();
    private final Map<String, int[]> effectTimes = new HashMap<>();
    private final Set<String> showing = new HashSet<>();

    /** Key a value where it differs from the last one keyed on the channel */
    private <T> void key(KeyframeChannel<T> channel, int tick, T value)
    {
        Object previous = this.last.get(channel);

        if (previous == null || !channel.getFactory().compare(previous, value))
        {
            int index = channel.insert(tick, value);
            Keyframe<T> keyframe = channel.get(index);

            /* Held until the next change, as the game shows it: a bar does not slide between two readings */
            if (keyframe != null)
            {
                keyframe.getInterpolation().setInterp(Interpolations.CONST);
            }

            this.last.put(channel, value);
        }
    }

    public void record(int tick, ClientPlayerEntity player)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ReplayPov pov = this.pov;

        /* The interface */
        this.key(pov.hudVisible, tick, !mc.options.hudHidden);
        this.key(pov.gameMode, tick, mc.interactionManager == null ? 0 : mc.interactionManager.getCurrentGameMode().getId());
        this.key(pov.health, tick, (double) player.getHealth());
        this.key(pov.maxHealth, tick, (double) player.getMaxHealth());
        this.key(pov.absorption, tick, (double) player.getAbsorptionAmount());
        this.key(pov.armor, tick, (double) player.getArmor());
        this.key(pov.hunger, tick, (double) player.getHungerManager().getFoodLevel());
        this.key(pov.air, tick, (double) player.getAir());
        this.key(pov.experience, tick, (double) player.experienceProgress);
        this.key(pov.experienceLevel, tick, player.experienceLevel);
        this.key(pov.hardcore, tick, player.getWorld().getLevelProperties().isHardcore());
        this.key(pov.mountHealth, tick, player.getVehicle() instanceof LivingEntity mount ? (double) mount.getHealth() : 0D);

        /* The inventory above the hotbar, as one group */
        this.key(pov.inventorySlots, tick, new PovSlotSet(player.getInventory().main.subList(9, 36)));

        /* The hands: the one swinging, by how far into its swing */
        float swing = player.getHandSwingProgress(1F);
        boolean offHand = player.preferredHand == Hand.OFF_HAND;
        boolean mainRight = player.getMainArm() == Arm.RIGHT;
        boolean swingingRight = mainRight != offHand;

        pov.rightSwing.insert(tick, swingingRight ? (double) swing : 0D);
        pov.leftSwing.insert(tick, swingingRight ? 0D : (double) swing);

        /* The equip dip: how far down each hand is, from the game's own first-person hand (0 up, 1 out of view) */
        if (mc.getEntityRenderDispatcher().getHeldItemRenderer() instanceof PovHeldItemRendererAccessor held)
        {
            pov.mainEquip.insert(tick, 1D - held.bbs$getEquipProgressMainHand());
            pov.offhandEquip.insert(tick, 1D - held.bbs$getEquipProgressOffHand());
        }

        /* The walking bob: the step phase (-horizontalSpeed) and the stride (strideDistance) */
        pov.bobPhase.insert(tick, (double) -player.horizontalSpeed);
        pov.bobStrength.insert(tick, (double) player.strideDistance);

        this.recordEffects(tick, player);

        /* The cursor, while a screen of the game is open (not BBS's own) */
        Screen screen = mc.currentScreen instanceof UIScreen ? null : mc.currentScreen;

        this.key(pov.cursorVisible, tick, screen != null);

        if (screen != null)
        {
            Window window = mc.getWindow();
            double scaleX = window.getScaledWidth() / (double) Math.max(window.getWidth(), 1);
            double scaleY = window.getScaledHeight() / (double) Math.max(window.getHeight(), 1);

            pov.cursorX.insert(tick, mc.mouse.getX() * scaleX - window.getScaledWidth() / 2D);
            pov.cursorY.insert(tick, mc.mouse.getY() * scaleY - window.getScaledHeight() / 2D);
            this.key(pov.cursorItem, tick, player.currentScreenHandler.getCursorStack().copy());
        }

        /* The screen: a clip from when it opened to when it closed (or turned into another one) */
        String type = typeOf(screen);

        if (this.open != null && !this.open.screen.get().equals(type))
        {
            this.close(tick);
        }

        if (this.open == null && type != null)
        {
            this.open = new PovGuiClip();
            this.open.screen.set(type);
            this.open.tick.set(tick);
            this.clips.add(this.open);
        }

        if (this.open != null)
        {
            this.recordScreen(tick - this.open.tick.get(), screen, player);
        }
    }

    /**
     * What the open screen shows, in the clip's own time: its slot groups and the state the clip's
     * channels for its type carry (the recipe book, the creative tab and search, the progress
     * bars, the beacon's powers, the book's pages).
     */
    private void recordScreen(int rel, Screen screen, ClientPlayerEntity player)
    {
        PovGuiClip clip = this.open;
        String type = clip.screen.get();

        if (screen instanceof HandledScreen<?> handled)
        {
            ScreenHandler handler = handled.getScreenHandler();
            List<Slot> slots = handler.slots;

            switch (type)
            {
                case "inventory" -> this.key(clip.crafting2x2, rel, group(slots, 0, 5));
                case "crafting" -> this.key(clip.crafting3x3, rel, group(slots, 0, 10));
                case "beacon" ->
                {
                    if (!slots.isEmpty())
                    {
                        this.key(clip.payment, rel, slots.get(0).getStack().copy());
                    }

                    if (handler instanceof BeaconScreenHandler beacon)
                    {
                        this.key(clip.beaconLevel, rel, beacon.getProperties());
                        this.key(clip.primaryPower, rel, effectId(beacon.getPrimaryEffect()));
                        this.key(clip.secondaryPower, rel, effectId(beacon.getSecondaryEffect()));
                    }
                }
                case "creative" -> this.recordCreative(rel, screen);
                default ->
                {
                    List<ItemStack> own = new ArrayList<>();

                    for (Slot slot : slots)
                    {
                        if (slot.inventory != player.getInventory())
                        {
                            own.add(slot.getStack());
                        }
                    }

                    this.key(clip.containerSlots, rel, new PovSlotSet(own));
                }
            }

            if (type.equals("inventory") || type.equals("crafting"))
            {
                this.key(clip.recipeBook, rel, player.getRecipeBook().isGuiOpen(RecipeBookCategory.CRAFTING));
                this.key(clip.showingCraftable, rel, player.getRecipeBook().isFilteringCraftable(RecipeBookCategory.CRAFTING));

                /* The book's tab, search and page, so the arrows clicked in the take turn its pages in the clip */
                if (screen instanceof RecipeBookProvider provider && provider.getRecipeBookWidget() instanceof PovRecipeBookWidgetAccessor book)
                {
                    RecipeGroupButtonWidget tab = book.bbs$getCurrentTab();
                    TextFieldWidget search = book.bbs$getSearchField();

                    if (tab != null)
                    {
                        this.key(clip.recipeCategory, rel, Math.max(0, RecipeBookGroup.getGroups(RecipeBookCategory.CRAFTING).indexOf(tab.getCategory())));
                    }

                    if (search != null)
                    {
                        this.key(clip.recipeSearch, rel, search.getText());
                        this.key(clip.recipeSearchFocus, rel, search.isFocused());
                    }

                    if (book.bbs$getRecipesArea() instanceof PovRecipeBookResultsAccessor results)
                    {
                        this.key(clip.recipePage, rel, results.bbs$getCurrentPage());
                    }
                }
            }

            if (handler instanceof AbstractFurnaceScreenHandler furnace)
            {
                this.key(clip.cookProgress, rel, (double) furnace.getCookProgress());
                this.key(clip.burnProgress, rel, furnace.isBurning() ? (double) furnace.getFuelProgress() : 0D);
            }
            else if (handler instanceof BrewingStandScreenHandler brewing)
            {
                int brewTime = brewing.getBrewTime();

                this.key(clip.brewProgress, rel, brewTime > 0 ? 1D - brewTime / 400D : 0D);
                this.key(clip.brewFuel, rel, brewing.getFuel() / 20D);
            }
        }
        else if (screen instanceof DeathScreen && screen instanceof PovDeathScreenAccessor death)
        {
            /* What killed the player, the score, and the buttons, which the game lets be pressed after a second */
            this.key(clip.deathMessage, rel, death.bbs$getMessage() == null ? "" : death.bbs$getMessage().getString());
            this.key(clip.score, rel, String.valueOf(player.getScore()));
            this.key(clip.buttonsActive, rel, !death.bbs$getButtons().isEmpty() && death.bbs$getButtons().get(0).active);
        }
        else if (screen instanceof PovBookEditScreenAccessor book)
        {
            this.key(clip.writable, rel, true);
            this.key(clip.signing, rel, book.bbs$isSigning());
            this.key(clip.bookPage, rel, book.bbs$getCurrentPage());
            this.key(clip.bookPages, rel, String.join("\n" + PovGuiClip.PAGE_BREAK + "\n", book.bbs$getPages()));
            this.key(clip.bookTitle, rel, book.bbs$getTitle() == null ? "" : book.bbs$getTitle());
            this.key(clip.bookAuthor, rel, player.getName().getString());
        }
        else if (screen instanceof PovBookScreenAccessor book)
        {
            BookScreen.Contents contents = book.bbs$getContents();
            List<String> pages = new ArrayList<>();

            for (int i = 0; contents != null && i < contents.getPageCount(); i++)
            {
                pages.add(contents.getPage(i).getString());
            }

            this.key(clip.writable, rel, false);
            this.key(clip.bookPage, rel, book.bbs$getPageIndex());
            this.key(clip.bookPages, rel, String.join("\n" + PovGuiClip.PAGE_BREAK + "\n", pages));
        }
    }

    /** The creative inventory's tab (by its place among the shown tabs), the grid's scroll row, the search */
    private void recordCreative(int rel, Screen screen)
    {
        PovGuiClip clip = this.open;
        ItemGroup tab = PovCreativeScreenAccessor.bbs$getSelectedTab();
        List<ItemGroup> groups = ItemGroups.getGroupsToDisplay();
        int index = Math.max(0, groups.indexOf(tab));

        this.key(clip.creativeTab, rel, index);
        this.key(clip.creativePage, rel, PovCreativePages.currentPage(screen));

        if (screen instanceof PovCreativeScreenAccessor creative)
        {
            if (screen instanceof CreativeInventoryScreen handled && handled.getScreenHandler() != null)
            {
                int rows = (handled.getScreenHandler().itemList.size() + 8) / 9;
                int overflow = Math.max(0, rows - 5);

                this.key(clip.creativeScrollRow, rel, Math.round(creative.bbs$getScrollPosition() * overflow));
            }

            TextFieldWidget search = creative.bbs$getSearchBox();

            if (search != null)
            {
                this.key(clip.creativeSearch, rel, search.getText());
                this.key(clip.creativeSearchFocus, rel, search.isFocused());
            }
        }
    }

    private static PovSlotSet group(List<Slot> slots, int from, int size)
    {
        List<ItemStack> items = new ArrayList<>(size);

        for (int i = from; i < from + size; i++)
        {
            items.add(i < slots.size() ? slots.get(i).getStack() : ItemStack.EMPTY);
        }

        return new PovSlotSet(items);
    }

    private static String effectId(StatusEffect effect)
    {
        return effect == null ? "" : String.valueOf(Registries.STATUS_EFFECT.getId(effect));
    }

    /**
     * The status effects the interface shows: a clip from the first one on to the last one off. An
     * effect's time left is keyed where it starts and again only where it stops counting down by
     * one a tick (made longer, cut short), so a long potion is a couple of keyframes, not thousands.
     */
    private void recordEffects(int tick, ClientPlayerEntity player)
    {
        List<StatusEffectInstance> active = new ArrayList<>();

        for (StatusEffectInstance instance : player.getStatusEffects())
        {
            if (instance.shouldShowIcon())
            {
                active.add(instance);
            }
        }

        if (this.effects == null)
        {
            if (active.isEmpty())
            {
                return;
            }

            this.effects = new PovStatusEffectsClip();
            this.effects.tick.set(tick);
            this.effectEntries.clear();
            this.effectTimes.clear();
            this.showing.clear();
            this.clips.add(this.effects);
        }

        int rel = tick - this.effects.tick.get();
        Set<String> seen = new HashSet<>();

        for (StatusEffectInstance instance : active)
        {
            String id = String.valueOf(Registries.STATUS_EFFECT.getId(instance.getEffectType()));
            PovStatusEffectsClip.Effect entry = this.effectEntries.computeIfAbsent(id, (key) -> this.effects.effects.add(key));
            int duration = instance.getDuration();
            int[] last = this.effectTimes.get(id);

            entry.ambient.set(instance.isAmbient());
            seen.add(id);

            if (last == null || duration != last[1] - (rel - last[0]))
            {
                if (last != null && rel - 1 > last[0])
                {
                    entry.duration.insert(rel - 1, (double) (last[1] - (rel - 1 - last[0])));
                }

                entry.duration.insert(rel, (double) duration);
                this.effectTimes.put(id, new int[] {rel, duration});
            }

            if (this.showing.add(id))
            {
                entry.alpha.insert(rel, 1D);
            }
        }

        for (String id : new ArrayList<>(this.showing))
        {
            if (!seen.contains(id))
            {
                this.effectEntries.get(id).alpha.insert(rel, 0D);
                this.showing.remove(id);
                this.effectTimes.remove(id);
            }
        }

        if (active.isEmpty())
        {
            this.effects.duration.set(Math.max(1, rel));
            this.effects = null;
        }
    }

    /**
     * A toast popping up: an advancement (its frame for the preset, its name for the description,
     * its icon), or a notice (its title and lines). The game's other toasts (recipes, tutorials) are
     * hints for the player at the keyboard, not part of a take.
     */
    public void recordToast(Toast toast, int tick)
    {
        PovToastClip clip = new PovToastClip();

        if (toast instanceof AdvancementToast && toast instanceof PovAdvancementToastAccessor accessor)
        {
            AdvancementDisplay display = accessor.bbs$getAdvancement().value().display().orElse(null);

            if (display == null)
            {
                return;
            }

            AdvancementFrame frame = display.getFrame();

            clip.preset.set(frame == AdvancementFrame.GOAL ? PovToastClip.GOAL : frame == AdvancementFrame.CHALLENGE ? PovToastClip.CHALLENGE : PovToastClip.TASK);
            clip.description.set(display.getTitle().getString());
            clip.item.set(display.getIcon().copy());
        }
        else if (toast instanceof SystemToast && toast instanceof PovSystemToastAccessor accessor)
        {
            StringBuilder lines = new StringBuilder();

            for (OrderedText line : accessor.bbs$getLines())
            {
                if (lines.length() > 0)
                {
                    lines.append(' ');
                }

                line.accept((index, style, codePoint) ->
                {
                    lines.appendCodePoint(codePoint);

                    return true;
                });
            }

            clip.preset.set(PovToastClip.NOTICE);
            clip.titleText.set(accessor.bbs$getTitle() == null ? "" : accessor.bbs$getTitle().getString());
            clip.description.set(lines.toString());
        }
        else
        {
            return;
        }

        clip.tick.set(tick);
        clip.duration.set(TOAST_TICKS);
        this.clips.add(clip);
    }

    /** A totem of undying popped (the game shows its floating item): a screen effects clip with the pop, on that tick */
    public void recordTotem(ItemStack stack, int tick)
    {
        mchorse.bbs_mod.film.pov.PovScreenEffectsClip clip = new mchorse.bbs_mod.film.pov.PovScreenEffectsClip();
        mchorse.bbs_mod.film.pov.PovScreenEffectsClip.Effect effect = clip.effects.add("totem");

        effect.item.set(stack == null ? ItemStack.EMPTY : stack.copy());
        clip.tick.set(tick);
        clip.duration.set(40);
        this.clips.add(clip);
    }

    private void close(int tick)
    {
        if (this.open != null)
        {
            this.open.duration.set(Math.max(1, tick - this.open.tick.get()));
            this.open = null;
        }
    }

    /** The take is over: the screen still open closes here, the effects still on are keyed to the end, the channels drop their repeats */
    public void finish(int tick)
    {
        this.close(tick);

        if (this.effects != null)
        {
            /* Still on when the take ends: the times left are keyed up to the end, so they keep counting down */
            int rel = tick - this.effects.tick.get();

            for (Map.Entry<String, int[]> time : this.effectTimes.entrySet())
            {
                int[] last = time.getValue();

                if (rel > last[0])
                {
                    this.effectEntries.get(time.getKey()).duration.insert(rel, (double) (last[1] - (rel - last[0])));
                }
            }

            this.effects.duration.set(Math.max(1, rel));
            this.effects = null;
        }

        for (KeyframeChannel<?> channel : this.pov.getChannels())
        {
            channel.simplify();
        }
    }

    /** Lay the take over the replay: its channels over the replay's from the take's start, its screens, effects and toasts as clips */
    public void applyTo(Replay replay)
    {
        List<KeyframeChannel<?>> own = this.pov.getChannels();
        List<KeyframeChannel<?>> target = replay.pov.getChannels();

        for (int i = 0; i < own.size() && i < target.size(); i++)
        {
            target.get(i).copyOver(own.get(i), 0);
        }

        for (PovClip clip : this.clips)
        {
            clip.layer.set(replay.pov.actions.findFreeLayer(clip));
            replay.pov.actions.addClip(clip);
        }
    }

    /** Which interface clip screen a game screen is, or null for one it has none for */
    public static String typeOf(Screen screen)
    {
        if (screen == null) return null;
        if (screen instanceof InventoryScreen) return "inventory";
        if (screen instanceof CreativeInventoryScreen) return "creative";
        if (screen instanceof CraftingScreen) return "crafting";
        if (screen instanceof BlastFurnaceScreen) return "blast_furnace";
        if (screen instanceof SmokerScreen) return "smoker";
        if (screen instanceof FurnaceScreen) return "furnace";
        if (screen instanceof AnvilScreen) return "anvil";
        if (screen instanceof GrindstoneScreen) return "grindstone";
        if (screen instanceof StonecutterScreen) return "stonecutter";
        if (screen instanceof SmithingScreen) return "smithing";
        if (screen instanceof LoomScreen) return "loom";
        if (screen instanceof CartographyTableScreen) return "cartography";
        if (screen instanceof EnchantmentScreen) return "enchanting";
        if (screen instanceof BrewingStandScreen) return "brewing";
        if (screen instanceof BeaconScreen) return "beacon";
        if (screen instanceof ShulkerBoxScreen) return "shulker_box";
        if (screen instanceof HopperScreen) return "hopper";
        if (screen instanceof Generic3x3ContainerScreen) return "dispenser";
        if (screen instanceof GenericContainerScreen container)
        {
            if (container.getScreenHandler().getRows() >= 6) return "large_chest";

            boolean barrel = container.getTitle().getContent() instanceof TranslatableTextContent text && text.getKey().equals("container.barrel");

            return barrel ? "barrel" : "chest";
        }
        if (screen instanceof BookScreen || screen instanceof BookEditScreen) return "book";
        if (screen instanceof GameMenuScreen) return "game_menu";
        if (screen instanceof DeathScreen) return "death";
        if (screen instanceof SleepingChatScreen) return "sleep";

        return null;
    }
}
