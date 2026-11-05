/*
 * BBS CS lighting layer. Original code written for BBS Catalyst Studio; it carries no irlight code.
 * See Light.java and THIRD_PARTY_LICENSES.md.
 */
package mchorse.bbs_mod.graphics.lights;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The lights of the frame in progress. Cleared when the world pass starts, filled by every light form
 * as it is drawn, read by whatever lights the scene. With no back end reading them, nothing happens
 * and nothing is paid beyond the list.
 */
public final class LightManager
{
    /** More than this in one frame is a mistake, not a scene */
    public static final int MAX_LIGHTS = 256;

    private static final List<Light> LIGHTS = new ArrayList<>();

    private LightManager()
    {}

    /** The world pass begins: last frame's lights are gone */
    public static void beginFrame()
    {
        LIGHTS.clear();
    }

    public static void submit(Light light)
    {
        if (LIGHTS.size() < MAX_LIGHTS)
        {
            LIGHTS.add(light);
        }
    }

    /** The lights submitted so far this frame, read-only */
    public static List<Light> lights()
    {
        return Collections.unmodifiableList(LIGHTS);
    }
}
