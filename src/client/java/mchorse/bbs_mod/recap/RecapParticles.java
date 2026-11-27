package mchorse.bbs_mod.recap;

import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * The particles of the world while a recap was recorded, each with its tick.
 *
 * <p>Tracks spawned playback particles so scrubbing immediately clears lingering smoke and dust
 * from an earlier cursor position, and supports deterministic {@link #preRoll} aging so cuts and
 * video exports starting mid-action already have active particles in flight.</p>
 */
public class RecapParticles
{
    private static final int MAGIC = 0x52435031;
    private static final int VERSION = 1;
    private static final int PER_TICK = 400;

    public static final byte EFFECT = 0;
    public static final byte BLOCK_BREAK = 1;
    public static final byte BLOCK_HIT = 2;

    /** Set while a recap's own particles are sent off, so neither the recorder nor the muting takes them. */
    public static boolean replaying;
    private static final List<Particle> spawned = new ArrayList<>();
    private static List<Particle> batchCapture;

    private int[] ticks = new int[64];
    private byte[] kinds = new byte[64];
    private int[] types = new int[64];
    private double[] xs = new double[64];
    private double[] ys = new double[64];
    private double[] zs = new double[64];
    private float[] vxs = new float[64];
    private float[] vys = new float[64];
    private float[] vzs = new float[64];
    private byte[][] payloads = new byte[64][];
    private short[] dims = new short[64];
    private int head;
    private int size;

    private final List<String> names = new ArrayList<>();
    private final List<String> dimensions = new ArrayList<>();

    private int countedTick = Integer.MIN_VALUE;
    private int counted;

    /** Called by {@code RecapParticlesMixin} whenever a particle enters {@code ParticleManager} during replay. */
    public static void trackSpawned(Particle particle)
    {
        if (particle == null)
        {
            return;
        }

        if (spawned.size() > 4096)
        {
            spawned.removeIf((p) -> p == null || !p.isAlive());
        }

        spawned.add(particle);

        if (batchCapture != null)
        {
            batchCapture.add(particle);
        }
    }

    /** Immediately removes all particles created by Recap playback (on scrub, rewind or scene exit). */
    public static void clearSpawned()
    {
        if (spawned.isEmpty())
        {
            return;
        }

        for (Particle particle : spawned)
        {
            if (particle != null && particle.isAlive())
            {
                particle.markDead();
            }
        }

        spawned.clear();
    }

    public int size()
    {
        return this.size;
    }

    public boolean isEmpty()
    {
        return this.size == 0;
    }

    public void clear()
    {
        this.head = 0;
        this.size = 0;
        Arrays.fill(this.payloads, null);
        this.names.clear();
        this.dimensions.clear();
    }

    private static int index(List<String> list, String value)
    {
        int index = list.indexOf(value);

        if (index < 0)
        {
            index = list.size();
            list.add(value);
        }

        return index;
    }

    private boolean room(int tick)
    {
        if (tick != this.countedTick)
        {
            this.countedTick = tick;
            this.counted = 0;
        }

        return this.counted++ < PER_TICK;
    }

    public void addEffect(int tick, ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz, String dimension)
    {
        if (effect == null || effect.getType() == ParticleTypes.RAIN || !this.room(tick))
        {
            return;
        }

        Identifier id = Registries.PARTICLE_TYPE.getId(effect.getType());

        if (id == null)
        {
            return;
        }

        byte[] payload = null;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        try
        {
            effect.write(buf);

            if (buf.readableBytes() > 0)
            {
                payload = new byte[buf.readableBytes()];
                buf.readBytes(payload);
            }
        }
        catch (RuntimeException e)
        {
            return;
        }
        finally
        {
            buf.release();
        }

        this.push(tick, EFFECT, index(this.names, id.toString()), x, y, z, (float) vx, (float) vy, (float) vz, payload, this.dim(dimension));
    }

    public void addBlockBreak(int tick, BlockPos pos, BlockState state, String dimension)
    {
        if (state == null || !this.room(tick))
        {
            return;
        }

        byte[] payload = intBytes(Block.getRawIdFromState(state));

        this.push(tick, BLOCK_BREAK, -1, pos.getX(), pos.getY(), pos.getZ(), 0F, 0F, 0F, payload, this.dim(dimension));
    }

    public void addBlockHit(int tick, BlockPos pos, Direction side, String dimension)
    {
        if (side == null || !this.room(tick))
        {
            return;
        }

        this.push(tick, BLOCK_HIT, -1, pos.getX(), pos.getY(), pos.getZ(), 0F, 0F, 0F, new byte[] {(byte) side.ordinal()}, this.dim(dimension));
    }

    public void append(RecapParticles other, int tickOffset)
    {
        if (other == null || other.isEmpty())
        {
            return;
        }

        for (int i = 0; i < other.size; i++)
        {
            int at = other.head + i;
            int mappedType = other.types[at] < 0 ? -1 : index(this.names, other.names.get(other.types[at]));
            int dIdx = other.dims[at];
            String dim = dIdx >= 0 && dIdx < other.dimensions.size() ? other.dimensions.get(dIdx) : null;

            this.push(other.ticks[at] + tickOffset, other.kinds[at], mappedType, other.xs[at], other.ys[at], other.zs[at],
                other.vxs[at], other.vys[at], other.vzs[at], other.payloads[at], this.dim(dim));
        }
    }

    private int dim(String dimension)
    {
        return dimension == null ? -1 : index(this.dimensions, dimension);
    }

    private static byte[] intBytes(int value)
    {
        return new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    private static int bytesInt(byte[] bytes)
    {
        return bytes == null || bytes.length < 4 ? 0 : ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
    }

    private void push(int tick, int kind, int type, double x, double y, double z, float vx, float vy, float vz, byte[] payload, int dim)
    {
        if (this.head + this.size == this.ticks.length)
        {
            if (this.head > 0 && this.head >= this.ticks.length / 4)
            {
                this.compact();
            }
            else
            {
                int capacity = Math.max(64, this.ticks.length * 2);

                this.ticks = Arrays.copyOf(this.ticks, capacity);
                this.kinds = Arrays.copyOf(this.kinds, capacity);
                this.types = Arrays.copyOf(this.types, capacity);
                this.xs = Arrays.copyOf(this.xs, capacity);
                this.ys = Arrays.copyOf(this.ys, capacity);
                this.zs = Arrays.copyOf(this.zs, capacity);
                this.vxs = Arrays.copyOf(this.vxs, capacity);
                this.vys = Arrays.copyOf(this.vys, capacity);
                this.vzs = Arrays.copyOf(this.vzs, capacity);
                this.payloads = Arrays.copyOf(this.payloads, capacity);
                this.dims = Arrays.copyOf(this.dims, capacity);
            }
        }

        int i = this.head + this.size++;

        this.ticks[i] = tick;
        this.kinds[i] = (byte) kind;
        this.types[i] = type;
        this.xs[i] = x;
        this.ys[i] = y;
        this.zs[i] = z;
        this.vxs[i] = vx;
        this.vys[i] = vy;
        this.vzs[i] = vz;
        this.payloads[i] = payload;
        this.dims[i] = (short) dim;
    }

    private void compact()
    {
        System.arraycopy(this.ticks, this.head, this.ticks, 0, this.size);
        System.arraycopy(this.kinds, this.head, this.kinds, 0, this.size);
        System.arraycopy(this.types, this.head, this.types, 0, this.size);
        System.arraycopy(this.xs, this.head, this.xs, 0, this.size);
        System.arraycopy(this.ys, this.head, this.ys, 0, this.size);
        System.arraycopy(this.zs, this.head, this.zs, 0, this.size);
        System.arraycopy(this.vxs, this.head, this.vxs, 0, this.size);
        System.arraycopy(this.vys, this.head, this.vys, 0, this.size);
        System.arraycopy(this.vzs, this.head, this.vzs, 0, this.size);
        System.arraycopy(this.payloads, this.head, this.payloads, 0, this.size);
        System.arraycopy(this.dims, this.head, this.dims, 0, this.size);
        Arrays.fill(this.payloads, this.size, this.payloads.length, null);
        this.head = 0;
    }

    private int firstAtOrAfter(int tick)
    {
        int low = 0;
        int high = this.size;

        while (low < high)
        {
            int middle = (low + high) >>> 1;

            if (this.ticks[this.head + middle] < tick)
            {
                low = middle + 1;
            }
            else
            {
                high = middle;
            }
        }

        return low;
    }

    public void dropBefore(int oldest)
    {
        int first = this.firstAtOrAfter(oldest);

        Arrays.fill(this.payloads, this.head, this.head + first, null);
        this.head += first;
        this.size -= first;

        if (this.size == 0)
        {
            this.head = 0;
        }
    }

    public static RecapParticles tail(RecapParticles source, int from)
    {
        RecapParticles particles = new RecapParticles();

        particles.names.addAll(source.names);
        particles.dimensions.addAll(source.dimensions);

        for (int i = source.firstAtOrAfter(from); i < source.size; i++)
        {
            int at = source.head + i;

            particles.push(source.ticks[at] - from, source.kinds[at], source.types[at], source.xs[at], source.ys[at], source.zs[at],
                source.vxs[at], source.vys[at], source.vzs[at], source.payloads[at], source.dims[at]);
        }

        return particles;
    }

    public void play(int from, int to, String dimension)
    {
        if (to <= from || this.size == 0)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null)
        {
            return;
        }

        int sent = 0;

        replaying = true;

        try
        {
            for (int i = this.firstAtOrAfter(from + 1); i < this.size && sent < 1500; i++)
            {
                int at = this.head + i;

                if (this.ticks[at] > to)
                {
                    break;
                }

                int dim = this.dims[at];

                if (dim >= 0 && dimension != null && !this.dimensions.get(dim).equals(dimension))
                {
                    continue;
                }

                try
                {
                    this.send(mc, at);
                    sent += 1;
                }
                catch (RuntimeException e)
                {
                    /* A particle that cannot be read back is left out, never the picture */
                }
            }
        }
        finally
        {
            replaying = false;
        }
    }

    /**
     * Spawns particles from {@code [targetTick - window, targetTick]} and steps each forward by
     * {@code targetTick - spawnTick} ticks so scrubbed frames and mid-timeline video exports start
     * with warm, naturally aged particles.
     */
    public void preRoll(int targetTick, int window, String dimension)
    {
        if (this.size == 0 || targetTick < 0)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null)
        {
            return;
        }

        int from = Math.max(0, targetTick - Math.max(1, window));
        List<Particle> created = new ArrayList<>();
        int sent = 0;

        replaying = true;

        try
        {
            for (int i = this.firstAtOrAfter(from); i < this.size && sent < 800; i++)
            {
                int at = this.head + i;
                int spawnTick = this.ticks[at];

                if (spawnTick > targetTick)
                {
                    break;
                }

                int dim = this.dims[at];

                if (dim >= 0 && dimension != null && !this.dimensions.get(dim).equals(dimension))
                {
                    continue;
                }

                created.clear();
                batchCapture = created;

                try
                {
                    this.send(mc, at);
                    sent += 1;

                    int ageSteps = targetTick - spawnTick;

                    for (int p = 0; p < created.size(); p++)
                    {
                        Particle particle = created.get(p);

                        for (int step = 0; step < ageSteps && particle.isAlive(); step++)
                        {
                            particle.tick();
                        }
                    }
                }
                catch (RuntimeException ignored)
                {}
                finally
                {
                    batchCapture = null;
                }
            }
        }
        finally
        {
            batchCapture = null;
            replaying = false;
        }
    }

    private void send(MinecraftClient mc, int at)
    {
        byte kind = this.kinds[at];

        if (kind == BLOCK_BREAK)
        {
            BlockState state = Block.getStateFromRawId(bytesInt(this.payloads[at]));

            mc.particleManager.addBlockBreakParticles(BlockPos.ofFloored(this.xs[at], this.ys[at], this.zs[at]), state);

            return;
        }

        if (kind == BLOCK_HIT)
        {
            byte[] payload = this.payloads[at];
            Direction side = Direction.values()[payload == null || payload.length == 0 ? 0 : Math.floorMod(payload[0], 6)];

            mc.particleManager.addBlockBreakingParticles(BlockPos.ofFloored(this.xs[at], this.ys[at], this.zs[at]), side);

            return;
        }

        ParticleType<?> type = Registries.PARTICLE_TYPE.get(Identifier.tryParse(this.names.get(this.types[at])));

        if (type == null)
        {
            return;
        }

        byte[] payload = this.payloads[at];
        PacketByteBuf buf = new PacketByteBuf(payload == null ? Unpooled.buffer(0) : Unpooled.wrappedBuffer(payload));

        try
        {
            ParticleEffect effect = read(type, buf);

            mc.particleManager.addParticle(effect, this.xs[at], this.ys[at], this.zs[at], this.vxs[at], this.vys[at], this.vzs[at]);
        }
        finally
        {
            buf.release();
        }
    }

    private static <T extends ParticleEffect> ParticleEffect read(ParticleType<T> type, PacketByteBuf buf)
    {
        return type.getParametersFactory().read(type, buf);
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
                DeflaterOutputStream packed = new DeflaterOutputStream(raw, deflater, 1 << 16);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(packed, 1 << 16));

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
        out.writeInt(this.names.size());

        for (String name : this.names)
        {
            out.writeUTF(name);
        }

        out.writeInt(this.dimensions.size());

        for (String dimension : this.dimensions)
        {
            out.writeUTF(dimension);
        }

        out.writeInt(this.size);

        for (int i = 0; i < this.size; i++)
        {
            int at = this.head + i;
            byte[] payload = this.payloads[at];

            out.writeInt(this.ticks[at]);
            out.writeByte(this.kinds[at]);
            out.writeInt(this.types[at]);
            out.writeDouble(this.xs[at]);
            out.writeDouble(this.ys[at]);
            out.writeDouble(this.zs[at]);
            out.writeFloat(this.vxs[at]);
            out.writeFloat(this.vys[at]);
            out.writeFloat(this.vzs[at]);
            out.writeShort(this.dims[at]);
            out.writeInt(payload == null ? 0 : payload.length);

            if (payload != null)
            {
                out.write(payload);
            }
        }
    }

    public static RecapParticles read(File file)
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

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 16), 1 << 16)))
            {
                return readFromStream(in);
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }

    public static RecapParticles readFromStream(DataInputStream in) throws IOException
    {
        RecapParticles particles = new RecapParticles();
        int names = in.readInt();

        for (int i = 0; i < names; i++)
        {
            particles.names.add(in.readUTF());
        }

        int dimensions = in.readInt();

        for (int i = 0; i < dimensions; i++)
        {
            particles.dimensions.add(in.readUTF());
        }

        int count = in.readInt();

        for (int i = 0; i < count; i++)
        {
            int tick = in.readInt();
            int kind = in.readByte();
            int type = in.readInt();
            double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
            float vx = in.readFloat(), vy = in.readFloat(), vz = in.readFloat();
            int dim = in.readShort();
            int length = in.readInt();

            if (length < 0 || length > 1 << 20)
            {
                throw new IOException("Bad particle payload");
            }

            byte[] payload = length == 0 ? null : new byte[length];

            if (payload != null)
            {
                in.readFully(payload);
            }

            if (type < names && dim < dimensions)
            {
                particles.push(tick, kind, type, x, y, z, vx, vy, vz, payload, dim);
            }
        }

        return particles;
    }
}
