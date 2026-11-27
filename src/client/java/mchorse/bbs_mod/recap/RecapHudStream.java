package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.mixin.client.RecapBossBarAccessor;
import mchorse.bbs_mod.mixin.client.RecapChatAccessor;
import mchorse.bbs_mod.mixin.client.RecapInGameHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Recorded first-person interface overlays: chat lines, big titles and subtitles, the action bar
 * above the hotbar, and boss bars.
 *
 * <p>Sampled once a game tick from {@link InGameHud} and deduplicated through {@link RecapPool},
 * so first-person playback and video exports draw what was actually on screen at
 * {@code ticks + transition} rather than whatever happens to be in the live client HUD now.</p>
 */
public class RecapHudStream
{
    private static final int MAGIC = 0x52434831;
    private static final int VERSION = 1;
    private static final int CHAT_VISIBLE_TICKS = 200;

    private final RecapPool pool = new RecapPool();

    /* Chat events: sorted by tick */
    private int[] chatTicks = new int[16];
    private int[] chatTextIds = new int[16];
    private int chatSize;
    private ChatHudLine lastChatHead;

    /* Title spans */
    private int[] titleTicks = new int[8];
    private int[] titleIds = new int[8];
    private int[] subtitleIds = new int[8];
    private short[] titleFadeIn = new short[8];
    private short[] titleStay = new short[8];
    private short[] titleFadeOut = new short[8];
    private short[] titleStartRemain = new short[8];
    private int titleSize;
    private int lastTitleId = -1;
    private int lastSubtitleId = -1;
    private int lastTitleRemain;

    /* Action bar spans */
    private int[] overlayTicks = new int[8];
    private int[] overlayIds = new int[8];
    private short[] overlayStartRemain = new short[8];
    private byte[] overlayTinted = new byte[8];
    private int overlaySize;
    private int lastOverlayId = -1;
    private int lastOverlayRemain;

    /* Boss bar sparse frames */
    private int[] bossTicks = new int[8];
    private BossEntry[][] bossFrames = new BossEntry[8][];
    private int bossSize;
    private long lastBossSignature;

    public record BossEntry(int nameId, float percent, int colorOrdinal, int styleOrdinal)
    {}

    public static class Snapshot
    {
        public final List<ChatEntry> chat = new ArrayList<>();
        public String title = "";
        public String subtitle = "";
        public float titleRemain;
        public int titleFadeIn = 10;
        public int titleStay = 70;
        public int titleFadeOut = 20;
        public String actionBar = "";
        public float actionRemain;
        public boolean actionTinted;
        public final List<BossBarView> bossBars = new ArrayList<>();

        public void clear()
        {
            this.chat.clear();
            this.title = "";
            this.subtitle = "";
            this.titleRemain = 0F;
            this.actionBar = "";
            this.actionRemain = 0F;
            this.actionTinted = false;
            this.bossBars.clear();
        }
    }

    public record ChatEntry(String text, float ageTicks)
    {}

    public record BossBarView(String name, float percent, int colorOrdinal, int styleOrdinal)
    {}

    public boolean isEmpty()
    {
        return this.chatSize == 0 && this.titleSize == 0 && this.overlaySize == 0 && this.bossSize == 0;
    }

    public void clear()
    {
        this.pool.clear();
        this.chatSize = 0;
        this.lastChatHead = null;
        this.titleSize = 0;
        this.lastTitleId = -1;
        this.lastSubtitleId = -1;
        this.lastTitleRemain = 0;
        this.overlaySize = 0;
        this.lastOverlayId = -1;
        this.lastOverlayRemain = 0;
        this.bossSize = 0;
        this.lastBossSignature = 0L;
        Arrays.fill(this.bossFrames, null);
    }

    /** Captures any new HUD state at {@code tick}. Safe against custom HUD replacements. */
    public void record(MinecraftClient mc, int tick)
    {
        if (mc == null || mc.inGameHud == null)
        {
            return;
        }

        try
        {
            InGameHud hud = mc.inGameHud;
            RecapInGameHudAccessor access = (RecapInGameHudAccessor) hud;

            this.recordChat(hud, tick);
            this.recordTitle(access, tick);
            this.recordActionBar(access, tick);
            this.recordBossBars(hud, tick);
        }
        catch (Throwable ignored)
        {
            /* Never let a modified HUD class interrupt recording */
        }
    }

