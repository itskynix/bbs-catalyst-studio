package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Matrix3f;

/**
 * The inverse of the camera's rotation, which billboards (particles, trails) are turned by. Up to
 * 1.20.4 the render system holds it; since 1.21 nothing does, so it comes from the vanilla camera
 * unless a renderer that draws with its own camera (the model and particle previews, the stencil
 * picker) has set one for the length of its pass.
 */
public class InverseViewRotation
{
    //? if >=1.21 {
    /*private static Matrix3f override;
    *///?}

    public static Matrix3f get()
    {
        //? if >=1.21 {
        /*if (override != null)
        {
            return new Matrix3f(override);
        }

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        return new Matrix3f().rotation(camera.getRotation());
        *///?} else {
        return new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        //?}
    }

    /** What to put back with {@link #setOverride(Matrix3f)} after a pass; null when none was set. */
    public static Matrix3f getOverride()
    {
        //? if >=1.21 {
        /*return override == null ? null : new Matrix3f(override);
        *///?} else {
        return new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        //?}
    }

    public static void setOverride(Matrix3f matrix)
    {
        //? if >=1.21 {
        /*override = matrix == null ? null : new Matrix3f(matrix);
        *///?} else {
        if (matrix != null)
        {
            RenderSystem.setInverseViewRotationMatrix(matrix);
        }
        //?}
    }
}
