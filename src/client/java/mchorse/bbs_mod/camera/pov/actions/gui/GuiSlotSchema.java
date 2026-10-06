/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.actions.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuiSlotSchema {
    private static final Map<String, GuiSlotSchema> SCHEMAS = new LinkedHashMap<String, GuiSlotSchema>();
    public final String guiId;
    public final List<Slot> slots;
    public final boolean groupedSlots;
    public final int groupColumns;
    public final boolean playerInventory;

    private GuiSlotSchema(String guiId, List<Slot> slots, boolean groupedSlots, int groupColumns, boolean playerInventory) {
        this.guiId = guiId;
        this.slots = Collections.unmodifiableList(slots);
        this.groupedSlots = groupedSlots;
        this.groupColumns = groupColumns;
        this.playerInventory = playerInventory;
    }

    public static GuiSlotSchema get(String guiId) {
        return SCHEMAS.getOrDefault(guiId, SCHEMAS.get("inventory"));
    }

    public static List<GuiSlotSchema> getAll() {
        return List.copyOf(SCHEMAS.values());
    }

    public boolean isCraftingSlot(Slot slot) {
        return !(!"inventory".equals(this.guiId) && !"crafting_table".equals(this.guiId) || !"craft_result".equals(slot.id()) && !slot.id().startsWith("craft_"));
    }

    public List<Slot> getCraftingSlots() {
        ArrayList<Slot> result = new ArrayList<Slot>();
        for (Slot slot : this.slots) {
            if (!this.isCraftingSlot(slot)) continue;
            result.add(slot);
        }
        return result;
    }

    public boolean hasCraftingSlots() {
        return !this.getCraftingSlots().isEmpty();
    }

    public boolean isChestSlot(Slot slot) {
        return slot != null && slot.id().startsWith("chest_");
    }

    public boolean isEquipmentSlot(Slot slot) {
        return slot != null && ("saddle".equals(slot.id()) || "armor".equals(slot.id()));
    }

    public List<Slot> getGroupedSlots() {
        if (!this.groupedSlots) {
            return List.of();
        }
        ArrayList<Slot> grouped = new ArrayList<Slot>();
        for (Slot slot : this.slots) {
            if (!this.isChestSlot(slot) && !slot.id().startsWith("container_")) continue;
            grouped.add(slot);
        }
        return grouped.isEmpty() ? this.slots : grouped;
    }

    public Slot slotByHandlerIndex(int handlerIndex) {
        for (Slot slot : this.slots) {
            if (slot.handlerIndex() != handlerIndex) continue;
            return slot;
        }
        return null;
    }

    public static int playerInventoryStart(String guiId, int totalSlots) {
        return "inventory".equals(guiId) ? 9 : Math.max(0, totalSlots - 36);
    }

    private static void register(String guiId, boolean grouped, int columns, boolean playerInventory, Slot ... slots) {
        SCHEMAS.put(guiId, new GuiSlotSchema(guiId, new ArrayList<Slot>(List.of(slots)), grouped, columns, playerInventory));
    }

    private static Slot slot(String id, String label, int x, int y, int handlerIndex) {
        return new Slot(id, label, x, y, handlerIndex);
    }

    private static Slot[] grid(String prefix, String label, int count, int columns, int x, int y, int firstHandler) {
        Slot[] slots = new Slot[count];
        for (int i = 0; i < count; ++i) {
            slots[i] = GuiSlotSchema.slot(prefix + i, label, x + i % columns * 18, y + i / columns * 18, firstHandler + i);
        }
        return slots;
    }

    private static Slot[] concat(Slot[] first, Slot[] second) {
        Slot[] result = new Slot[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    static {
        GuiSlotSchema.register("inventory", false, 1, true, GuiSlotSchema.slot("craft_result", "Craft Result", 154, 28, 0), GuiSlotSchema.slot("craft_0", "Crafting", 98, 18, 1), GuiSlotSchema.slot("craft_1", "Crafting", 116, 18, 2), GuiSlotSchema.slot("craft_2", "Crafting", 98, 36, 3), GuiSlotSchema.slot("craft_3", "Crafting", 116, 36, 4), GuiSlotSchema.slot("armor_head", "Head Armor", 8, 8, 5), GuiSlotSchema.slot("armor_chest", "Chest Armor", 8, 26, 6), GuiSlotSchema.slot("armor_legs", "Leg Armor", 8, 44, 7), GuiSlotSchema.slot("armor_feet", "Feet Armor", 8, 62, 8), GuiSlotSchema.slot("offhand", "Offhand", 77, 62, 45));
        GuiSlotSchema.register("creative_inventory", false, 1, true, GuiSlotSchema.slot("armor_head", "Head Armor", 54, 6, 5), GuiSlotSchema.slot("armor_chest", "Chest Armor", 54, 33, 6), GuiSlotSchema.slot("armor_legs", "Leg Armor", 108, 6, 7), GuiSlotSchema.slot("armor_feet", "Feet Armor", 108, 33, 8), GuiSlotSchema.slot("offhand", "Offhand", 35, 20, 45));
        GuiSlotSchema.register("crafting_table", false, 1, true, GuiSlotSchema.concat(new Slot[]{GuiSlotSchema.slot("craft_result", "Craft Result", 124, 35, 0)}, GuiSlotSchema.grid("craft_", "Crafting", 9, 3, 30, 17, 1)));
        GuiSlotSchema.register("anvil", false, 1, true, GuiSlotSchema.slot("input_0", "Input", 27, 47, 0), GuiSlotSchema.slot("input_1", "Input", 76, 47, 1), GuiSlotSchema.slot("result", "Result", 134, 47, 2));
        GuiSlotSchema.register("smithing_table", false, 1, true, GuiSlotSchema.slot("template", "Template", 8, 48, 0), GuiSlotSchema.slot("base", "Base", 26, 48, 1), GuiSlotSchema.slot("addition", "Addition", 44, 48, 2), GuiSlotSchema.slot("result", "Result", 98, 48, 3));
        GuiSlotSchema.register("grindstone", false, 1, true, GuiSlotSchema.slot("top", "Top Input", 49, 19, 0), GuiSlotSchema.slot("bottom", "Bottom Input", 49, 40, 1), GuiSlotSchema.slot("result", "Result", 129, 34, 2));
        GuiSlotSchema.register("stonecutter", false, 1, true, GuiSlotSchema.slot("input", "Input", 20, 33, 0), GuiSlotSchema.slot("result", "Result", 143, 33, 1));
        GuiSlotSchema.register("cartography_table", false, 1, true, GuiSlotSchema.slot("map", "Map", 15, 15, 0), GuiSlotSchema.slot("addition", "Addition", 15, 52, 1), GuiSlotSchema.slot("result", "Result", 145, 39, 2));
        GuiSlotSchema.register("loom", false, 1, true, GuiSlotSchema.slot("banner", "Banner", 13, 26, 0), GuiSlotSchema.slot("dye", "Dye", 33, 26, 1), GuiSlotSchema.slot("pattern", "Pattern", 23, 45, 2), GuiSlotSchema.slot("result", "Result", 143, 57, 3));
        Slot[] furnace = new Slot[]{GuiSlotSchema.slot("input", "Input", 56, 17, 0), GuiSlotSchema.slot("fuel", "Fuel", 56, 53, 1), GuiSlotSchema.slot("result", "Result", 116, 35, 2)};
        GuiSlotSchema.register("furnace", false, 1, true, furnace);
        GuiSlotSchema.register("blast_furnace", false, 1, true, furnace);
        GuiSlotSchema.register("smoker", false, 1, true, furnace);
        GuiSlotSchema.register("brewing_stand", false, 1, true, GuiSlotSchema.slot("potion_0", "Potion", 56, 51, 0), GuiSlotSchema.slot("potion_1", "Potion", 79, 58, 1), GuiSlotSchema.slot("potion_2", "Potion", 102, 51, 2), GuiSlotSchema.slot("ingredient", "Ingredient", 79, 17, 3), GuiSlotSchema.slot("fuel", "Fuel", 17, 17, 4));
        GuiSlotSchema.register("chest", true, 9, true, GuiSlotSchema.grid("container_", "Chest Slot", 27, 9, 8, 18, 0));
        GuiSlotSchema.register("barrel", true, 9, true, GuiSlotSchema.grid("container_", "Barrel Slot", 27, 9, 8, 18, 0));
        GuiSlotSchema.register("ender_chest", true, 9, true, GuiSlotSchema.grid("container_", "Ender Chest Slot", 27, 9, 8, 18, 0));
        GuiSlotSchema.register("shulker_box", true, 9, true, GuiSlotSchema.grid("container_", "Shulker Slot", 27, 9, 8, 18, 0));
        GuiSlotSchema.register("large_chest", true, 9, true, GuiSlotSchema.grid("container_", "Chest Slot", 54, 9, 8, 18, 0));
        GuiSlotSchema.register("hopper", true, 5, true, GuiSlotSchema.grid("container_", "Hopper Slot", 5, 5, 44, 20, 0));
        GuiSlotSchema.register("dispenser", true, 3, true, GuiSlotSchema.grid("container_", "Dispenser Slot", 9, 3, 62, 17, 0));
        GuiSlotSchema.register("dropper", true, 3, true, GuiSlotSchema.grid("container_", "Dropper Slot", 9, 3, 62, 17, 0));
        GuiSlotSchema.register("crafter", true, 3, true, GuiSlotSchema.grid("container_", "Crafter Slot", 9, 3, 26, 17, 0));
        GuiSlotSchema.register("enchanting_table", false, 1, true, GuiSlotSchema.slot("item", "Item", 15, 47, 0), GuiSlotSchema.slot("lapis", "Lapis", 35, 47, 1));
        GuiSlotSchema.register("villager", false, 1, true, GuiSlotSchema.slot("input_0", "Trade Input", 136, 37, 0), GuiSlotSchema.slot("input_1", "Trade Input", 162, 37, 1), GuiSlotSchema.slot("result", "Trade Result", 220, 37, 2));
        GuiSlotSchema.register("horse", false, 1, true, GuiSlotSchema.slot("saddle", "Saddle", 8, 18, 0), GuiSlotSchema.slot("armor", "Armor", 8, 36, 1));
        GuiSlotSchema.register("donkey", true, 5, true, GuiSlotSchema.concat(new Slot[]{GuiSlotSchema.slot("saddle", "Saddle", 8, 18, 0)}, GuiSlotSchema.grid("chest_", "Donkey Storage", 15, 5, 80, 18, 2)));
        GuiSlotSchema.register("beacon", false, 1, true, GuiSlotSchema.slot("payment", "Payment", 136, 110, 0));
        GuiSlotSchema.register("book", false, 1, false, new Slot[0]);
        GuiSlotSchema.register("gamemode_switcher", false, 1, false, new Slot[0]);
    }

    public record Slot(String id, String label, int x, int y, int handlerIndex) {
    }
}