    private void recordChat(InGameHud hud, int tick)
    {
        List<ChatHudLine> messages = ((RecapChatAccessor) hud.getChatHud()).bbs$getMessages();

        if (messages == null || messages.isEmpty())
        {
            this.lastChatHead = null;

            return;
        }

        ChatHudLine newest = messages.get(0);

        if (newest == this.lastChatHead)
        {
            return;
        }

        /* Collect lines added since lastChatHead in chronological order (oldest first) */
        int stop = Math.min(messages.size(), 16);
        int foundAt = stop;

        for (int i = 0; i < stop; i++)
        {
            if (messages.get(i) == this.lastChatHead)
            {
                foundAt = i;

                break;
            }
        }

        /* On the very first tick of a recording, only capture lines that just arrived */
        if (this.lastChatHead == null)
        {
            int currentHudTicks = hud.getTicks();

            for (int i = stop - 1; i >= 0; i--)
            {
                ChatHudLine line = messages.get(i);

                if (currentHudTicks - line.creationTick() <= 2 && line.content() != null)
                {
                    this.pushChat(tick, this.pool.putString(line.content().getString()));
                }
            }
        }
        else
        {
            for (int i = foundAt - 1; i >= 0; i--)
            {
                Text content = messages.get(i).content();

                if (content != null)
                {
                    this.pushChat(tick, this.pool.putString(content.getString()));
                }
            }
        }

        this.lastChatHead = newest;
    }

    private void recordTitle(RecapInGameHudAccessor access, int tick)
    {
        Text title = access.bbs$getTitle();
        int remain = access.bbs$getTitleRemainTicks();

        if (title == null || remain <= 0)
        {
            this.lastTitleRemain = 0;

            return;
        }

        int titleId = this.pool.putString(title.getString());
        Text sub = access.bbs$getSubtitle();
        int subId = sub == null ? -1 : this.pool.putString(sub.getString());

        if (titleId != this.lastTitleId || subId != this.lastSubtitleId || remain > this.lastTitleRemain)
        {
            this.pushTitle(tick, titleId, subId, access.bbs$getTitleFadeInTicks(), access.bbs$getTitleStayTicks(), access.bbs$getTitleFadeOutTicks(), remain);
            this.lastTitleId = titleId;
            this.lastSubtitleId = subId;
        }

        this.lastTitleRemain = remain;
    }

    private void recordActionBar(RecapInGameHudAccessor access, int tick)
    {
        Text overlay = access.bbs$getOverlayMessage();
        int remain = access.bbs$getOverlayRemaining();

        if (overlay == null || remain <= 0)
        {
            this.lastOverlayRemain = 0;

            return;
        }

        int id = this.pool.putString(overlay.getString());

        if (id != this.lastOverlayId || remain > this.lastOverlayRemain)
        {
            this.pushOverlay(tick, id, remain, access.bbs$isOverlayTinted());
            this.lastOverlayId = id;
        }

        this.lastOverlayRemain = remain;
    }

    private void recordBossBars(InGameHud hud, int tick)
    {
        Map<UUID, ClientBossBar> map = ((RecapBossBarAccessor) hud.getBossBarHud()).bbs$getBossBars();

        if (map == null || map.isEmpty())
        {
            if (this.lastBossSignature != 0L)
            {
                this.pushBossFrame(tick, new BossEntry[0]);
                this.lastBossSignature = 0L;
            }

            return;
        }

        long sig = 1469598103934665603L;

        for (ClientBossBar bar : map.values())
        {
            sig = (sig ^ bar.getName().getString().hashCode()) * 1099511628211L;
            sig = (sig ^ Float.floatToRawIntBits(bar.getPercent())) * 1099511628211L;
            sig = (sig ^ bar.getColor().ordinal()) * 1099511628211L;
            sig = (sig ^ bar.getStyle().ordinal()) * 1099511628211L;
        }

        if (sig == 0L)
        {
            sig = 1L;
        }

        if (sig != this.lastBossSignature)
        {
            int limit = Math.min(4, map.size());
            BossEntry[] entries = new BossEntry[limit];
            int idx = 0;

            for (ClientBossBar bar : map.values())
            {
                if (idx >= limit)
                {
                    break;
                }

                entries[idx++] = new BossEntry(this.pool.putString(bar.getName().getString()), bar.getPercent(), bar.getColor().ordinal(), bar.getStyle().ordinal());
            }

            this.pushBossFrame(tick, entries);
            this.lastBossSignature = sig;
        }
    }

