package mchorse.bbs_mod.physics;

import com.github.stephengold.joltjni.Jolt;
import mchorse.bbs_mod.BBSMod;

/**
 * The process-wide Jolt context: the native library, the object factory and the registered shape types.
 *
 * <p>Failure is a normal outcome, not a crash: an unusual platform, a disk that cannot be written, a policy
 * that forbids loading libraries. Then physics reports itself unavailable once and the film editor goes on
 * animating without it. Everything that touches Jolt asks {@link #available()} first, because the bindings
 * answer a call made before registration by crashing the JVM, not by throwing.</p>
 */
public final class JoltEngine
{
    private static boolean attempted;
    private static boolean available;

    private JoltEngine()
    {}

    public static boolean available()
    {
        if (!attempted)
        {
            attempted = true;

            try
            {
                JoltNatives.load();

                Jolt.registerDefaultAllocator();
                Jolt.installDefaultAssertCallback();
                Jolt.installDefaultTraceCallback();

                if (!Jolt.newFactory())
                {
                    throw new IllegalStateException("Jolt refused to make its object factory.");
                }

                Jolt.registerTypes();

                available = true;

                BBSMod.LOGGER.info("Jolt Physics {} ({}) is up.", Jolt.versionString(), Jolt.buildType());
            }
            catch (Throwable e)
            {
                BBSMod.LOGGER.error("Jolt did not start, so the physics studio is unavailable.", e);
            }
        }

        return available;
    }
}
