package mchorse.bbs_mod.ui.film.viewport;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/** What a preview pane looks through */
public enum ViewportCameraSource
{
    /** The film's camera, with its post effects */
    RENDER_CAMERA(UIFilmController.CAMERA_MODE_CAMERA, UIKeys.FILM_VIEWPORT_SOURCE_RENDER, Icons.VIDEO_CAMERA),
    /** A camera of its own that flies about the scene whatever the clips do */
    FREE_ORBIT(UIFilmController.CAMERA_MODE_ORBIT, UIKeys.FILM_VIEWPORT_SOURCE_ORBIT, Icons.ORBIT),
    /** One camera clip of the film */
    SPECIFIC_CLIP(UIFilmController.CAMERA_MODE_CAMERA, UIKeys.FILM_VIEWPORT_SOURCE_CLIP, Icons.CAMERA),
    /** The eyes of the selected actor */
    ACTOR_POV(UIFilmController.CAMERA_MODE_FIRST_PERSON, UIKeys.FILM_VIEWPORT_SOURCE_ACTOR, Icons.VISIBLE);

    public final int pov;
    public final IKey label;
    public final Icon icon;

    ViewportCameraSource(int pov, IKey label, Icon icon)
    {
        this.pov = pov;
        this.label = label;
        this.icon = icon;
    }

    /** The source that a camera mode and whether a clip is followed amount to */
    public static ViewportCameraSource of(int pov, boolean clip)
    {
        if (pov == UIFilmController.CAMERA_MODE_CAMERA)
        {
            return clip ? SPECIFIC_CLIP : RENDER_CAMERA;
        }

        if (pov == UIFilmController.CAMERA_MODE_FIRST_PERSON)
        {
            return ACTOR_POV;
        }

        return FREE_ORBIT;
    }
}
