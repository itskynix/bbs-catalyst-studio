/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 */
package mchorse.bbs_mod.camera.pov.actions.bossbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class BossBarTypeEntry {
    private static final Map<String, BossBarTypeEntry> REGISTRY = new LinkedHashMap<String, BossBarTypeEntry>();
    private static final List<BossBarTypeEntry> ALL = new ArrayList<BossBarTypeEntry>();
    public final String id;
    public final String name;
    public final String defaultTitle;
    public final String defaultColor;
    public final String defaultStyle;
    public static final BossBarTypeEntry DRAGON = BossBarTypeEntry.register("dragon", "Ender Dragon", "Ender Dragon", "pink", "progress");
    public static final BossBarTypeEntry WITHER = BossBarTypeEntry.register("wither", "Wither", "Wither", "purple", "progress");
    public static final BossBarTypeEntry RAID = BossBarTypeEntry.register("raid", "Raid", "Raid", "red", "notched_10");

    private BossBarTypeEntry(String id, String name, String defaultTitle, String defaultColor, String defaultStyle) {
        this.id = id;
        this.name = name;
        this.defaultTitle = defaultTitle;
        this.defaultColor = defaultColor;
        this.defaultStyle = defaultStyle;
    }

    private static BossBarTypeEntry register(String id, String name, String defaultTitle, String defaultColor, String defaultStyle) {
        BossBarTypeEntry entry = new BossBarTypeEntry(id, name, defaultTitle, defaultColor, defaultStyle);
        REGISTRY.put(id, entry);
        ALL.add(entry);
        return entry;
    }

    public static List<BossBarTypeEntry> getAll() {
        return Collections.unmodifiableList(ALL);
    }

    public static BossBarTypeEntry findById(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    public static BossBarTypeEntry next(String currentId) {
        BossBarTypeEntry current = BossBarTypeEntry.findById(currentId);
        int index = current == null ? -1 : ALL.indexOf(current);
        return ALL.get((index + 1) % ALL.size());
    }

    public ItemStack getIcon() {
        return switch (this.id) {
            case "wither" -> new ItemStack((ItemConvertible)Items.WITHER_SKELETON_SKULL);
            case "raid" -> new ItemStack((ItemConvertible)Items.TOTEM_OF_UNDYING);
            default -> new ItemStack((ItemConvertible)Items.DRAGON_HEAD);
        };
    }
}

