package mchorse.bbs_mod.dev;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.ui.film.PostFxRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * The developer screen's three helpers: purging the GPU buffers, forcing the tick rate of the
 * integrated server (and so of the film timeline that follows it), and the log verbosity.
 */
public final class DevTools
{
    /** Zero for the game's own speed; otherwise the TPS the tick simulator holds the integrated server at. */
    public enum TickMode
    {
        NORMAL(0F),
        SLOW_10_TPS(10F),
        LOCKED_20_TPS(20F),
        TURBO_60_TPS(60F);

        public final float tps;

        TickMode(float tps)
        {
            this.tps = tps;
        }
    }

    public enum LogLevel
    {
        INFO(Level.INFO),
        DEBUG(Level.DEBUG),
        TRACE(Level.TRACE),
        /** Only warnings, errors and crashes */
        SILENT(Level.WARN);

        public final Level level;

        LogLevel(Level level)
        {
            this.level = level;
        }
    }

    /** The loggers of this mod and the engines inside it: events under these names follow the chosen level. */
    private static final String[] LOGGERS = {"bbs_mod", "mchorse.bbs_mod", "mchorse.bbs", "qualet", "org.qualet"};

    /* Not saved: a world always starts at the game's own speed */
    private static TickMode tickMode = TickMode.NORMAL;
    private static MinecraftServer overriddenServer;
    private static float originalRate;
    private static boolean registered;

    private DevTools()
    {}

    public static void init()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register((client) -> updateTicks(client));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register((client) -> DevStageBadge.refreshWindowTitle());

        if (DevAccessGuard.hasAccess())
        {
            applyLogLevel(DevConfig.getLogLevel());
        }
    }

    /* Purge */

    /** Frees the post, portal and pooled form buffers; each is made again clean by the next frame that needs it. */
    public static void purgeBuffers()
    {
        PostFxRenderer.purge();
        FormPortals.purge();
        BBSModClient.getFramebuffers().purgePool();

        BBSMod.LOGGER.info("[BBS/dev] Purged VRAM: post-fx scratch and depth buffers, portal scene buffer and pooled form framebuffers");
    }

    /* Tick simulator */

    public static TickMode getTickMode()
    {
        return tickMode;
    }

    public static void setTickMode(TickMode mode)
    {
        tickMode = mode;

        BBSMod.LOGGER.info("[BBS/dev] Simulated TPS: {}", mode);
        updateTicks(MinecraftClient.getInstance());
    }

    private static void updateTicks(MinecraftClient client)
    {
        MinecraftServer server = client.getServer();
        TickMode mode = DevAccessGuard.hasAccess() ? tickMode : TickMode.NORMAL;

        if (overriddenServer != null && overriddenServer != server)
        {
            /* That world is gone, and with it what we changed */
            overriddenServer = null;
        }

        if (server == null)
        {
            return;
        }

        if (mode == TickMode.NORMAL)
        {
            if (overriddenServer == server)
            {
                server.getTickManager().setTickRate(originalRate);
                overriddenServer = null;
            }

            return;
        }

        if (overriddenServer != server)
        {
            originalRate = server.getTickManager().getTickRate();
            overriddenServer = server;
        }

        if (server.getTickManager().getTickRate() != mode.tps)
        {
            server.getTickManager().setTickRate(mode.tps);
        }
    }

    /* Log verbosity */

    public static void applyLogLevel(LogLevel level)
    {
        for (String name : LOGGERS)
        {
            Configurator.setLevel(name, level.level);
        }

        BBSMod.LOGGER.info("[BBS/dev] Log verbosity: {}", level);
    }
}
