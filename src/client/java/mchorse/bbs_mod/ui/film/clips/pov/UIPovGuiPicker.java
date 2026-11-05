package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovGuiClip;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The interface clip's screen picker: every screen as a cell of the grid, with a picture of the
 * screen, the block (or item) it opens from, its name and the group it is filed under, and a search
 * over the names and ids.
 */
public class UIPovGuiPicker extends UIPovGridPicker<String>
{
    private final Consumer<String> callback;
    private final String current;

    public UIPovGuiPicker(String current, Consumer<String> callback)
    {
        super(L10n.lang("bbs.ui.film.pov_clips.gui.picker"));

        this.current = current;
        this.callback = callback;
        this.refresh();
    }

    public static IKey name(String type)
    {
        return L10n.lang("bbs.ui.film.pov_clips.gui.screens." + type);
    }

    private static IKey groupName(String type)
    {
        return L10n.lang("bbs.ui.film.pov_clips.gui.groups." + PovGuiClip.groupOf(type));
    }

    private static int groupColor(String type)
    {
        return switch (PovGuiClip.groupOf(type))
        {
            case "player" -> 0x3d8eff;
            case "crafting" -> 0xe08a2e;
            case "smelting" -> 0xd04040;
            case "utility" -> 0xaa55ff;
            case "storage" -> 0x55aa55;
            case "book" -> 0xb08850;
            default -> 0x888888;
        };
    }

    /** The block or item a screen opens from */
    private static Item icon(String type)
    {
        return switch (type)
        {
            case "inventory" -> Items.CHEST;
            case "creative" -> Items.COMPASS;
            case "crafting" -> Items.CRAFTING_TABLE;
            case "anvil" -> Items.ANVIL;
            case "smithing" -> Items.SMITHING_TABLE;
            case "grindstone" -> Items.GRINDSTONE;
            case "stonecutter" -> Items.STONECUTTER;
            case "cartography" -> Items.CARTOGRAPHY_TABLE;
            case "loom" -> Items.LOOM;
            case "furnace" -> Items.FURNACE;
            case "blast_furnace" -> Items.BLAST_FURNACE;
            case "smoker" -> Items.SMOKER;
            case "enchanting" -> Items.ENCHANTING_TABLE;
            case "brewing" -> Items.BREWING_STAND;
            case "beacon" -> Items.BEACON;
            case "chest", "large_chest" -> Items.CHEST;
            case "barrel" -> Items.BARREL;
            case "shulker_box" -> Items.SHULKER_BOX;
            case "hopper" -> Items.HOPPER;
            case "dispenser" -> Items.DISPENSER;
            case "book" -> Items.WRITABLE_BOOK;
            case "death" -> Items.SKELETON_SKULL;
            case "sleep" -> Items.RED_BED;
            default -> Items.OAK_DOOR;
        };
    }

    /** The screen's texture; null for the menus, which are drawn */
    private static Identifier texture(String type)
    {
        String path = switch (type)
        {
            case "inventory" -> "container/inventory";
            case "creative" -> "container/creative_inventory/tab_items";
            case "crafting" -> "container/crafting_table";
            case "cartography" -> "container/cartography_table";
            case "enchanting" -> "container/enchanting_table";
            case "brewing" -> "container/brewing_stand";
            case "chest", "large_chest", "barrel" -> "container/generic_54";
            case "book" -> "book";
            case "game_menu", "death", "sleep" -> null;
            default -> "container/" + type;
        };

        return path == null ? null : Ids.of("minecraft", "textures/gui/" + path + ".png");
    }

    /** The part of its texture a screen is: {width, height} */
    private static int[] region(String type)
    {
        return switch (type)
        {
            case "creative" -> new int[] {195, 136};
            case "beacon" -> new int[] {230, 219};
            case "chest", "barrel" -> new int[] {176, 168};
            case "large_chest" -> new int[] {176, 222};
            case "hopper" -> new int[] {176, 133};
            case "book" -> new int[] {192, 192};
            default -> new int[] {176, 166};
        };
    }

    @Override
    protected List<String> getEntries()
    {
        return PovGuiClip.TYPES;
    }

    @Override
    protected boolean matches(String type, String needle)
    {
        return type.contains(needle) || name(type).get().toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    protected void pick(String type)
    {
        this.callback.accept(type);
    }

    @Override
    protected boolean isCurrent(String type)
    {
        return type.equals(this.current);
    }

    @Override
    protected String describe(String type)
    {
        return "minecraft:" + type;
    }

    @Override
    protected void renderCell(UIContext context, String type, int x, int y, int w, int h, boolean hovered)
    {
        FontRenderer font = context.batcher.getFont();

        this.renderPreview(context, type, x + 4, y + 4, w - 8, h - 30);

        /* The block, the name and the group under the picture */
        int textY = y + h - 23;

        FormUtilsClient.getProvider().setUI(true);
        context.batcher.getContext().drawItem(new ItemStack(icon(type)), x + 4, textY + 2);
        FormUtilsClient.getProvider().setUI(false);

        context.batcher.textShadow(font.limitToWidth(name(type).get(), w - 28), x + 23, textY, Colors.WHITE);
        context.batcher.textShadow(font.limitToWidth(groupName(type).get(), w - 28), x + 23, textY + 10, Colors.A100 | groupColor(type));
    }

    /** The screen's own texture fitted into the cell, or for a menu a little picture of one */
    private void renderPreview(UIContext context, String type, int x, int y, int w, int h)
    {
        Identifier texture = texture(type);

        if (texture == null)
        {
            int top = switch (type)
            {
                case "death" -> 0xff501010;
                case "sleep" -> 0xff101020;
                default -> 0xff202020;
            };
            int bottom = switch (type)
            {
                case "death" -> 0xff803030;
                case "sleep" -> 0xff000000;
                default -> 0xff101010;
            };

            context.batcher.gradientVBox(x, y, x + w, y + h, top, bottom);

            for (int i = 0; i < (type.equals("game_menu") ? 3 : 1); i++)
            {
                int bw = Math.min(w - 8, 60);
                int by = y + h / 2 - 8 + i * 9 - (type.equals("game_menu") ? 9 : 0);

                context.batcher.box(x + (w - bw) / 2, by, x + (w + bw) / 2, by + 7, 0xff6b6b6b);
            }

            return;
        }

        int[] region = region(type);
        float scale = Math.min(w / (float) region[0], h / (float) region[1]);
        int pw = Math.max(1, (int) (region[0] * scale));
        int ph = Math.max(1, (int) (region[1] * scale));

        context.batcher.getContext().drawTexture(texture, x + (w - pw) / 2, y + (h - ph) / 2, pw, ph, 0F, 0F, region[0], region[1], 256, 256);
    }
}
