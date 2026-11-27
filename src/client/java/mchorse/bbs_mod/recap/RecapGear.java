package mchorse.bbs_mod.recap;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

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
 * What the recorded player had on it and how it was doing, tick by tick: the hotbar, the off hand
 * and the armor, which slot was picked, and the hearts, absorption, food, air, armor points,
 * experience and game mode its interface showed.
 *
 * <p>The numbers are kept every tick, like the camera; the items only when a slot changes, so a
 * hotbar held for an hour is fourteen items, not seventy two thousand times fourteen. An item at a
 * tick is the slot's last change at or before it.</p>
 *
 * <p>Played back by lending them to the player while the recorded player is drawn, its first person
 * hands are drawn and its interface is drawn over the frame, and taking them back after.</p>
 */
public class RecapGear
{
    private static final int MAGIC = 0x52434731;
    private static final int VERSION = 1;

    /** Slots 0 to 8 are the hotbar, 9 the off hand, 10 to 13 the armor from the feet up. */
    public static final int SLOTS = 14;
    public static final int OFF_HAND = 9;
    public static final int ARMOR = 10;

    /* Every tick */
    private byte[] selected = new byte[64];
    private float[] health = new float[64];
    private float[] maxHealth = new float[64];
    private float[] absorption = new float[64];
    private byte[] food = new byte[64];
    private short[] air = new short[64];
    private int[] level = new int[64];
    private float[] progress = new float[64];
    private byte[] armor = new byte[64];
    private byte[] mode = new byte[64];
    private int size;

    /* The items, by slot, as they changed */
    @SuppressWarnings("unchecked")
    private final List<Change>[] items = new List[SLOTS];

    public record Change(int tick, ItemStack stack)
    {}

    /** One moment of it. */
    public static class Frame
    {
        public int selected;
        public float health;
        public float maxHealth;
        public float absorption;
        public int food;
        public int air;
        public int level;
        public float progress;
        public int armor;
        /** The game mode's id, or -1 when it was not known. */
        public int mode = -1;
        public final ItemStack[] items = new ItemStack[SLOTS];

        public ItemStack mainHand()
        {
            ItemStack stack = this.items[Math.max(0, Math.min(8, this.selected))];

            return stack == null ? ItemStack.EMPTY : stack;
        }

        public ItemStack item(int slot)
        {
            ItemStack stack = this.items[slot];

            return stack == null ? ItemStack.EMPTY : stack;
        }
    }

    public RecapGear()
    {
        for (int i = 0; i < SLOTS; i++)
        {
            this.items[i] = new ArrayList<>();
        }
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
        this.size = 0;

        for (List<Change> list : this.items)
        {
            list.clear();
        }
    }

    private static ItemStack slot(PlayerInventory inventory, int slot)
    {
        if (slot < 9)
        {
            return inventory.main.get(slot);
        }

        return slot == OFF_HAND ? inventory.offHand.get(0) : inventory.armor.get(slot - ARMOR);
    }

    /** Keeps the player as it is this tick; {@code gameMode} is the game mode's id, or -1. */
    public void add(PlayerEntity player, int gameMode)
    {
        PlayerInventory inventory = player.getInventory();
        int tick = this.size;

        for (int i = 0; i < SLOTS; i++)
        {
            ItemStack now = slot(inventory, i);
            List<Change> list = this.items[i];
            ItemStack last = list.isEmpty() ? ItemStack.EMPTY : list.get(list.size() - 1).stack;

            if (list.isEmpty() ? !now.isEmpty() : !ItemStack.areEqual(last, now))
            {
                list.add(new Change(tick, now.copy()));
            }
        }

        this.push(inventory.selectedSlot, player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount(), player.getHungerManager().getFoodLevel(),
            player.getAir(), player.experienceLevel, player.experienceProgress, player.getArmor(), gameMode);
    }

    /** The last tick again, so the frames keep step with the ticks while there is no player. */
    public void repeat()
    {
        if (this.size == 0)
        {
            return;
        }

        int i = this.size - 1;

        this.push(this.selected[i], this.health[i], this.maxHealth[i], this.absorption[i], this.food[i], this.air[i], this.level[i], this.progress[i], this.armor[i], this.mode[i]);
    }

    private void push(int selected, float health, float maxHealth, float absorption, int food, int air, int level, float progress, int armor, int mode)
    {
        if (this.size == this.selected.length)
        {
            int capacity = this.size * 2;

            this.selected = Arrays.copyOf(this.selected, capacity);
            this.health = Arrays.copyOf(this.health, capacity);
            this.maxHealth = Arrays.copyOf(this.maxHealth, capacity);
            this.absorption = Arrays.copyOf(this.absorption, capacity);
            this.food = Arrays.copyOf(this.food, capacity);
            this.air = Arrays.copyOf(this.air, capacity);
            this.level = Arrays.copyOf(this.level, capacity);
            this.progress = Arrays.copyOf(this.progress, capacity);
            this.armor = Arrays.copyOf(this.armor, capacity);
            this.mode = Arrays.copyOf(this.mode, capacity);
        }

        int i = this.size++;

        this.selected[i] = (byte) selected;
        this.health[i] = health;
        this.maxHealth[i] = maxHealth;
        this.absorption[i] = absorption;
        this.food[i] = (byte) food;
        this.air[i] = (short) air;
        this.level[i] = level;
        this.progress[i] = progress;
        this.armor[i] = (byte) armor;
        this.mode[i] = (byte) mode;
    }

