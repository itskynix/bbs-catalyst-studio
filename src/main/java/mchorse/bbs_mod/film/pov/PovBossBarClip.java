package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A boss bar at the top of the replay's screen. A preset gives the name, colour and style of the
 * game's own bosses; the channels, when keyed, override them and fill the bar (0 to 1).
 */
public class PovBossBarClip extends PovClip
{
    public static final List<String> PRESETS = Collections.unmodifiableList(Arrays.asList("custom", "ender_dragon", "wither", "raid"));
    /** The game's colours and styles, in its order */
    public static final List<String> COLORS = Collections.unmodifiableList(Arrays.asList("pink", "blue", "red", "green", "yellow", "purple", "white"));
    public static final List<String> STYLES = Collections.unmodifiableList(Arrays.asList("progress", "notched_6", "notched_10", "notched_12", "notched_20"));

    public final ValueString preset = new ValueString("preset", "custom");

    public final KeyframeChannel<String> name = this.channel("name", KeyframeFactories.STRING);
    public final KeyframeChannel<Double> health = this.channel("health", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Integer> color = this.channel("color", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> style = this.channel("style", KeyframeFactories.INTEGER);

    public PovBossBarClip()
    {
        super();

        this.add(this.preset);
    }

    @Override
    protected Clip create()
    {
        return new PovBossBarClip();
    }
}
