package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * An interface open in front of the replay: a container (the inventory, a crafting table, a
 * furnace, an anvil, a chest, a beacon...), a book, or a menu (the game menu, the death screen,
 * the bed's "Leave Bed"). The menus live here rather than in a clip of their own so that one clip
 * runs whatever is on screen.
 *
 * <p>Every screen has the same three channels (its layout, its opacity and the darkness behind
 * it); everything else belongs to some screens only, and {@link #channelsFor} says which, so the
 * dope sheet shows a chest its slots and a beacon its powers and neither the other's. Slots go by
 * groups (the chest's, the crafting grid's), each one row holding all of its items. The player's
 * part of a screen is the replay's own: its hotbar and inventory, its armor and off hand, unless the
 * clip keys the armor and the off hand itself.</p>
 */
public class PovGuiClip extends PovClip
{
    public static final List<String> PLAYER = list("inventory", "creative");
    public static final List<String> CRAFTING = list("crafting", "anvil", "smithing", "grindstone", "stonecutter", "cartography", "loom");
    public static final List<String> SMELTING = list("furnace", "blast_furnace", "smoker");
    public static final List<String> UTILITY = list("enchanting", "brewing", "beacon");
    public static final List<String> STORAGE = list("chest", "large_chest", "barrel", "shulker_box", "hopper", "dispenser");
    public static final List<String> BOOKS = list("book");
    public static final List<String> MENUS = list("game_menu", "death", "sleep");

    /** The screens by the group the picker files them under, and the groups' names */
    public static final List<List<String>> GROUPS = list(PLAYER, CRAFTING, SMELTING, UTILITY, STORAGE, BOOKS, MENUS);
    public static final List<String> GROUP_NAMES = list("player", "crafting", "smelting", "utility", "storage", "book", "menu");
    public static final List<String> TYPES = all();

    /** A line holding only this splits the book's text into pages */
    public static final String PAGE_BREAK = "---";

    private static final List<String> COMMON = list("gui_layout", "gui_opacity", "darkness_opacity");
    private static final List<String> ARMOR = list("armor_head", "armor_chest", "armor_legs", "armor_feet", "offhand");
    private static final List<String> RECIPES = list("recipe_book", "showing_craftable", "recipe_search", "recipe_search_focus", "recipe_category", "recipe_page", "selected_recipe", "recipe_button");

    public final ValueString screen = new ValueString("screen", "inventory");

    /* Every screen: where it sits and how big (from the screen's middle), how much of it shows, how dark the world behind it is (1 is the game's) */
    public final KeyframeChannel<Transform> layout = this.channel("gui_layout", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Double> opacity = this.channel("gui_opacity", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> darkness = this.channel("darkness_opacity", KeyframeFactories.DOUBLE);

    /* The player's armor and off hand; unkeyed, the replay's own */
    public final KeyframeChannel<ItemStack> armorHead = this.channel("armor_head", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> armorChest = this.channel("armor_chest", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> armorLegs = this.channel("armor_legs", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> armorFeet = this.channel("armor_feet", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> offhand = this.channel("offhand", KeyframeFactories.ITEM_STACK);

    /* The crafting grids, the result first */
    public final KeyframeChannel<PovSlotSet> crafting2x2 = this.channel("crafting_2x2", KeyframeFactories.POV_SLOTS);
    public final KeyframeChannel<PovSlotSet> crafting3x3 = this.channel("crafting_3x3", KeyframeFactories.POV_SLOTS);

    /* The recipe book of the inventory and the crafting table */
    public final KeyframeChannel<Boolean> recipeBook = this.channel("recipe_book", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> showingCraftable = this.channel("showing_craftable", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<String> recipeSearch = this.channel("recipe_search", KeyframeFactories.STRING);
    public final KeyframeChannel<Boolean> recipeSearchFocus = this.channel("recipe_search_focus", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Integer> recipeCategory = this.channel("recipe_category", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> recipePage = this.channel("recipe_page", KeyframeFactories.INTEGER);
    /** Which recipe of the page is picked, from the top left; -1 none */
    public final KeyframeChannel<Integer> selectedRecipe = this.channel("selected_recipe", KeyframeFactories.INTEGER);
    /** The green book button lit, as under the cursor */
    public final KeyframeChannel<Boolean> recipeButton = this.channel("recipe_button", KeyframeFactories.BOOLEAN);

    /* The slots of a container, in the game's order, and the progress some of them show */
    public final KeyframeChannel<PovSlotSet> containerSlots = this.channel("container_slots", KeyframeFactories.POV_SLOTS);
    public final KeyframeChannel<Double> cookProgress = this.channel("cook_progress", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> burnProgress = this.channel("burn_progress", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> brewProgress = this.channel("brew_progress", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> brewFuel = this.channel("brew_fuel", KeyframeFactories.DOUBLE);

    /* The beacon: what is paid, how many pyramid levels it stands on, the powers picked (effect ids) */
    public final KeyframeChannel<ItemStack> payment = this.channel("payment", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<Integer> beaconLevel = this.channel("beacon_level", KeyframeFactories.INTEGER);
    public final KeyframeChannel<String> primaryPower = this.channel("primary_power", KeyframeFactories.STRING);
    public final KeyframeChannel<String> secondaryPower = this.channel("secondary_power", KeyframeFactories.STRING);

    /* The creative inventory: the tab, its page, how far its grid is scrolled, the search */
    public final KeyframeChannel<Integer> creativeTab = this.channel("creative_tab", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> creativePage = this.channel("creative_page", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> creativeScrollRow = this.channel("creative_scroll_row", KeyframeFactories.INTEGER);
    public final KeyframeChannel<String> creativeSearch = this.channel("creative_search", KeyframeFactories.STRING);
    public final KeyframeChannel<Boolean> creativeSearchFocus = this.channel("creative_search_focus", KeyframeFactories.BOOLEAN);

    /* The book: the page open, its text (pages split by PAGE_BREAK lines), a book and quill or a written book, its signing */
    public final KeyframeChannel<Integer> bookPage = this.channel("book_page", KeyframeFactories.INTEGER);
    public final KeyframeChannel<String> bookPages = this.channel("book_pages", KeyframeFactories.STRING);
    public final KeyframeChannel<Boolean> writable = this.channel("writable", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> signing = this.channel("signing", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<String> bookTitle = this.channel("book_title", KeyframeFactories.STRING);
    public final KeyframeChannel<String> bookAuthor = this.channel("book_author", KeyframeFactories.STRING);

    /* The death screen: what killed the replay, how red the screen goes, the score it shows and whether its
     * buttons can be pressed yet; the bed: the fall into sleep, and its button */
    public final KeyframeChannel<String> deathMessage = this.channel("death_message", KeyframeFactories.STRING);
    public final KeyframeChannel<Double> redBackground = this.channel("red_background", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<String> score = this.channel("score", KeyframeFactories.STRING);
    public final KeyframeChannel<Boolean> buttonsActive = this.channel("buttons_active", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Double> sleepDarkness = this.channel("sleep_darkness", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Boolean> leaveBed = this.channel("leave_bed", KeyframeFactories.BOOLEAN);

    /** The slots as the first version of this clip kept them, one channel each; only read, to be moved into the groups */
    public final Slots slots = new Slots("slots");

    /** How long the game keeps the death screen's buttons greyed out, in ticks */
    public static final int DEATH_BUTTONS_DELAY = 20;

    /**
     * The death screen's buttons as the game has them, when nothing says otherwise yet: greyed
     * out from the start, pressable a second later. Keys on the buttons channel, if it has none.
     */
    public void seedDeathButtons()
    {
        if (!this.buttonsActive.isEmpty())
        {
            return;
        }

        this.buttonsActive.insert(0F, false);
        this.buttonsActive.insert(DEATH_BUTTONS_DELAY, true);
    }

    public PovGuiClip()
    {
        super();

        this.add(this.screen);
        this.add(this.slots);
    }

    @SafeVarargs
    private static <T> List<T> list(T... values)
    {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    private static List<String> all()
    {
        List<String> types = new ArrayList<>();

        for (List<String> group : GROUPS)
        {
            types.addAll(group);
        }

        return Collections.unmodifiableList(types);
    }

    public static String groupOf(String type)
    {
        for (int i = 0; i < GROUPS.size(); i++)
        {
            if (GROUPS.get(i).contains(type))
            {
                return GROUP_NAMES.get(i);
            }
        }

        return "menu";
    }

    public boolean isMenu()
    {
        return MENUS.contains(this.screen.get());
    }

    /** The channels a screen is driven by, the three every screen has first */
    public static List<String> channelsFor(String type)
    {
        List<String> ids = new ArrayList<>(COMMON);

        switch (type)
        {
            case "inventory" ->
            {
                ids.addAll(ARMOR);
                ids.add("crafting_2x2");
                ids.addAll(RECIPES);
            }
            case "creative" ->
            {
                ids.addAll(ARMOR);
                ids.addAll(list("creative_tab", "creative_page", "creative_scroll_row", "creative_search", "creative_search_focus"));
            }
            case "crafting" ->
            {
                ids.add("crafting_3x3");
                ids.addAll(RECIPES);
            }
            case "furnace", "blast_furnace", "smoker" -> ids.addAll(list("container_slots", "cook_progress", "burn_progress"));
            case "brewing" -> ids.addAll(list("container_slots", "brew_progress", "brew_fuel"));
            case "beacon" -> ids.addAll(list("payment", "beacon_level", "primary_power", "secondary_power"));
            case "book" -> ids.addAll(list("book_page", "book_pages", "writable", "signing", "book_title", "book_author"));
            case "death" -> ids.addAll(list("death_message", "red_background", "score", "buttons_active"));
            case "sleep" -> ids.addAll(list("sleep_darkness", "leave_bed"));
            case "game_menu" -> {}
            default -> ids.add("container_slots");
        }

        return ids;
    }

    /** How many slots of its own (not the player's) a container has, the game's order; 0 for the rest */
    public static int containerSize(String type)
    {
        return switch (type)
        {
            case "chest", "barrel", "shulker_box" -> 27;
            case "large_chest" -> 54;
            case "hopper", "brewing" -> 5;
            case "dispenser" -> 9;
            case "furnace", "blast_furnace", "smoker", "anvil", "grindstone", "cartography" -> 3;
            case "smithing", "loom" -> 4;
            case "stonecutter", "enchanting" -> 2;
            default -> 0;
        };
    }

    /** How many slots a slot group channel has, for the dope sheet's fresh keyframes */
    public int groupSize(String id)
    {
        return switch (id)
        {
            case "crafting_2x2" -> 5;
            case "crafting_3x3" -> 10;
            case "container_slots" -> containerSize(this.screen.get());
            default -> 0;
        };
    }

    /** The book's text split into its pages */
    public static List<String> pages(String text)
    {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();

        for (String line : text.split("\n", -1))
        {
            if (line.trim().equals(PAGE_BREAK))
            {
                pages.add(page.toString());
                page.setLength(0);

                continue;
            }

            if (page.length() > 0)
            {
                page.append('\n');
            }

            page.append(line);
        }

        pages.add(page.toString());

        return pages;
    }

    @Override
    protected Clip create()
    {
        return new PovGuiClip();
    }

    @Override
    public void fromData(BaseType data)
    {
        /* The first version's names for the channels every screen has, and for the creative search */
        if (data != null && data.isMap())
        {
            MapType map = (MapType) data.asMap().copy();

            rename(map, "opacity", "gui_opacity");
            rename(map, "darkness", "darkness_opacity");
            rename(map, "search", "creative_search");
            data = map;
        }

        super.fromData(data);
        this.migrateSlots();
    }

    private static void rename(MapType map, String from, String to)
    {
        if (map.has(from) && !map.has(to))
        {
            map.put(to, map.get(from));
            map.remove(from);
        }
    }

    /**
     * Move the first version's one-channel-per-slot items into the groups, by the screen's own
     * slot numbers: the inventory's crafting grid, armor and off hand, the crafting table's grid,
     * the beacon's payment, a container's own slots. The player's part was the replay's anyway.
     */
    private void migrateSlots()
    {
        List<KeyframeChannel<?>> legacy = channelsOf(this.slots);

        if (legacy.isEmpty())
        {
            return;
        }

        String type = this.screen.get();

        switch (type)
        {
            case "inventory" ->
            {
                this.moveGroup(this.crafting2x2, 0, 5);
                this.moveSingle(5, this.armorHead);
                this.moveSingle(6, this.armorChest);
                this.moveSingle(7, this.armorLegs);
                this.moveSingle(8, this.armorFeet);
                this.moveSingle(45, this.offhand);
            }
            case "crafting" -> this.moveGroup(this.crafting3x3, 0, 10);
            case "beacon" -> this.moveSingle(0, this.payment);
            default ->
            {
                int size = containerSize(type);

                if (size > 0)
                {
                    this.moveGroup(this.containerSlots, 0, size);
                }
            }
        }

        for (KeyframeChannel<?> channel : legacy)
        {
            this.slots.remove(channel);
        }
    }

    private void moveSingle(int slot, KeyframeChannel<ItemStack> target)
    {
        KeyframeChannel<ItemStack> channel = this.slots.slot(slot, false);

        if (channel != null && !channel.isEmpty() && target.isEmpty())
        {
            target.copyOver(channel, 0);
        }
    }

    private void moveGroup(KeyframeChannel<PovSlotSet> target, int from, int size)
    {
        if (!target.isEmpty())
        {
            return;
        }

        TreeSet<Float> ticks = new TreeSet<>();

        /* From the start: the first version keyed a slot only when it changed, so before that it was empty */
        ticks.add(0F);

        for (int i = 0; i < size; i++)
        {
            KeyframeChannel<ItemStack> channel = this.slots.slot(from + i, false);

            if (channel != null)
            {
                for (Keyframe<ItemStack> keyframe : channel.getKeyframes())
                {
                    ticks.add(keyframe.getTick());
                }
            }
        }

        PovSlotSet last = null;

        for (float tick : ticks)
        {
            List<ItemStack> items = new ArrayList<>(size);

            for (int i = 0; i < size; i++)
            {
                KeyframeChannel<ItemStack> channel = this.slots.slot(from + i, false);
                ItemStack stack = channel == null || channel.isEmpty() || tick < channel.getKeyframes().get(0).getTick() ? ItemStack.EMPTY : channel.interpolate(tick);

                items.add(stack == null ? ItemStack.EMPTY : stack);
            }

            PovSlotSet set = new PovSlotSet(items);

            if (!set.equals(last))
            {
                target.insert(tick, set);
                last = set;
            }
        }
    }

    /**
     * The first version's slots, one item channel per slot of the screen by the slot's number in it.
     * Read so the clips made then keep their items; nothing writes them any more.
     */
    public static class Slots extends ValueGroup
    {
        public Slots(String id)
        {
            super(id);
        }

        public static String key(int slot)
        {
            return "slot_" + slot;
        }

        @SuppressWarnings("unchecked")
        public KeyframeChannel<ItemStack> slot(int slot, boolean create)
        {
            BaseValue value = this.get(key(slot));

            if (value == null && create)
            {
                value = channel(this, key(slot), KeyframeFactories.ITEM_STACK);
            }

            return (KeyframeChannel<ItemStack>) value;
        }

        @Override
        protected boolean canPersist(BaseValue value)
        {
            return !(value instanceof KeyframeChannel<?> channel) || !channel.isEmpty();
        }

        @Override
        public void copy(BaseValueGroup group)
        {
            for (BaseValue value : group.getAll())
            {
                if (this.get(value.getId()) == null && value.getId().startsWith("slot_"))
                {
                    channel(this, value.getId(), KeyframeFactories.ITEM_STACK);
                }
            }

            super.copy(group);
        }

        @Override
        public void fromData(BaseType data)
        {
            if (data.isMap())
            {
                for (String key : data.asMap().keys())
                {
                    if (this.get(key) == null && key.startsWith("slot_"))
                    {
                        channel(this, key, KeyframeFactories.ITEM_STACK);
                    }
                }
            }

            super.fromData(data);
        }
    }
}
