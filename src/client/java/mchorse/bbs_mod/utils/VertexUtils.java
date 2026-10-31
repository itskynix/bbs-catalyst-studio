package mchorse.bbs_mod.utils;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Normals through a matrix. The overloads taking a {@code Matrix3f} went away in 1.21, where the
 * stack entry is passed instead.
 */
public class VertexUtils
{
    private static final Vector3f TEMP = new Vector3f();

    public static VertexConsumer normal(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z)
    {
        //? if >=1.21 {
        /*return consumer.normal(entry, x, y, z);
        *///?} else {
        return consumer.normal(entry.getNormalMatrix(), x, y, z);
        //?}
    }

    public static VertexConsumer normal(VertexConsumer consumer, Matrix3f matrix, float x, float y, float z)
    {
        matrix.transform(x, y, z, TEMP);

        return consumer.normal(TEMP.x, TEMP.y, TEMP.z);
    }
}
