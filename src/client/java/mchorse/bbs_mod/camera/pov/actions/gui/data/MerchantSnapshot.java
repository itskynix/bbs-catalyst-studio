/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.registry.Registries
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.village.TradeOffer
 *  net.minecraft.village.TradeOfferList
 */
package mchorse.bbs_mod.camera.pov.actions.gui.data;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

public final class MerchantSnapshot {
    private static final String[] PROFESSIONS = new String[]{"None", "Armorer", "Butcher", "Cartographer", "Cleric", "Farmer", "Fisherman", "Fletcher", "Leatherworker", "Librarian", "Mason", "Nitwit", "Shepherd", "Toolsmith", "Weaponsmith"};
    private static final String[] LEVELS = new String[]{"Novice", "Apprentice", "Journeyman", "Expert", "Master"};
    public final String offers;
    public final int profession;
    public final int level;
    public final int experience;
    public final int selectedOffer;
    public final int scrollOffset;
    public final String title;
    public final boolean canLevel;

    public MerchantSnapshot(String offers, int profession, int level, int experience, int selectedOffer, int scrollOffset, String title, boolean canLevel) {
        this.offers = offers == null ? "" : offers;
        this.profession = Math.max(0, Math.min(PROFESSIONS.length - 1, profession));
        this.level = Math.max(1, Math.min(5, level));
        this.experience = Math.max(0, experience);
        this.selectedOffer = Math.max(0, selectedOffer);
        this.scrollOffset = Math.max(0, scrollOffset);
        this.title = title == null ? "" : title;
        this.canLevel = canLevel;
    }

    public static MerchantSnapshot fromOffers(TradeOfferList tradeOffers, int merchantProfession, int merchantLevel, int merchantXp, int selectedIndex, int scrollIndex, String merchantTitle, boolean isLeveledMerchant) {
        String packed = MerchantSnapshot.pack(tradeOffers);
        int profession = Math.max(0, Math.min(PROFESSIONS.length - 1, merchantProfession));
        int level = Math.max(1, Math.min(5, merchantLevel));
        String title = merchantTitle == null || merchantTitle.isEmpty() ? MerchantSnapshot.getDefaultTitle(profession, level, isLeveledMerchant) : merchantTitle;
        return new MerchantSnapshot(packed, profession, level, merchantXp, selectedIndex, scrollIndex, title, isLeveledMerchant);
    }

    public static String getProfessionName(int profIndex) {
        if (profIndex >= 0 && profIndex < PROFESSIONS.length) {
            return PROFESSIONS[profIndex];
        }
        return "Villager";
    }

    public static String getLevelName(int lvl) {
        int idx = Math.max(1, Math.min(5, lvl)) - 1;
        return LEVELS[idx];
    }

    public static Text getProfessionText(int profIndex) {
        String key = switch (profIndex) {
            case 1 -> "entity.minecraft.villager.armorer";
            case 2 -> "entity.minecraft.villager.butcher";
            case 3 -> "entity.minecraft.villager.cartographer";
            case 4 -> "entity.minecraft.villager.cleric";
            case 5 -> "entity.minecraft.villager.farmer";
            case 6 -> "entity.minecraft.villager.fisherman";
            case 7 -> "entity.minecraft.villager.fletcher";
            case 8 -> "entity.minecraft.villager.leatherworker";
            case 9 -> "entity.minecraft.villager.librarian";
            case 10 -> "entity.minecraft.villager.mason";
            case 11 -> "entity.minecraft.villager.nitwit";
            case 12 -> "entity.minecraft.villager.shepherd";
            case 13 -> "entity.minecraft.villager.toolsmith";
            case 14 -> "entity.minecraft.villager.weaponsmith";
            default -> "entity.minecraft.villager.none";
        };
        return Text.translatable((String)key);
    }

    public static Text getLevelText(int lvl) {
        int clamped = Math.max(1, Math.min(5, lvl));
        return Text.translatable((String)("merchant.level." + clamped));
    }

