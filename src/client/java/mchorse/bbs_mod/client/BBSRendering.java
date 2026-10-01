package mchorse.bbs_mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.api.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.WorldFilmController;
import mchorse.bbs_mod.forms.FormRenderLast;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.forms.structure.StructureWand;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.FrameOverlays;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.cubic.model.ModelSetupQueue;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.sodium.SodiumUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.impl.client.rendering.WorldRenderContextImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import mchorse.bbs_mod.mixin.client.PostEffectPassAccessor;
import mchorse.bbs_mod.mixin.client.PostEffectProcessorAccessor;
import mchorse.bbs_mod.mixin.client.WorldRendererAccessor;

public class BBSRendering
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Cached rendered model blocks
     */
    public static final Set<ModelBlockEntity> capturedModelBlocks = new HashSet<>();

    public static boolean canRender;

    public static boolean renderingWorld;
    public static int lastAction;

    private static boolean customSize;
    private static boolean iris;
    private static boolean sodium;
    private static boolean optifine;

    private static int width;
    private static int height;

    /* Orbit distance for the orthographic projection; negative = perspective.
     * Re-armed every frame by the film editor's orbit camera (which is set up
     * from Camera#update, between renderWorld's HEAD and its projection use),
     * so it can never go stale when another controller takes over. */
    private static float orthoDistance = -1F;

    private static boolean toggleFramebuffer;
    private static boolean frameCapturedThisRender;
    private static Framebuffer framebuffer;
    private static Framebuffer clientFramebuffer;
    private static Texture texture;
    private static mchorse.bbs_mod.graphics.Framebuffer /* NECESSARY */ exportFramebuffer;

    private static float fogRed = 0.5F;
    private static float fogGreen = 0.7F;
    private static float fogBlue = 1.0F;

    public static void setFogColor(float r, float g, float b)
    {
        fogRed = r;
        fogGreen = g;
        fogBlue = b;
    }

    public static float getFogRed()
    {
        return fogRed;
    }

    public static float getFogGreen()
    {
        return fogGreen;
    }

    public static float getFogBlue()
    {
        return fogBlue;
    }

    private static Runnable pendingExportResolutionAction;

    public static int getMotionBlur()
    {
        return getMotionBlur(BBSSettings.videoFrameRate.get(), getMotionBlurFactor());
    }

    public static int getMotionBlur(double fps, int target)
    {
        int i = 0;

        while (fps < target)
        {
            fps *= 2;

            i++;
        }

        return i;
    }

    public static int getMotionBlurFactor()
    {
        return getMotionBlurFactor(BBSSettings.videoMotionBlur.get());
    }

    public static int getMotionBlurFactor(int integer)
    {
        return integer == 0 ? 0 : (int) Math.pow(2, 6 + integer);
    }

    public static int getVideoWidth()
    {
        return width == 0 ? BBSSettings.videoWidth.get() : width;
    }

    public static int getVideoHeight()
    {
        return height == 0 ? BBSSettings.videoHeight.get() : height;
    }

    public static int getVideoFrameRate()
    {
        int frameRate = BBSSettings.videoFrameRate.get();

        return frameRate * (1 << getMotionBlur(frameRate, getMotionBlurFactor()));
    }

    public static File getVideoFolder()
    {
        File defaultExportDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("bbs_videos").toFile();
        String custom = BBSSettings.videoExportPath.get();

        if (custom != null && !custom.trim().isEmpty())
        {
            File exportPath = new File(custom.replace("\"", "").trim());
            if (exportPath.isDirectory())
            {
                return exportPath;
            }
        }

        defaultExportDir.mkdirs();

        return defaultExportDir;
    }

    public static boolean canReplaceFramebuffer()
    {
        /* The world always renders at the export size. The interface (HUD) is drawn after the
         * world but still into our export framebuffer — toggleFramebuffer stays on until the blit —
         * so it must use the export size too. Otherwise it renders at the real window size and, when
         * the window can't physically reach the requested resolution, comes out stretched in the
         * file. Excluded while a BBS editor is open so the film panel's own UI keeps rendering at the
         * real window size. */
        return customSize && (renderingWorld || (toggleFramebuffer && UIScreen.getCurrentMenu() == null));
    }

    public static boolean isCustomSize()
    {
        return customSize;
    }

    public static void setCustomSize(boolean customSize)
    {
        setCustomSize(customSize, 0, 0);
    }

    public static void setCustomSize(boolean customSize, int w, int h)
    {
        if (customSize && (w <= 0 || h <= 0))
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            Window window = mc != null ? mc.getWindow() : null;

            if (window != null && window.getFramebufferWidth() > 0 && window.getFramebufferHeight() > 0)
            {
                w = window.getFramebufferWidth();
                h = window.getFramebufferHeight();
            }
            else
            {
                w = Math.max(2, BBSSettings.videoWidth.get());
                h = Math.max(2, BBSSettings.videoHeight.get());
            }
        }

        int newWidth = !customSize ? 0 : w;
        int newHeight = !customSize ? 0 : h;

        /* No-op when nothing actually changes. A redundant setCustomSize(false)
         * — e.g. a film panel disappearing while custom size is already off, which
         * happens when the dashboard is first lazily created by the teleport/record
         * keybinds — must NOT resize the vanilla framebuffers: that stalls the GPU
         * and freezes the screen for a frame even though the state didn't change. */
        if (BBSRendering.customSize == customSize && width == newWidth && height == newHeight)
        {
            return;
        }

        LOGGER.info("[BBS film] setCustomSize customSize={} w={} h={} (stored width/height will be {})",
            customSize, w, h, customSize ? w + "/" + h : "0/0");
        BBSRendering.customSize = customSize;

        width = newWidth;
        height = newHeight;

        if (!customSize)
        {
            updateFabulousTransparency(false);
            resizeExtraFramebuffers();
        }
    }

    public static Texture getTexture()
    {
        if (texture == null)
        {
            texture = new Texture();
            texture.setFormat(TextureFormat.RGBA_U8);
            texture.setFilter(GL11.GL_NEAREST);
        }

        return texture;
    }

    public static void startTick()
    {
        capturedModelBlocks.clear();
    }

    public static void setup()
    {
        iris = FabricLoader.getInstance().isModLoaded("iris");
        sodium = FabricLoader.getInstance().isModLoaded("sodium");
        optifine = FabricLoader.getInstance().isModLoaded("optifabric");

        ModelBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.getWorld().isClient())
            {
                capturedModelBlocks.add(entity);
            }
        });

        if (!iris)
        {
            return;
        }

        IrisUtils.setup();
    }

    /* Framebuffers */

    public static Framebuffer getFramebuffer()
    {
        return framebuffer;
    }

    public static void setupFramebuffer()
    {
        Window window = MinecraftClient.getInstance().getWindow();

        framebuffer = new WindowFramebuffer(window.getFramebufferWidth(), window.getFramebufferHeight());
    }

    public static void resizeExtraFramebuffers()
    {
        Set<Framebuffer> buffers = new HashSet<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        buffers.add(mc.worldRenderer.getEntityOutlinesFramebuffer());
        buffers.add(mc.worldRenderer.getTranslucentFramebuffer());
        buffers.add(mc.worldRenderer.getEntityFramebuffer());
        buffers.add(mc.worldRenderer.getParticlesFramebuffer());
        buffers.add(mc.worldRenderer.getWeatherFramebuffer());
        buffers.add(mc.worldRenderer.getCloudsFramebuffer());

        for (Framebuffer buffer : buffers)
        {
            resizeFramebuffer(buffer);
        }
    }

    public static void resizeFramebuffer(Framebuffer framebuffer)
    {
        if (framebuffer == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = Math.max(1, mc.getWindow().getFramebufferWidth());
        int h = Math.max(1, mc.getWindow().getFramebufferHeight());

        if (customSize && width > 0 && height > 0)
        {
            w = width;
            h = height;
        }

        if (framebuffer.textureWidth == w && framebuffer.textureHeight == h)
        {
            return;
        }

        framebuffer.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
    }

    public static void resizeFramebuffer(int w, int h)
    {
        if (framebuffer == null)
        {
            return;
        }

        w = Math.max(1, w);
        h = Math.max(1, h);

        if (framebuffer.textureWidth == w && framebuffer.textureHeight == h)
        {
            return;
        }

        framebuffer.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
    }

    public static void toggleFramebuffer(boolean toggleFramebuffer)
    {
        if (toggleFramebuffer == BBSRendering.toggleFramebuffer)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();

        BBSRendering.toggleFramebuffer = toggleFramebuffer;

        if (toggleFramebuffer)
        {
            int w = Math.max(1, mc.getWindow().getFramebufferWidth());
            int h = Math.max(1, mc.getWindow().getFramebufferHeight());

            if (customSize && width > 0 && height > 0)
            {
                w = width;
                h = height;
            }

            resizeExtraFramebuffers();

            if (framebuffer == null)
            {
                setupFramebuffer();
            }

            if (framebuffer.textureWidth != w || framebuffer.textureHeight != h)
            {
                framebuffer.resize(w, h, MinecraftClient.IS_SYSTEM_MAC);
            }

            clientFramebuffer = mc.getFramebuffer();

            reassignFramebuffer(framebuffer);

            updateFabulousTransparency(true);

            framebuffer.beginWrite(true);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);

            float r;
            float g;
            float b;

            if (BBSSettings.chromaSkyEnabled.get())
            {
                Integer fromCurve = BBSRendering.getChromaSkyColorArgb();
                int argb = fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get();
                Color color = Color.rgba(argb);

                r = color.r;
                g = color.g;
                b = color.b;
            }
            else
            {
                r = fogRed;
                g = fogGreen;
                b = fogBlue;
            }

            framebuffer.setClearColor(r, g, b, 1F);
            RenderSystem.clearColor(r, g, b, 1F);
            framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);

            framebuffer.beginWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        }
        else
        {
            int drawW = window.getFramebufferWidth();
            int drawH = window.getFramebufferHeight();
            reassignFramebuffer(clientFramebuffer);

            if (!customSize)
            {
                updateFabulousTransparency(false);
            }

            mc.getFramebuffer().beginWrite(true);

            if (width != 0)
            {
                /* When the film panel is open, the UI draws the preview texture in its block; do not
                 * blit our framebuffer to the full window or the preview would stretch to full screen. */
                UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
                boolean filmPanelShowing = currentMenu instanceof UIDashboard dashboard
                    && dashboard.getPanels().panel instanceof UIFilmPanel;
                if (!filmPanelShowing)
                {
                    framebuffer.draw(drawW, drawH);
                }
            }
        }
    }

    private static void reassignFramebuffer(Framebuffer framebuffer)
    {
        MinecraftClient.getInstance().framebuffer = framebuffer;
    }

    public static Framebuffer getClientFramebuffer()
    {
        return clientFramebuffer;
    }

    public static void updateFabulousTransparency(boolean forBBS)
    {
        if (!MinecraftClient.isFabulousGraphicsOrBetter())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.worldRenderer == null)
        {
            return;
        }

        PostEffectProcessor processor = ((WorldRendererAccessor) mc.worldRenderer).bbs$getTransparencyPostProcessor();
        if (processor == null)
        {
            return;
        }

        Framebuffer targetFb = forBBS ? framebuffer : clientFramebuffer;
        if (targetFb == null)
        {
            return;
        }

        int targetW = targetFb.textureWidth;
        int targetH = targetFb.textureHeight;

        PostEffectProcessorAccessor procAcc = (PostEffectProcessorAccessor) processor;
        procAcc.setMainTarget(targetFb);
        if (procAcc.getTargetsByName() != null)
        {
            procAcc.getTargetsByName().put("minecraft:main", targetFb);
        }

        if (procAcc.getPasses() != null)
        {
            for (PostEffectPass pass : procAcc.getPasses())
            {
                PostEffectPassAccessor passAcc = (PostEffectPassAccessor) pass;
                if (forBBS)
                {
                    if (clientFramebuffer != null && pass.input == clientFramebuffer)
                    {
                        passAcc.setInput(framebuffer);
                    }
                    if (clientFramebuffer != null && pass.output == clientFramebuffer)
                    {
                        passAcc.setOutput(framebuffer);
                    }
                }
                else
                {
                    if (pass.input == framebuffer)
                    {
                        passAcc.setInput(clientFramebuffer);
                    }
                    if (pass.output == framebuffer)
                    {
                        passAcc.setOutput(clientFramebuffer);
                    }
                }

                List<String> names = passAcc.getSamplerNames();
                List<IntSupplier> values = passAcc.getSamplerValues();
                List<Integer> widths = passAcc.getSamplerWidths();
                List<Integer> heights = passAcc.getSamplerHeights();

                if (names != null && values != null)
                {
                    for (int i = 0; i < names.size(); i++)
                    {
                        String name = names.get(i);
                        if ("DiffuseDepthSampler".equals(name))
                        {
                            final Framebuffer depthFb = targetFb;
                            values.set(i, depthFb::getDepthAttachment);
                        }
                        if (widths != null && i < widths.size())
                        {
                            widths.set(i, targetW);
                        }
                        if (heights != null && i < heights.size())
                        {
                            heights.set(i, targetH);
                        }
                    }
                }
            }
        }

        if (procAcc.getWidth() != targetW || procAcc.getHeight() != targetH)
        {
            processor.setupDimensions(targetW, targetH);
        }
    }

    /* Rendering */

    public static void onWorldRenderBegin()
    {
        if (orthoDistance > 0F)
        {
            /* Give back the culling disabled for the previous ortho frame
             * (see setOrthoDistance); re-armed by the orbit if still on. */
            MinecraftClient.getInstance().chunkCullingEnabled = true;

            if (sodium)
            {
                SodiumUtils.restorePointCameraCulling();
            }
        }

        orthoDistance = -1F;

        MinecraftClient mc = MinecraftClient.getInstance();

        /* The frame boundary the profiler's counters roll over on; the flag is mirrored here
         * so the hot-path checks read a plain static boolean. */
        BBSProfiler.enabled = BBSSettings.profilerOverlay != null && BBSSettings.profilerOverlay.get();
        BBSProfiler.frame();
        RenderFrame.nextFrame();
        Gizmo.INSTANCE.forgetPlacement();

        /* The budgeted tail of model loading: VAO bakes for whatever the background loader
         * finished, a few milliseconds' worth per frame instead of all of them at once. */
        ModelSetupQueue.drain();

        BBSModClient.getVideos().startFrame();
        BBSModClient.getFilms().startRenderFrame(mc.getTickDelta());

        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null)
        {
            menu.startRenderFrame(mc.getTickDelta());
        }

        renderingWorld = true;
        frameCapturedThisRender = false;

        if (!customSize)
        {
            return;
        }

        toggleFramebuffer(true);
    }

    public static void onWorldRenderEnd()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController controller)
        {
            DrawContext drawContext = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());
            Batcher2D batcher = new Batcher2D(drawContext);

            FrameOverlays.render(batcher.getContext().getMatrices(), batcher, controller.getContext());
        }

        if (customSize)
        {
            UIBaseMenu currentMenu = UIScreen.getCurrentMenu();

            if (currentMenu instanceof UIDashboard dashboard)
            {
                if (dashboard.getPanels().panel instanceof UIFilmPanel panel)
                {
                    FrameOverlays.render(currentMenu.context.batcher.getContext().getMatrices(), currentMenu.context.batcher, panel.getRunner().getContext());
                }
            }
        }

        renderingWorld = false;
    }

    public static void onRenderBeforeScreen()
    {
        if (frameCapturedThisRender)
        {
            return;
        }

        frameCapturedThisRender = true;

        boolean needsExportFrame = pendingExportResolutionAction != null || BBSModClient.getVideoRecorder().isRecording();

        if (needsExportFrame && canRender)
        {
            captureExportFrame();
        }

        if (BBSModClient.getVideoRecorder().isRecording() && canRender)
        {
            BBSModClient.getMinecraftSoundCapture().captureFrame();
            BBSModClient.getVideoRecorder().recordFrame();
        }

        renderRecordingOverlay();

        if (customSize)
        {
            toggleFramebuffer(false);
        }

        runPendingExportAction();
    }

    /**
     * Hand over the action waiting for a frame rendered at the new export size. Nothing else
     * calls it: without this a queued "Render Now" never leaves the queue.
     */
    private static void runPendingExportAction()
    {
        if (pendingExportResolutionAction == null)
        {
            return;
        }

        Runnable action = pendingExportResolutionAction;

        pendingExportResolutionAction = null;

        MinecraftClient.getInstance().execute(action);
    }

    public static int getExportFboId()
    {
        if (exportFramebuffer != null && exportFramebuffer.id > 0)
        {
            return exportFramebuffer.id;
        }
        if (framebuffer != null && framebuffer.fbo > 0)
        {
            return framebuffer.fbo;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getFramebuffer() != null)
        {
            return mc.getFramebuffer().fbo;
        }
        return 0;
    }

    public static mchorse.bbs_mod.graphics.Framebuffer getExportFramebuffer()
    {
        return exportFramebuffer;
    }

    public static void setExportFramebuffer(mchorse.bbs_mod.graphics.Framebuffer fbo)
    {
        exportFramebuffer = fbo;
    }

    public static mchorse.bbs_mod.graphics.Framebuffer getOrCreateExportFramebuffer(int targetWidth, int targetHeight)
    {
        targetWidth = Math.max(2, targetWidth);
        targetHeight = Math.max(2, targetHeight);

        Texture texture = getTexture();

        if (exportFramebuffer == null || texture.width != targetWidth || texture.height != targetHeight)
        {
            if (exportFramebuffer != null)
            {
                exportFramebuffer.delete();
                exportFramebuffer = null;
            }
            texture.bind();
            texture.setSize(targetWidth, targetHeight);
            texture.unbind();

            exportFramebuffer = new mchorse.bbs_mod.graphics.Framebuffer();
            exportFramebuffer.attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            exportFramebuffer.unbind();
        }

        return exportFramebuffer;
    }

    public static void captureExportFrame()
    {
        int targetWidth = Math.max(2, getVideoWidth());
        int targetHeight = Math.max(2, getVideoHeight());

        getOrCreateExportFramebuffer(targetWidth, targetHeight);

        if (framebuffer == null || exportFramebuffer == null)
        {
            return;
        }

        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, exportFramebuffer.id);

        float r;
        float g;
        float b;

        if (BBSSettings.chromaSkyEnabled.get())
        {
            Integer fromCurve = BBSRendering.getChromaSkyColorArgb();
            int argb = fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get();
            Color color = Color.rgba(argb);

            r = color.r;
            g = color.g;
            b = color.b;
        }
        else
        {
            r = fogRed;
            g = fogGreen;
            b = fogBlue;
        }

        GL11.glClearColor(r, g, b, 1F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL30.glBlitFramebuffer(
            0, 0, framebuffer.textureWidth, framebuffer.textureHeight,
            0, 0, targetWidth, targetHeight,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        );

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
    }


    public static void scheduleAfterNextExportFrame(Runnable action)
    {
        pendingExportResolutionAction = action;
    }

    public static void onRenderChunkLayer(MatrixStack stack)
    {
        WorldRenderContextImpl worldRenderContext = new WorldRenderContextImpl();
        MinecraftClient mc = MinecraftClient.getInstance();

        worldRenderContext.prepare(
            mc.worldRenderer, stack, mc.getTickDelta(), mc.getRenderTime(), false,
            mc.gameRenderer.getCamera(), mc.gameRenderer, mc.gameRenderer.getLightmapTextureManager(),
            RenderSystem.getProjectionMatrix(), mc.getBufferBuilders().getEntityVertexConsumers(), null, false, mc.world
        );

        if (isIrisShadersEnabled())
        {
            renderCoolStuff(worldRenderContext);
        }
    }

    public static void renderHud(DrawContext drawContext, float tickDelta)
    {
        Batcher2D batcher2D = new Batcher2D(drawContext);

        BBSModClient.getFilms().renderHud(batcher2D, tickDelta);
        StructureWand.renderHud(batcher2D);
    }

    /**
     * Draw the recording countdown / frame-counter overlay. This is operator UI: it is drawn from
     * {@link #onRenderBeforeScreen()} after the export blit but before the buffer is copied to the
     * screen, so it shows up on screen but is never captured into the file.
     */
    private static void renderRecordingOverlay()
    {
        if (!BBSSettings.recordingOverlays.get() || UIScreen.getCurrentMenu() != null)
        {
            return;
        }

        String label;

        if (BBSModClient.isVideoExportDelayPending())
        {
            int countdown = Math.max(0, (int) Math.ceil(BBSModClient.getVideoExportDelayRemainingMs() / 50D));

            label = String.valueOf(countdown / 20F);
        }
        else if (BBSModClient.getVideoRecorder().isRecording())
        {
            int count = BBSModClient.getVideoRecorder().getCounter();

            label = UIKeys.FILM_VIDEO_RECORDING.format(
                count,
                BBSModClient.getKeyRecordVideo().getBoundKeyLocalizedText().getString()
            ).get();
        }
        else
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        DrawContext drawContext = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());

        renderRecordingTimerOverlay(new Batcher2D(drawContext), label);

        drawContext.draw();
    }

    public static void renderRecordingTimerOverlay(Batcher2D batcher2D, String label)
    {
        renderRecordingTimerOverlay(batcher2D, label, 5, 5);
    }

    public static void renderRecordingTimerOverlay(Batcher2D batcher2D, String label, int x, int y)
    {
        int iconX = x + 16;

        batcher2D.icon(Icons.SPHERE, Colors.RED | Colors.A100, iconX, y, 1F, 0F);
        batcher2D.textCard(label, iconX + 3, y + 4, Colors.WHITE, Colors.A50);
    }

    /** Whether the entity pass opened the render-last scope — false when one was already open. */
    private static boolean entityPassRenderLast;

    /**
     * The world's entity pass: between these two calls vanilla draws the actors, model blocks
     * and morphed players, and without a shader pack {@link #renderCoolStuff} draws the films
     * at its end — one render-last scope spans it all, so a form set to render last draws after
     * every other form of the frame. Under Iris the films run earlier, at the solid layer, in a
     * scope of their own; this one still covers what the entity loop drew.
     */
    public static void beginEntityPass()
    {
        entityPassRenderLast = FormRenderLast.open();
    }

    public static void endEntityPass()
    {
        FormRenderLast.close(entityPassRenderLast);

        entityPassRenderLast = false;
    }

    public static void renderCoolStuff(WorldRenderContext worldRenderContext)
    {
        /* A scope over everything drawn here, for when this runs on its own — under Iris, at the
         * solid layer: forms set to render last draw when it closes, after the last replay, still
         * in this pass. Inside the entity pass's scope this opens nothing and they wait for it. */
        boolean renderLast = FormRenderLast.open();

        try
        {
            if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
            {
                screen.renderInWorld(worldRenderContext);
            }

            BBSModClient.getFilms().render(worldRenderContext);
        }
        finally
        {
            FormRenderLast.close(renderLast);
        }
    }

    public static boolean isOptifinePresent()
    {
        return optifine;
    }

    public static boolean isRenderingWorld()
    {
        return renderingWorld;
    }

    /**
     * Arm the orthographic projection for the current frame. Pass the orbit
     * camera's distance to the pivot; negative disables. The value is reset
     * at the beginning of every world render, so the caller must re-arm it
     * each frame for as long as ortho should stay on.
     */
    public static void setOrthoDistance(float distance)
    {
        orthoDistance = distance;

        if (distance > 0F)
        {
            /* The chunk occlusion culling walks sections outward from the
             * camera POINT, which is only sound for a perspective projection —
             * under ortho's parallel sightlines it over-culls sections near
             * the screen edges. Disable it for the frame (Sodium honours the
             * same flag); the frustum and render distance still cull. Sodium's
             * own point-camera heuristics get the same treatment. */
            MinecraftClient.getInstance().chunkCullingEnabled = false;

            if (sodium)
            {
                SodiumUtils.disablePointCameraCulling();
            }
        }
    }

    public static boolean isOrthoActive()
    {
        return orthoDistance > 0F;
    }

    /**
     * Build the orthographic projection replacing the given perspective one
     * (returns the input untouched when ortho is not armed). FOV and aspect are
     * derived from the perspective matrix itself, so the ortho frame height
     * matches the perspective frame height at the orbit pivot's distance: the
     * subject keeps its size when toggling projections, and the scroll zoom
     * keeps working through the orbit distance.
     *
     * @param minHalfHeight a lower bound on the frame's half height, and the
     *        slack behind the camera plane the near plane is given; the frustum
     *        culling matrix is built with a loose bound on both, so culling
     *        stays conservative when zoomed all the way in.
     */
    public static Matrix4f getOrthoProjection(GameRenderer renderer, Matrix4f perspective, float minHalfHeight)
    {
        if (orthoDistance <= 0F)
        {
            return perspective;
        }

        float tanHalfFov = 1F / perspective.m11();
        float aspect = perspective.m11() / perspective.m00();
        float halfHeight = Math.max(minHalfHeight, orthoDistance * tanHalfFov);
        float halfWidth = halfHeight * aspect;

        /* The near plane sits exactly at the camera, the way a perspective one
         * effectively does: under ortho's parallel sightlines everything BEHIND
         * the camera projects into the frame as well, so a hillside the camera
         * stands in paints itself over the subject, and no amount of orbiting
         * gets past it. Clipping at the camera plane drops precisely what the
         * eye has already passed and nothing the eye still faces — pushing the
         * plane any further in would slice the ground in front of the camera
         * and leave a hole where it was. Zooming in walks the camera towards
         * the pivot, so the zoom doubles as the control over how much of an
         * obstacle in front gets cut.
         *
         * The far plane is the one vanilla builds its perspective with, which
         * already bounds everything the game draws; together with the near
         * plane it keeps the box tight enough for the frustum to cull with,
         * which matters here because chunk occlusion culling is off (see
         * setOrthoDistance). */
        float near = -minHalfHeight;
        float far = renderer.getFarPlaneDistance();

        return new Matrix4f().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far);
    }

    public static boolean isIrisShadersEnabled()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShaderPackEnabled();
    }

    /**
     * Whether a shader pack is shading this very draw. Unlike {@link #isIrisShadersEnabled()}
     * it turns off inside {@link #renderOffscreen(Runnable)}, where our own programs take over.
     */
    public static boolean isIrisWorldShadersEnabled()
    {
        return iris && renderingWorld && IrisUtils.shouldOverrideShaders();
    }

    /** Render into a framebuffer of ours: see {@link IrisUtils#renderOffscreen(Runnable)}. */
    public static void renderOffscreen(Runnable render)
    {
        if (iris)
        {
            IrisUtils.renderOffscreen(render);
        }
        else
        {
            render.run();
        }
    }

    public static boolean isIrisShadowPass()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShadowPass();
    }

    /**
     * Hold the vertex layout Iris hands out steady while a render layer's buffer is uploaded
     * outside of the immediate provider's own draw — the deferred translucent pass ends and
     * uploads those buffers itself (see CustomVertexConsumerProvider#draw). Without it a form
     * drawn where the level isn't rendering, like the form editor's viewport, gets its plain
     * entity vertices read at Iris' extended stride and shreds into stretched triangles. Returns
     * the previous state, to be handed back to {@link #endIrisBufferUpload(boolean)}.
     */
    public static boolean beginIrisBufferUpload(BufferBuilder builder)
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.beginBufferUpload(builder);
    }

    public static void endIrisBufferUpload(boolean extended)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.endBufferUpload(extended);
    }

    /**
     * Iris considers a vanilla core program applied during world rendering a stray
     * draw into its G-buffers and masks its color/depth writes. Reporting that the
     * main framebuffer isn't bound (like vanilla render targets do via bindWrite)
     * turns both core shader overrides and that masking off.
     */
    public static void setIrisMainBound(boolean bound)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.setMainBound(bound);
    }

    public static void trackTexture(Texture texture)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.trackTexture(texture);
    }

    public static float[] calculateTangents(float[] t, float[] v, float[] n, float[] u)
    {
        if (!iris)
        {
            return t;
        }

        return IrisUtils.calculateTangents(t, v, n, u);
    }

    public static float[] calculateTangents(float[] v, float[] n, float[] u)
    {
        if (!iris)
        {
            return v;
        }

        return IrisUtils.calculateTangents(v, n, u);
    }

    public static void addUniforms(List<CachedUniform> list, Map<String, ShaderCurves.ShaderVariable> variableMap)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.addUniforms(list, variableMap);
    }

    public static List<String> getShadersSliderOptions()
    {
        if (!iris)
        {
            return Collections.emptyList();
        }

        return IrisUtils.getSliderProperties();
    }

    public static Map<String, String> getShadersLanguageMap(String language)
    {
        if (!iris)
        {
            return Collections.emptyMap();
        }

        return IrisUtils.getShadersLanguageMap(language);
    }

    /* Curves */

    public static Long getTimeOfDay()
    {
        Double value = getCurveValue(ShaderCurves.SUN_ROTATION, CurveClip::getValues);

        return value == null ? null : (long) (value * 1000L);
    }

    public static Double getBrightness()
    {
        return getCurveValue(ShaderCurves.BRIGHTNESS, CurveClip::getValues);
    }

    public static Double getWeather()
    {
        return getCurveValue(ShaderCurves.WEATHER, CurveClip::getValues);
    }

    public static float getSunHorizontalRotation()
    {
        Double value = getCurveValue(ShaderCurves.SUN_HORIZONTAL_ROTATION, CurveClip::getValues);

        return value == null ? 0F : value.floatValue();
    }

    public static Integer getChromaSkyColorArgb()
    {
        return getCurveValue(CurveClip.CHROMA_SKY_COLOR, CurveClip::getColorValues);
    }

    /** Camera work takes priority; films played without a camera supply missing values. */
    private static <T> T getCurveValue(String key, Function<CameraClipContext, Map<String, T>> values)
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            T value = values.apply(controller.getContext()).get(key);

            if (value != null)
            {
                return value;
            }
        }

        if (BBSModClient.getFilms() != null)
        {
            for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
            {
                if (controller instanceof WorldFilmController worldFilm)
                {
                    T value = values.apply(worldFilm.getContext()).get(key);

                    if (value != null)
                    {
                        return value;
                    }
                }
            }
        }

        return null;
    }

    public static Function<VertexConsumer, VertexConsumer> getColorConsumer(Color color)
    {
        if (sodium)
        {
            return (b) -> SodiumUtils.createVertexBuffer(b, color);
        }

        return (b) -> new RecolorVertexConsumer(b, color);
    }
}