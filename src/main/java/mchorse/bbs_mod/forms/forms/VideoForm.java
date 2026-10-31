package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * A billboard that plays a video file: the quad machinery (crop, color,
 * billboarding, shading) is inherited, only the texture comes from a video
 * decoder instead of a picture. The playback clock is the carrier entity's
 * age, so scrubbing and export stay frame exact.
 */
public class VideoForm extends BillboardForm
{
    public static final Icon VIDEO_ICON = Icons.VIDEO_CAMERA;

    public final ValueLink video = new ValueLink("video", null);
    public final ValueBoolean loop = new ValueBoolean("loop", true);
    public final ValueFloat videoOffset = new ValueFloat("videoOffset", 0F);

    /** Whether the frame is extruded into a solid: front and back faces, and walls along
     * the edges of its opaque parts, rebuilt as the video plays. */
    public final ValueBoolean extrude = new ValueBoolean("extrude", false);
    /** How thick the extrusion is, in blocks; the faces stand half of it off the middle. */
    public final ValueFloat depth = new ValueFloat("depth", 0.1F, 0.01F, 5F);
    /** The alpha a pixel needs above it to count as solid for the walls. */
    public final ValueFloat alphaThreshold = new ValueFloat("alphaThreshold", 0.1F, 0F, 1F);
    /** Whether the walls are darkened by the way they face, like the sides of an extruded item. */
    public final ValueBoolean sideShading = new ValueBoolean("sideShading", true);

    public VideoForm()
    {
        super();

        /* The quad's own texture is dead here — the renderer takes the frame from
         * the decoder instead, so it must not offer a track that does nothing. */
        this.texture.invisible();

        /* Looping is an authoring switch, like the render layer. */
        this.loop.invisible();

        /* So are the extrusion switches; its depth and threshold are tracks */
        this.extrude.invisible();
        this.sideShading.invisible();

        this.add(this.video);
        this.add(this.loop);
        this.add(this.videoOffset);
        this.add(this.extrude);
        this.add(this.depth);
        this.add(this.alphaThreshold);
        this.add(this.sideShading);
    }

    @Override
    public String getDefaultDisplayName()
    {
        Link link = this.video.get();

        return link == null ? "video" : link.toString();
    }

    @Override
    public Icon getIcon()
    {
        return VIDEO_ICON;
    }
}
