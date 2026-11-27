package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * The tracks of a recap's timeline, each one keyframed value that reworks how the recap is shown.
 * An empty track changes nothing: the recap plays as it was recorded.
 *
 * <ul>
 *     <li>{@link #CAMERA}: where the camera is and where it looks, in the world; replaces the
 *     recorded camera while it has keyframes.</li>
 *     <li>{@link #CAMERA_ORBIT}: yaw, pitch, distance and height of a camera circling the point the
 *     camera would be at.</li>
 *     <li>{@link #TRACK_ENTITY}: the name of an entity the camera keeps looking at.</li>
 *     <li>{@link #CAMERA_SHAKE}: how much the camera sways, nought to a hundred.</li>
 *     <li>{@link #FOV}: the lens, in degrees.</li>
 *     <li>{@link #SPEED}: how fast the recording runs under the timeline, one being as recorded.</li>
 *     <li>{@link #TIMELAPSE}: day time ticks that pass with every tick.</li>
 *     <li>{@link #TIME_OF_DAY}: the day time, nought to 24000.</li>
 *     <li>{@link #FREEZE}: the recording held still while on.</li>
 *     <li>{@link #AUDIO}: a sound played where each keyframe is, its name the value.</li>
 * </ul>
 */
public enum RecapTrackKind
{
    CAMERA("camera", KeyframeFactories.TRANSFORM, Icons.VIDEO_CAMERA),
    CAMERA_ORBIT("camera_orbit", KeyframeFactories.VECTOR4F, Icons.REFRESH),
    TRACK_ENTITY("track_entity", KeyframeFactories.STRING, Icons.VISIBLE),
    CAMERA_SHAKE("camera_shake", KeyframeFactories.FLOAT, Icons.EXCHANGE),
    FOV("fov", KeyframeFactories.FLOAT, Icons.SEARCH),
    SPEED("speed", KeyframeFactories.FLOAT, Icons.FRAME_NEXT),
    TIMELAPSE("timelapse", KeyframeFactories.FLOAT, Icons.STOPWATCH),
    TIME_OF_DAY("time_of_day", KeyframeFactories.FLOAT, Icons.TIME),
    FREEZE("freeze", KeyframeFactories.BOOLEAN, Icons.PAUSE),
    AUDIO("audio", KeyframeFactories.STRING, Icons.SOUND);

    public final String id;
    public final IKeyframeFactory<?> factory;
    public final Icon icon;

    RecapTrackKind(String id, IKeyframeFactory<?> factory, Icon icon)
    {
        this.id = id;
        this.factory = factory;
        this.icon = icon;
    }

    public static RecapTrackKind byId(String id)
    {
        for (RecapTrackKind kind : values())
        {
            if (kind.id.equals(id))
            {
                return kind;
            }
        }

        return null;
    }
}
