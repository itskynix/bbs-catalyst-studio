package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The toast clip's advancement picker: the game's advancements (the story, the adventure, the
 * nether, the end, husbandry...) as cells of the grid, each with its icon, its title in the colour of
 * its kind (a challenge purple, the rest yellow), its description and its tab. In a world of one's
 * own every advancement is there; on a server, the ones the server has told this player of.
 */
public class UIPovToastPicker extends UIPovGridPicker<UIPovToastPicker.Entry>
{
    private final Consumer<Entry> callback;

    /** An advancement's id, its display, and the tab it is in (its id's first part) */
    public record Entry(String id, AdvancementDisplay display, String tab)
    {}

    public UIPovToastPicker(Consumer<Entry> callback)
    {
        super(L10n.lang("bbs.ui.film.pov_clips.toast.picker"));

        this.callback = callback;
        this.refresh();
    }

    @Override
    protected float cellAspect()
    {
        return 0.6F;
    }

    @Override
    protected List<Entry> getEntries()
    {
        List<Entry> entries = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        IntegratedServer server = mc.getServer();
        Collection<AdvancementEntry> advancements = new ArrayList<>();

        if (server != null)
        {
            advancements.addAll(server.getAdvancementLoader().getAdvancements());
        }
        else if (mc.getNetworkHandler() != null)
        {
            for (PlacedAdvancement placed : mc.getNetworkHandler().getAdvancementHandler().getManager().getAdvancements())
            {
                advancements.add(placed.getAdvancementEntry());
            }
        }

        for (AdvancementEntry advancement : advancements)
        {
            AdvancementDisplay display = advancement.value().display().orElse(null);

            if (display == null)
            {
                continue;
            }

            String id = advancement.id().toString();
            String path = advancement.id().getPath();
            String tab = path.contains("/") ? path.substring(0, path.indexOf('/')) : path;

            entries.add(new Entry(id, display, tab));
        }

        entries.sort((a, b) -> a.id().compareTo(b.id()));

        return entries;
    }

    @Override
    protected boolean matches(Entry entry, String needle)
    {
        return entry.id().contains(needle)
            || entry.display().getTitle().getString().toLowerCase(Locale.ROOT).contains(needle)
            || entry.display().getDescription().getString().toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    protected void pick(Entry entry)
    {
        this.callback.accept(entry);
    }

    @Override
    protected String describe(Entry entry)
    {
        return entry.id();
    }

    @Override
    protected void renderCell(UIContext context, Entry entry, int x, int y, int w, int h, boolean hovered)
    {
        FontRenderer font = context.batcher.getFont();
        AdvancementDisplay display = entry.display();
        int color = display.getFrame() == AdvancementFrame.CHALLENGE ? 0xffff88ff : 0xffffff00;

        FormUtilsClient.getProvider().setUI(true);
        context.batcher.getContext().drawItem(display.getIcon(), x + 5, y + 5);
        FormUtilsClient.getProvider().setUI(false);

        context.batcher.textShadow(font.limitToWidth(display.getTitle().getString(), w - 30), x + 25, y + 6, color);
        /* The tab by its name, a mod's tab (which has none here) by its id */
        String key = "bbs.ui.film.pov_clips.toast.tabs." + entry.tab();
        String tab = L10n.lang(key).get();

        context.batcher.textShadow(font.limitToWidth(tab.equals(key) ? entry.tab() : tab, w - 30), x + 25, y + 16, Colors.A100 | 0x888888);

        /* The description, as many lines as fit */
        String description = display.getDescription().getString();
        int lineY = y + 28;

        for (String line : font.wrap(description, w - 10))
        {
            if (lineY + font.getHeight() > y + h - 3)
            {
                break;
            }

            context.batcher.text(line, x + 5, lineY, Colors.A100 | 0xcccccc);
            lineY += font.getHeight() + 1;
        }
    }
}