    private void pushChat(int tick, int textId)
    {
        if (textId < 0)
        {
            return;
        }

        if (this.chatSize == this.chatTicks.length)
        {
            int cap = this.chatSize * 2;

            this.chatTicks = Arrays.copyOf(this.chatTicks, cap);
            this.chatTextIds = Arrays.copyOf(this.chatTextIds, cap);
        }

        this.chatTicks[this.chatSize] = tick;
        this.chatTextIds[this.chatSize] = textId;
        this.chatSize += 1;
    }

    private void pushTitle(int tick, int titleId, int subId, int fadeIn, int stay, int fadeOut, int remain)
    {
        if (this.titleSize == this.titleTicks.length)
        {
            int cap = this.titleSize * 2;

            this.titleTicks = Arrays.copyOf(this.titleTicks, cap);
            this.titleIds = Arrays.copyOf(this.titleIds, cap);
            this.subtitleIds = Arrays.copyOf(this.subtitleIds, cap);
            this.titleFadeIn = Arrays.copyOf(this.titleFadeIn, cap);
            this.titleStay = Arrays.copyOf(this.titleStay, cap);
            this.titleFadeOut = Arrays.copyOf(this.titleFadeOut, cap);
            this.titleStartRemain = Arrays.copyOf(this.titleStartRemain, cap);
        }

        int i = this.titleSize++;

        this.titleTicks[i] = tick;
        this.titleIds[i] = titleId;
        this.subtitleIds[i] = subId;
        this.titleFadeIn[i] = (short) Math.max(1, fadeIn);
        this.titleStay[i] = (short) Math.max(0, stay);
        this.titleFadeOut[i] = (short) Math.max(1, fadeOut);
        this.titleStartRemain[i] = (short) Math.max(1, remain);
    }

    private void pushOverlay(int tick, int textId, int remain, boolean tinted)
    {
        if (this.overlaySize == this.overlayTicks.length)
        {
            int cap = this.overlaySize * 2;

            this.overlayTicks = Arrays.copyOf(this.overlayTicks, cap);
            this.overlayIds = Arrays.copyOf(this.overlayIds, cap);
            this.overlayStartRemain = Arrays.copyOf(this.overlayStartRemain, cap);
            this.overlayTinted = Arrays.copyOf(this.overlayTinted, cap);
        }

        int i = this.overlaySize++;

        this.overlayTicks[i] = tick;
        this.overlayIds[i] = textId;
        this.overlayStartRemain[i] = (short) Math.max(1, remain);
        this.overlayTinted[i] = (byte) (tinted ? 1 : 0);
    }

    private void pushBossFrame(int tick, BossEntry[] entries)
    {
        if (this.bossSize == this.bossTicks.length)
        {
            int cap = this.bossSize * 2;

            this.bossTicks = Arrays.copyOf(this.bossTicks, cap);
            this.bossFrames = Arrays.copyOf(this.bossFrames, cap);
        }

        this.bossTicks[this.bossSize] = tick;
        this.bossFrames[this.bossSize] = entries;
        this.bossSize += 1;
    }

