package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * The input pieces of the trigger box editor: a combo box (a text field with a drop down list of
 * suggestions beside it, so a value can be picked or typed), the lists it offers (the entity that set
 * the flow off and the players online, the films, a film's replays), and the hint button that
 * explains a field when hovered.
 */
public class FlowFields
{
    /** A suggestion: what goes into the field, and what the list shows for it. */
    public record Option(String value, String label) {}

    public static IKey key(String id)
    {
        return L10n.lang("bbs.ui.trigger_box." + id);
    }

    /** The hint of a field, or the fallback when it has none of its own. */
    public static IKey hint(String id, String fallback)
    {
        IKey key = key("hint." + id);

        return key.get().equals("bbs.ui.trigger_box.hint." + id) ? key("hint." + fallback) : key;
    }

    /** A small ? that explains the field when the pointer is over it. */
    public static UIIcon hintButton(IKey hint)
    {
        UIIcon icon = new UIIcon(Icons.HELP, (b) -> {});

        icon.tooltip(hint, 180, Direction.LEFT);

        return icon;
    }

    /**
     * A combo box: a text field and, beside it, a button that lists suggestions. Picking one puts it
     * in the field and hands it on, as typing would. The list is asked for when the button is
     * clicked, so it is always current (and may arrive later, from a repository).
     */
    public static UIElement combo(String value, Consumer<String> setter, Consumer<Consumer<List<Option>>> options, IKey placeholder)
    {
        UITextbox field = new UITextbox(1000, setter);
        UIIcon open = new UIIcon(Icons.ARROW_DOWN, null);
        UIElement row = new UIElement();

        field.setText(value);

        if (placeholder != null)
        {
            field.placeholder(placeholder);
        }

        open.callback = (b) -> options.accept((list) ->
        {
            UIContext context = open.getContext();

            if (context == null)
            {
                return;
            }

            context.replaceContextMenu((menu) ->
            {
                if (list.isEmpty())
                {
                    menu.action(Icons.EXCLAMATION, key("combo_empty"), () -> {});
                }

                for (Option option : list)
                {
                    menu.action(Icons.CONVERT, IKey.constant(option.label()), () ->
                    {
                        field.setText(option.value());
                        setter.accept(option.value());
                    });
                }
            });
        });
        open.tooltip(key("combo_open"));

        row.h(20);
        field.relative(row).w(1F, -20).h(20);
        open.relative(row).x(1F, -20).wh(20, 20);
        row.add(field, open);

        return row;
    }

    /* Lists */

    /** The entity that set the flow off, then every player online by name. */
    public static void entities(Consumer<List<Option>> callback)
    {
        List<Option> list = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        list.add(new Option("@entity", "@entity  " + key("option.entity").get()));

        if (mc.getNetworkHandler() != null)
        {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList())
            {
                String name = entry.getProfile().getName();

                list.add(new Option(name, name + "  " + key("option.player").get()));
            }
        }

        callback.accept(list);
    }

    /** Every film there is, asked of the film repository (which may answer from the server). */
    public static void films(Consumer<List<Option>> callback)
    {
        ContentType.FILMS.getRepository().requestKeys((keys) -> MinecraftClient.getInstance().execute(() ->
        {
            List<Option> list = new ArrayList<>();

            for (String id : sorted(keys))
            {
                list.add(new Option(id, id));
            }

            callback.accept(list);
        }));
    }

    /** The replays of a film, by their id, shown with their names; "any replay" first. */
    public static void replays(String filmId, Consumer<List<Option>> callback)
    {
        if (filmId == null || filmId.isBlank())
        {
            callback.accept(new ArrayList<>());

            return;
        }

        ContentType.FILMS.getRepository().load(filmId, (data) -> MinecraftClient.getInstance().execute(() ->
        {
            List<Option> list = new ArrayList<>();

            list.add(new Option("", key("option.any_replay").get()));

            if (data instanceof Film film)
            {
                List<Replay> replays = film.replays.getList();

                for (Replay replay : replays)
                {
                    list.add(new Option(replay.getId(), replay.getId() + "  " + replay.getName()));
                }
            }

            callback.accept(list);
        }));
    }

    private static List<String> sorted(Collection<String> keys)
    {
        List<String> list = new ArrayList<>(keys);

        list.sort(String::compareToIgnoreCase);

        return list;
    }
}
