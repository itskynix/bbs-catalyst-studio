package mchorse.bbs_mod.ui.film.viewport;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.camera.OrbitViewportController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

/** One pane of the preview: its own camera mode, orbit, picture zoom and the last frame drawn for it */
public class ViewportPane
{
    public final int index;

    /** The camera mode of {@link UIFilmController}; the clip at {@link #clip} when there is one */
    public int pov = UIFilmController.CAMERA_MODE_CAMERA;
    /** The camera clip of the film this pane follows, or null for the film's camera as it is */
    public CameraClip clip;
    public OrbitViewportController.OrbitState orbit;

    public float zoom = 1F;
    public float panX;
    public float panY;

    /** Where the pane is on the screen, its title and the arrow that opens its camera menu */
    public final Area rect = new Area();
    public final Area title = new Area();
    public final Area chevron = new Area();

    /** The last frame rendered for this pane; the world is drawn from one camera at a time */
    private Framebuffer target;
    private boolean hasContent;

    public ViewportPane(int index)
    {
        this.index = index;
    }

    public ViewportCameraSource source()
    {
        return ViewportCameraSource.of(this.pov, this.clip != null);
    }

    public Framebuffer getTarget()
    {
        return this.target;
    }

    public boolean hasContent()
    {
        return this.hasContent && this.target != null && this.target.getColorAttachment() > 0;
    }

    public void markContent()
    {
        this.hasContent = true;
    }

    /** The picture's buffer, made or grown to the size of the frame it keeps */
    public Framebuffer ensure(int width, int height)
    {
        if (this.target == null)
        {
            this.target = new SimpleFramebuffer(width, height, false, MinecraftClient.IS_SYSTEM_MAC);
            this.hasContent = false;
        }
        else if (this.target.textureWidth != width || this.target.textureHeight != height)
        {
            this.target.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
            this.hasContent = false;
        }

        return this.target;
    }

    public void release()
    {
        if (this.target != null)
        {
            this.target.delete();
            this.target = null;
        }

        this.hasContent = false;
    }
}
