package mchorse.bbs_mod.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is editing what on a server, so two players with the same film open don't overwrite each
 * other's work.
 *
 * <p>Every connected player gets a session: an id of its own, new each time they join, so a lock
 * left by an earlier connection of the same player is told apart from the current one. Three kinds
 * of hold are kept:</p>
 *
 * <ul>
 *     <li>a film is locked to the session that opened it for editing; while the lock holds, only
 *     that session may save, rename or delete it (the others still read it). It lapses when the
 *     session ends, when the player moves on to another film or out of the editor, or after
 *     {@link #LOCK_TIMEOUT} untouched;</li>
 *     <li>the replay a player has picked in the film editor, which nobody else may start recording
 *     over while they work on it (the busy guard);</li>
 *     <li>the replay a player is recording live, which everyone else sees as read only.</li>
 * </ul>
 *
 * <p>All of it only matters where more than one player can be: a dedicated server, or a world
 * opened to LAN ({@link #isShared}). Everything here runs on the server thread (the packet
 * handlers and the connection events), hence no synchronization.</p>
 */
public class EditSessions
{
    /** How long (ms) a film lock holds without being touched */
    public static final long LOCK_TIMEOUT = 5L * 60L * 1000L;

    public static final byte KIND_FILM = 0;
    public static final byte KIND_EDITING = 1;
    public static final byte KIND_RECORDING = 2;

    private static final Map<UUID, UUID> sessions = new HashMap<>();
    private static final Map<String, Lock> locks = new HashMap<>();
    /** The replay each player has picked in their film editor */
    private static final Map<UUID, Lock> editing = new HashMap<>();
    /** The replays being recorded live, by {@link #replay} key */
    private static final Map<String, Lock> recording = new HashMap<>();

    /** A player's hold on one document or replay. */
    public static class Lock
    {
        public final UUID player;
        public final UUID session;
        public final String playerName;
        public final byte kind;
        public final String film;
        public final int replay;
        public long touched;

        public Lock(UUID player, UUID session, String playerName, byte kind, String film, int replay)
        {
            this.player = player;
            this.session = session;
            this.playerName = playerName;
            this.kind = kind;
            this.film = film;
            this.replay = replay;
            this.touched = System.currentTimeMillis();
        }

        public boolean isExpired(long now)
        {
            return this.kind == KIND_FILM && now - this.touched > LOCK_TIMEOUT;
        }

        public void write(PacketByteBuf buf)
        {
            buf.writeByte(this.kind);
            buf.writeString(this.film);
            buf.writeInt(this.replay);
            buf.writeUuid(this.player);
            buf.writeString(this.playerName);
        }
    }

    /** Whether more than one player can be here: a dedicated server, or a world opened to LAN. */
    public static boolean isShared(MinecraftServer server)
    {
        return server != null && (server.isDedicated() || server.isRemote());
    }

    /* Sessions */

    public static UUID start(ServerPlayerEntity player)
    {
        UUID session = UUID.randomUUID();

        sessions.put(player.getUuid(), session);

        return session;
    }

    /** The player left: their session ends and every hold it had goes with it. */
    public static void end(UUID player)
    {
        UUID session = sessions.remove(player);

        locks.values().removeIf((lock) -> lock.player.equals(player) && (session == null || lock.session.equals(session)));
        recording.values().removeIf((lock) -> lock.player.equals(player));
        editing.remove(player);
    }

    /** The player's session, started on the spot for one that joined before sessions were kept. */
    public static UUID session(ServerPlayerEntity player)
    {
        UUID session = sessions.get(player.getUuid());

        return session == null ? start(player) : session;
    }

    private static Lock lock(ServerPlayerEntity player, byte kind, String film, int replay)
    {
        return new Lock(player.getUuid(), session(player), player.getGameProfile().getName(), kind, film, replay);
    }

    /* Film locks */

    /**
     * Lock the document to the player, unless someone else holds it.
     *
     * @return null when the player holds it now; otherwise the lock of whoever does
     */
    public static Lock acquire(String key, ServerPlayerEntity player)
    {
        Lock held = holder(key);
        UUID session = session(player);

        if (held != null && !held.session.equals(session))
        {
            return held;
        }

        if (held == null)
        {
            locks.put(key, lock(player, KIND_FILM, key.startsWith("film:") ? key.substring(5) : key, -1));
        }
        else
        {
            held.touched = System.currentTimeMillis();
        }

        return null;
    }

    /** Whether the player may write the document: nobody else holds it (it is then theirs). */
    public static boolean canWrite(String key, ServerPlayerEntity player)
    {
        return acquire(key, player) == null;
    }

    /** Let go of the document, if the player holds it. */
    public static void release(String key, ServerPlayerEntity player)
    {
        Lock held = locks.get(key);

        if (held != null && held.session.equals(sessions.get(player.getUuid())))
        {
            locks.remove(key);
        }
    }

    /** The lock on a document that still holds, or null; lapsed ones are dropped on the way. */
    public static Lock holder(String key)
    {
        Lock held = locks.get(key);

        if (held != null && held.isExpired(System.currentTimeMillis()))
        {
            locks.remove(key);

            return null;
        }

        return held;
    }

    /** A document renamed on disk keeps its lock under the new key. */
    public static void move(String from, String to)
    {
        Lock held = locks.remove(from);

        if (held != null)
        {
            Lock moved = new Lock(held.player, held.session, held.playerName, held.kind, to.startsWith("film:") ? to.substring(5) : to, held.replay);

            locks.put(to, moved);
        }
    }

    /** Drop every lapsed lock; cheap enough to call now and then. */
    public static void purge()
    {
        long now = System.currentTimeMillis();
        Iterator<Lock> iterator = locks.values().iterator();

        while (iterator.hasNext())
        {
            if (iterator.next().isExpired(now))
            {
                iterator.remove();
            }
        }
    }

    /** The key a film is locked under. */
    public static String film(String id)
    {
        return "film:" + id;
    }

    /** The key a replay is held under. */
    public static String replay(String film, int replay)
    {
        return film + "/" + replay;
    }

    /* Replays */

    /**
     * What the player is working on in their film editor: a replay of a film, a film with no
     * replay picked (-1), or nothing (an empty film id, the editor closed). The film locks the
     * player's session holds on other films are let go of, now that they have moved on.
     */
    public static void setEditing(ServerPlayerEntity player, String film, int replay)
    {
        UUID session = session(player);

        locks.values().removeIf((lock) -> lock.session.equals(session) && !lock.film.equals(film));

        if (film.isEmpty() || replay < 0)
        {
            editing.remove(player.getUuid());
        }
        else
        {
            editing.put(player.getUuid(), lock(player, KIND_EDITING, film, replay));
        }
    }

    /**
     * Start recording a replay live, unless someone else is recording it or has it picked to work on.
     *
     * @return null when the recording may go on (it is held now); otherwise who is in the way
     */
    public static Lock startRecording(ServerPlayerEntity player, String film, int replay)
    {
        String key = replay(film, replay);
        Lock held = recording.get(key);

        if (held != null && !held.player.equals(player.getUuid()))
        {
            return held;
        }

        for (Lock lock : editing.values())
        {
            if (!lock.player.equals(player.getUuid()) && lock.film.equals(film) && lock.replay == replay)
            {
                return lock;
            }
        }

        recording.put(key, lock(player, KIND_RECORDING, film, replay));

        return null;
    }

    /**
     * Another player recording the replay ({@code replay} >= 0), or any replay of the film
     * ({@code replay} < 0), live - what a change from this player must not land on. Null if none.
     */
    public static Lock recordedByOther(ServerPlayerEntity player, String film, int replay)
    {
        for (Lock lock : recording.values())
        {
            if (!lock.player.equals(player.getUuid()) && lock.film.equals(film) && (replay < 0 || lock.replay == replay))
            {
                return lock;
            }
        }

        return null;
    }

    /** The player's live recording ended; whether they had one. */
    public static boolean stopRecording(ServerPlayerEntity player)
    {
        return recording.values().removeIf((lock) -> lock.player.equals(player.getUuid()));
    }

    /* Telling the clients */

    /** Every hold there is, as the clients mirror it. */
    public static void write(PacketByteBuf buf)
    {
        purge();

        List<Lock> all = new ArrayList<>(locks.values());

        all.addAll(editing.values());
        all.addAll(recording.values());

        buf.writeInt(all.size());

        for (Lock lock : all)
        {
            lock.write(buf);
        }
    }
}
