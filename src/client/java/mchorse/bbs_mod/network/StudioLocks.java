package mchorse.bbs_mod.network;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The client's mirror of the server's editing holds (see the server's {@code EditSessions}):
 * which films are open for editing by whom, which replay each player has picked, which replays
 * are being recorded live. Filled by the server only where more than one player can be, so in a
 * single player world it stays empty and nothing here limits anything.
 *
 * <p>Every question is about <em>other</em> players: the player's own holds never count against
 * them.</p>
 */
public class StudioLocks
{
    public static final byte KIND_FILM = 0;
    public static final byte KIND_EDITING = 1;
    public static final byte KIND_RECORDING = 2;

    private static List<Entry> entries = Collections.emptyList();
    /** Whether the server lets this player write films (it says so with every update) */
    private static boolean canWrite = true;

    /* What was last told to the server, so it is told again only when it changes */
    private static String sentFilm = "";
    private static int sentReplay = -1;

    /* The last busy notice shown, and when */
    private static String lastNotice;
    private static long lastNoticeTime;
    private static final long NOTICE_INTERVAL = 4000L;

    public static class Entry
    {
        public final byte kind;
        public final String film;
        public final int replay;
        public final UUID player;
        public final String playerName;

        public Entry(byte kind, String film, int replay, UUID player, String playerName)
        {
            this.kind = kind;
            this.film = film;
            this.replay = replay;
            this.player = player;
            this.playerName = playerName;
        }
    }

    /** Whether other players can be here: a server, or this world opened to LAN. */
    public static boolean isShared()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return !mc.isInSingleplayer() || (mc.getServer() != null && mc.getServer().isRemote());
    }

    public static void read(PacketByteBuf buf)
    {
        canWrite = buf.readBoolean();

        int count = buf.readInt();
        List<Entry> list = new ArrayList<>(count);

        for (int i = 0; i < count; i++)
        {
            byte kind = buf.readByte();
            String film = buf.readString();
            int replay = buf.readInt();
            UUID player = buf.readUuid();
            String name = buf.readString();

            list.add(new Entry(kind, film, replay, player, name));
        }

        entries = list;
    }

    /** A new connection: nothing is known about it yet. */
    public static void reset()
    {
        entries = Collections.emptyList();
        canWrite = true;
        sentFilm = "";
        sentReplay = -1;
    }

    private static boolean isOther(Entry entry)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.player == null || !entry.player.equals(mc.player.getUuid());
    }

    private static Entry find(byte kind, String film, int replay)
    {
        if (film == null)
        {
            return null;
        }

        for (Entry entry : entries)
        {
            if (entry.kind == kind && entry.film.equals(film) && (kind == KIND_FILM || entry.replay == replay) && isOther(entry))
            {
                return entry;
            }
        }

        return null;
    }

    /** Whether this player may write films here; true where the server never said otherwise. */
    public static boolean canWrite()
    {
        return canWrite;
    }

    /** Another player who has this film open for editing, or null. */
    public static Entry filmHolder(String film)
    {
        return find(KIND_FILM, film, -1);
    }

    /** Another player recording this replay live, or null. */
    public static Entry recordingOf(String film, int replay)
    {
        return find(KIND_RECORDING, film, replay);
    }

    /** Another player who has this replay: recording it, or working on it. Null when it is free to record. */
    public static Entry busy(String film, int replay)
    {
        Entry entry = recordingOf(film, replay);

        return entry == null ? find(KIND_EDITING, film, replay) : entry;
    }

    /** Tell the server what this player works on, when it changed (shared worlds only). */
    public static void sendEditing(String film, int replay)
    {
        film = film == null ? "" : film;

        if (!isShared() || !ClientNetwork.isIsBBSModOnServer() || (Objects.equals(film, sentFilm) && replay == sentReplay))
        {
            return;
        }

        sentFilm = film;
        sentReplay = replay;
        ClientNetwork.sendReplayEditing(film, replay);
    }

    /**
     * Tell the player a replay is somebody else's to record right now: in the chat, and as a
     * message over the interface when it is open.
     */
    public static void notifyBusy(Entry entry)
    {
        /* Once per try: whatever retries the take (a key held, a session that tries again every
         * tick) must not fill the chat and stack the windows with the same notice */
        long now = System.currentTimeMillis();
        String said = entry.film + "/" + entry.replay + "/" + entry.player;

        if (said.equals(lastNotice) && now - lastNoticeTime < NOTICE_INTERVAL)
        {
            return;
        }

        lastNotice = said;
        lastNoticeTime = now;

        MinecraftClient mc = MinecraftClient.getInstance();
        Text text = Text.literal(L10n.lang("bbs.multiplayer.replay_busy").format(entry.playerName).get());

        if (mc.player != null)
        {
            mc.player.sendMessage(text, false);
        }

        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null)
        {
            UIOverlay.addOverlay(menu.context, new UIMessageOverlayPanel(L10n.lang("bbs.multiplayer.busy_title"), L10n.lang("bbs.multiplayer.replay_busy").format(entry.playerName)));
        }
    }
}
