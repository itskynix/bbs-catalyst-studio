package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
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

    /** A drop down list of suggestions that are picked straight from it; its last entry lets a value be typed. */
    public static UIElement combo(String value, Consumer<String> setter, Consumer<Consumer<List<Option>>> options, IKey placeholder)
    {
        return new UIComboBox(value, setter, options, placeholder);
    }

    /**
     * A form field: a drop down whose entries are the BBS form picker and clearing it. The form is kept
     * as BBS form data, as the form editor copies it.
     */
    public static UIElement form(String value, Consumer<String> setter, UIElement palette)
    {
        UIComboBox box = new UIComboBox(formLabel(value), (v) -> { if (v.isEmpty()) setter.accept(""); }, (callback) ->
        {
            List<Option> list = new ArrayList<>();

            list.add(new Option("", key("option.no_form").get()));
            callback.accept(list);
        }, key("form_placeholder"));

        box.custom(key("pick_form"), (b) ->
        {
            Form current = null;

            try
            {
                MapType data = DataToString.mapFromString(value);

                current = data == null ? null : FormUtils.fromData(data);
            }
            catch (Exception e)
            {}

            UIFormPalette.open(palette, false, current, (form) ->
            {
                String data = form == null ? "" : DataToString.toString(FormUtils.toData(form));

                setter.accept(data);
                b.pick(formLabel(data));
            });
        });

        return box;
    }

    /** What a form field shows: the form's id, or nothing. */
    private static String formLabel(String data)
    {
        try
        {
            MapType map = DataToString.mapFromString(data);
            Form form = map == null ? null : FormUtils.fromData(map);

            return form == null ? "" : form.getFormIdOrName();
        }
        catch (Exception e)
        {
            return data == null ? "" : data;
        }
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
