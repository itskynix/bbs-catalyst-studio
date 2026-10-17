package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Placeholder for the node-based screen shader. It takes its place on the timeline and in the
 * add menu so projects can already hold it; the graph editor and its output come later, until
 * then it draws nothing.
 */
public class ScreenShaderGraphClip extends FxClip
{
    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {}

    @Override
    protected Clip create()
    {
        return new ScreenShaderGraphClip();
    }
}