    /** It at {@code time}, in ticks: the tick at or before it; false when empty. */
    public boolean sample(float time, Frame out)
    {
        if (this.size == 0)
        {
            return false;
        }

        int i = Math.max(0, Math.min(this.size - 1, (int) Math.floor(time)));

        out.selected = this.selected[i];
        out.health = this.health[i];
        out.maxHealth = this.maxHealth[i];
        out.absorption = this.absorption[i];
        out.food = this.food[i];
        out.air = this.air[i];
        out.level = this.level[i];
        out.progress = this.progress[i];
        out.armor = this.armor[i];
        out.mode = this.mode[i];

        for (int slot = 0; slot < SLOTS; slot++)
        {
            out.items[slot] = itemAt(this.items[slot], i);
        }

        return true;
    }

    private static ItemStack itemAt(List<Change> list, int tick)
    {
        int low = 0;
        int high = list.size() - 1;
        ItemStack found = ItemStack.EMPTY;

        while (low <= high)
        {
            int middle = (low + high) >>> 1;
            Change change = list.get(middle);

            if (change.tick <= tick)
            {
                found = change.stack;
                low = middle + 1;
            }
            else
            {
                high = middle - 1;
            }
        }

        return found;
    }

    /** The last {@code count} ticks of another, the items as they were where they start. */
    public static RecapGear tail(RecapGear from, int count)
    {
        RecapGear gear = new RecapGear();
        int start = Math.max(0, from.size - count);

        for (int i = start; i < from.size; i++)
        {
            gear.push(from.selected[i], from.health[i], from.maxHealth[i], from.absorption[i], from.food[i], from.air[i], from.level[i], from.progress[i], from.armor[i], from.mode[i]);
        }

        for (int slot = 0; slot < SLOTS; slot++)
        {
            ItemStack first = itemAt(from.items[slot], start);

            if (!first.isEmpty())
            {
                gear.items[slot].add(new Change(0, first));
            }

            for (Change change : from.items[slot])
            {
                if (change.tick > start)
                {
                    gear.items[slot].add(new Change(change.tick - start, change.stack));
                }
            }
        }

        return gear;
    }

    /** Drops the oldest ticks so no more than about {@code limit} are kept, in chunks. */
    public void trim(int limit)
    {
        if (this.size < limit + Math.max(20, limit / 8))
        {
            return;
        }

        RecapGear kept = tail(this, limit);

        this.selected = kept.selected;
        this.health = kept.health;
        this.maxHealth = kept.maxHealth;
        this.absorption = kept.absorption;
        this.food = kept.food;
        this.air = kept.air;
        this.level = kept.level;
        this.progress = kept.progress;
        this.armor = kept.armor;
        this.mode = kept.mode;
        this.size = kept.size;

        for (int slot = 0; slot < SLOTS; slot++)
        {
            this.items[slot].clear();
            this.items[slot].addAll(kept.items[slot]);
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
                DeflaterOutputStream packed = new DeflaterOutputStream(raw, deflater, 1 << 15);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(packed, 1 << 15));

                out.writeInt(this.size);

                for (int i = 0; i < this.size; i++)
                {
                    out.writeByte(this.selected[i]);
                    out.writeFloat(this.health[i]);
                    out.writeFloat(this.maxHealth[i]);
                    out.writeFloat(this.absorption[i]);
                    out.writeByte(this.food[i]);
                    out.writeShort(this.air[i]);
                    out.writeInt(this.level[i]);
                    out.writeFloat(this.progress[i]);
                    out.writeByte(this.armor[i]);
                    out.writeByte(this.mode[i]);
                }

                for (int slot = 0; slot < SLOTS; slot++)
                {
                    List<Change> list = this.items[slot];

                    out.writeInt(list.size());

                    for (Change change : list)
                    {
                        out.writeInt(change.tick);
                        writeLong(out, change.stack.isEmpty() ? "" : change.stack.writeNbt(new NbtCompound()).toString());
                    }
                }

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

    public static RecapGear read(File file)
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

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 15), 1 << 15)))
            {
                int size = in.readInt();

                if (size < 0 || size > 20 * 60 * 60 * 6)
                {
                    return null;
                }

                RecapGear gear = new RecapGear();

                for (int i = 0; i < size; i++)
                {
                    gear.push(in.readByte(), in.readFloat(), in.readFloat(), in.readFloat(), in.readByte(), in.readShort(), in.readInt(), in.readFloat(), in.readByte(), in.readByte());
                }

                for (int slot = 0; slot < SLOTS; slot++)
                {
                    int count = in.readInt();

                    for (int c = 0; c < count; c++)
                    {
                        int tick = in.readInt();

                        gear.items[slot].add(new Change(tick, readStack(readLong(in))));
                    }
                }

                return gear;
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }

    private static ItemStack readStack(String text)
    {
        if (text.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        try
        {
            return ItemStack.fromNbt(StringNbtReader.parse(text));
        }
        catch (Exception e)
        {
            return ItemStack.EMPTY;
        }
    }

    /* Item data can be longer than a short string allows */

    static void writeLong(DataOutputStream out, String text) throws IOException
    {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static String readLong(DataInputStream in) throws IOException
    {
        int length = in.readInt();

        if (length < 0 || length > 1 << 24)
        {
            throw new IOException("Bad text");
        }

        byte[] bytes = new byte[length];

        in.readFully(bytes);

        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
