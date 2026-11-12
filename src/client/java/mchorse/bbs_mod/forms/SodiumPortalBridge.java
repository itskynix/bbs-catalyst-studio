package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.util.iterator.ReversibleObjectArrayIterator;
import mchorse.bbs_mod.mixin.client.sodium.RenderSectionManagerAccessor;
import mchorse.bbs_mod.mixin.client.sodium.SodiumWorldRendererAccessor;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the terrain seen through a portal window when Sodium owns the chunk rendering.
 *
 * <p>Sodium replaces the vanilla chunk storage with its own region storage, so the vanilla pass of
 * {@link FormPortals} finds nothing to draw. This class is the only one that names Sodium: it is
 * loaded by {@link FormPortals} exclusively after {@code FabricLoader.isModLoaded("sodium")}, and
 * the accessor mixins it uses are filtered by {@code BBSClientMixinPlugin} in the same way.</p>
 *
 * <p>Sodium's own render lists hold what the real camera sees, which is not what the partner's
 * window shows. The lists are therefore built again from every built section the same way the
 * vanilla pass picks its chunks - in front of the window and near it - and handed to Sodium's
 * chunk renderer directly. Stencil, depth and the oblique projection are plain GL state and
 * uniforms, so they reach Sodium's draw calls untouched.</p>
 */
public final class SodiumPortalBridge
{
    /** A region with the sections of it that were picked, and how far it is from the window. */
    private record Entry(ChunkRenderList list, float distance)
    {}

    /** The sorted lists as Sodium's chunk renderer asks for them. */
    private static final class PickedLists implements ChunkRenderListIterable
    {
        private final ObjectArrayList<ChunkRenderList> lists;

        /** Whether an Iris shader pack draws the terrain */
        private final boolean shaders;

        /** The GL state the chunk program draws with, read once per pass while diagnosing */
        private String seen;
        /** The framebuffer the pass was moved to under a shader pack, or 0 */
        private int retargeted;

        private PickedLists(ObjectArrayList<ChunkRenderList> lists, boolean shaders)
        {
            this.lists = lists;
            this.shaders = shaders;
        }

        @Override
        public Iterator<ChunkRenderList> iterator(boolean reverse)
        {
            /* Sodium asks for the lists after its program (or Iris's) is bound and its framebuffer set
             * up, and before the first draw: the one point the draw can still be pointed elsewhere.
             * Iris's solid and cutout framebuffers write to the buffers of before the translucent flip,
             * which a pack no longer reads this late in the frame (see IrisPortalTerrain); its
             * translucent one already follows the flip. */
            int irisBound = diagnose ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : 0;

            this.retargeted = this.shaders && !reverse ? IrisPortalTerrain.retarget() : 0;

            if (diagnose)
            {
                this.seen = describeDrawState() + (this.retargeted != 0 ? ", retargeted from fbo " + irisBound + " to " + this.retargeted : ", not retargeted");
            }

            return new ReversibleObjectArrayIterator<>(this.lists, reverse);
        }
    }

    /** Whether this pass writes the diagnostic line; set for one pass per shader pack state. */
    private static boolean diagnose;
    /** The shader pack state the last line was written for: null before the first */
    private static Boolean diagnosedShaders;

    /**
     * Preserves OpenGL state around Sodium chunk rendering so that subsequent pipeline passes
     * (such as portal model block entities and film actors) are not affected by leaked state.
     */
    private static final class GlStateGuard
    {
        private int program;
        private int vao;
        private int arrayBuffer;
        private int elementArrayBuffer;

        private boolean depthTest;
        private boolean depthMask;
        private int depthFunc;

        private boolean stencilTest;
        private int stencilMask;
        private int stencilFunc;
        private int stencilRef;
        private int stencilValueMask;
        private int stencilFail;
        private int stencilPassDepthFail;
        private int stencilPassDepthPass;

        private final byte[] colorMask = new byte[4];

        private boolean blend;
        private int blendSrcRgb;
        private int blendDstRgb;
        private int blendSrcAlpha;
        private int blendDstAlpha;

        private boolean cull;
        private int activeTexture;

        public static GlStateGuard capture()
        {
            GlStateGuard guard = new GlStateGuard();

            guard.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            guard.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            guard.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            guard.elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);

            guard.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            guard.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            guard.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

