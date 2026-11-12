package mchorse.bbs_mod.dev;

import mchorse.bbs_mod.BBSMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.io.File;
import java.util.UUID;

/**
 * Whether the developer screen exists at all. The session must be the developer's (nick or UUID) -
 * account, and the marker file {@code bbs/.developer} (or {@code bbs/.dev_lock}) is on this disk.
 * When either is missing nothing of the developer screen is built or drawn, so a distributed jar
 * shows end users nothing.
 *
 * <p>This is a convenience gate that keeps developer tooling out of the way, not a security
 * boundary: the source is what it is, and offline mode accepts any name.</p>
 */
public final class DevAccessGuard
{
    private static final UUID DEV_UUID = UUID.fromString("5668a2b6-edb2-48cf-90cb-f72d4e40f47f");
    private static final String DEV_NICK = "ItsKynix";
    private static final long RECHECK_MS = 2000L;

    private static long checkedAt = Long.MIN_VALUE;
    private static boolean cached;

    private DevAccessGuard()
    {}

    public static boolean hasAccess()
    {
        long now = System.currentTimeMillis();

        if (now - checkedAt > RECHECK_MS)
        {
            checkedAt = now;

            try
            {
                cached = compute();
            }
            catch (Throwable e)
            {
                cached = false;
            }
        }

        return cached;
    }

    private static boolean compute()
    {
        return evaluate(false);
    }

    /**
     * One line in the log for the settings window: who the session is, whether the marker file is
     * there and what was decided, so a refusal explains itself. Also re-evaluates right away.
     */
    public static void logDiagnostics()
    {
        cached = evaluate(true);
        checkedAt = System.currentTimeMillis();
    }

    /**
     * The nick OR the UUID is enough. The marker file is no longer required: when the identity
     * matches and the file is missing it is created empty, so it can be deleted again to hide the screen
     * only by changing the identity, not by the file.
     */
    private static boolean evaluate(boolean log)
    {
        Session session = MinecraftClient.getInstance().getSession();
        String nick = session == null ? null : session.getUsername();
        UUID uuid = session == null ? null : session.getUuidOrNull();
        boolean who = session != null && (DEV_UUID.equals(uuid) || DEV_NICK.equalsIgnoreCase(nick));
        File developer = BBSMod.getGamePath("bbs/.developer");
        File lock = BBSMod.getGamePath("bbs/.dev_lock");
        boolean exists = developer.isFile() || lock.isFile();

        if (who && !exists)
        {
            try
            {
                developer.getParentFile().mkdirs();
                exists = developer.createNewFile() || developer.isFile();
            }
            catch (Exception e)
            {
                exists = false;
            }
        }

        if (log)
        {
            BBSMod.LOGGER.info("[BBS/DevGuard] Session Nick: {}, Session UUID: {}, File Exists: {}, Access Granted: {}", nick, uuid, exists, who);
        }

        return who;
    }

    /** An external IRLights / IRLCore mod is installed next to the embedded engine. */
    public static boolean externalIrlModLoaded()
    {
        FabricLoader loader = FabricLoader.getInstance();

        return loader.isModLoaded("irlights") || loader.isModLoaded("irlcore");
    }
}
