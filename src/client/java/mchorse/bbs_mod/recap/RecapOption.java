package mchorse.bbs_mod.recap;

/**
 * What a recap can show, hide or override, as its editor's right panel lists it. Each option is
 * kept in the recap itself, so every recap remembers its own look.
 *
 * <p>A {@link Group#HUD} option is a piece of the game's interface and is off until asked for, a
 * {@link Group#SCENE} option a part of the scene and on until hidden, and a {@link Group#OVERRIDE}
 * option replaces what the recording had and is off until switched on; some of those carry a
 * number ({@link #hasValue}).</p>
 */
public enum RecapOption
{
    CHAT("chat", Group.HUD),
    BOSS_BAR("boss_bar", Group.HUD),
    TITLE("title", Group.HUD),
    SCOREBOARD("scoreboard", Group.HUD),
    ACTION_BAR("action_bar", Group.HUD),

    BLOCKS("blocks", Group.SCENE),
    ENTITIES("entities", Group.SCENE),
    PLAYERS("players", Group.SCENE),
    PARTICLES("particles", Group.SCENE),
    SKY("sky", Group.SCENE),
    NAME_TAGS("name_tags", Group.SCENE),
    BEACON_BEAMS("beacon_beams", Group.SCENE),

    FOG("fog", Group.OVERRIDE, 128, 0, 512),
    FOG_COLOR("fog_color", Group.OVERRIDE, 0xc0d8ff, 0, 0xffffff),
    FOV("fov", Group.OVERRIDE, 70, 30, 130),
    TIME_OF_DAY("time_of_day", Group.OVERRIDE, 6000, 0, 23999),
    NIGHT_VISION("night_vision", Group.OVERRIDE),
    CAMERA_SHAKE("camera_shake", Group.OVERRIDE, 20, 0, 100),
    CAMERA_ROLL("camera_roll", Group.OVERRIDE, 0, -180, 180),
    WEATHER("weather", Group.OVERRIDE, 0, 0, 2),
    THIRDS_GUIDE("thirds_guide", Group.OVERRIDE);

    public enum Group
    {
        HUD, SCENE, OVERRIDE
    }

    public final String id;
    public final Group group;
    public final boolean hasValue;
    public final int fallback;
    public final int min;
    public final int max;

    RecapOption(String id, Group group)
    {
        this.id = id;
        this.group = group;
        this.hasValue = false;
        this.fallback = 0;
        this.min = 0;
        this.max = 0;
    }

    RecapOption(String id, Group group, int fallback, int min, int max)
    {
        this.id = id;
        this.group = group;
        this.hasValue = true;
        this.fallback = fallback;
        this.min = min;
        this.max = max;
    }

    /** Whether the option starts switched on. */
    public boolean onByDefault()
    {
        return this.group == Group.SCENE;
    }
}
