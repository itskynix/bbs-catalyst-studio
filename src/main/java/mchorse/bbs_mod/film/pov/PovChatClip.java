package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * The replay's chat: the line being typed at the bottom (how much of it is typed so far, where the
 * cursor stands), the input bar behind it, the lines already sent above (one per line of the
 * executed text) scrolled by the scroll channel, and, for a command, the game's suggestions.
 */
public class PovChatClip extends PovClip
{
    public final KeyframeChannel<String> chatText = this.channel("chat_text", KeyframeFactories.STRING);
    /** How many letters of the chat text are typed; unkeyed, all of them */
    public final KeyframeChannel<Double> typed = this.channel("typed", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Boolean> background = this.channel("background_bar", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Integer> cursor = this.channel("cursor_position", KeyframeFactories.INTEGER);
    public final KeyframeChannel<String> executed = this.channel("executed_text", KeyframeFactories.STRING);
    public final KeyframeChannel<Double> scroll = this.channel("chat_scroll", KeyframeFactories.DOUBLE);

    public final ValueBoolean suggestions = new ValueBoolean("command_suggestions", true);

    public PovChatClip()
    {
        super();

        this.add(this.suggestions);
    }

    @Override
    protected Clip create()
    {
        return new PovChatClip();
    }
}
