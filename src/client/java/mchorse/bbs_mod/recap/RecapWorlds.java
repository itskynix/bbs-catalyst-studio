package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.recap.UIRecapPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

/**
 * Which world the client is in, and getting into the world a recap was recorded in: staying when it
 * is the current one, otherwise leaving and loading it, then opening the recap's editor once in.
 */
public final class RecapWorlds
{
    /** A world: its key (compared against a recap's), the name shown, and what loads it. */
    public record Info(String key, String name, String target, boolean singleplayer)
    {}

    /** How long, in client ticks, a requested recap waits for its world before it is forgotten. */
    private static final int PENDING_TIMEOUT = 20 * 120;

    private static String pending;
    private static int pendingTicks;

    private RecapWorlds()
    {}

    public static Info current()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null)
        {
            return null;
        }

        if (mc.getServer() != null)
        {
            String folder = mc.getServer().getSavePath(WorldSavePath.ROOT).normalize().getFileName().toString();

            return new Info("sp:" + folder, mc.getServer().getSaveProperties().getLevelName(), folder, true);
        }

        ServerInfo server = mc.getCurrentServerEntry();

        if (server != null)
        {
            return new Info("mp:" + server.address, server.name, server.address, false);
        }

        return null;
    }

    public static boolean isCurrent(RecapRecord record)
    {
        Info info = current();

        return info != null && info.key().equals(record.worldKey);
    }

    /** Whether the recap's world can be reached from here: a local world must still exist. */
    public static boolean canReach(RecapRecord record)
    {
        if (record.worldTarget.isEmpty())
        {
            return false;
        }

        return !record.singleplayer || MinecraftClient.getInstance().getLevelStorage().levelExists(record.worldTarget);
    }

    /**
     * Leaves the current world, if any, and loads the recap's; its editor opens once the world is
     * ready. Only called after the author agreed, when a world is being left.
     */
    public static void travel(RecapRecord record)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        pending = record.id;
        pendingTicks = 0;

        /* Next tick: the click that asked for this comes from the very screen that goes away */
        mc.send(() ->
        {
            try
            {
                if (mc.world != null)
                {
                    boolean local = mc.isInSingleplayer();

                    mc.world.disconnect();

                    if (local)
                    {
                        mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
                    }
                    else
                    {
                        mc.disconnect();
                    }
                }

                if (record.singleplayer)
                {
                    mc.createIntegratedServerLoader().start(record.worldTarget, () ->
                    {
                        pending = null;
                        mc.setScreen(new TitleScreen());
                    });
                }
                else
                {
                    ServerInfo info = new ServerInfo(record.worldName, record.worldTarget, ServerInfo.ServerType.OTHER);

                    ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(record.worldTarget), info, false);
                }
            }
            catch (Throwable e)
            {
                pending = null;
                BBSMod.LOGGER.error("Could not enter the world of a recap", e);
                mc.setScreen(new TitleScreen());
            }
        });
    }

    /** Once a client tick: opens a recap waiting for its world, once the player is in it. */
    public static void tick(MinecraftClient mc)
    {
        if (pending == null)
        {
            return;
        }

        if (++pendingTicks > PENDING_TIMEOUT)
        {
            pending = null;

            return;
        }

        if (mc.world == null || mc.player == null || mc.currentScreen != null)
        {
            return;
        }

        RecapRecord record = RecapStore.get(pending);

        pending = null;

        if (record == null || !isCurrent(record))
        {
            return;
        }

        UIDashboard dashboard = BBSModClient.getDashboard();
        UIRecapPanel panel = dashboard.getPanel(UIRecapPanel.class);

        dashboard.setPanel(panel);
        panel.openEditor(record);
        UIScreen.open(dashboard);
    }
}
