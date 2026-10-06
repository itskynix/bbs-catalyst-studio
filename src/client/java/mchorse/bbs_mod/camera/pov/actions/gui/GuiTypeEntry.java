/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GuiTypeEntry {
    private static final Map<String, GuiTypeEntry> REGISTRY = new LinkedHashMap<String, GuiTypeEntry>();
    private static final List<GuiTypeEntry> ALL_ENTRIES = new ArrayList<GuiTypeEntry>();
    public final String id;
    public final String name;
    public final String category;
    public final Identifier texture;
    public final int u;
    public final int v;
    public final int regionWidth;
    public final int regionHeight;
    public final int textureWidth;
    public final int textureHeight;
    public final String title;
    public final String titleKey;
    public final int titleX;
    public final int titleY;
    public final int inventoryTitleX;
    public final int inventoryTitleY;
    public static final GuiTypeEntry INVENTORY = GuiTypeEntry.register("inventory", "Inventory", "Player", "textures/gui/container/inventory.png", 0, 0, 176, 166, 256, 256, "Crafting", "container.crafting", 97, 6);
    public static final GuiTypeEntry CREATIVE = GuiTypeEntry.register("creative_inventory", "Creative Inventory", "Player", "textures/gui/container/creative_inventory/tab_items.png", 0, 0, 195, 136, 256, 256, "", "", 0, 0);
    public static final GuiTypeEntry CRAFTING_TABLE = GuiTypeEntry.register("crafting_table", "Crafting Table", "Crafting", "textures/gui/container/crafting_table.png", 0, 0, 176, 166, 256, 256, "Crafting", "container.crafting", 29, 6);
    public static final GuiTypeEntry ANVIL = GuiTypeEntry.register("anvil", "Anvil", "Crafting", "textures/gui/container/anvil.png", 0, 0, 176, 166, 256, 256, "Repair & Name", "container.repair", 60, 6);
    public static final GuiTypeEntry SMITHING_TABLE = GuiTypeEntry.register("smithing_table", "Smithing Table", "Crafting", "textures/gui/container/smithing.png", 0, 0, 176, 166, 256, 256, "Upgrade Gear", "container.upgrade", 44, 15);
    public static final GuiTypeEntry GRINDSTONE = GuiTypeEntry.register("grindstone", "Grindstone", "Crafting", "textures/gui/container/grindstone.png", 0, 0, 176, 166, 256, 256, "Repair & Disenchant", "container.grindstone_title", 8, 6);
    public static final GuiTypeEntry STONECUTTER = GuiTypeEntry.register("stonecutter", "Stonecutter", "Crafting", "textures/gui/container/stonecutter.png", 0, 0, 176, 166, 256, 256, "Stonecutter", "container.stonecutter", 8, 6);
    public static final GuiTypeEntry CARTOGRAPHY_TABLE = GuiTypeEntry.register("cartography_table", "Cartography Table", "Crafting", "textures/gui/container/cartography_table.png", 0, 0, 176, 166, 256, 256, "Cartography Table", "container.cartography_table", 8, 4);
    public static final GuiTypeEntry LOOM = GuiTypeEntry.register("loom", "Loom", "Crafting", "textures/gui/container/loom.png", 0, 0, 176, 166, 256, 256, "Loom", "container.loom", 8, 6);
    public static final GuiTypeEntry FURNACE = GuiTypeEntry.register("furnace", "Furnace", "Smelting", "textures/gui/container/furnace.png", 0, 0, 176, 166, 256, 256, "Furnace", "container.furnace", -1, 6);
    public static final GuiTypeEntry BLAST_FURNACE = GuiTypeEntry.register("blast_furnace", "Blast Furnace", "Smelting", "textures/gui/container/blast_furnace.png", 0, 0, 176, 166, 256, 256, "Blast Furnace", "container.blast_furnace", -1, 6);
    public static final GuiTypeEntry SMOKER = GuiTypeEntry.register("smoker", "Smoker", "Smelting", "textures/gui/container/smoker.png", 0, 0, 176, 166, 256, 256, "Smoker", "container.smoker", -1, 6);
    public static final GuiTypeEntry BREWING_STAND = GuiTypeEntry.register("brewing_stand", "Brewing Stand", "Brewing", "textures/gui/container/brewing_stand.png", 0, 0, 176, 166, 256, 256, "Brewing Stand", "container.brewing", -1, 6);
    public static final GuiTypeEntry CHEST_SMALL = GuiTypeEntry.register("chest", "Chest (Small 9x3)", "Containers", "textures/gui/container/generic_54.png", 0, 0, 176, 168, 256, 256, "Chest", "container.chest", 8, 6);
    public static final GuiTypeEntry CHEST_LARGE = GuiTypeEntry.register("large_chest", "Chest (Large 9x6)", "Containers", "textures/gui/container/generic_54.png", 0, 0, 176, 222, 256, 256, "Large Chest", "container.chestDouble", 8, 6);
    public static final GuiTypeEntry BARREL = GuiTypeEntry.register("barrel", "Barrel", "Containers", "textures/gui/container/generic_54.png", 0, 0, 176, 168, 256, 256, "Barrel", "container.barrel", 8, 6);
    public static final GuiTypeEntry ENDER_CHEST = GuiTypeEntry.register("ender_chest", "Ender Chest", "Containers", "textures/gui/container/generic_54.png", 0, 0, 176, 168, 256, 256, "Ender Chest", "container.enderchest", 8, 6);
    public static final GuiTypeEntry SHULKER_BOX = GuiTypeEntry.register("shulker_box", "Shulker Box", "Containers", "textures/gui/container/shulker_box.png", 0, 0, 176, 166, 256, 256, "Shulker Box", "container.shulkerBox", 8, 6);
    public static final GuiTypeEntry HOPPER = GuiTypeEntry.register("hopper", "Hopper", "Containers", "textures/gui/container/hopper.png", 0, 0, 176, 133, 256, 256, "Item Hopper", "container.hopper", 8, 6);
    public static final GuiTypeEntry DISPENSER = GuiTypeEntry.register("dispenser", "Dispenser", "Redstone", "textures/gui/container/dispenser.png", 0, 0, 176, 166, 256, 256, "Dispenser", "container.dispenser", -1, 6);
    public static final GuiTypeEntry DROPPER = GuiTypeEntry.register("dropper", "Dropper", "Redstone", "textures/gui/container/dispenser.png", 0, 0, 176, 166, 256, 256, "Dropper", "container.dropper", -1, 6);
    public static final GuiTypeEntry ENCHANTING_TABLE = GuiTypeEntry.register("enchanting_table", "Enchanting Table", "Enchanting", "textures/gui/container/enchanting_table.png", 0, 0, 176, 166, 256, 256, "Enchant", "container.enchant", 8, 5);
    public static final GuiTypeEntry BEACON = GuiTypeEntry.register("beacon", "Beacon", "Special", "textures/gui/container/beacon.png", 0, 0, 230, 219, 256, 256, "", "", 0, 0);
    public static final GuiTypeEntry VILLAGER = GuiTypeEntry.register("villager", "Villager Trading", "Trading", "textures/gui/container/villager.png", 0, 0, 276, 166, 512, 256, "Merchant", "entity.minecraft.villager", 136, 6);
    public static final GuiTypeEntry HORSE = GuiTypeEntry.register("horse", "Horse Inventory", "Entities", "textures/gui/container/horse.png", 0, 0, 176, 166, 256, 256, "Horse", "entity.minecraft.horse", 8, 6);
    public static final GuiTypeEntry DONKEY = GuiTypeEntry.register("donkey", "Donkey Inventory", "Entities", "textures/gui/container/horse.png", 0, 0, 176, 166, 256, 256, "Donkey", "entity.minecraft.donkey", 8, 6);
    public static final int BOOK_SCREEN_Y = 2;
    public static final GuiTypeEntry BOOK = GuiTypeEntry.register("book", "Book", "Special", "textures/gui/book.png", 0, 0, 192, 220, 256, 256, "", "", 0, 0);
    public static final GuiTypeEntry GAMEMODE_SWITCHER = GuiTypeEntry.register("gamemode_switcher", "Gamemode Switcher", "Special", "textures/gui/container/gamemode_switcher.png", 0, 0, 125, 75, 128, 128, "", "", 0, 0);

    public GuiTypeEntry(String id, String name, String category, Identifier texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, String title, String titleKey, int titleX, int titleY) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.title = title;
        this.titleKey = titleKey == null ? "" : titleKey;
        this.titleX = titleX;
        this.titleY = titleY;
        this.inventoryTitleX = GuiTypeEntry.inventoryTitleX(id);
        this.inventoryTitleY = this.inventoryTitleX < 0 ? -1 : regionHeight - 94;
    }

    public Text getTitleText() {
        if (this.titleKey != null && !this.titleKey.isEmpty()) {
            return Text.translatable((String)this.titleKey);
        }
        return this.title != null && !this.title.isEmpty() ? Text.literal((String)this.title) : Text.empty();
    }

    private static int inventoryTitleX(String id) {
        return switch (id) {
            case "inventory", "creative_inventory", "beacon", "book", "gamemode_switcher" -> -1;
            case "villager" -> 107;
            default -> 8;
        };
    }

    public ItemStack getIcon() {
        return switch (this.id) {
            case "inventory" -> new ItemStack((ItemConvertible)Items.CHEST);
            case "creative_inventory" -> new ItemStack((ItemConvertible)Items.COMPASS);
            case "crafting_table" -> new ItemStack((ItemConvertible)Items.CRAFTING_TABLE);
            case "anvil" -> new ItemStack((ItemConvertible)Items.ANVIL);
            case "smithing_table" -> new ItemStack((ItemConvertible)Items.SMITHING_TABLE);
            case "grindstone" -> new ItemStack((ItemConvertible)Items.GRINDSTONE);
            case "stonecutter" -> new ItemStack((ItemConvertible)Items.STONECUTTER);
            case "cartography_table" -> new ItemStack((ItemConvertible)Items.CARTOGRAPHY_TABLE);
            case "loom" -> new ItemStack((ItemConvertible)Items.LOOM);
            case "furnace" -> new ItemStack((ItemConvertible)Items.FURNACE);
            case "blast_furnace" -> new ItemStack((ItemConvertible)Items.BLAST_FURNACE);
            case "smoker" -> new ItemStack((ItemConvertible)Items.SMOKER);
            case "brewing_stand" -> new ItemStack((ItemConvertible)Items.BREWING_STAND);
            case "chest", "large_chest" -> new ItemStack((ItemConvertible)Items.CHEST);
            case "barrel" -> new ItemStack((ItemConvertible)Items.BARREL);
            case "ender_chest" -> new ItemStack((ItemConvertible)Items.ENDER_CHEST);
            case "shulker_box" -> new ItemStack((ItemConvertible)Items.SHULKER_BOX);
            case "hopper" -> new ItemStack((ItemConvertible)Items.HOPPER);
            case "dispenser" -> new ItemStack((ItemConvertible)Items.DISPENSER);
            case "dropper" -> new ItemStack((ItemConvertible)Items.DROPPER);
            case "enchanting_table" -> new ItemStack((ItemConvertible)Items.ENCHANTING_TABLE);
            case "beacon" -> new ItemStack((ItemConvertible)Items.BEACON);
            case "villager" -> new ItemStack((ItemConvertible)Items.EMERALD);
            case "horse" -> new ItemStack((ItemConvertible)Items.SADDLE);
            case "donkey" -> new ItemStack((ItemConvertible)Items.CHEST);
            case "book" -> new ItemStack((ItemConvertible)Items.WRITABLE_BOOK);
            case "gamemode_switcher" -> new ItemStack((ItemConvertible)Items.COMMAND_BLOCK);
            default -> new ItemStack((ItemConvertible)Items.CHEST);
        };
    }

    public static int getCategoryColor(String category) {
        return switch (category == null ? "" : category) {
            case "Player" -> -13928234;
            case "Crafting" -> -2525148;
            case "Smelting" -> -2078682;
            case "Brewing" -> -6735144;
            case "Containers" -> -7577031;
            case "Redstone" -> -2742232;
            case "Enchanting" -> -4708680;
            case "Special" -> -2843885;
            case "Trading" -> -14305204;
            case "Entities" -> -6269924;
            default -> -7829368;
        };
    }

    public static GuiTypeEntry register(String id, String name, String category, String texturePath, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, String title, String titleKey, int titleX, int titleY) {
        Identifier tex = new Identifier("minecraft", texturePath);
        GuiTypeEntry entry = new GuiTypeEntry(id, name, category, tex, u, v, regionWidth, regionHeight, textureWidth, textureHeight, title, titleKey, titleX, titleY);
        REGISTRY.put(id, entry);
        ALL_ENTRIES.add(entry);
        return entry;
    }

    public static GuiTypeEntry register(String id, String name, String category, String texturePath, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, String title, int titleX, int titleY) {
        return GuiTypeEntry.register(id, name, category, texturePath, u, v, regionWidth, regionHeight, textureWidth, textureHeight, title, "", titleX, titleY);
    }

    public static List<GuiTypeEntry> getAll() {
        return Collections.unmodifiableList(ALL_ENTRIES);
    }

    public static GuiTypeEntry findById(String id) {
        if (id == null || id.isBlank()) {
            return INVENTORY;
        }
        GuiTypeEntry entry = REGISTRY.get(id);
        if (entry != null) {
            return entry;
        }
        for (GuiTypeEntry e : ALL_ENTRIES) {
            if (!e.id.equalsIgnoreCase(id) && !e.id.endsWith(":" + id) && !id.endsWith(":" + e.id)) continue;
            return e;
        }
        return INVENTORY;
    }

    public String toString() {
        return this.name + " " + this.id + " " + this.category;
    }
}