            guard.stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            guard.stencilMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK) & 0xFF;
            guard.stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
            guard.stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
            guard.stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK) & 0xFF;
            guard.stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
            guard.stencilPassDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
            guard.stencilPassDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);

            guard.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            guard.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            guard.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            guard.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            guard.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

            guard.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            guard.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

            try (MemoryStack stack = MemoryStack.stackPush())
            {
                ByteBuffer mask = stack.malloc(4);

                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);

                for (int i = 0; i < 4; i++)
                {
                    guard.colorMask[i] = mask.get(i);
                }
            }

            return guard;
        }

        public void restore()
        {
            /* 1. Unbind Sodium's shader program through Minecraft's cache and restore previous program if any */
            ShaderProgram any = GameRenderer.getPositionColorProgram();

            if (any != null)
            {
                any.unbind();
            }
            else
            {
                GlStateManager._glUseProgram(0);
            }

            if (this.program > 0)
            {
                GlStateManager._glUseProgram(this.program);
            }

            /* 2. Unbind Sodium's VAO and buffers, then restore saved bindings */
            GL30.glBindVertexArray(this.vao);

            if (this.vao == 0)
            {
                VertexBuffer.unbind();
            }

            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, this.arrayBuffer);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBuffer);

            /* 3. Restore depth state */
            if (this.depthTest)
            {
                RenderSystem.enableDepthTest();
            }
            else
            {
                RenderSystem.disableDepthTest();
            }

            RenderSystem.depthMask(this.depthMask);
            RenderSystem.depthFunc(this.depthFunc);

            /* 4. Restore stencil state */
            if (this.stencilTest)
            {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            }
            else
            {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }

            GL11.glStencilMask(this.stencilMask);
            GL11.glStencilFunc(this.stencilFunc, this.stencilRef, this.stencilValueMask);
            GL11.glStencilOp(this.stencilFail, this.stencilPassDepthFail, this.stencilPassDepthPass);

            /* 5. Restore color mask */
            RenderSystem.colorMask(this.colorMask[0] != 0, this.colorMask[1] != 0, this.colorMask[2] != 0, this.colorMask[3] != 0);

            /* 6. Restore blend state */
            if (this.blend)
            {
                RenderSystem.enableBlend();
            }
            else
            {
                RenderSystem.disableBlend();
            }

            RenderSystem.blendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);

            /* 7. Restore culling */
            if (this.cull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            /* 8. Reset texture unit to 0 and shader color to white */
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            GL11.glGetError();
        }
    }

    private SodiumPortalBridge()
    {}

    /**
     * Draws the sections in front of {@code source} through Sodium.
     *
     * @param view the camera view of the real scene
     * @param modelView {@code view} moved to the partner window
     * @param source the window the terrain lies in front of
     * @param projection the oblique projection of the pass
     * @param camera the real camera position
     * @param limit how far from the window sections are still picked
     */
    public static void drawTerrain(Matrix4f view, Matrix4f modelView, Matrix4f source, Matrix4f projection, Vec3d camera, float limit)
    {
        /* One line per shader pack state: the first pass with a pack on, and the first with it off */
        boolean shaders = isShaderPackInUse();

        diagnose = diagnosedShaders == null || diagnosedShaders != shaders;
        diagnosedShaders = shaders;

        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();

        if (renderer == null)
        {
            skipped(shaders, "no Sodium world renderer");

            return;
        }

        RenderSectionManager manager = ((SodiumWorldRendererAccessor) renderer).bbs$getRenderSectionManager();

        if (manager == null)
        {
            skipped(shaders, "no section manager");

            return;
        }

        RenderSectionManagerAccessor access = (RenderSectionManagerAccessor) manager;
        ChunkRenderer chunkRenderer = access.bbs$getChunkRenderer();
        RenderRegionManager regions = access.bbs$getRegions();

        if (chunkRenderer == null || regions == null)
        {
            skipped(shaders, "no chunk renderer or regions");

            return;
        }

        ObjectArrayList<ChunkRenderList> picked = pick(regions, view, source, camera, limit);

        if (picked.isEmpty())
        {
            skipped(shaders, "no built section in front of the window (loaded regions=" + regions.getLoadedRegions().size() + ")");

            return;
        }

        /* The scene is moved by the portal, so the eye the faces are culled against is the partner's eye:
         * modelView = [A | t] sees a point p as A * (p - camera) + t, which is A * (p - eye) with the
         * eye below, so Sodium gets that eye and the rotation without the translation. */
        Vec3d eye = virtualEye(modelView, camera);
        Matrix4f rotation = new Matrix4f(modelView).setTranslation(0F, 0F, 0F);
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(projection, rotation);
        CameraTransform transform = new CameraTransform(eye.x, eye.y, eye.z);
        PickedLists lists = new PickedLists(picked, shaders);
        StringBuilder report = diagnose ? new StringBuilder() : null;

        if (report != null)
        {
            report.append("[BBS] Portal terrain pass (Sodium): shaderPack=").append(shaders)
                .append(", regions=").append(picked.size())
                .append(", ").append(countSections(picked, projection, rotation, eye))
                .append(", before={").append(describeDrawState()).append('}');
        }

        GlStateGuard guard = GlStateGuard.capture();
        boolean entered = false;

        try
        {
            /* Sodium's device only hands out command lists inside its managed block, which it opens
             * around its own terrain layers and closes again. The portal pass runs after that, so it
             * opens the block itself - and closes only what it opened, never a block it was called in. */
            if (!isDeviceActive())
            {
                RenderDevice.enterManagedCode();
                entered = true;
            }

            CommandList commands = RenderDevice.INSTANCE.createCommandList();

            for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL)
            {
                if (report == null)
                {
                    chunkRenderer.render(matrices, commands, lists, pass, transform);

                    continue;
                }

                /* Counts the samples of the pass that got through every test - scissor, stencil, depth.
                 * None with sections drawn means they are cut away; some means they are drawn and
                 * something later paints over them */
                GL11.glGetError();
                lists.seen = null;

                int query = GL15.glGenQueries();

                GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
                chunkRenderer.render(matrices, commands, lists, pass, transform);
                GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);

                int samples = GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT);
                int error = GL11.glGetError();

                GL15.glDeleteQueries(query);

                report.append(", ").append(passName(pass)).append("={samples=").append(samples)
                    .append(", glError=0x").append(Integer.toHexString(error))
                    .append(", draw={").append(lists.seen == null ? "not reached" : lists.seen).append("}}");
            }

            commands.flush();
        }
        finally
        {
            diagnose = false;

            if (entered)
            {
                RenderDevice.exitManagedCode();
            }

            guard.restore();

            if (report != null)
            {
                System.out.println(report);
            }
        }
    }

    private static void skipped(boolean shaders, String why)
    {
        if (diagnose)
        {
            diagnose = false;

            System.out.println("[BBS] Portal terrain pass (Sodium): shaderPack=" + shaders + ", nothing drawn: " + why);
        }
    }

    private static String passName(TerrainRenderPass pass)
    {
        if (pass == DefaultTerrainRenderPasses.SOLID) return "solid";
        if (pass == DefaultTerrainRenderPasses.CUTOUT) return "cutout";
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) return "translucent";

        return String.valueOf(pass);
    }

    /** Whether an Iris shader pack is shading the world; false without Iris. */
    private static boolean isShaderPackInUse()
    {
        if (!FabricLoader.getInstance().isModLoaded("iris"))
        {
            return false;
        }

        try
        {
            return IrisUtils.isShaderPackEnabled();
        }
        catch (Throwable e)
        {
            return false;
        }
    }

    /**
     * How many sections were picked, how many of them hold geometry, and how many have their centre in
     * front of the eye the pass draws from (w above zero after the pass's projection) - the ones the
     * vertex shader can put on the screen at all.
     */
    private static String countSections(ObjectArrayList<ChunkRenderList> picked, Matrix4f projection, Matrix4f rotation, Vec3d eye)
    {
        Matrix4f clip = new Matrix4f(projection).mul(rotation);
        Vector4f point = new Vector4f();
        int sections = 0;
        int geometry = 0;
        int front = 0;

        for (ChunkRenderList list : picked)
        {
            RenderRegion region = list.getRegion();

            for (int i = 0; i < RenderRegion.REGION_SIZE; i++)
            {
                RenderSection section = region.getSection(i);

                if (section == null || section.isDisposed() || !section.isBuilt())
                {
                    continue;
                }

                sections += 1;

                if ((section.getFlags() & 1) == 0)
                {
                    continue;
                }

                geometry += 1;

                clip.transform(point.set((float) (section.getCenterX() - eye.x), (float) (section.getCenterY() - eye.y), (float) (section.getCenterZ() - eye.z), 1F));

                if (point.w > 0F)
                {
                    front += 1;
                }
            }
        }

        return "regionSections=" + sections + ", withGeometry=" + geometry + ", inFrontOfEye=" + front;
    }

    /** The GL state a draw would go out with right now. */
    private static String describeDrawState()
    {
        try
        {
            int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            String stencil;

            if (framebuffer == 0)
            {
                stencil = "default";
            }
            else
            {
                int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                int depth = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

                stencil = (type == GL11.GL_NONE ? "none" : "yes") + ", depthTex=" + depth;
            }

            int main = MinecraftClient.getInstance().getFramebuffer() == null ? -1 : MinecraftClient.getInstance().getFramebuffer().getDepthAttachment();
            int[] box = new int[4];

            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);

            ByteBuffer mask;

            try (MemoryStack stack = MemoryStack.stackPush())
            {
                mask = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);

                return "program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", fbo=" + framebuffer + " (stencil=" + stencil + ", mainDepthTex=" + main + ")"
                    + ", stencilTest=" + GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
                    + " func=0x" + Integer.toHexString(GL11.glGetInteger(GL11.GL_STENCIL_FUNC)) + " ref=" + GL11.glGetInteger(GL11.GL_STENCIL_REF)
                    + ", depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST) + " func=0x" + Integer.toHexString(GL11.glGetInteger(GL11.GL_DEPTH_FUNC))
                    + " mask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                    + ", scissor=" + GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) + " [" + box[0] + "," + box[1] + "," + box[2] + "," + box[3] + "]"
                    + ", colorMask=" + mask.get(0) + mask.get(1) + mask.get(2) + mask.get(3)
                    + ", blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                    + ", cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE);
            }
        }
        catch (Throwable e)
        {
            return "unreadable: " + e;
        }
        finally
        {
            GL11.glGetError();
        }
    }

    /**
     * Whether Sodium's render device is inside its managed block already. Sodium keeps the flag to
     * itself; asking for a command list is the one public way to find out, and it costs nothing, as
     * the device hands out the same list every time.
     */
    private static boolean isDeviceActive()
    {
        try
        {
            RenderDevice.INSTANCE.createCommandList();

            return true;
        }
        catch (IllegalStateException e)
        {
            return false;
        }
    }

    /** The world position the moved scene is seen from, in the coordinates of the real camera. */
    private static Vec3d virtualEye(Matrix4f modelView, Vec3d camera)
    {
        Matrix3f linear = new Matrix3f(modelView);

        if (Math.abs(linear.determinant()) < 1.0E-8F)
        {
            return camera;
        }

        Vector3f offset = modelView.getTranslation(new Vector3f());

        linear.invert();
        linear.transform(offset);

        return new Vec3d(camera.x - offset.x, camera.y - offset.y, camera.z - offset.z);
    }

    /** Every built section in front of the window and near it, grouped by region, the nearest region first. */
    private static ObjectArrayList<ChunkRenderList> pick(RenderRegionManager regions, Matrix4f view, Matrix4f source, Vec3d camera, float limit)
    {
        Matrix4f toLocal = new Matrix4f(source).invert().mul(view);
        Vector3f center = new Vector3f();
        List<Entry> entries = new ArrayList<>();

        for (RenderRegion region : regions.getLoadedRegions())
        {
            ChunkRenderList list = null;
            float nearest = Float.MAX_VALUE;

            for (int i = 0; i < RenderRegion.REGION_SIZE; i++)
            {
                RenderSection section = region.getSection(i);

                if (section == null || section.isDisposed() || !section.isBuilt())
                {
                    continue;
                }

                toLocal.transformPosition((float) (section.getCenterX() - camera.x), (float) (section.getCenterY() - camera.y), (float) (section.getCenterZ() - camera.z), center);

                /* Only what is in front of the window is seen through its partner, and not far from it */
                if (center.z < -14F || center.length() > limit)
                {
                    continue;
                }

                if (list == null)
                {
                    list = new ChunkRenderList(region);
                    list.reset(0);
                }

                list.add(section);
                nearest = Math.min(nearest, center.lengthSquared());
            }

            if (list != null)
            {
                entries.add(new Entry(list, nearest));
            }
        }

        entries.sort(Comparator.comparingDouble(Entry::distance));

        ObjectArrayList<ChunkRenderList> lists = new ObjectArrayList<>(entries.size());

        for (Entry entry : entries)
        {
            lists.add(entry.list());
        }

        return lists;
    }
}

