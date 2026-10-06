/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.impl.itemgroup.FabricItemGroup
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.item.ItemGroup
 *  net.minecraft.item.ItemGroup$Row
 *  net.minecraft.item.ItemGroup$Type
 *  net.minecraft.item.ItemGroups
 *  net.minecraft.item.Items
 *  net.minecraft.text.TextContent
 *  net.minecraft.text.TranslatableTextContent
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.gui;

import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.impl.itemgroup.FabricItemGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.MathHelper;

public final class CreativeInventoryTabs {
    private static Boolean isForgeCache = null;

    private CreativeInventoryTabs() {
    }

    public static boolean isForge() {
        if (isForgeCache != null) {
            return isForgeCache;
        }
        if (Boolean.getBoolean("bbs_pov.force_forge")) {
            isForgeCache = true;
            return true;
        }
        try {
            if (FabricLoader.getInstance().isModLoaded("connector") || FabricLoader.getInstance().isModLoaded("connectormod") || FabricLoader.getInstance().isModLoaded("forge") || FabricLoader.getInstance().isModLoaded("neoforge")) {
                isForgeCache = true;
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            isForgeCache = true;
            return true;
        }
        catch (Throwable throwable) {
            try {
                Class.forName("net.minecraftforge.fml.loading.FMLLoader");
                isForgeCache = true;
                return true;
            }
            catch (Throwable throwable2) {
                isForgeCache = false;
                return false;
            }
        }
    }

    public static boolean isCommonGroup(ItemGroup group) {
        if (group == null) {
            return false;
        }
        ItemGroup.Type type = group.getType();
        return type == ItemGroup.Type.SEARCH || type == ItemGroup.Type.INVENTORY || type == ItemGroup.Type.HOTBAR;
    }

    public static boolean isVanillaGroup(ItemGroup group) {
        TranslatableTextContent trans;
        String key;
        TextContent textContent;
        if (group == null) {
            return false;
        }
        if (CreativeInventoryTabs.isCommonGroup(group)) {
            return true;
        }
        return group.getDisplayName() != null && (textContent = group.getDisplayName().getContent()) instanceof TranslatableTextContent && (key = (trans = (TranslatableTextContent)textContent).getKey()) != null && key.startsWith("itemGroup.") && !key.contains(":") && !key.startsWith("itemGroup.bbs");
    }

    public static List<ItemGroup> groups() {
        return ItemGroups.getGroupsToDisplay().stream().filter(group -> !group.getIcon().isOf(Items.COMMAND_BLOCK)).toList();
    }

    private static int getFabricPage(ItemGroup group) {
        Object res;
        Method m2;
        if (group == null) {
            return 0;
        }
        if (group instanceof FabricItemGroup) {
            FabricItemGroup fig = (FabricItemGroup)group;
            return fig.getPage();
        }
        try {
            m2 = group.getClass().getMethod("getPage", new Class[0]);
            res = m2.invoke(group, new Object[0]);
            if (res instanceof Integer) {
                Integer i = (Integer)res;
                return i;
            }
        }
        catch (Throwable ignored) {
            // empty catch block
        }
        try {
            m2 = group.getClass().getMethod("fabric_getPage", new Class[0]);
            res = m2.invoke(group, new Object[0]);
            if (res instanceof Integer) {
                Integer i = (Integer)res;
                return i;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 0;
    }

    public static int page(ItemGroup group, int currentPage) {
        if (CreativeInventoryTabs.isForge()) {
            if (CreativeInventoryTabs.isCommonGroup(group)) {
                return currentPage;
            }
            if (CreativeInventoryTabs.isVanillaGroup(group)) {
                return 0;
            }
            List<ItemGroup> moddedGroups = CreativeInventoryTabs.groups().stream().filter(g -> !CreativeInventoryTabs.isVanillaGroup(g) && g.getType() != ItemGroup.Type.SEARCH && g.getType() != ItemGroup.Type.INVENTORY).toList();
            int idx = moddedGroups.indexOf(group);
            return idx >= 0 ? 1 + idx / 10 : 0;
        }
        if (CreativeInventoryTabs.isCommonGroup(group)) {
            return currentPage;
        }
        return CreativeInventoryTabs.getFabricPage(group);
    }

    public static boolean visibleOnPage(ItemGroup group, int page) {
        if (CreativeInventoryTabs.isForge()) {
            if (CreativeInventoryTabs.isCommonGroup(group)) {
                return true;
            }
            if (page == 0) {
                return CreativeInventoryTabs.isVanillaGroup(group);
            }
            return CreativeInventoryTabs.page(group, page) == page;
        }
        return CreativeInventoryTabs.isCommonGroup(group) || CreativeInventoryTabs.page(group, page) == page;
    }

    public static int maxPage() {
        if (CreativeInventoryTabs.isForge()) {
            List<ItemGroup> moddedGroups = CreativeInventoryTabs.groups().stream().filter(g -> !CreativeInventoryTabs.isVanillaGroup(g) && g.getType() != ItemGroup.Type.SEARCH && g.getType() != ItemGroup.Type.INVENTORY).toList();
            return moddedGroups.isEmpty() ? 0 : 1 + (moddedGroups.size() - 1) / 10;
        }
        int max = 0;
        int nonCommon = 0;
        for (ItemGroup group : CreativeInventoryTabs.groups()) {
            if (CreativeInventoryTabs.isCommonGroup(group)) continue;
            max = Math.max(max, CreativeInventoryTabs.page(group, 0));
        }
        for (ItemGroup group : ItemGroups.getGroupsToDisplay()) {
            if (CreativeInventoryTabs.isCommonGroup(group)) continue;
            ++nonCommon;
        }
        int countedPages = Math.max(0, (nonCommon + 9) / 10 - 1);
        return Math.max(max, countedPages);
    }

    public static boolean isSpecial(ItemGroup group, int page) {
        if (CreativeInventoryTabs.isForge()) {
            return CreativeInventoryTabs.isCommonGroup(group);
        }
        return group != null && group.isSpecial();
    }

    public static boolean isTop(ItemGroup group, int page) {
        if (CreativeInventoryTabs.isForge()) {
            if (CreativeInventoryTabs.isCommonGroup(group)) {
                return group.getType() == ItemGroup.Type.SEARCH;
            }
            if (page == 0) {
                List<ItemGroup> vanillaGroups = CreativeInventoryTabs.groups().stream().filter(CreativeInventoryTabs::isVanillaGroup).filter(g -> !CreativeInventoryTabs.isCommonGroup(g)).toList();
                int idx = vanillaGroups.indexOf(group);
                if (idx >= 0) {
                    return idx < 5;
                }
            } else {
                List<ItemGroup> moddedGroups = CreativeInventoryTabs.groups().stream().filter(g -> !CreativeInventoryTabs.isVanillaGroup(g) && !CreativeInventoryTabs.isCommonGroup(g)).toList();
                int pageStart = (page - 1) * 10;
                int idx = moddedGroups.indexOf(group) - pageStart;
                if (idx >= 0 && idx < 10) {
                    return idx < 5;
                }
            }
        }
        return group != null && group.getRow() == ItemGroup.Row.TOP;
    }

    public static int getColumn(ItemGroup group, int page) {
        if (CreativeInventoryTabs.isForge()) {
            if (CreativeInventoryTabs.isCommonGroup(group)) {
                return 6;
            }
            if (page == 0) {
                List<ItemGroup> vanillaGroups = CreativeInventoryTabs.groups().stream().filter(CreativeInventoryTabs::isVanillaGroup).filter(g -> !CreativeInventoryTabs.isCommonGroup(g)).toList();
                int idx = vanillaGroups.indexOf(group);
                if (idx >= 0) {
                    return idx % 5;
                }
            } else {
                List<ItemGroup> moddedGroups = CreativeInventoryTabs.groups().stream().filter(g -> !CreativeInventoryTabs.isVanillaGroup(g) && !CreativeInventoryTabs.isCommonGroup(g)).toList();
                int pageStart = (page - 1) * 10;
                int idx = moddedGroups.indexOf(group) - pageStart;
                if (idx >= 0 && idx < 10) {
                    return idx % 5;
                }
            }
        }
        return group == null ? 0 : MathHelper.clamp((int)group.getColumn(), (int)0, (int)6);
    }
}

