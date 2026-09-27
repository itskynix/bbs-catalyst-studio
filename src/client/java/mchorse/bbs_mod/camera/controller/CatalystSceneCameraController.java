package mchorse.bbs_mod.camera.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.utils.clips.Clips;

/**
 * Camera controller used by the Catalyst SCENE layer to evaluate a film's camera
 * clips at a specific tick determined by the composition playhead.
 *
 * <p>Unlike {@link PlayCameraController} this controller does NOT auto-advance the
 * tick and does NOT remove itself — Catalyst drives the tick externally each render
 * frame via {@link #setTick(int)}.  The controller is added to / removed from the
 * global camera-controller stack by {@link mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel}
 * when a SCENE layer is visible.
 *
 * <p>Priority is 11 (slightly above RunnerCameraController at 10) so that when the
 * Catalyst panel is on-screen the film camera always wins even if the film editor was
 * left open in the background.
 */
public class CatalystSceneCameraController extends CameraWorkCameraController
{
    private int tick;

    /** Whether this controller currently has a valid camera track to apply. */
    private boolean active;

    public CatalystSceneCameraController()
    {
        super();
    }

    /** Set the BBS-tick that corresponds to the current composition playhead position. */
    public void setTick(int tick)
    {
        this.tick = tick;
    }

    /**
     * Point this controller at a film's camera track.
     * Call this whenever the selected SCENE layer changes its resource path.
     *
     * @param clips the {@code film.camera} Clips object; pass {@code null} to deactivate.
     */
    public void setFilmCamera(Clips clips)
    {
        this.context.clips = clips;
        this.active = (clips != null);
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        if (!this.active || this.context.clips == null)
        {
            return;
        }

        this.apply(camera, this.tick, transition);
    }

    @Override
    public int getPriority()
    {
        return 11;
    }
}
