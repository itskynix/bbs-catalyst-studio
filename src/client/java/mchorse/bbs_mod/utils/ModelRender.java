package mchorse.bbs_mod.utils;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
//? if >=1.21
/*import net.minecraft.util.math.ColorHelper;*/

/**
 * Renders model parts with a color given as floats. Since 1.21 the color is one packed ARGB int.
 */
public class ModelRender
{
    public static void part(ModelPart part, MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a)
    {
        //? if >=1.21 {
        /*part.render(matrices, vertices, light, overlay, ColorHelper.Argb.fromFloats(a, r, g, b));
        *///?} else {
        part.render(matrices, vertices, light, overlay, r, g, b, a);
        //?}
    }

    public static void model(Model model, MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a)
    {
        //? if >=1.21 {
        /*model.render(matrices, vertices, light, overlay, ColorHelper.Argb.fromFloats(a, r, g, b));
        *///?} else {
        model.render(matrices, vertices, light, overlay, r, g, b, a);
        //?}
    }
}