    /** Evaluates all HUD pieces at exact {@code sourceTime} ({@code ticks + transition}). */
    public boolean sample(float sourceTime, Snapshot out)
    {
        out.clear();

        if (this.isEmpty())
        {
            return false;
        }

        int whole = Math.max(0, (int) Math.floor(sourceTime));

        /* Chat lines visible at sourceTime (newest last in Snapshot.chat, up to 10 lines) */
        int chatEnd = upperBound(this.chatTicks, this.chatSize, whole);
        int chatStart = Math.max(0, chatEnd - 10);

        for (int i = chatStart; i < chatEnd; i++)
        {
            float age = sourceTime - this.chatTicks[i];

            if (age >= 0F && age < CHAT_VISIBLE_TICKS)
            {
                out.chat.add(new ChatEntry(this.pool.getString(this.chatTextIds[i]), age));
            }
        }

        /* Active title */
        int tIdx = upperBound(this.titleTicks, this.titleSize, whole) - 1;

        if (tIdx >= 0)
        {
            float elapsed = sourceTime - this.titleTicks[tIdx];
            float remain = this.titleStartRemain[tIdx] - elapsed;

            if (remain > 0F)
            {
                out.title = this.pool.getString(this.titleIds[tIdx]);
                out.subtitle = this.pool.getString(this.subtitleIds[tIdx]);
                out.titleRemain = remain;
                out.titleFadeIn = this.titleFadeIn[tIdx];
                out.titleStay = this.titleStay[tIdx];
                out.titleFadeOut = this.titleFadeOut[tIdx];
            }
        }

        /* Active action bar */
        int oIdx = upperBound(this.overlayTicks, this.overlaySize, whole) - 1;

        if (oIdx >= 0)
        {
            float elapsed = sourceTime - this.overlayTicks[oIdx];
            float remain = this.overlayStartRemain[oIdx] - elapsed;

            if (remain > 0F)
            {
                out.actionBar = this.pool.getString(this.overlayIds[oIdx]);
                out.actionRemain = remain;
                out.actionTinted = this.overlayTinted[oIdx] != 0;
            }
        }

        /* Active boss bars */
        int bIdx = upperBound(this.bossTicks, this.bossSize, whole) - 1;

        if (bIdx >= 0)
        {
            BossEntry[] entries = this.bossFrames[bIdx];

            if (entries != null)
            {
                for (BossEntry entry : entries)
                {
                    out.bossBars.add(new BossBarView(this.pool.getString(entry.nameId), entry.percent, entry.colorOrdinal, entry.styleOrdinal));
                }
            }
        }

        return true;
    }

    private static int upperBound(int[] ticks, int size, int target)
    {
        int low = 0;
        int high = size;

        while (low < high)
        {
            int mid = (low + high) >>> 1;

            if (ticks[mid] <= target)
            {
                low = mid + 1;
            }
            else
            {
                high = mid;
            }
        }

        return low;
    }

    /** Appends another slice's HUD stream shifted by {@code tickOffset}. */
    public void append(RecapHudStream other, int tickOffset)
    {
        if (other == null || other.isEmpty())
        {
            return;
        }

        int[] remap = this.pool.mergeFrom(other.pool);

        for (int i = 0; i < other.chatSize; i++)
        {
            this.pushChat(other.chatTicks[i] + tickOffset, RecapPool.remapId(remap, other.chatTextIds[i]));
        }

        for (int i = 0; i < other.titleSize; i++)
        {
            this.pushTitle(other.titleTicks[i] + tickOffset, RecapPool.remapId(remap, other.titleIds[i]), RecapPool.remapId(remap, other.subtitleIds[i]),
                other.titleFadeIn[i], other.titleStay[i], other.titleFadeOut[i], other.titleStartRemain[i]);
        }

        for (int i = 0; i < other.overlaySize; i++)
        {
            this.pushOverlay(other.overlayTicks[i] + tickOffset, RecapPool.remapId(remap, other.overlayIds[i]), other.overlayStartRemain[i], other.overlayTinted[i] != 0);
        }

        for (int i = 0; i < other.bossSize; i++)
        {
            BossEntry[] src = other.bossFrames[i];
            BossEntry[] dst = new BossEntry[src == null ? 0 : src.length];

            for (int j = 0; j < dst.length; j++)
            {
                dst[j] = new BossEntry(RecapPool.remapId(remap, src[j].nameId), src[j].percent, src[j].colorOrdinal, src[j].styleOrdinal);
            }

            this.pushBossFrame(other.bossTicks[i] + tickOffset, dst);
        }
    }

