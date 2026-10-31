package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level diffuse lighting: up to 1.20.4 the render system turns the two light directions by the
 * view matrix, since 1.21 it takes them as they are.
 */
public class LightingCompat
{
    public static void setupLevel(Vector3f first, Vector3f second, Matrix4f view)
    {
        //? if >=1.21 {
        /*RenderSystem.setupLevelDiffuseLighting(view.transformDirection(first, new Vector3f()), view.transformDirection(second, new Vector3f()));
        *///?} else {
        RenderSystem.setupLevelDiffuseLighting(first, second, view);
        //?}
    }
}
