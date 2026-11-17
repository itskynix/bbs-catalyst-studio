package mchorse.bbs_mod.physics;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The physics engine integrated into BBS CS, adapted from the BBS Physics Engine add-on by
 * Wemppy (MIT). It plugs into BBS through the same addon API an external add-on would use:
 * {@link BBSPhysicsAddon} on both sides and {@code client.BBSPhysicsClientAddon} on the client,
 * both registered directly by BBS CS rather than through {@code fabric.mod.json} entry points.
 */
public class BBSPhysics
{
    public static final String MOD_ID = "bbs_physics";

    /**
     * The id of the standalone add-on. When it is installed next to BBS CS, the integrated copy
     * steps aside: both would hook the same methods and register the same values on every form.
     */
    public static final String STANDALONE_ID = "bbs_physics";

    /**
     * The asset source prefix the engine's own files are addressed by, as in
     * {@code bbs_physics:textures/cloth.png}. Kept from the add-on, so forms saved with it still
     * find their textures.
     */
    public static final String ASSETS = MOD_ID;

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Boolean integrated;

    /** Whether the integrated engine runs, which it does unless the standalone add-on is installed. */
    public static boolean integrated()
    {
        if (integrated == null)
        {
            integrated = !FabricLoader.getInstance().isModLoaded(STANDALONE_ID);

            if (!integrated)
            {
                LOGGER.warn("The standalone physics add-on is installed, so the physics engine built into BBS CS stays off.");
            }
        }

        return integrated;
    }
}
