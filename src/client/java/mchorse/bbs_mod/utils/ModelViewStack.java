package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/**
 * The global model-view stack of the render system. It is a {@code MatrixStack} up to 1.20.4 and
 * a plain JOML stack since 1.21, so everything that pushes onto it goes through here.
 */
public class ModelViewStack
{
    public static void push()
    {
        //? if >=1.21 {
        /*RenderSystem.getModelViewStack().pushMatrix();
        *///?} else {
        RenderSystem.getModelViewStack().push();
        //?}
    }

    public static void pop()
    {
        //? if >=1.21 {
        /*RenderSystem.getModelViewStack().popMatrix();
        *///?} else {
        RenderSystem.getModelViewStack().pop();
        //?}
    }

    public static void loadIdentity()
    {
        //? if >=1.21 {
        /*RenderSystem.getModelViewStack().identity();
        *///?} else {
        RenderSystem.getModelViewStack().loadIdentity();
        //?}
    }

    /** Multiplies the top of the stack with the matrix (the matrix is applied last). */
    public static void multiply(Matrix4f matrix)
    {
        //? if >=1.21 {
        /*RenderSystem.getModelViewStack().mul(matrix);
        *///?} else {
        RenderSystem.getModelViewStack().multiplyPositionMatrix(matrix);
        //?}
    }

    /** The top of the stack; it is live, so changing it changes the stack. */
    public static Matrix4f top()
    {
        //? if >=1.21 {
        /*return RenderSystem.getModelViewStack();
        *///?} else {
        return RenderSystem.getModelViewStack().peek().getPositionMatrix();
        //?}
    }

    /** Resets the top of the stack, normal matrix included where there still is one. */
    public static void identityTop()
    {
        //? if >=1.21 {
        /*RenderSystem.getModelViewStack().identity();
        *///?} else {
        RenderSystem.getModelViewStack().peek().getPositionMatrix().identity();
        RenderSystem.getModelViewStack().peek().getNormalMatrix().identity();
        //?}
    }
}
