package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The potion effects clip's picker: every effect of the game (and of the mods) as a cell with its
 * icon on the game's effect frame and its name, under three tabs: the beneficial, the harmful and
 * the ones no potion is for (the bad omen, the hero of the village...).
 */
public class UIPovEffectPicker extends UIPovGridPicker<Identifier>
{
    private static final StatusEffectCategory[] CATEGORIES = {StatusEffectCategory.BENEFICIAL, StatusEffectCategory.HARMFUL, StatusEffectCategory.NEUTRAL};
    private static final int[] COLORS = {0x55dd55, 0xdd5555, 0xaaaaaa};

    private final Consumer<Identifier> callback;

    public UIPovEffectPicker(Consumer<Identifier> callback)
    {
        super(L10n.lang("bbs.ui.film.pov_clips.status.picker"));

        this.callback = callback;
        this.refresh();
    }

    private static int categoryOf(Identifier id)
    {
        StatusEffect effect = Registries.STATUS_EFFECT.get(id);

        for (int i = 0; i < CATEGORIES.length; i++)
        {
            if (effect != null && effect.getCategory() == CATEGORIES[i])
            {
                return i;
            }
        }

        return CATEGORIES.length - 1;
    }

    @Override
    protected List<IKey> getTabs()
    {
        return List.of(
            L10n.lang("bbs.ui.film.pov_clips.status.tabs.beneficial"),
            L10n.lang("bbs.ui.film.pov_clips.status.tabs.harmful"),
            L10n.lang("bbs.ui.film.pov_clips.status.tabs.neutral"));
    }

    @Override
    protected boolean inTab(Identifier entry, int tab)
    {
        return categoryOf(entry) == tab;
    }

    @Override
    protected int tabColor(int tab)
    {
        return COLORS[tab];
    }

    @Override
    protected float cellAspect()
    {
        return 0.45F;
    }

    @Override
    protected List<Identifier> getEntries()
    {
        List<Identifier> ids = new ArrayList<>(Registries.STATUS_EFFECT.getIds());

        ids.sort((a, b) -> UIPovStatusEffectsClip.name(a.toString()).compareTo(UIPovStatusEffectsClip.name(b.toString())));

        return ids;
    }

    @Override
    protected boolean matches(Identifier entry, String needle)
    {
        return entry.toString().contains(needle) || UIPovStatusEffectsClip.name(entry.toString()).toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    protected void pick(Identifier entry)
    {
        this.callback.accept(entry);
    }

    @Override
    protected String describe(Identifier entry)
    {
        return entry.toString();
    }

    @Override
    protected void renderCell(UIContext context, Identifier entry, int x, int y, int w, int h, boolean hovered)
    {
        FontRenderer font = context.batcher.getFont();
        StatusEffect effect = Registries.STATUS_EFFECT.get(entry);
        int category = categoryOf(entry);

        if (effect != null)
        {
            int ix = x + 4;
            int iy = y + (h - 24) / 2;

            context.batcher.getContext().drawGuiTexture(Ids.of("minecraft", "hud/effect_background"), ix, iy, 24, 24);
            context.batcher.getContext().drawSprite(ix + 3, iy + 3, 0, 18, 18, MinecraftClient.getInstance().getStatusEffectSpriteManager().getSprite(effect));
        }

        int tx = x + 32;
        String name = UIPovStatusEffectsClip.name(entry.toString());

        context.batcher.textShadow(font.limitToWidth(name, w - 36), tx, y + h / 2 - font.getHeight(), Colors.WHITE);
        context.batcher.textShadow(font.limitToWidth(this.getTabs().get(category).get(), w - 36), tx, y + h / 2 + 2, Colors.A100 | COLORS[category]);
    }
}