    /** Returns the events from {@code from} tick onwards, rebased to {@code 0}. */
    public static RecapHudStream tail(RecapHudStream source, int from)
    {
        RecapHudStream copy = new RecapHudStream();

        if (source == null || source.isEmpty())
        {
            return copy;
        }

        for (int i = upperBound(source.chatTicks, source.chatSize, from - CHAT_VISIBLE_TICKS); i < source.chatSize; i++)
        {
            int rebased = source.chatTicks[i] - from;

            if (rebased >= -CHAT_VISIBLE_TICKS)
            {
                copy.pushChat(Math.max(0, rebased), copy.pool.putString(source.pool.getString(source.chatTextIds[i])));
            }
        }

        for (int i = Math.max(0, upperBound(source.titleTicks, source.titleSize, from) - 1); i < source.titleSize; i++)
        {
            int elapsed = Math.max(0, from - source.titleTicks[i]);
            int remain = source.titleStartRemain[i] - elapsed;

            if (remain > 0)
            {
                copy.pushTitle(Math.max(0, source.titleTicks[i] - from),
                    copy.pool.putString(source.pool.getString(source.titleIds[i])),
                    copy.pool.putString(source.pool.getString(source.subtitleIds[i])),
                    source.titleFadeIn[i], source.titleStay[i], source.titleFadeOut[i], remain);
            }
        }

        for (int i = Math.max(0, upperBound(source.overlayTicks, source.overlaySize, from) - 1); i < source.overlaySize; i++)
        {
            int elapsed = Math.max(0, from - source.overlayTicks[i]);
            int remain = source.overlayStartRemain[i] - elapsed;

            if (remain > 0)
            {
                copy.pushOverlay(Math.max(0, source.overlayTicks[i] - from),
                    copy.pool.putString(source.pool.getString(source.overlayIds[i])),
                    remain, source.overlayTinted[i] != 0);
            }
        }

        int bossStart = Math.max(0, upperBound(source.bossTicks, source.bossSize, from) - 1);

        for (int i = bossStart; i < source.bossSize; i++)
        {
            BossEntry[] src = source.bossFrames[i];
            BossEntry[] dst = new BossEntry[src == null ? 0 : src.length];

            for (int j = 0; j < dst.length; j++)
            {
                dst[j] = new BossEntry(copy.pool.putString(source.pool.getString(src[j].nameId)), src[j].percent, src[j].colorOrdinal, src[j].styleOrdinal);
            }

            copy.pushBossFrame(Math.max(0, source.bossTicks[i] - from), dst);
        }

        return copy;
    }

    public void dropBefore(int oldest)
    {
        if (oldest <= 0 || this.isEmpty())
        {
            return;
        }

        int chatDrop = upperBound(this.chatTicks, this.chatSize, oldest - CHAT_VISIBLE_TICKS);

        if (chatDrop > 0 && chatDrop <= this.chatSize)
        {
            System.arraycopy(this.chatTicks, chatDrop, this.chatTicks, 0, this.chatSize - chatDrop);
            System.arraycopy(this.chatTextIds, chatDrop, this.chatTextIds, 0, this.chatSize - chatDrop);
            this.chatSize -= chatDrop;
        }
    }

