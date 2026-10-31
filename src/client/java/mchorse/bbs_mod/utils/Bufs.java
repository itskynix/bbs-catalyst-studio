package mchorse.bbs_mod.utils;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
//? if >=1.21 {
/*import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.BufferAllocator;
*///?}

/**
 * Starts a buffer. Up to 1.20.4 the tessellator owns one builder that is begun in place; since
 * 1.21 a builder is created already begun, so call sites take what this returns instead of
 * begin()ing a builder they fetched earlier.
 */
public class Bufs
{
    public static BufferBuilder begin(VertexFormat.DrawMode mode, VertexFormat format)
    {
        //? if >=1.21 {
        /*return Tessellator.getInstance().begin(mode, format);
        *///?} else {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(mode, format);

        return builder;
        //?}
    }

    /**
     * Ends the builder and draws it with the global program. Since 1.21 ending a builder that
     * got no vertices throws, so then there is simply nothing to draw.
     */
    public static void draw(BufferBuilder builder)
    {
        //? if >=1.21 {
        /*BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            BufferRenderer.drawWithGlobalProgram(built);
        }
        *///?} else {
        BufferRenderer.drawWithGlobalProgram(builder.end());
        //?}
    }

    /** Ends the builder into the bound vertex buffer; an empty builder uploads nothing. */
    public static void upload(VertexBuffer buffer, BufferBuilder builder)
    {
        //? if >=1.21 {
        /*BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            buffer.upload(built);
        }
        *///?} else {
        buffer.upload(builder.end());
        //?}
    }

    /**
     * A begun builder of its own, for code that holds one open while the shared tessellator is in
     * use. It has to be closed: since 1.21 it owns native memory.
     */
    public static class Owned implements AutoCloseable
    {
        private final BufferBuilder builder;
        //? if >=1.21
        /*private final BufferAllocator allocator;*/

        public Owned(int size, VertexFormat.DrawMode mode, VertexFormat format)
        {
            //? if >=1.21 {
            /*this.allocator = new BufferAllocator(size);
            this.builder = new BufferBuilder(this.allocator, mode, format);
            *///?} else {
            this.builder = new BufferBuilder(size);

            this.builder.begin(mode, format);
            //?}
        }

        public BufferBuilder builder()
        {
            return this.builder;
        }

        @Override
        public void close()
        {
            //? if >=1.21
            /*this.allocator.close();*/
        }
    }
}
