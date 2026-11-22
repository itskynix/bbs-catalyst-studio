package mchorse.bbs_mod.client;

import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.Optional;

public class BBSShaders
{
    private static ShaderProgram model;
    private static ShaderProgram multiLink;
    private static ShaderProgram subtitles;
    private static ShaderProgram selection;
    private static ShaderProgram billboardFlat;
    private static ShaderProgram pixelArt;
    private static ShaderProgram pixelArtText;
    private static ShaderProgram pixelArtTextIntensity;
    private static ShaderProgram postFx;
    private static ShaderProgram postFxLibrary;
    private static ShaderProgram decay;

    private static ShaderProgram pickerPreview;
    private static ShaderProgram pickerOutline;
    private static ShaderProgram pickerBillboard;
    private static ShaderProgram pickerBillboardNoShading;
    private static ShaderProgram pickerParticles;
    private static ShaderProgram pickerModels;

    static
    {
        setup();
    }

    public static void setup()
    {
        if (model != null) model.close();
        if (subtitles != null) subtitles.close();
        if (selection != null) selection.close();
        if (billboardFlat != null) billboardFlat.close();
        if (pixelArt != null) pixelArt.close();
        if (pixelArtText != null) pixelArtText.close();
        if (pixelArtTextIntensity != null) pixelArtTextIntensity.close();
        if (postFx != null) postFx.close();
        if (postFxLibrary != null) postFxLibrary.close();

        postFx = null;
        postFxLibrary = null;

        if (pickerPreview != null) pickerPreview.close();
        if (pickerOutline != null) pickerOutline.close();
        if (pickerBillboard != null) pickerBillboard.close();
        if (pickerBillboardNoShading != null) pickerBillboardNoShading.close();
        if (pickerParticles != null) pickerParticles.close();
        if (pickerModels != null) pickerModels.close();

        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            model = new ShaderProgram(factory, "model", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            multiLink = new ShaderProgram(factory, "multilink", VertexFormats.POSITION_TEXTURE_COLOR);
            billboardFlat = new ShaderProgram(factory, "billboard_flat", VertexFormats.POSITION_TEXTURE_LIGHT_COLOR);
            subtitles = new ShaderProgram(factory, "subtitles", VertexFormats.POSITION_TEXTURE_COLOR);
            selection = new ShaderProgram(factory, "selection", VertexFormats.POSITION_TEXTURE_COLOR);

            pickerPreview = new ShaderProgram(factory, "picker_preview", VertexFormats.POSITION_TEXTURE_COLOR);
            pickerOutline = new ShaderProgram(factory, "picker_outline", VertexFormats.POSITION_TEXTURE_COLOR);
            pickerBillboard = new ShaderProgram(factory, "picker_billboard", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            pickerBillboardNoShading = new ShaderProgram(factory, "picker_billboard_no_shading", VertexFormats.POSITION_TEXTURE_LIGHT_COLOR);
            pickerParticles = new ShaderProgram(factory, "picker_particles", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
            pickerModels = new ShaderProgram(factory, "picker_models", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        /* Kept in a try of their own: these are a cosmetic nicety, and a driver
         * that refuses to compile them must not take the shaders the editor
         * can't work without (bone picking, models) down with them. Everything
         * asking for these falls back to vanilla's programs when they're null. */
        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            pixelArt = new ShaderProgram(factory, "pixelart", VertexFormats.POSITION_TEXTURE_COLOR);
            pixelArtText = new ShaderProgram(factory, "pixelart_text", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
            pixelArtTextIntensity = new ShaderProgram(factory, "pixelart_text_intensity", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
        }
        catch (IOException e)
        {
            /* All or nothing: text must not end up half swapped */
            if (pixelArt != null) pixelArt.close();
            if (pixelArtText != null) pixelArtText.close();
            if (pixelArtTextIntensity != null) pixelArtTextIntensity.close();

            pixelArt = pixelArtText = pixelArtTextIntensity = null;

            e.printStackTrace();
        }

        /* Also on its own: a driver that cannot build the post-production pass only loses the
         * post-production clips, which then draw nothing */
        try
        {
            postFx = new ShaderProgram(new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager()), "postfx", VertexFormats.POSITION_TEXTURE_COLOR);
        }
        catch (Throwable e)
        {
            if (postFx != null) postFx.close();

            postFx = null;

            e.printStackTrace();
        }

        /* The post-process effect library: a program of its own, so a driver that will not build
         * it only loses the library's effects and keeps the rest of post-production */
        try
        {
            postFxLibrary = new ShaderProgram(new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager()), "postfx_lib", VertexFormats.POSITION_TEXTURE_COLOR);

            /* A uniform the shader does not have would be set into nothing, silently: say so once */
            for (String uniform : new String[] {"Mode", "Size", "Time", "Intensity", "P", "Tint", "DepthInfo"})
            {
                if (postFxLibrary.getUniform(uniform) == null)
                {
                    System.err.println("[BBS] Shader 'postfx_lib' has no uniform '" + uniform + "': postfx_lib.json and postfx_lib.fsh disagree.");
                }
            }
        }
        catch (Throwable e)
        {
            if (postFxLibrary != null) postFxLibrary.close();

            postFxLibrary = null;

            /* Says which shader and why, so the reason is one search away in latest.log: the
             * exception's message carries the driver's own compile or link log */
            System.err.println("[BBS] Shader 'postfx_lib' (assets/bbs/shaders/core/postfx_lib.fsh) failed to compile or link; the post-process effect library draws nothing until it is fixed. Reason: " + e.getMessage());
            e.printStackTrace();
        }

        /* The decay form's per-pixel pattern: on its own too, a driver that will not build it only
         * sends the decay to its CPU atlas, the path a shader pack uses anyway */
        if (decay != null) decay.close();

        decay = null;

        try
        {
            decay = new ShaderProgram(new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager()), "decay", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        }
        catch (Throwable e)
        {
            if (decay != null) decay.close();

            decay = null;

            System.err.println("[BBS] Shader 'decay' (assets/bbs/shaders/core/decay.fsh) failed to compile or link; decay forms fall back to their CPU atlas. Reason: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** The decay form's per-pixel pattern; null when it could not be built, and then the form draws through its CPU atlas. */
    public static ShaderProgram getDecayProgram()
    {
        return decay;
    }

    /** The post-process effect library's pass; null when it could not be built, which draws nothing. */
    public static ShaderProgram getPostFxLibraryProgram()
    {
        return postFxLibrary;
    }

    /**
     * The full-screen pass of the post-production clips. Null when the driver refused to build it,
     * and whoever asks must treat that as "no effect", never as an error.
     */
    public static ShaderProgram getPostFxProgram()
    {
        return postFx;
    }

    public static ShaderProgram getModel()
    {
        return model;
    }

    public static ShaderProgram getMultilinkProgram()
    {
        return multiLink;
    }

    public static ShaderProgram getSubtitlesProgram()
    {
        return subtitles;
    }

    public static ShaderProgram getSelectionProgram()
    {
        return selection;
    }

    /**
     * Textured UI quads with the seam between texels smoothed, for when the
     * interface is drawn at a fractional scale (see the shader's own comment).
     */
    public static ShaderProgram getPixelArtProgram()
    {
        return pixelArt;
    }

    public static ShaderProgram getPixelArtTextProgram()
    {
        return pixelArtText;
    }

    public static ShaderProgram getPixelArtTextIntensityProgram()
    {
        return pixelArtTextIntensity;
    }

    public static ShaderProgram getPickerOutlineProgram()
    {
        return pickerOutline;
    }

    /** An unshaded billboard lit by the lightmap only; vanilla has no such program since 1.21. */
    public static ShaderProgram getBillboardFlat()
    {
        return billboardFlat;
    }

    public static ShaderProgram getPickerPreviewProgram()
    {
        return pickerPreview;
    }

    public static ShaderProgram getPickerBillboardProgram()
    {
        return pickerBillboard;
    }

    public static ShaderProgram getPickerBillboardNoShadingProgram()
    {
        return pickerBillboardNoShading;
    }

    public static ShaderProgram getPickerParticlesProgram()
    {
        return pickerParticles;
    }

    public static ShaderProgram getPickerModelsProgram()
    {
        return pickerModels;
    }

    private static class ProxyResourceFactory implements ResourceFactory
    {
        private ResourceManager manager;

        public ProxyResourceFactory(ResourceManager manager)
        {
            this.manager = manager;
        }

        @Override
        public Optional<Resource> getResource(Identifier id)
        {
            if (id.getPath().contains("/core/"))
            {
                return this.manager.getResource(Ids.of(BBSMod.MOD_ID, id.getPath()));
            }

            /* #moj_import always resolves in the minecraft namespace, so our own
             * includes have to be looked up in BBS first, vanilla's second */
            if (id.getPath().contains("/include/"))
            {
                Optional<Resource> resource = this.manager.getResource(Ids.of(BBSMod.MOD_ID, id.getPath()));

                if (resource.isPresent())
                {
                    return resource;
                }
            }

            return this.manager.getResource(id);
        }
    }
}
