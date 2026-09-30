package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.ReplayListEntry;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replay manipulation actions: bulk selection, duplicate to crowd target,
 * and actor state resets.
 */
public class ReplayActions
{
    /**
     * Selects all replays in the film, expanding closed folders so all replays
     * have corresponding list entries.
     */
    public static void selectAll(UIReplayList list)
    {
        UIFilmPanel panel = list.panel;

        if (panel == null)
        {
            return;
        }

        Film film = panel.getData();

        if (film == null)
        {
            return;
        }

        /* Expand all categories so all replays have corresponding rows in list */
        for (String catPath : list.collectCategoryPaths(film))
        {
            film.replayCategories.setExpanded(catPath, true);
        }

        list.refreshReplayList();

        List<ReplayListEntry> replays = new ArrayList<>();

        for (ReplayListEntry e : list.getList())
        {
            if (e.isReplay())
            {
                replays.add(e);
            }
        }

        list.selection.setAll(replays);
        list.refreshReplayList();
    }

    /**
     * Resets film actors and replays without having to exit and re-enter the film editor.
     * Rewinds server actor states and respawns dead/desynced replay entities.
     */
    public static void resetReplays(UIReplayList list, UIFilmPanel panel)
    {
        if (panel != null)
        {
            panel.notifyServer(ActionState.RESTART);
            respawnCast(panel);

            if (panel.getController() != null)
            {
                panel.getController().createEntities();
            }

            panel.setCursor(panel.getCursor());
        }

        list.refreshReplayList();
    }

    private static void respawnCast(UIFilmPanel panel)
    {
        Film film = panel.getData();

        if (film != null)
        {
            ClientNetwork.sendSyncData(film.getId(), film);
        }
    }

    /**
     * Selects all replays that share a model or form definition with any currently
     * selected replay.
     */
    public static void selectSameModel(UIReplayList list, Film film)
    {
        if (film == null)
        {
            return;
        }

        Set<String> wanted = new HashSet<>();

        for (Replay replay : list.getSelectedReplays())
        {
            String identity = identityOf(replay.form.get());

            if (identity != null)
            {
                wanted.add(identity);
            }
        }

        if (wanted.isEmpty())
        {
            return;
        }

        /* Expand folders containing matched replays so their rows are present */
        for (Replay replay : film.replays.getList())
        {
            if (wanted.contains(identityOf(replay.form.get())))
            {
                String category = Replay.normalizeCategory(replay.category.get());

                if (!category.isEmpty())
                {
                    list.expandTo(category);
                }
            }
        }

        list.refreshReplayList();

        List<ReplayListEntry> entries = new ArrayList<>();

        for (ReplayListEntry entry : list.getList())
        {
            if (entry.isReplay() && wanted.contains(identityOf(entry.replay.form.get())))
            {
                entries.add(entry);
            }
        }

        list.selection.setAll(entries);
        list.refreshReplayList();
    }

    public static String identityOf(Form form)
    {
        if (form instanceof ModelForm)
        {
            ModelForm model = (ModelForm) form;
            String modelId = model.model.get();

            return modelId.isEmpty() ? null : modelId;
        }

        return form == null ? null : form.toData().toString();
    }

    /**
     * Distributes a total target count evenly across selected replays, organizing
     * each source replay and its copies into numbered category folders.
     */
    public static Replay duplicateToTotal(Film film, List<Replay> selected, int total)
    {
        if (selected == null || selected.isEmpty() || total <= 0)
        {
            return null;
        }

        int count = selected.size();
        int base = total / count;
        int remainder = total % count;

        Set<String> used = new HashSet<>();

        for (Replay replay : film.replays.getList())
        {
            used.add(replay.category.get());
        }

        Replay last = null;

        for (int i = 0; i < count; i++)
        {
            /* The original takes one of the share, so it is one fewer copy to make */
            int share = base + (i < remainder ? 1 : 0);
            Replay source = selected.get(i);
            String category = uniqueCategory(used, source.getName() + " D #");

            source.category.set(category);
            used.add(category);

            for (int copy = 1; copy < share; copy++)
            {
                last = copyReplay(film, source, category);
            }
        }

        return last;
    }

    private static Replay copyReplay(Film film, Replay source, String category)
    {
        Replay copy = film.replays.addReplay();

        copy.copy(source);
        copy.category.set(category);

        return copy;
    }

    private static String uniqueCategory(Set<String> used, String prefix)
    {
        for (int i = 1; i < 10000; i++)
        {
            String candidate = prefix + i;

            if (!used.contains(candidate))
            {
                return candidate;
            }
        }

        return prefix + System.nanoTime();
    }
}
