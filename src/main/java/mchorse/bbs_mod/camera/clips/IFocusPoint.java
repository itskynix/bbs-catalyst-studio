package mchorse.bbs_mod.camera.clips;

import org.joml.Vector3d;

/** An actor that can say where one of its bones is in the world, for a camera to focus on. */
public interface IFocusPoint
{
    /** Writes the bone's world position into {@code out}; false when it has no such bone. */
    boolean focusPoint(String bone, float transition, Vector3d out);
}
