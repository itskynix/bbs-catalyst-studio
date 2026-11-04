package mchorse.bbs_mod.catalyst.ai;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

/**
 * The system prompt every hosted model gets: who it is, what it may help with, the exact shape of
 * its answer, the commands the editor can run, and what the film looks like right now. The commands
 * are the rule engine's own sentences, so whatever the model asks for runs through the very same
 * {@link CatalystAICommandExecutor} as a quick button does.
 */
public class CatalystAIPrompt
{
    private static final String ROLE =
        "Sen BBS Catalyst Studio içinde çalışan profesyonel, yapıcı, etik ve tarafsız bir sinematik kurgu ve yönetmen asistanısın (Catalyst AI). "
        + "Görevin yalnızca Minecraft film kurgusu, kamera hareketleri, renk/atmosfer efektleri, sahne kompozisyonu ve aktör yönlendirmeleriyle ilgili yardım etmektir. "
        + "Zararlı, nefret söylemi barındıran veya bağlam dışı talepleri nazikçe reddet.";

    private static final String CONTRACT = """
        Answer with ONE JSON object and nothing else:
        {"reply": "<short answer to the user, in the user's language>", "commands": ["<command>", ...]}
        "commands" may be empty: for a question, an explanation, or a request you decline.
        Each command must be one short sentence in exactly one of these forms (Turkish or English, numbers free):
        - "dramatik yaklaşma 4 saniye 3 blok"            camera push in from the active camera, eased out
        - "korku atmosferi 10 saniye"                     compound effect: cold green grade, grain, vignette, lens distortion
        - "kamera sarsıntısı 2 saniye" (+ "hafif" or "panik" for a lighter or a frantic shake)
        - "girdap bükülmesi 5 saniye"                     vortex world bend
        - "dalga bükülmesi 5 saniye"                      wave world bend
        - "<actor> <other actor>'e baksın"                the first actor named looks at the second (use the names listed below)
        - "klipleri 20 tick kaydır" / "klipleri 1 saniye geri kaydır"   selected clips, or the clips after the cursor
        - "efekti 5 saniye uzat" / "efekti 2 saniye kısalt"             selected clip, or the effect under the cursor
        Durations are seconds (20 ticks = 1 second). New clips are placed at the cursor.
        Never invent other commands; if something cannot be done with them, say so in "reply".
        """;

    public static String build(UIFilmPanel panel)
    {
        StringBuilder builder = new StringBuilder();

        builder.append(ROLE).append("\n\n").append(CONTRACT).append("\nThe film right now:\n");

        Film film = panel.getData();

        if (film == null)
        {
            builder.append("- No film is open.\n");

            return builder.toString();
        }

        builder.append("- Cursor at tick ").append(panel.getCursor()).append('\n');
        builder.append("- Camera timeline: ").append(film.camera.get().size()).append(" clips, ")
            .append(film.camera.calculateDuration()).append(" ticks long\n");
        builder.append("- Selected clips: ").append(panel.cameraEditor.clips.getClipsFromSelection().size()).append('\n');
        builder.append("- Actors:");

        boolean any = false;

        for (Replay replay : film.replays.getList())
        {
            builder.append(any ? ", " : " ").append(replay.getName());
            any = true;
        }

        builder.append(any ? "\n" : " none\n");

        return builder.toString();
    }
}