    public static Text getTitleText(String baseTitle, int profIndex, int lvl, boolean isLeveled) {
        if (!(baseTitle == null || baseTitle.isEmpty() || "Merchant".equalsIgnoreCase(baseTitle) || "Villager".equalsIgnoreCase(baseTitle) || "merchant.title".equalsIgnoreCase(baseTitle))) {
            return Text.literal((String)baseTitle);
        }
        Text profText = MerchantSnapshot.getProfessionText(profIndex);
        if (!isLeveled || profIndex == 0 || profIndex == 11) {
            return profText;
        }
        Text lvlText = MerchantSnapshot.getLevelText(lvl);
        return Text.translatable((String)"merchant.title", (Object[])new Object[]{profText, lvlText});
    }

    public static String formatTitle(String baseTitle, int profIndex, int lvl, boolean isLeveled) {
        return MerchantSnapshot.getTitleText(baseTitle, profIndex, lvl, isLeveled).getString();
    }

    public static String getDefaultTitle(int profIndex, int lvl, boolean isLeveled) {
        return MerchantSnapshot.formatTitle("", profIndex, lvl, isLeveled);
    }

    public static String pack(TradeOfferList tradeOffers) {
        if (tradeOffers == null || tradeOffers.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tradeOffers.size(); ++i) {
            if (i > 0) {
                sb.append(';');
            }
            TradeOffer offer = (TradeOffer)tradeOffers.get(i);
            MerchantSnapshot.packStack(sb, offer.getOriginalFirstBuyItem());
            sb.append('|');
            MerchantSnapshot.packStack(sb, offer.getSecondBuyItem());
            sb.append('|');
            MerchantSnapshot.packStack(sb, offer.getSellItem());
            sb.append('|').append(offer.getUses());
            sb.append('|').append(offer.getMaxUses());
            sb.append('|').append(offer.getSpecialPrice());
            sb.append('|').append(offer.isDisabled() ? 1 : 0);
        }
        return sb.toString();
    }

    private static void packStack(StringBuilder sb, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            sb.append("empty");
            return;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        sb.append(id != null ? id.toString() : "minecraft:air").append('*').append(stack.getCount());
    }

    public static List<ParsedOffer> parse(String packed) {
        String[] rows;
        ArrayList<ParsedOffer> list = new ArrayList<ParsedOffer>();
        if (packed == null || packed.isEmpty()) {
            return list;
        }
        for (String row : rows = packed.split(";", -1)) {
            String[] parts;
            if (row.isEmpty() || (parts = row.split("\\|", -1)).length < 7) continue;
            ItemStack buy1 = MerchantSnapshot.parseStack(parts[0]);
            ItemStack buy2 = MerchantSnapshot.parseStack(parts[1]);
            ItemStack sell = MerchantSnapshot.parseStack(parts[2]);
            int uses = MerchantSnapshot.parseInt(parts[3], 0);
            int maxUses = MerchantSnapshot.parseInt(parts[4], 12);
            int specialPrice = MerchantSnapshot.parseInt(parts[5], 0);
            boolean disabled = MerchantSnapshot.parseInt(parts[6], 0) == 1 || uses >= maxUses;
            list.add(new ParsedOffer(buy1, buy2, sell, uses, maxUses, specialPrice, disabled));
        }
        return list;
    }

    private static ItemStack parseStack(String part) {
        if (part == null || part.isEmpty() || "empty".equals(part)) {
            return ItemStack.EMPTY;
        }
        int star = part.indexOf(42);
        String itemId = star >= 0 ? part.substring(0, star) : part;
        int count = star >= 0 ? MerchantSnapshot.parseInt(part.substring(star + 1), 1) : 1;
        try {
            Identifier id = Identifier.tryParse((String)itemId);
            if (id != null && Registries.ITEM.containsId(id)) {
                Item item = (Item)Registries.ITEM.get(id);
                return new ItemStack((ItemConvertible)item, Math.max(1, count));
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return ItemStack.EMPTY;
    }

    private static int parseInt(String str, int fallback) {
        if (str == null || str.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(str.trim());
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record ParsedOffer(ItemStack buy1, ItemStack buy2, ItemStack sell, int uses, int maxUses, int specialPrice, boolean disabled) {
    }
}

