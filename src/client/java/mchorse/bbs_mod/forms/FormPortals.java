package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.utils.ModelViewStack;
import mchorse.bbs_mod.utils.Bufs;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.PortalForm;
import mchorse.bbs_mod.mixin.client.WorldRendererAccessor;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.utils.colors.Color;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two-way portals, in the manner of Portal 2. Two portal forms of one frequency are each other's
 * other end ({@link #getLinked}); a third is refused and shows up in red.
 *
 * <p><b>Seeing through.</b> Each window shows the scene as seen from its partner: the scene is drawn
 * again inside the window, moved by {@code A * turn * B^-1} (a point in front of B lands behind A),
 * which is the partner's view of it without a second camera. A window is cut into the stencil, the
 * world's depth inside it is replaced by the window's own - so what is seen is only what lies behind
 * the window, and the wall it is set in does not hide it - and its colour by the fog colour, as the
 * background of what is drawn. Only the films' forms are drawn again, not the terrain or vanilla
 * entities.</p>
 *
 * <p><b>Going through.</b> Each tick, a player whose body centre crosses a window from its front to
 * its back, inside its outline, is moved to the partner by the same transform; velocity and view are
 * turned by its rotation, so what goes in at one comes out of the other at the same speed, thrown out
 * along the partner's facing.</p>
 *
 * <p>The stencil is used in three steps, all inside one {@link #flush()} and all undone before it
 * returns: the window is cut into the stencil with colour and depth writes off; the scene is drawn where
 * the stencil matches; and the test is turned off and the buffer cleared again. The buffer being drawn into
 * has to have a stencil. The game's main framebuffer does not, so the first time a portal finds none it
 * asks for one, and the next frame begins with a stencil on it.</p>
 */
public class FormPortals
{
    private static final int SEGMENTS = 48;
    private static final float FRAME_WIDTH = 0.02F;
    private static final float FRAME_OFFSET = 0.004F;
    private static final int GUARD_TICKS = 60;

    /** The longest a player moves in a tick and still counts as walking, falling or flying into a portal */
    private static final double MAX_STEP = 4D;
    private static final float GUARD_CLEAR = 0.5F;
    private static final int STALE = 10;
    private static final float OBLIQUE_EPSILON = 0.001F;

    /** How far from the partner, in chunks, the terrain is drawn again through a window */
    private static final int TERRAIN_CHUNKS = 6;

    /** How far past a window's plane and outline the player still counts as in it */
    private static final float PLAYER_MARGIN = 0.5F;

    /** Portal forms seen drawing lately, in the order they were first seen: the pairing's source of truth. */
    private static final Map<PortalForm, Live> live = new LinkedHashMap<>();
    private static final Set<PortalForm> conflicts = new HashSet<>();
    private static final List<Window> windows = new ArrayList<>();

    private static WorldRenderContext context;
    private static boolean passActive;
    private static Matrix4f passProjection;
    private static boolean wantStencil;
    private static boolean warned;
    private static boolean terrainFailed;

    /* Sodium owns the chunk rendering when it is loaded; its bridge is never touched otherwise */
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private static long ticks;
    private static PortalForm guard;
    private static int guardAge;

    /** The speed a jump left the player with, to be given back if the server's confirmation takes it */
    private static Vector3f pendingSpeed;
    private static int pendingTicks;

    /** What the portal form was when it last drew, in the world's own frame. */
    private static class Live
    {
        private final Matrix4f world = new Matrix4f();
        private boolean placed;
        private int frequency;
        private int shape;
        private float width;
        private float height;
        private long seen;
    }

    /** A window of the pass being drawn, in the frame it was drawn in. */
    private record Window(PortalForm form, Matrix4f matrix, int shape, float width, float height, int frequency)
    {}

    /* Pairing */

    private static List<PortalForm> sameFrequency(int frequency)
    {
        List<PortalForm> list = new ArrayList<>();

        for (Map.Entry<PortalForm, Live> entry : live.entrySet())
        {
            if (entry.getValue().frequency == frequency)
            {
                list.add(entry.getKey());
            }
        }

        return list;
    }

    /** The portal at the other end of this one, or null when it has none (alone on its frequency, or the third). */
    public static PortalForm getLinked(PortalForm form)
    {
        Live self = live.get(form);

        if (self == null)
        {
            return null;
        }

        List<PortalForm> same = sameFrequency(self.frequency);

        if (same.size() < 2 || !same.subList(0, 2).contains(form))
        {
            return null;
        }

        return same.get(0) == form ? same.get(1) : same.get(0);
    }

    /** Which end of its pair the portal is: 1 for the first (blue), 2 for the second (orange), 0 for none. */
    public static int getRole(PortalForm form)
    {
        Live self = live.get(form);

        if (self == null)
        {
            return 0;
        }

        List<PortalForm> same = sameFrequency(self.frequency);

        if (same.size() < 2)
        {
            return 0;
        }

        int index = same.subList(0, 2).indexOf(form);

        return index + 1;
    }

    /** Whether the portal is a third on its frequency, which has no partner. */
    public static boolean isConflicted(PortalForm form)
    {
        return conflicts.contains(form);
    }

    /**
     * Whether a form that is not already on this frequency may be given it: not when two portals
     * have it already. Based on what has been drawn lately.
     */
    public static boolean isTaken(PortalForm form, int frequency)
    {
        Live self = live.get(form);

        if (self != null && self.frequency == frequency)
        {
            return false;
        }

        return sameFrequency(frequency).size() >= 2;
    }

    /* Frame bookkeeping */

    public static void setContext(WorldRenderContext context)
    {
        FormPortals.context = context;
    }

    /**
     * A matrix with only its place and turning: scale and shear dropped. A portal may be sized by its
     * form's scale or hung on a scaled parent, and the world seen through it is still the world at
     * its natural size - the windows keep their size, what is seen through them does not take it on.
     */
    private static Matrix4f rigid(Matrix4f matrix)
    {
        Quaternionf rotation = matrix.getNormalizedRotation(new Quaternionf());

        return new Matrix4f().translation(matrix.getTranslation(new Vector3f())).rotate(rotation);
    }

    /** Whether the scene is being drawn again through a window, where nothing may be culled by the real view. */
    public static boolean isPassActive()
    {
        return passActive;
    }

    /** Whether a portal's own scene is being drawn, in which no portal draws its frame or arrow. */
    public static boolean isRenderingPortal()
    {
        return passActive;
    }

    /**
     * Called as a frame begins. Drops what an aborted pass left behind, and gives the game's
     * framebuffer the stencil a portal asked for.
     */
    public static void beginFrame()
    {
        windows.clear();

        /* A stencil that outlives its frame is a ghost on the next: masks leave none, but this
         * is the one point nothing can be mid-draw */
        if (hasStencil())
        {
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        if (wantStencil)
        {
            wantStencil = false;

            addStencil(MinecraftClient.getInstance().getFramebuffer());
        }
    }

    /**
     * Gives a framebuffer a stencil, by turning its depth texture into a depth-and-stencil one. The
     * game builds its framebuffers without, and has no way to add it later. Called as a frame begins,
     * when the depth buffer has just been cleared and holds nothing to lose; it is cleared again here,
     * as the new storage starts out undefined. A resize builds the framebuffer afresh, without the
     * stencil, and the next portal asks for it again.
     */
    private static void addStencil(Framebuffer framebuffer)
    {
        if (framebuffer == null || !framebuffer.useDepthAttachment || framebuffer.getDepthAttachment() <= 0)
        {
            return;
        }

        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        RenderSystem.activeTexture(GL13.GL_TEXTURE0);

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer.fbo);
            GlStateManager._bindTexture(framebuffer.getDepthAttachment());
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, framebuffer.textureWidth, framebuffer.textureHeight, 0, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, 0L);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, framebuffer.getDepthAttachment(), 0);

            RenderSystem.depthMask(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        finally
        {
            GlStateManager._bindTexture(previousTexture);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glGetError();
        }
    }

    /** A portal form drew this frame: remember where it stands, for the pairing and for the ticks. */
    public static void seen(PortalForm form, Matrix4f world)
    {
        Live entry = live.computeIfAbsent(form, (f) -> new Live());

        if (world != null)
        {
            entry.world.set(world);
            entry.placed = true;
        }

        entry.frequency = form.frequency.get();
        entry.shape = form.shape.get();
        entry.width = form.width.get();
        entry.height = form.height.get();
        entry.seen = ticks;
    }

    /** A portal form's window, at the frame it has been drawn in; drawn when the pass closes. */
    public static void addWindow(PortalForm form, Matrix4f matrix)
    {
        windows.add(new Window(form, new Matrix4f(matrix), form.shape.get(), form.width.get(), form.height.get(), form.frequency.get()));
    }

    /* Teleporting */

    /** Whether a point of a window's plane, in the window's own frame, lies inside its outline. */
    private static boolean inside(int shape, float width, float height, float x, float y)
    {
        float hw = width / 2F;
        float hh = height / 2F;

        if (hw <= 0F || hh <= 0F)
        {
            return false;
        }

        if (shape == PortalForm.SHAPE_CIRCLE)
        {
            return (x * x) / (hw * hw) + (y * y) / (hh * hh) <= 1F;
        }

        return Math.abs(x) <= hw && Math.abs(y) <= hh;
    }

    /**
     * Once per client tick: forget portals that stopped drawing, find the third portals, and send the
     * player through a window they have crossed.
     */
    public static void tick(MinecraftClient mc)
    {
        ticks += 1;

        Iterator<Live> it = live.values().iterator();

        while (it.hasNext())
        {
            if (ticks - it.next().seen > STALE)
            {
                it.remove();
            }
        }

        conflicts.clear();

        Map<Integer, Integer> counts = new HashMap<>();

        for (Map.Entry<PortalForm, Live> entry : live.entrySet())
        {
            if (counts.merge(entry.getValue().frequency, 1, Integer::sum) > 2)
            {
                conflicts.add(entry.getKey());
            }
        }

        if (pendingTicks > 0 && --pendingTicks == 0)
        {
            pendingSpeed = null;
        }

        ClientPlayerEntity player = mc.player;

        if (player == null || mc.world == null || mc.isPaused() || mc.currentScreen != null || player.isSpectator())
        {
            return;
        }

        updateGuard(player);

        for (PortalForm form : new ArrayList<>(live.keySet()))
        {
            PortalForm other = getLinked(form);

            if (other != null && crossing(player, form, other))
            {
                return;
            }
        }
    }

    /** The player's centre, in the frame of a portal's world matrix with its scale and shear dropped. */
    private static Vector3f local(Live portal, ClientPlayerEntity player, double lift)
    {
        return rigid(portal.world).invert().transformPosition(new Vector3f((float) player.getX(), (float) (player.getY() + lift), (float) player.getZ()));
    }

    /**
     * Sends the player from {@code from} to {@code to} if their centre crossed {@code from}'s plane
     * this tick, from its front to its back, through its outline. The player keeps their place in the
     * window, their distance from its plane and their velocity, all turned by the two portals' difference
     * of rotation - nothing is added, nothing is delayed.
     */
    private static boolean crossing(ClientPlayerEntity player, PortalForm from, PortalForm to)
    {
        Live a = live.get(from);
        Live b = live.get(to);

        if (!a.placed || !b.placed || from == guard)
        {
            return false;
        }

        double lift = player.getHeight() / 2D;
        Matrix4f aInverse = new Matrix4f(a.world).invert();
        Vector3f before = aInverse.transformPosition(new Vector3f((float) player.prevX, (float) (player.prevY + lift), (float) player.prevZ));
        Vector3f now = aInverse.transformPosition(new Vector3f((float) player.getX(), (float) (player.getY() + lift), (float) player.getZ()));

        /* From the front to the back, through the outline */
        if (!(before.z > 0F && now.z <= 0F))
        {
            return false;
        }

        if (player.squaredDistanceTo(player.prevX, player.prevY, player.prevZ) > MAX_STEP * MAX_STEP)
        {
            return false;
        }

        float t = before.z / (before.z - now.z);

        if (!inside(a.shape, a.width, a.height, before.x + (now.x - before.x) * t, before.y + (now.y - before.y) * t))
        {
            return false;
        }

        /* B * turn * A^-1: the same point of the other window, facing out of it */
        Matrix4f move = rigid(b.world).rotateY((float) Math.PI).mul(rigid(a.world).invert());
        Quaternionf rotation = move.getUnnormalizedRotation(new Quaternionf());
        Vector3f center = move.transformPosition(new Vector3f((float) player.getX(), (float) (player.getY() + lift), (float) player.getZ()));
        Vec3d velocity = player.getVelocity();
        Vector3f speed = rotation.transform(new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z));
        Vec3d look = player.getRotationVec(1F);
        Vector3f direction = rotation.transform(new Vector3f((float) look.x, (float) look.y, (float) look.z));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1F, Math.min(1F, direction.y))));
        double x = center.x;
        double y = center.y - lift;
        double z = center.z;

        player.refreshPositionAndAngles(x, y, z, yaw, pitch);
        player.setVelocity(speed.x, speed.y, speed.z);
        player.fallDistance = 0F;
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        ClientNetwork.sendTeleport(x, y, z, yaw, yaw, pitch);

        pendingSpeed = new Vector3f(speed);
        pendingTicks = 40;

        /* The window they came out of lets them go before it takes them again */
        guard = to;
        guardAge = 0;

        return true;
    }

    /**
     * The window the player came out of is one-way until they have left it: it does not take them back
     * while they stand in its plane. Left behind once they are clear of it in front, off to its side,
     * or after a while.
     */
    private static void updateGuard(ClientPlayerEntity player)
    {
        if (guard == null)
        {
            return;
        }

        Live portal = live.get(guard);
        guardAge += 1;

        if (portal == null || guardAge > GUARD_TICKS)
        {
            guard = null;

            return;
        }

        Vector3f at = local(portal, player, player.getHeight() / 2D);
        Vector3f scale = portal.world.getScale(new Vector3f());

        if (at.z > GUARD_CLEAR || Math.abs(at.x) > portal.width * scale.x / 2F + GUARD_CLEAR || Math.abs(at.y) > portal.height * scale.y / 2F + GUARD_CLEAR)
        {
            guard = null;
        }
    }

    /** Whether the portal is inside the form tree somewhere: itself, or hung on it as a part. */
    public static boolean hasPortal(Form form)
    {
        if (form == null)
        {
            return false;
        }

        if (form instanceof PortalForm)
        {
            return true;
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            if (hasPortal(part.getForm()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The server answered a jump with a position packet, which may have left the player standing
     * still: gives back the speed they left the portal with, once.
     */
    public static void onServerPosition()
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (pendingSpeed == null || player == null)
        {
            return;
        }

        Vec3d velocity = player.getVelocity();

        if (velocity.length() < pendingSpeed.length() * 0.5F)
        {
            player.setVelocity(pendingSpeed.x, pendingSpeed.y, pendingSpeed.z);
        }

        pendingSpeed = null;
        pendingTicks = 0;
    }

    /* Drawing */

    /** Whether the framebuffer being drawn into has a stencil buffer - form masks use it as well. */
    public static boolean stencilReady()
    {
        return hasStencil();
    }

    /** A stencil renderbuffer given to a framebuffer that is not the game's: its id, the buffer (or -1 for a driver that refused), and its size. */
    private static final java.util.Map<Integer, int[]> attachedStencils = new java.util.HashMap<>();

    /**
     * Makes sure the framebuffer being drawn into has a stencil, now if it can be done now.
     *
     * <p>The game's own framebuffer gets it by the depth texture becoming a depth-and-stencil one,
     * which empties the depth buffer - so only when {@code depthIsEmpty} says there is nothing in
     * it worth keeping (a viewport that has just cleared it). Any other framebuffer - the film
     * preview may draw into one of its own - is given a stencil-only renderbuffer beside its depth,
     * leaving the depth as it is. Returns whether the framebuffer has a stencil afterwards.</p>
     */
    public static boolean ensureStencil(boolean depthIsEmpty)
    {
        if (hasStencil())
        {
            return true;
        }

        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        int bound = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        if (main != null && bound == main.fbo)
        {
            if (!depthIsEmpty)
            {
                return false;
            }

            addStencil(main);

            return hasStencil();
        }

        if (bound == 0)
        {
            return false;
        }

        attachStencilRenderbuffer(bound);

        return hasStencil();
    }

    private static void attachStencilRenderbuffer(int framebuffer)
    {
        int width = 0;
        int height = 0;
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);

        try
        {
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int name = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

            if (type == GL11.GL_TEXTURE)
            {
                GlStateManager._bindTexture(name);

                width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            }
            else if (type == GL30.GL_RENDERBUFFER)
            {
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, name);

                width = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_WIDTH);
                height = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_HEIGHT);
            }

            if (width <= 0 || height <= 0)
            {
                return;
            }

            int[] known = attachedStencils.get(framebuffer);

            if (known != null && known[0] < 0 && known[1] == width && known[2] == height)
            {
                /* The driver said no to this size once; asking every frame would not change it */
                return;
            }

            int buffer;

            if (known != null && known[0] > 0 && known[1] == width && known[2] == height)
            {
                buffer = known[0];
            }
            else
            {
                if (known != null && known[0] > 0)
                {
                    GL30.glDeleteRenderbuffers(known[0]);
                }

                buffer = GL30.glGenRenderbuffers();

                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, buffer);
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_STENCIL_INDEX8, width, height);
            }

            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, buffer);

            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
            {
                GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, 0);
                GL30.glDeleteRenderbuffers(buffer);

                attachedStencils.put(framebuffer, new int[] {-1, width, height});

                System.err.println("[BBS] Framebuffer " + framebuffer + " would not take a stencil buffer; form masks are drawn whole there.");

                return;
            }

            attachedStencils.put(framebuffer, new int[] {buffer, width, height});
        }
        finally
        {
            GlStateManager._bindTexture(previousTexture);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            GL11.glGetError();
        }
    }

    /** Asks for a stencil to be added as the next frame begins; masks wait for it. */
    public static void requestStencil()
    {
        wantStencil = true;
    }

    /** Whether the framebuffer being drawn into has a stencil buffer. */
    private static boolean hasStencil()
    {
        try
        {
            int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

            if (framebuffer == 0)
            {
                return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL11.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE) > 0;
            }

            return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_NONE;
        }
        catch (Throwable e)
        {
            return false;
        }
        finally
        {
            /* A query the driver did not like must not show up as somebody else's error */
            GL11.glGetError();
        }
    }

    /**
     * Draw every linked window of the pass with the scene as its partner sees it. Called when the
     * render scope closes, after the forms set to render last.
     */
    public static void flush()
    {
        /* The scene seen through a window is drawn once, and holds no windows of its own */
        if (passActive)
        {
            return;
        }

        if (windows.isEmpty())
        {
            return;
        }

        List<Window> pass = new ArrayList<>(windows);

        windows.clear();

        if (context == null)
        {
            return;
        }

        place(pass);

        /* The first two windows of a frequency are a pair; a third has no partner */
        Map<Integer, List<Window>> pairs = new HashMap<>();

        for (Window window : pass)
        {
            List<Window> pair = pairs.computeIfAbsent(window.frequency(), (f) -> new ArrayList<>());

            if (pair.size() < 2)
            {
                pair.add(window);
            }
        }

        pairs.values().removeIf((pair) -> pair.size() < 2);

        if (pairs.isEmpty())
        {
            return;
        }

        if (!hasStencil())
        {
            wantStencil = true;

            if (!warned)
            {
                warned = true;

                System.err.println("[BBS] The framebuffer has no stencil buffer; asking for one. Portal windows show nothing until the next frame.");
            }

            return;
        }

        boolean queue = FormTranslucentQueue.suspend();
        RenderState before = RenderState.capture();

        try
        {
            /* Whatever the stencil held is not ours to keep */
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

            for (List<Window> pair : pairs.values())
            {
                drawThrough(pair.get(0), pair.get(1));
                drawThrough(pair.get(1), pair.get(0));
            }
        }
        finally
        {
            FormTranslucentQueue.restore(queue);

            /* The pass hands the world's state back as it took it - not as it last left it */
            before.restore();
        }
    }

    /**
     * What the pass changes of the graphics state, taken before it begins and given back when it ends:
     * every window of the pass writes depth, colour and stencil in ways that must not outlast it. The
     * depth function and the depth mask are the world's own - less-or-equal, writing - whatever they
     * were found at.
     */
    private static class RenderState
    {
        private int framebuffer;
        private boolean depthTest;
        private boolean cull;
        private boolean blend;
        private boolean stencilTest;
        private final boolean[] colorMask = {true, true, true, true};
        private final float[] depthRange = {0F, 1F};
        private final int[] viewport = new int[4];

        static RenderState capture()
        {
            RenderState state = new RenderState();

            state.framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            state.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            state.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            state.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            state.stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);

            java.nio.ByteBuffer mask = org.lwjgl.BufferUtils.createByteBuffer(4);

            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);

            for (int i = 0; i < 4; i++)
            {
                state.colorMask[i] = mask.get(i) != 0;
            }

            java.nio.FloatBuffer range = org.lwjgl.BufferUtils.createFloatBuffer(2);

            GL11.glGetFloatv(GL11.GL_DEPTH_RANGE, range);

            state.depthRange[0] = range.get(0);
            state.depthRange[1] = range.get(1);

            java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(4);

            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

            for (int i = 0; i < 4; i++)
            {
                state.viewport[i] = viewport.get(i);
            }

            return state;
        }

        void restore()
        {
            /* Drawn into the framebuffer it was found at, and into no other */
            if (GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) != this.framebuffer)
            {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
            }

            RenderSystem.colorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            GL11.glDepthRange(this.depthRange[0], this.depthRange[1]);
            RenderSystem.viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);

            GL11.glStencilMask(0xFF);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            if (this.stencilTest)
            {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            }
            else
            {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }

            if (this.depthTest)
            {
                RenderSystem.enableDepthTest();
            }
            else
            {
                RenderSystem.disableDepthTest();
            }

            if (this.cull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            if (this.blend)
            {
                RenderSystem.enableBlend();
            }
            else
            {
                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * Where each window stands in the world, from the matrix it was drawn with and the view it was drawn
     * in: what the teleport needs, and what a form drawn by a block entity cannot give, as its world
     * frame lacks the block's own transform.
     */
    private static void place(List<Window> pass)
    {
        Vec3d camera = context.camera() == null ? Vec3d.ZERO : context.camera().getPos();
        Matrix4f toWorld = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(new Matrix4f(context.matrixStack().peek().getPositionMatrix()).invert());

        for (Window window : pass)
        {
            Live entry = live.get(window.form());

            if (entry != null)
            {
                entry.world.set(toWorld).mul(window.matrix());
                entry.placed = true;
            }
        }
    }

    /** The window {@code target}, showing what its partner {@code source} looks out on. */
    private static void drawThrough(Window target, Window source)
    {
        if (passActive || target.width() <= 0F || target.height() <= 0F)
        {
            return;
        }

        /* Takes a point in front of the source to behind the target */
        Matrix4f sourceRigid = rigid(source.matrix());
        Matrix4f move = rigid(target.matrix()).rotateY((float) Math.PI).mul(new Matrix4f(sourceRigid).invert());

        if (!Float.isFinite(move.m00()) || !Float.isFinite(move.m33()))
        {
            return;
        }

        /* The scene is to show only behind the window: its plane becomes the near plane. Seen from behind there is no window to look through */
        Matrix4f oblique = obliqueProjection(target.matrix());

        if (oblique == null)
        {
            return;
        }

        /* Below the bit a form mask takes inside the pass (FormMasking.NESTED_BIT), so the two never share one */
        int reference = Math.max(1, Math.min(FormMasking.NESTED_BIT - 1, target.frequency()));
        float[] fog = RenderSystem.getShaderFogColor();

        /* The scissor is the second lock after the stencil: nothing of the scene seen through a window
         * can land outside the window's own rectangle on the screen, whatever else happens to the
         * stencil test on the way. A window entirely off the screen has nothing to draw. */
        int[] scissor = windowScissor(target);

        if (scissor != null && scissor[2] <= 0)
        {
            return;
        }

        boolean wasScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] wasBox = new int[4];

        {
            java.nio.IntBuffer box = org.lwjgl.BufferUtils.createIntBuffer(4);

            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);

            for (int i = 0; i < 4; i++)
            {
                wasBox[i] = box.get(i);
            }
        }

        try
        {
            if (scissor != null)
            {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();

            /* A: cut the window into the stencil, writing neither colour nor depth. The stencil is
             * written only where the window is in front of what is already there. */
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            GL11.glStencilFunc(GL11.GL_ALWAYS, reference, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);

            drawWindow(target, 1F, 1F, 1F);

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);

            /* B: from here on only the window can be drawn on. The window takes the place of whatever
             * is in it - the wall it is set in - with the fog colour for a backdrop and the far depth,
             * so that all the scene behind it passes the depth test; what lies in front of the window
             * plane is cut by the oblique near plane instead. */
            GL11.glStencilFunc(GL11.GL_EQUAL, reference, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glStencilMask(0x00);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);

            GL11.glDepthRange(1D, 1D);
            drawWindow(target, fog[0], fog[1], fog[2]);
            GL11.glDepthRange(0D, 1D);

            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();

            drawScene(move, sourceRigid, oblique, rigid(target.matrix()), target, reference);
        }
        finally
        {
            /* C: nothing of it stays behind. The scissor goes first: it would cut the clearing of the
             * stencil short as well. */
            if (wasScissor)
            {
                GL11.glScissor(wasBox[0], wasBox[1], wasBox[2], wasBox[3]);
            }
            else
            {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            GL11.glDepthRange(0D, 1D);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }
    }

    /**
     * The window's rectangle on the screen, in the pixels of the viewport: {x, y, width, height}, with a
     * margin of a pixel or two. Empty (width 0) when it is entirely off the screen, and null when a corner
     * of it lies behind the eye, where there is no rectangle to speak of and the whole screen stays open.
     */
    private static int[] windowScissor(Window window)
    {
        Matrix4f clip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(window.matrix());
        float hw = window.width() / 2F;
        float hh = window.height() / 2F;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int i = 0; i < 4; i++)
        {
            Vector4f corner = clip.transform(new Vector4f((i & 1) == 0 ? -hw : hw, (i & 2) == 0 ? -hh : hh, 0F, 1F));

            if (!(corner.w > 1.0E-4F))
            {
                return null;
            }

            float x = corner.x / corner.w;
            float y = corner.y / corner.w;

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        if (!Float.isFinite(minX + maxX + minY + maxY))
        {
            return null;
        }

        if (maxX < -1F || minX > 1F || maxY < -1F || minY > 1F)
        {
            return new int[] {0, 0, 0, 0};
        }

        java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(4);

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        int vx = viewport.get(0);
        int vy = viewport.get(1);
        int vw = viewport.get(2);
        int vh = viewport.get(3);
        int x1 = Math.max(vx, vx + (int) Math.floor((Math.max(minX, -1F) * 0.5F + 0.5F) * vw) - 2);
        int x2 = Math.min(vx + vw, vx + (int) Math.ceil((Math.min(maxX, 1F) * 0.5F + 0.5F) * vw) + 2);
        int y1 = Math.max(vy, vy + (int) Math.floor((Math.max(minY, -1F) * 0.5F + 0.5F) * vh) - 2);
        int y2 = Math.min(vy + vh, vy + (int) Math.ceil((Math.min(maxY, 1F) * 0.5F + 0.5F) * vh) + 2);

        return new int[] {x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1)};
    }

    /**
     * The projection with its near plane moved onto the window's plane, so that only what lies behind
     * the window is drawn - the oblique near-plane clip. The scene is moved so that the partner's
     * surroundings land behind the window, but what lay behind the partner lands in front of it, between
     * the eye and the window, and has to go. Null when the eye is not in front of the window, which has
     * nothing to look through then.
     */
    private static Matrix4f obliqueProjection(Matrix4f window)
    {
        Vector3f origin = window.getTranslation(new Vector3f());
        Vector3f front = window.transformDirection(new Vector3f(0F, 0F, 1F)).normalize();

        /* The plane in eye space, positive on the far side of the window; the eye is the origin */
        Vector4f plane = new Vector4f(-front.x, -front.y, -front.z, front.dot(origin));

        if (!(plane.w < -0.001F))
        {
            return null;
        }

        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Vector4f corner = new Vector4f(plane.x >= 0F ? 1F : -1F, plane.y >= 0F ? 1F : -1F, 1F, 1F);

        projection.invert(new Matrix4f()).transform(corner);

        /* The scale is 2 over the plane at the frustum's far corner; when that is too small the matrix
         * would blow up, and the plain projection is used, which only lets what lies in front of the
         * window show */
        float dot = plane.dot(corner);

        if (!(dot > OBLIQUE_EPSILON))
        {
            return new Matrix4f(RenderSystem.getProjectionMatrix());
        }

        float scale = 2F / dot;
        Matrix4f oblique = new Matrix4f(projection)
            .m02(plane.x * scale - projection.m03())
            .m12(plane.y * scale - projection.m13())
            .m22(plane.z * scale - projection.m23())
            .m32(plane.w * scale - projection.m33());

        return Float.isFinite(oblique.m22()) && Float.isFinite(oblique.m32()) ? oblique : new Matrix4f(RenderSystem.getProjectionMatrix());
    }

    /**
     * The world's own terrain, its model blocks and the films' forms again, with the world moved by
     * {@code move} and nothing culled by the real view. {@code source} is the window the scene is seen
     * from, {@code projection} the one the real view has, cut at the window the scene is shown in.
     */
    /**
     * Locks what is drawn next to the window: the stencil test on, matching the window's value, writing
     * nothing. Set again before every part of the scene, so a part that changed the stencil test on the
     * way cannot carry the rest of the scene out of the window.
     */
    private static void lockToWindow(int reference)
    {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_EQUAL, reference, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glStencilMask(0x00);
    }

    private static void drawScene(Matrix4f move, Matrix4f source, Matrix4f projection, Matrix4f target, Window targetWindow, int reference)
    {
        MatrixStack stack = context.matrixStack();
        Matrix4f view = new Matrix4f(stack.peek().getPositionMatrix());
        Matrix4f realProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorter = RenderSystem.getVertexSorting();

        stack.push();
        stack.peek().getPositionMatrix().mulLocal(move);
        stack.peek().getNormalMatrix().mulLocal(move.normal(new Matrix3f()));

        passActive = true;
        passProjection = projection;

        RenderSystem.setProjectionMatrix(projection, sorter);

        try
        {
            lockToWindow(reference);
            drawTerrain(view, stack.peek().getPositionMatrix(), source);
            lockToWindow(reference);
            drawModelBlocks(view, source);

            lockToWindow(reference);
            BBSModClient.getFilms().render(context);

            float limit = Math.min(MinecraftClient.getInstance().options.getClampedViewDistance(), TERRAIN_CHUNKS) * 16F;

            lockToWindow(reference);
            drawPlayer(view, source, target, targetWindow, limit);
            lockToWindow(reference);
            drawEffects(view, move);
        }
        finally
        {
            /* The drain below still draws the pass's leftovers: it too stays in the window */
            lockToWindow(reference);

            /* Anything the pass left in the shared vertex buffers carries the moved scene's matrices; it is
             * drawn here, still locked to the window, or the world's own flush would draw it later without */
            try
            {
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
                FormUtilsClient.getProvider().draw();
            }
            catch (RuntimeException e)
            {
                System.err.println("[BBS] Draining the portal pass failed: " + e);
            }

            passActive = false;
            passProjection = null;

            RenderSystem.setProjectionMatrix(realProjection, sorter);

            stack.pop();

            RenderSystem.enableDepthTest();
        }
    }

    /**
     * The local player, drawn into the scene seen through a window, in two ways. Where the player really
     * stands, if that is in front of the window's partner: they are part of the world seen from there,
     * and drawn with the moved scene. And the part of them that has gone through the target window
     * already - the half of the body that is past its plane - where it really is, which the window
     * hides behind its fog and which the oblique near plane cuts down to just that half.
     */
    private static void drawPlayer(Matrix4f view, Matrix4f source, Matrix4f target, Window targetWindow, float limit)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null || context.camera() == null)
        {
            return;
        }

        try
        {
            Vec3d camera = context.camera().getPos();
            MatrixStack stack = context.matrixStack();
            float tickDelta = context./*$ wtd*/tickDelta()/**/;
            double lift = player.getHeight() / 2D;
            double x = MathHelper.lerp(tickDelta, player.prevX, player.getX());
            double y = MathHelper.lerp(tickDelta, player.prevY, player.getY());
            double z = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
            Vector3f rel = new Vector3f((float) (x - camera.x), (float) (y + lift - camera.y), (float) (z - camera.z));

            /* Where they stand, seen from the partner */
            Vector3f fromSource = new Matrix4f(source).invert().mul(view).transformPosition(new Vector3f(rel));
            boolean there = fromSource.z >= -PLAYER_MARGIN && fromSource.length() <= limit;

            /* The half of them through the window */
            Vector3f fromTarget = new Matrix4f(target).invert().mul(view).transformPosition(new Vector3f(rel));
            Vector3f scale = targetWindow.matrix().getScale(new Vector3f());
            boolean through = Math.abs(fromTarget.z) <= PLAYER_MARGIN + 0.5F
                && Math.abs(fromTarget.x) <= targetWindow.width() * scale.x / 2F + PLAYER_MARGIN
                && Math.abs(fromTarget.y) <= targetWindow.height() * scale.y / 2F + PLAYER_MARGIN;

            if (!there && !through)
            {
                return;
            }

            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();
            float yaw = MathHelper.lerp(tickDelta, player.prevYaw, player.getYaw());
            int light = dispatcher.getLight(player, tickDelta);

            if (there)
            {
                dispatcher.render(player, x - camera.x, y - camera.y, z - camera.z, yaw, tickDelta, stack, provider, light);
                provider.draw();
            }

            if (through)
            {
                /* The real view, not the moved one */
                stack.push();
                stack.peek().getPositionMatrix().set(view);
                stack.peek().getNormalMatrix().set(view.normal(new Matrix3f()));

                try
                {
                    dispatcher.render(player, x - camera.x, y - camera.y, z - camera.z, yaw, tickDelta, stack, provider, light);
                    provider.draw();
                }
                finally
                {
                    stack.pop();
                }
            }
        }
        catch (RuntimeException | LinkageError e)
        {
            System.err.println("[BBS] Drawing the player through a portal failed: " + e);
        }
        finally
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /**
     * The particles and the weather of the world, drawn into the moved scene after everything else, as
     * the world draws them. Particles sit in the world and move with the scene. Rain and snow are made
     * around the eye, so they are made around the eye the scene is seen from - the real eye taken back
     * through the move - and turned the way the scene is turned.
     */
    private static void drawEffects(Matrix4f view, Matrix4f move)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || context.camera() == null)
        {
            return;
        }

        try
        {
            MatrixStack stack = context.matrixStack();
            Vec3d camera = context.camera().getPos();
            float tickDelta = context./*$ wtd*/tickDelta()/**/;
            LightmapTextureManager lightmap = mc.gameRenderer.getLightmapTextureManager();

            //? if >=1.21 {
            /*ModelViewStack.push();
            ModelViewStack.multiply(stack.peek().getPositionMatrix());
            RenderSystem.applyModelViewMatrix();

            try
            {
                mc.particleManager.renderParticles(lightmap, context.camera(), tickDelta);
            }
            finally
            {
                ModelViewStack.pop();

                RenderSystem.applyModelViewMatrix();
            }
            *///?} else {
            mc.particleManager.renderParticles(stack, mc.getBufferBuilders().getEntityVertexConsumers(), lightmap, context.camera(), tickDelta);
            //?}

            /* The move in the world's own frame around the camera, and the eye it takes back to */
            Matrix4f world = new Matrix4f(view).invert().mul(move).mul(view);
            Vector3f eye = new Matrix4f(world).invert().transformPosition(new Vector3f());
            Matrix4f turn = new Matrix4f(world).setTranslation(0F, 0F, 0F);
            Matrix4f wanted = new Matrix4f(view).mul(turn);
            Matrix4f base = new Matrix4f(RenderSystem.getModelViewMatrix());

            ModelViewStack.push();
            ModelViewStack.multiply(base.invert().mul(wanted));
            RenderSystem.applyModelViewMatrix();

            try
            {
                ((WorldRendererAccessor) mc.worldRenderer).bbs$renderWeather(lightmap, tickDelta, camera.x + eye.x, camera.y + eye.y, camera.z + eye.z);
            }
            finally
            {
                ModelViewStack.pop();

                RenderSystem.applyModelViewMatrix();
            }
        }
        catch (RuntimeException | LinkageError e)
        {
            System.err.println("[BBS] Drawing weather and particles through a portal failed: " + e);
        }
        finally
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /**
     * The model blocks near {@code source}, drawn again by their own renderer with the matrices of the
     * moved scene: a model block is a block entity, which the films' pass does not reach.
     */
    private static void drawModelBlocks(Matrix4f view, Matrix4f source)
    {
        if (terrainFailed)
        {
            return;
        }

        try
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld world = mc.world;

            if (world == null || context.camera() == null)
            {
                return;
            }

            Vec3d camera = context.camera().getPos();
            MatrixStack stack = context.matrixStack();
            Matrix4f toLocal = new Matrix4f(source).invert().mul(view);
            Vector3f anchor = new Matrix4f(view).invert().transformPosition(source.getTranslation(new Vector3f()));
            int chunkX = (int) Math.floor(camera.x + anchor.x) >> 4;
            int chunkZ = (int) Math.floor(camera.z + anchor.z) >> 4;
            float limit = Math.min(mc.options.getClampedViewDistance(), TERRAIN_CHUNKS) * 16F;
            ModelBlockEntityRenderer renderer = new ModelBlockEntityRenderer(null);
            VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();
            Vector3f local = new Vector3f();

            for (int dx = -TERRAIN_CHUNKS; dx <= TERRAIN_CHUNKS; dx++)
            {
                for (int dz = -TERRAIN_CHUNKS; dz <= TERRAIN_CHUNKS; dz++)
                {
                    WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX + dx, chunkZ + dz);

                    if (chunk == null)
                    {
                        continue;
                    }

                    for (BlockEntity entity : new ArrayList<>(chunk.getBlockEntities().values()))
                    {
                        if (!(entity instanceof ModelBlockEntity block))
                        {
                            continue;
                        }

                        BlockPos pos = block.getPos();
                        double x = pos.getX() - camera.x;
                        double y = pos.getY() - camera.y;
                        double z = pos.getZ() - camera.z;

                        toLocal.transformPosition((float) (x + 0.5D), (float) (y + 0.5D), (float) (z + 0.5D), local);

                        /* In front of the window, and near it; a model block is not small, so the margin is wide */
                        if (local.z < -16F || local.length() > limit)
                        {
                            continue;
                        }

                        stack.push();
                        stack.translate(x, y, z);

                        try
                        {
                            renderer.render(block, context./*$ wtd*/tickDelta()/**/, stack, provider, WorldRenderer.getLightmapCoordinates(world, pos), OverlayTexture.DEFAULT_UV);
                        }
                        finally
                        {
                            stack.pop();

                            RenderSystem.enableDepthTest();
                        }
                    }
                }
            }

            provider.draw();
        }
        catch (RuntimeException | LinkageError e)
        {
            terrainFailed = true;

            System.err.println("[BBS] Drawing model blocks through a portal failed, and they are left out from now on: " + e);
        }
        finally
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** A chunk of the world's terrain, and how far it is from the window it is seen from. */
    private record Piece(ChunkBuilder.BuiltChunk chunk, float distance)
    {}

    /**
     * The terrain that lies in front of {@code source}, near it, drawn again from the chunks the world
     * renderer has built. Done here and not by the world renderer itself, which would draw into the same
     * framebuffer and set its fog and sky up again: only its chunk buffers and shaders are used, one
     * layer after the other, as it does. {@code modelView} is the view moved to the partner.
     */
    private static void drawTerrain(Matrix4f view, Matrix4f modelView, Matrix4f source)
    {
        if (terrainFailed)
        {
            return;
        }

        try
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            /* With Sodium the vanilla chunk storage is empty: its own sections are drawn through the bridge */
            if (SODIUM_LOADED)
            {
                if (context.camera() != null)
                {
                    float reach = Math.min(mc.options.getClampedViewDistance(), TERRAIN_CHUNKS) * 16F + 14F;

                    SodiumPortalBridge.drawTerrain(view, modelView, source, passProjection, context.camera().getPos(), reach);
                }

                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                return;
            }

            BuiltChunkStorage storage = mc.worldRenderer == null ? null : ((WorldRendererAccessor) mc.worldRenderer).bbs$getChunks();

            /* A replaced terrain renderer keeps its chunks to itself; there is nothing to draw then */
            if (storage == null || storage.chunks == null || context.camera() == null)
            {
                return;
            }

            Vec3d camera = context.camera().getPos();
            Matrix4f toLocal = new Matrix4f(source).invert().mul(view);
            float limit = Math.min(mc.options.getClampedViewDistance(), TERRAIN_CHUNKS) * 16F + 14F;
            List<Piece> pieces = new ArrayList<>();
            Vector3f center = new Vector3f();

            for (ChunkBuilder.BuiltChunk chunk : storage.chunks)
            {
                if (chunk == null || chunk.getData().isEmpty())
                {
                    continue;
                }

                BlockPos origin = chunk.getOrigin();

                toLocal.transformPosition((float) (origin.getX() + 8 - camera.x), (float) (origin.getY() + 8 - camera.y), (float) (origin.getZ() + 8 - camera.z), center);

                /* Only what is in front of the window is seen through its partner, and not far from it */
                if (center.z < -14F || center.length() > limit)
                {
                    continue;
                }

                pieces.add(new Piece(chunk, center.lengthSquared()));
            }

            if (pieces.isEmpty())
            {
                return;
            }

            pieces.sort(Comparator.comparingDouble(Piece::distance));

            drawLayer(RenderLayer.getSolid(), pieces, false, modelView, camera);
            drawLayer(RenderLayer.getCutoutMipped(), pieces, false, modelView, camera);
            drawLayer(RenderLayer.getCutout(), pieces, false, modelView, camera);
            drawLayer(RenderLayer.getTranslucent(), pieces, true, modelView, camera);
        }
        catch (RuntimeException | LinkageError e)
        {
            terrainFailed = true;

            System.err.println("[BBS] Drawing terrain through a portal failed, and is left out from now on: " + e);
        }
        finally
        {
            VertexBuffer.unbind();

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** One render layer of the terrain, set up the way the world renderer sets it up for its own. */
    private static void drawLayer(RenderLayer layer, List<Piece> pieces, boolean farFirst, Matrix4f modelView, Vec3d camera)
    {
        layer.startDrawing();

        try
        {
            ShaderProgram shader = RenderSystem.getShader();

            if (shader == null)
            {
                return;
            }

            for (int i = 0; i < 12; i++)
            {
                shader.addSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
            }

            if (shader.modelViewMat != null) shader.modelViewMat.set(modelView);
            if (shader.projectionMat != null) shader.projectionMat.set(passProjection);
            //? if <1.21
            if (shader.viewRotationMat != null) shader.viewRotationMat.set(RenderSystem.getInverseViewRotationMatrix());
            if (shader.colorModulator != null) shader.colorModulator.set(RenderSystem.getShaderColor());
            if (shader.glintAlpha != null) shader.glintAlpha.set(RenderSystem.getShaderGlintAlpha());
            if (shader.fogStart != null) shader.fogStart.set(RenderSystem.getShaderFogStart());
            if (shader.fogEnd != null) shader.fogEnd.set(RenderSystem.getShaderFogEnd());
            if (shader.fogColor != null) shader.fogColor.set(RenderSystem.getShaderFogColor());
            if (shader.fogShape != null) shader.fogShape.set(RenderSystem.getShaderFogShape().getId());
            if (shader.textureMat != null) shader.textureMat.set(RenderSystem.getTextureMatrix());
            if (shader.gameTime != null) shader.gameTime.set(RenderSystem.getShaderGameTime());

            RenderSystem.setupShaderLights(shader);
            shader.bind();

            GlUniform offset = shader.chunkOffset;
            int size = pieces.size();

            for (int i = 0; i < size; i++)
            {
                ChunkBuilder.BuiltChunk chunk = pieces.get(farFirst ? size - 1 - i : i).chunk();

                if (chunk.getData().isEmpty(layer))
                {
                    continue;
                }

                BlockPos origin = chunk.getOrigin();
                VertexBuffer buffer = chunk.getBuffer(layer);

                if (offset != null)
                {
                    offset.set((float) (origin.getX() - camera.x), (float) (origin.getY() - camera.y), (float) (origin.getZ() - camera.z));
                    offset.upload();
                }

                buffer.bind();
                buffer.draw();
            }

            shader.unbind();
        }
        finally
        {
            layer.endDrawing();
        }
    }

    private static void drawWindow(Window window, float r, float g, float b)
    {
        BufferBuilder builder;

        builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        fill(builder, window.matrix(), window.shape(), window.width() / 2F, window.height() / 2F, r, g, b, 1F);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Bufs.draw(builder);
    }

    /* The frame */

    /**
     * A thin line around a portal's window and an arrow out of its centre along its facing - the
     * side it is entered from, and the way what comes out of it is thrown - for placing it. Drawn
     * where the portal stands, like any other form, and depth tested.
     *
     * <p>The side it faces shows in the colour of its end of the pair - blue for the first, orange for
     * the second - or the frame colour of the form when that is anything but white; the other side
     * is the same colour, dark. A portal with no partner is white, a third one red.</p>
     */
    public static void drawFrame(PortalForm form, Matrix4f matrix)
    {
        if (isRenderingPortal())
        {
            return;
        }

        float hw = form.width.get() / 2F;
        float hh = form.height.get() / 2F;
        Color custom = form.frameColor.get();
        float[] front;

        if (isConflicted(form))
        {
            front = new float[] {1F, 0.15F, 0.1F, 1F};
        }
        else if (custom.r < 0.99F || custom.g < 0.99F || custom.b < 0.99F)
        {
            front = new float[] {custom.r, custom.g, custom.b, custom.a};
        }
        else
        {
            int role = getRole(form);

            front = role == 1 ? new float[] {0.1F, 0.55F, 1F, 1F} : (role == 2 ? new float[] {1F, 0.55F, 0.05F, 1F} : new float[] {1F, 1F, 1F, custom.a});
        }

        BufferBuilder builder;

        builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* The two faces of the frame are a hair apart, so each is seen from its own side */
        ring(builder, matrix, form.shape.get(), hw, hh, FRAME_OFFSET, front[0], front[1], front[2], front[3]);
        ring(builder, matrix, form.shape.get(), hw, hh, -FRAME_OFFSET, front[0] * 0.3F, front[1] * 0.3F, front[2] * 0.3F, front[3]);
        arrow(builder, matrix, Math.max(0.6F, Math.max(hw, hh)), front[0], front[1], front[2]);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Bufs.draw(builder);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** The outline of a window, as a band of {@link #FRAME_WIDTH} just outside it, at depth {@code z}. */
    private static void ring(BufferBuilder builder, Matrix4f matrix, int shape, float hw, float hh, float z, float r, float g, float b, float a)
    {
        if (shape == PortalForm.SHAPE_CIRCLE)
        {
            for (int i = 0; i < SEGMENTS; i++)
            {
                double a0 = Math.PI * 2D * i / SEGMENTS;
                double a1 = Math.PI * 2D * (i + 1) / SEGMENTS;
                float ix0 = (float) Math.cos(a0) * hw;
                float iy0 = (float) Math.sin(a0) * hh;
                float ix1 = (float) Math.cos(a1) * hw;
                float iy1 = (float) Math.sin(a1) * hh;
                float ox0 = (float) Math.cos(a0) * (hw + FRAME_WIDTH);
                float oy0 = (float) Math.sin(a0) * (hh + FRAME_WIDTH);
                float ox1 = (float) Math.cos(a1) * (hw + FRAME_WIDTH);
                float oy1 = (float) Math.sin(a1) * (hh + FRAME_WIDTH);

                triangle(builder, matrix, ix0, iy0, z, ox0, oy0, z, ox1, oy1, z, r, g, b, a);
                triangle(builder, matrix, ix0, iy0, z, ox1, oy1, z, ix1, iy1, z, r, g, b, a);
            }
        }
        else
        {
            float f = FRAME_WIDTH;

            rectangle(builder, matrix, -hw - f, hh, hw + f, hh + f, z, r, g, b, a);
            rectangle(builder, matrix, -hw - f, -hh - f, hw + f, -hh, z, r, g, b, a);
            rectangle(builder, matrix, -hw - f, -hh, -hw, hh, z, r, g, b, a);
            rectangle(builder, matrix, hw, -hh, hw + f, hh, z, r, g, b, a);
        }
    }

    /**
     * A solid arrow from the window's centre out along +Z and nowhere else: a square shaft closed at
     * both ends and a pyramid for a head, which comes to a point at {@code length}.
     */
    private static void arrow(BufferBuilder builder, Matrix4f matrix, float length, float r, float g, float b)
    {
        float s = 0.03F;
        float h = 0.12F;
        float neck = length * 0.7F;

        /* Shaft: four sides and the two ends */
        box(builder, matrix, -s, -s, 0F, s, s, neck, r, g, b);

        /* Head: four sides to the tip, and a base */
        float[][] corners = {{-h, -h}, {h, -h}, {h, h}, {-h, h}};

        for (int i = 0; i < 4; i++)
        {
            float[] c0 = corners[i];
            float[] c1 = corners[(i + 1) % 4];
            float shade = 0.7F + 0.1F * i;

            triangle(builder, matrix, c0[0], c0[1], neck, c1[0], c1[1], neck, 0F, 0F, length, r * shade, g * shade, b * shade, 1F);
        }

        triangle(builder, matrix, corners[0][0], corners[0][1], neck, corners[2][0], corners[2][1], neck, corners[1][0], corners[1][1], neck, r * 0.5F, g * 0.5F, b * 0.5F, 1F);
        triangle(builder, matrix, corners[0][0], corners[0][1], neck, corners[3][0], corners[3][1], neck, corners[2][0], corners[2][1], neck, r * 0.5F, g * 0.5F, b * 0.5F, 1F);
    }

    private static void box(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b)
    {
        quad(builder, matrix, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r * 0.6F, g * 0.6F, b * 0.6F);
        quad(builder, matrix, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b);
        quad(builder, matrix, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r * 0.8F, g * 0.8F, b * 0.8F);
        quad(builder, matrix, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r * 0.8F, g * 0.8F, b * 0.8F);
        quad(builder, matrix, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r * 0.5F, g * 0.5F, b * 0.5F);
        quad(builder, matrix, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r * 0.5F, g * 0.5F, b * 0.5F);
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b)
    {
        triangle(builder, matrix, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, 1F);
        triangle(builder, matrix, x1, y1, z1, x3, y3, z3, x4, y4, z4, r, g, b, 1F);
    }

    private static void fill(BufferBuilder builder, Matrix4f matrix, int shape, float hw, float hh, float r, float g, float b, float a)
    {
        if (shape == PortalForm.SHAPE_CIRCLE)
        {
            for (int i = 0; i < SEGMENTS; i++)
            {
                double a0 = Math.PI * 2D * i / SEGMENTS;
                double a1 = Math.PI * 2D * (i + 1) / SEGMENTS;

                triangle(builder, matrix, 0F, 0F, 0F, (float) Math.cos(a0) * hw, (float) Math.sin(a0) * hh, 0F, (float) Math.cos(a1) * hw, (float) Math.sin(a1) * hh, 0F, r, g, b, a);
            }
        }
        else
        {
            rectangle(builder, matrix, -hw, -hh, hw, hh, 0F, r, g, b, a);
        }
    }

    private static void rectangle(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float x2, float y2, float z, float r, float g, float b, float a)
    {
        triangle(builder, matrix, x1, y1, z, x2, y1, z, x2, y2, z, r, g, b, a);
        triangle(builder, matrix, x1, y1, z, x2, y2, z, x1, y2, z, r, g, b, a);
    }

    private static void triangle(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float r, float g, float b, float a)
    {
        r = Math.min(1F, r);
        g = Math.min(1F, g);
        b = Math.min(1F, b);

        builder.vertex(matrix, x1, y1, z1).color(r, g, b, a)/*$ nx*/.next()/**/;
        builder.vertex(matrix, x2, y2, z2).color(r, g, b, a)/*$ nx*/.next()/**/;
        builder.vertex(matrix, x3, y3, z3).color(r, g, b, a)/*$ nx*/.next()/**/;
    }
}
