package mchorse.bbs_mod.forms;

import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Points the solid and cutout terrain of a portal pass at the colour buffers an Iris shader pack
 * composites. Loaded by {@link SodiumPortalBridge} only while Iris is loaded.
 *
 * <p>Iris keeps two textures per colour buffer and flips between them as the frame goes on: after
 * the deferred passes, which run as translucents begin, a pack's composite reads the other texture
 * of every buffer the deferred passes wrote. Iris's programs for entities follow the flip, but the
 * framebuffers it builds for Sodium's solid and cutout terrain are fixed to the side before it. The
 * portal pass runs late in the frame, after the flip, so its terrain went into textures nothing
 * read again: the samples were there and the screen showed none of them.</p>
 *
 * <p>So for those two passes the framebuffer Iris bound is swapped, after it is bound and before
 * anything is drawn, for one Iris builds the same way with the flip as it stands after translucents:
 * the same colour buffers in the same order, which the terrain program's outputs land in one to
 * one, and the same depth-and-stencil texture. Iris owns it and frees it with its pipeline.</p>
 */
final class IrisPortalTerrain
{
    private static final Map<WorldRenderingPipeline, Map<String, GlFramebuffer>> framebuffers = new WeakHashMap<>();

    private static Field renderTargetsField;
    private static Field depthVersionField;
    private static boolean versionFailed;
    private static boolean failed;

    private IrisPortalTerrain()
    {}

    /**
     * Rebinds the bound terrain framebuffer to its after-translucent twin when the pipeline is past
     * the flip. Returns the id of the framebuffer bound in its place, or 0 when nothing was changed.
     */
    static int retarget()
    {
        if (failed)
        {
            return 0;
        }

        try
        {
            WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

            if (!(pipeline instanceof IrisRenderingPipeline iris) || iris.isBeforeTranslucent)
            {
                return 0;
            }

            RenderTargets targets = renderTargets(iris);

            if (targets == null)
            {
                return 0;
            }

            int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int[] drawBuffers = colorBuffersOf(targets);

            if (bound == 0 || drawBuffers == null || drawBuffers.length == 0)
            {
                return 0;
            }

            StringBuilder key = new StringBuilder().append(bound);

            for (int buffer : drawBuffers)
            {
                RenderTarget target = targets.get(buffer);

                key.append(':').append(buffer).append('/').append(target.getMainTexture()).append('/').append(target.getAltTexture());
            }

            Map<String, GlFramebuffer> known = framebuffers.computeIfAbsent(pipeline, (p) -> new HashMap<>());
            GlFramebuffer framebuffer = known.get(key.toString());

            if (framebuffer == null)
            {
                ImmutableSet<Integer> flipped = iris.getFlippedAfterTranslucent();

                framebuffer = targets.createGbufferFramebuffer(flipped, drawBuffers);
                known.put(key.toString(), framebuffer);
            }

            framebuffer.bind();

            return framebuffer.getId();
        }
        catch (Throwable e)
        {
            failed = true;

            System.err.println("[BBS] Pointing portal terrain at the shader pack's buffers failed, and is left out from now on: " + e);
            e.printStackTrace();

            return 0;
        }
        finally
        {
            GL11.glGetError();
        }
    }

    /**
     * The colour buffer (colortexN) behind each draw buffer of the bound framebuffer, in draw buffer
     * order, found by the texture attached there. Null when one of them is not a buffer of the pack.
     */
    private static int[] colorBuffersOf(RenderTargets targets)
    {
        int max = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        int[] found = new int[max];
        int count = 0;

        for (int i = 0; i < max; i++)
        {
            int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i);

            if (attachment == GL11.GL_NONE)
            {
                break;
            }

            int texture = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int buffer = bufferOf(targets, texture);

            if (buffer < 0)
            {
                return null;
            }

            found[count++] = buffer;
        }

        int[] result = new int[count];

        System.arraycopy(found, 0, result, 0, count);

        return result;
    }

    private static int bufferOf(RenderTargets targets, int texture)
    {
        if (texture <= 0)
        {
            return -1;
        }

        for (int i = 0; i < targets.getRenderTargetCount(); i++)
        {
            RenderTarget target = targets.get(i);

            if (target != null && (target.getMainTexture() == texture || target.getAltTexture() == texture))
            {
                return i;
            }
        }

        return -1;
    }

    /**
     * Tells Iris the framebuffer's depth texture was rebuilt, after it was given a stencil. Iris keeps
     * a depth buffer version on the game's framebuffer, raised when the game rebuilds its buffers, and
     * attaches the depth texture to every framebuffer of its pipeline again only when that version
     * changes; a change of format alone it lets pass. Raised here, the pipeline attaches it again as it
     * starts the next frame, as depth-and-stencil now, and all its programs share the one stencil.
     */
    static void depthRebuilt(net.minecraft.client.gl.Framebuffer framebuffer)
    {
        if (versionFailed || framebuffer == null)
        {
            return;
        }

        try
        {
            if (depthVersionField == null)
            {
                depthVersionField = findField(framebuffer.getClass(), "iris$depthBufferVersion");
                depthVersionField.setAccessible(true);
            }

            depthVersionField.setInt(framebuffer, depthVersionField.getInt(framebuffer) + 1);
        }
        catch (Throwable e)
        {
            versionFailed = true;

            System.err.println("[BBS] Telling Iris about the portal stencil failed; reload the shader pack (F3+R) if portals spill out of their windows: " + e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException
    {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
        {
            try
            {
                return c.getDeclaredField(name);
            }
            catch (NoSuchFieldException e)
            {}
        }

        /* Mixin may rename a unique field when its name is taken; the rename keeps the name in it */
        for (Class<?> c = type; c != null; c = c.getSuperclass())
        {
            for (Field field : c.getDeclaredFields())
            {
                if (field.getType() == int.class && field.getName().contains("depthBufferVersion"))
                {
                    return field;
                }
            }
        }

        throw new NoSuchFieldException(name);
    }

    private static RenderTargets renderTargets(IrisRenderingPipeline pipeline) throws ReflectiveOperationException
    {
        if (renderTargetsField == null)
        {
            renderTargetsField = IrisRenderingPipeline.class.getDeclaredField("renderTargets");
            renderTargetsField.setAccessible(true);
        }

        return (RenderTargets) renderTargetsField.get(pipeline);
    }
}
