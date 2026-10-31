package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.minecraft.client.render.VertexConsumer;
import org.lwjgl.system.MemoryStack;
//? if >=1.21 {
/*import net.minecraft.client.render.VertexFormat;
*///?} else {
import mchorse.bbs_mod.mixin.client.sodium.SodiumBufferBuilderAccessor;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
//?}

public class RecolorVertexSodiumConsumer extends RecolorVertexConsumer implements VertexBufferWriter
{
    public RecolorVertexSodiumConsumer(VertexConsumer consumer, Color color)
    {
        super(consumer, color);

        newColor = color;
    }

    //? if >=1.21 {
    /*@Override
    public void push(MemoryStack memoryStack, long l, int i, VertexFormat vertexFormat)
    {
        /^ Sodium 0.6 has the buffer builder write vertices itself, so what is wrapped is one ^/
        VertexBufferWriter writer = VertexBufferWriter.tryOf(this.consumer);

        if (writer != null)
        {
            writer.push(memoryStack, l, i, vertexFormat);
        }
    }
    *///?} else {
    @Override
    public void push(MemoryStack memoryStack, long l, int i, VertexFormatDescription vertexFormatDescription)
    {
        if (this.consumer instanceof SodiumBufferBuilderAccessor accessor)
        {
            accessor.bbs$getBuilder().push(memoryStack, l, i, vertexFormatDescription);
        }
    }
    //?}
}
