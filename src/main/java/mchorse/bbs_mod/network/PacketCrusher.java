package mchorse.bbs_mod.network;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class PacketCrusher
{
    public static final int BUFFER_SIZE = 30_000;

    /* Upper bound on how many chunks a single logical packet may declare, and on the fully
     * assembled byte count. Both are attacker/corruption controlled (they come straight off
     * the wire) and are used to size allocations, so they must be clamped before any
     * new byte[]/ByteArrayOutputStream is created - otherwise a single malformed header can
     * force an OutOfMemoryError on the receiving side. */
    public static final int MAX_TOTAL_CHUNKS = 4_000;
    public static final int MAX_ASSEMBLED_SIZE = 32 * 1024 * 1024;

    private Map<Integer, ByteArrayOutputStream> chunks = new HashMap<>();
    private int counter;

    public void reset()
    {
        this.chunks.clear();
        this.counter = 0;
    }

    public void receive(PacketByteBuf buf, IBufferReceiver receiver)
    {
        int id = buf.readInt();
        int index = buf.readInt();
        int total = buf.readInt();
        int size = buf.readInt();

        /* Reject malformed or oversized chunk headers before allocating anything. A negative
         * or absurd total/size/index here means either data corruption or a hostile sender,
         * and letting it through would size a byte[]/ByteArrayOutputStream off of untrusted
         * input. */
        if (total <= 0 || total > MAX_TOTAL_CHUNKS || size < 0 || size > BUFFER_SIZE || index < 0 || index >= total)
        {
            return;
        }

        long declaredAssembledSize = (long) total * (long) BUFFER_SIZE;

        if (declaredAssembledSize > MAX_ASSEMBLED_SIZE)
        {
            this.chunks.remove(id);

            return;
        }

        byte[] bytes = new byte[size];

        buf.readBytes(bytes);

        int initialCapacity = (int) Math.min(declaredAssembledSize, MAX_ASSEMBLED_SIZE);
        ByteArrayOutputStream map = this.chunks.computeIfAbsent(id, (k) -> new ByteArrayOutputStream(initialCapacity));

        map.writeBytes(bytes);

        if (map.size() > MAX_ASSEMBLED_SIZE)
        {
            /* The assembled buffer grew past the cap chunk by chunk (each chunk itself is
             * within BUFFER_SIZE, so the earlier header check alone cannot catch this). Drop
             * the partial transfer instead of letting it keep growing. */
            this.chunks.remove(id);

            return;
        }

        if (index == total - 1)
        {
            byte[] finalBytes = map.toByteArray();

            if (finalBytes.length == 1 && finalBytes[0] == 69)
            {
                finalBytes = null;
            }

            receiver.receiveBuffer(finalBytes, buf);
            this.chunks.remove(id);
        }
    }

    public void send(PlayerEntity entity, Identifier identifier, BaseType baseType, Consumer<PacketByteBuf> consumer)
    {
        this.send(Collections.singleton(entity), identifier, baseType, consumer);
    }

    public void send(PlayerEntity entity, Identifier identifier, byte[] bytes, Consumer<PacketByteBuf> consumer)
    {
        this.send(Collections.singleton(entity), identifier, bytes, consumer);
    }

    public void send(Collection<PlayerEntity> entities, Identifier identifier, BaseType baseType, Consumer<PacketByteBuf> consumer)
    {
        this.send(entities, identifier, DataStorageUtils.writeToBytes(baseType), consumer);
    }

    public void send(Collection<PlayerEntity> entities, Identifier identifier, byte[] bytes, Consumer<PacketByteBuf> consumer)
    {
        if (bytes.length == 0)
        {
            bytes = new byte[]{69};
        }

        int total = Math.max((int) Math.ceil(bytes.length / (float) BUFFER_SIZE), 1);
        int counter = this.counter;

        for (int index = 0; index < total; index++)
        {
            int offset = index * BUFFER_SIZE;

            PacketByteBuf buf = PacketByteBufs.create();
            int size = Math.min(BUFFER_SIZE, bytes.length - offset);

            buf.writeInt(counter);
            buf.writeInt(index);
            buf.writeInt(total);
            buf.writeInt(size);
            buf.writeBytes(bytes, offset, size);

            if (consumer != null && index == total - 1)
            {
                consumer.accept(buf);
            }

            for (PlayerEntity playerEntity : entities)
            {
                this.sendBuffer(playerEntity, identifier, buf);
            }
        }

        this.counter += 1;
    }

    protected abstract void sendBuffer(PlayerEntity entity, Identifier identifier, PacketByteBuf buf);
}