    public boolean write(File file)
    {
        try (DataOutputStream raw = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
        {
            raw.writeInt(MAGIC);
            raw.writeInt(VERSION);

            Deflater deflater = new Deflater(Deflater.BEST_SPEED);

            try
            {
                DeflaterOutputStream packed = new DeflaterOutputStream(raw, deflater, 1 << 14);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(packed, 1 << 14));

                this.writeToStream(out);
                out.flush();
                packed.finish();
            }
            finally
            {
                deflater.end();
            }

            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public void writeToStream(DataOutputStream out) throws IOException
    {
        this.pool.write(out);

        RecapPool.writeVar(out, this.chatSize);

        for (int i = 0; i < this.chatSize; i++)
        {
            out.writeInt(this.chatTicks[i]);
            RecapPool.writeVar(out, this.chatTextIds[i] + 1);
        }

        RecapPool.writeVar(out, this.titleSize);

        for (int i = 0; i < this.titleSize; i++)
        {
            out.writeInt(this.titleTicks[i]);
            RecapPool.writeVar(out, this.titleIds[i] + 1);
            RecapPool.writeVar(out, this.subtitleIds[i] + 1);
            out.writeShort(this.titleFadeIn[i]);
            out.writeShort(this.titleStay[i]);
            out.writeShort(this.titleFadeOut[i]);
            out.writeShort(this.titleStartRemain[i]);
        }

        RecapPool.writeVar(out, this.overlaySize);

        for (int i = 0; i < this.overlaySize; i++)
        {
            out.writeInt(this.overlayTicks[i]);
            RecapPool.writeVar(out, this.overlayIds[i] + 1);
            out.writeShort(this.overlayStartRemain[i]);
            out.writeByte(this.overlayTinted[i]);
        }

        RecapPool.writeVar(out, this.bossSize);

        for (int i = 0; i < this.bossSize; i++)
        {
            out.writeInt(this.bossTicks[i]);

            BossEntry[] entries = this.bossFrames[i];
            int len = entries == null ? 0 : entries.length;

            RecapPool.writeVar(out, len);

            for (int j = 0; j < len; j++)
            {
                RecapPool.writeVar(out, entries[j].nameId + 1);
                out.writeFloat(entries[j].percent);
                out.writeByte(entries[j].colorOrdinal);
                out.writeByte(entries[j].styleOrdinal);
            }
        }
    }

    public static RecapHudStream read(File file)
    {
        if (!file.isFile())
        {
            return null;
        }

        try (DataInputStream raw = new DataInputStream(new BufferedInputStream(new FileInputStream(file))))
        {
            if (raw.readInt() != MAGIC || raw.readInt() != VERSION)
            {
                return null;
            }

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 14), 1 << 14)))
            {
                return readFromStream(in);
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }

    public static RecapHudStream readFromStream(DataInputStream in) throws IOException
    {
        RecapHudStream hud = new RecapHudStream();
        RecapPool loadedPool = RecapPool.read(in);
        int[] remap = hud.pool.mergeFrom(loadedPool);

        int chats = RecapPool.readVar(in);

        for (int i = 0; i < chats; i++)
        {
            int tick = in.readInt();
            int id = RecapPool.remapId(remap, RecapPool.readVar(in) - 1);

            hud.pushChat(tick, id);
        }

        int titles = RecapPool.readVar(in);

        for (int i = 0; i < titles; i++)
        {
            int tick = in.readInt();
            int tId = RecapPool.remapId(remap, RecapPool.readVar(in) - 1);
            int sId = RecapPool.remapId(remap, RecapPool.readVar(in) - 1);

            hud.pushTitle(tick, tId, sId, in.readShort(), in.readShort(), in.readShort(), in.readShort());
        }

        int overlays = RecapPool.readVar(in);

        for (int i = 0; i < overlays; i++)
        {
            int tick = in.readInt();
            int id = RecapPool.remapId(remap, RecapPool.readVar(in) - 1);

            hud.pushOverlay(tick, id, in.readShort(), in.readByte() != 0);
        }

        int bosses = RecapPool.readVar(in);

        for (int i = 0; i < bosses; i++)
        {
            int tick = in.readInt();
            int len = RecapPool.readVar(in);
            BossEntry[] entries = new BossEntry[Math.max(0, Math.min(16, len))];

            for (int j = 0; j < len; j++)
            {
                int nameId = RecapPool.remapId(remap, RecapPool.readVar(in) - 1);
                float pct = in.readFloat();
                int col = in.readByte();
                int sty = in.readByte();

                if (j < entries.length)
                {
                    entries[j] = new BossEntry(nameId, pct, col, sty);
                }
            }

            hud.pushBossFrame(tick, entries);
        }

        return hud;
    }
}
