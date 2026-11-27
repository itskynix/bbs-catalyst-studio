package mchorse.bbs_mod.recap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Deterministic post-scrub state settler and dimension-arrival handshake for Recap Studio.
 *
 * <p>Detects timeline seeks/rewinds, immediately clears stale sounds and particles from the
 * previous cursor position, runs a 20-tick deterministic particle pre-roll once scrubbing comes
 * to rest (or at export start), and verifies chunk/overlay/lighting readiness across dimension
 * transitions.</p>
 */
public final class RecapSettler
{
    public static final int PRE_ROLL_TICKS = 20;

    private static float lastSource = Float.NaN;
    private static String lastDimension;
    private static boolean pendingPreRoll;
    private static int lastPreRolledTick = Integer.MIN_VALUE;

    private RecapSettler()
    {}

    public static void reset()
    {
        lastSource = Float.NaN;
        lastDimension = null;
        pendingPreRoll = false;
        lastPreRolledTick = Integer.MIN_VALUE;
    }

    /**
     * Called each frame while a recap is active: purges stale effects on non-monotonic or jumped
     * cursor changes and triggers particle pre-roll once the scrub settles or export begins.
     */
    public static void update(RecapRecord record, RecapData data, String activeDimension, float sourceTime, boolean playing, boolean exporting)
    {
        if (record == null || data == null)
        {
            return;
        }

        boolean dimChanged = (activeDimension == null) != (lastDimension == null)
            || (activeDimension != null && !activeDimension.equals(lastDimension));
        boolean jumped = Float.isNaN(lastSource)
            || dimChanged
            || sourceTime < lastSource - 0.02F
            || sourceTime - lastSource > 2.5F
            || (!playing && Math.abs(sourceTime - lastSource) > 1e-3F);

        if (jumped)
        {
            RecapSounds.stopActive();
            RecapParticles.clearSpawned();

            int targetTick = (int) Math.floor(sourceTime);

            if (!Float.isNaN(lastSource))
            {
                pendingPreRoll = targetTick != lastPreRolledTick;
            }
            else if (exporting && targetTick > 0)
            {
                pendingPreRoll = true;
            }
        }

        lastSource = sourceTime;
        lastDimension = activeDimension;

        if (pendingPreRoll && data.particles != null && record.isOn(RecapOption.PARTICLES))
        {
            boolean mouseHeld = !exporting && isLeftMouseDown();

            if (!mouseHeld)
            {
                pendingPreRoll = false;
                int whole = (int) Math.floor(sourceTime);
                lastPreRolledTick = whole;
                data.particles.preRoll(whole, PRE_ROLL_TICKS, activeDimension);
            }
        }
    }

    /** Forces immediate full block, BlockEntity, light and particle pre-roll settling at {@code sourceTick}. */
    public static void settleForExportStart(ClientWorld world, RecapRecord record, RecapData data, String activeDimension, int sourceTick)
    {
        RecapSounds.stopActive();
        RecapParticles.clearSpawned();

        if (world != null && !RecapScene.showsIsolatedStage())
        {
            RecapWorldOverlay.settleImmediate(world, sourceTick);
        }

        if (data != null && data.particles != null && record != null && record.isOn(RecapOption.PARTICLES) && sourceTick > 0)
        {
            data.particles.preRoll(sourceTick, PRE_ROLL_TICKS, activeDimension);
        }

        lastSource = sourceTick;
        lastDimension = activeDimension;
        lastPreRolledTick = sourceTick;
        pendingPreRoll = false;
    }

    /**
     * Checks whether a newly traveled singleplayer dimension has loaded its surrounding chunks,
     * applied all recorded block/BlockEntity changes, and finished its lighting updates.
     */
    public static boolean isDimensionReady(MinecraftClient mc, String targetDimension, double focusX, double focusZ, int sourceTick, int waitedTicks)
    {
        if (mc == null || mc.world == null || mc.player == null)
        {
            return false;
        }

        if (!targetDimension.equals(RecapWorldOverlay.dimensionOf(mc.world)))
        {
            return false;
        }

        int cx = MathHelper.floor(focusX) >> 4;
        int cz = MathHelper.floor(focusZ) >> 4;

        if (!mc.world.getChunkManager().isChunkLoaded(cx, cz))
        {
            return false;
        }

        /* Wait for the immediate 3x3 chunk ring around the target camera/player unless near timeout */
        if (waitedTicks < 60)
        {
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (!mc.world.getChunkManager().isChunkLoaded(cx + dx, cz + dz))
                    {
                        return false;
                    }
                }
            }
        }

        if (waitedTicks < 4)
        {
            return false;
        }

        RecapWorldOverlay.settleImmediate(mc.world, sourceTick);

        return RecapWorldOverlay.isSettled() || waitedTicks >= 80;
    }

    private static boolean isLeftMouseDown()
    {
        try
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            return mc != null && mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        }
        catch (Throwable e)
        {
            return false;
        }
    }
}
