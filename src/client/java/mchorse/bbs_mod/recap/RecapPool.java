package mchorse.bbs_mod.recap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Content-addressed binary dictionary for deduplicating repeated payloads across a recap:
 * entity look NBT, item NBT, block entity NBT, HUD text payloads and section palettes.
 *
 * <p>Uses a 64-bit byte-mixing hash with open addressing and mandatory {@link Arrays#equals}
 * verification on every candidate match, eliminating hash collision bugs while keeping memory
 * and disk usage proportional to unique content only.</p>
 */
public final class RecapPool
{
    private static final byte[] EMPTY = new byte[0];
    private static final int MAX_ENTRY_BYTES = 1 << 24;
    private static final int MAX_ENTRIES = 1 << 22;

    private final List<byte[]> entries = new ArrayList<>();
    private final List<String> cachedStrings = new ArrayList<>();
    private long[] hashes = new long[64];
    private int[] next = new int[64];
    private int[] buckets = new int[128];

    public RecapPool()
    {
        Arrays.fill(this.buckets, -1);
    }

    public int size()
    {
        return this.entries.size();
    }

    public boolean isEmpty()
    {
        return this.entries.isEmpty();
    }

    public void clear()
    {
        this.entries.clear();
        this.cachedStrings.clear();
        Arrays.fill(this.buckets, -1);
    }

    /** Interns {@code data}, returning {@code -1} for null or empty arrays, else its {@code 0..N-1} id. */
    public int put(byte[] data)
    {
        if (data == null || data.length == 0)
        {
            return -1;
        }

        long hash = hash64(data);
        int mask = this.buckets.length - 1;
        int slot = (int) (hash ^ (hash >>> 32)) & mask;
        int candidate = this.buckets[slot];

        while (candidate >= 0)
        {
            if (this.hashes[candidate] == hash && Arrays.equals(this.entries.get(candidate), data))
            {
                return candidate;
            }

            candidate = this.next[candidate];
        }

        int id = this.entries.size();

        if (id == this.hashes.length)
        {
            int cap = this.hashes.length * 2;

            this.hashes = Arrays.copyOf(this.hashes, cap);
            this.next = Arrays.copyOf(this.next, cap);
        }

        byte[] copy = Arrays.copyOf(data, data.length);

        this.entries.add(copy);
        this.cachedStrings.add(null);
        this.hashes[id] = hash;
        this.next[id] = this.buckets[slot];
        this.buckets[slot] = id;

        if (this.entries.size() * 4 >= this.buckets.length * 3)
        {
            this.rehash(this.buckets.length * 2);
        }

        return id;
    }

    /** Interns a UTF-8 string, returning {@code -1} when null or empty. */
    public int putString(String text)
    {
        if (text == null || text.isEmpty())
        {
            return -1;
        }

        int id = this.put(text.getBytes(StandardCharsets.UTF_8));

        if (id >= 0 && this.cachedStrings.get(id) == null)
        {
            this.cachedStrings.set(id, text);
        }

        return id;
    }

    /** Returns the bytes stored at {@code id}, or an empty array when {@code id < 0} or out of bounds. */
    public byte[] get(int id)
    {
        if (id < 0 || id >= this.entries.size())
        {
            return EMPTY;
        }

        return this.entries.get(id);
    }

    /** Returns the decoded UTF-8 string stored at {@code id}, cached after first decode, or {@code ""}. */
    public String getString(int id)
    {
        if (id < 0 || id >= this.entries.size())
        {
            return "";
        }

        String cached = this.cachedStrings.get(id);

        if (cached == null)
        {
            cached = new String(this.entries.get(id), StandardCharsets.UTF_8);
            this.cachedStrings.set(id, cached);
        }

        return cached;
    }

    /** Copies all entries from {@code other}, returning a remap table from {@code other}'s ids to this pool's ids. */
    public int[] mergeFrom(RecapPool other)
    {
        if (other == null || other.isEmpty())
        {
            return new int[0];
        }

        int[] remap = new int[other.size()];

        for (int i = 0; i < remap.length; i++)
        {
            remap[i] = this.put(other.entries.get(i));
        }

        return remap;
    }

    public static int remapId(int[] remap, int id)
    {
        return id < 0 || remap == null || id >= remap.length ? -1 : remap[id];
    }

    private void rehash(int newBucketCount)
    {
        int[] fresh = new int[newBucketCount];
        int mask = newBucketCount - 1;

        Arrays.fill(fresh, -1);

        for (int i = 0, n = this.entries.size(); i < n; i++)
        {
            long hash = this.hashes[i];
            int slot = (int) (hash ^ (hash >>> 32)) & mask;

            this.next[i] = fresh[slot];
            fresh[slot] = i;
        }

        this.buckets = fresh;
    }

    static long hash64(byte[] data)
    {
        long h = 0x9E3779B97F4A7C15L ^ (data.length * 0xBF58476D1CE4E5B9L);
        int i = 0;
        int limit = data.length - 7;

        while (i < limit)
        {
            long word = ((long) data[i] & 0xFFL)
                | (((long) data[i + 1] & 0xFFL) << 8)
                | (((long) data[i + 2] & 0xFFL) << 16)
                | (((long) data[i + 3] & 0xFFL) << 24)
                | (((long) data[i + 4] & 0xFFL) << 32)
                | (((long) data[i + 5] & 0xFFL) << 40)
                | (((long) data[i + 6] & 0xFFL) << 48)
                | (((long) data[i + 7] & 0xFFL) << 56);

            word *= 0xBF58476D1CE4E5B9L;
            word ^= word >>> 31;
            h = (h ^ word) * 0x94D049BB133111EBL;
            i += 8;
        }

        while (i < data.length)
        {
            h ^= (long) data[i] & 0xFFL;
            h *= 0x94D049BB133111EBL;
            i += 1;
        }

        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;

        return h ^ (h >>> 31);
    }

    public void write(DataOutput out) throws IOException
    {
        writeVar(out, this.entries.size());

        for (int i = 0, n = this.entries.size(); i < n; i++)
        {
            byte[] bytes = this.entries.get(i);

            writeVar(out, bytes.length);
            out.write(bytes);
        }
    }

    public static RecapPool read(DataInput in) throws IOException
    {
        int count = readVar(in);

        if (count < 0 || count > MAX_ENTRIES)
        {
            throw new IOException("Bad pool count: " + count);
        }

        RecapPool pool = new RecapPool();

        for (int i = 0; i < count; i++)
        {
            int length = readVar(in);

            if (length < 0 || length > MAX_ENTRY_BYTES)
            {
                throw new IOException("Bad pool entry length: " + length);
            }

            byte[] bytes = new byte[length];

            in.readFully(bytes);
            pool.appendLoaded(bytes);
        }

        return pool;
    }

    /** Preserves exact serialized entry indices on load while rebuilding the hash index. */
    private void appendLoaded(byte[] bytes)
    {
        int id = this.entries.size();

        if (id == this.hashes.length)
        {
            int cap = this.hashes.length * 2;

            this.hashes = Arrays.copyOf(this.hashes, cap);
            this.next = Arrays.copyOf(this.next, cap);
        }

        long hash = hash64(bytes);
        int slot = (int) (hash ^ (hash >>> 32)) & (this.buckets.length - 1);

        this.entries.add(bytes);
        this.cachedStrings.add(null);
        this.hashes[id] = hash;
        this.next[id] = this.buckets[slot];
        this.buckets[slot] = id;

        if (this.entries.size() * 4 >= this.buckets.length * 3)
        {
            this.rehash(this.buckets.length * 2);
        }
    }

    public static void writeVar(DataOutput out, int value) throws IOException
    {
        while ((value & ~0x7F) != 0)
        {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        out.writeByte(value);
    }

    public static int readVar(DataInput in) throws IOException
    {
        int value = 0;

        for (int shift = 0; shift < 35; shift += 7)
        {
            int b = in.readByte();

            value |= (b & 0x7F) << shift;

            if ((b & 0x80) == 0)
            {
                return value;
            }
        }

        throw new IOException("Bad variable integer");
    }
}
