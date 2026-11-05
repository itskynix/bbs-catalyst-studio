package mchorse.bbs_mod.film.pov;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The items of a group of slots at once (a chest's, a crafting grid's, the player's inventory),
 * so the group keys as one row of the dope sheet instead of one row per slot. Never changed in
 * place: an edit makes a new set, which is what lets a keyframe hold one without copying it.
 */
public final class PovSlotSet
{
    public static final PovSlotSet EMPTY = new PovSlotSet(Collections.emptyList());

    private final List<ItemStack> items;

    public PovSlotSet(List<ItemStack> items)
    {
        List<ItemStack> copy = new ArrayList<>(items.size());

        for (ItemStack stack : items)
        {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }

        this.items = Collections.unmodifiableList(copy);
    }

    public static PovSlotSet ofSize(int size)
    {
        return new PovSlotSet(Collections.nCopies(Math.max(0, size), ItemStack.EMPTY));
    }

    public int size()
    {
        return this.items.size();
    }

    /** The slot's item; an empty one past the end, so a smaller set reads as a bigger one with nothing in the rest */
    public ItemStack get(int slot)
    {
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    public List<ItemStack> getItems()
    {
        return this.items;
    }

    /** The same set with one slot changed, grown to have that slot if it was smaller */
    public PovSlotSet with(int slot, ItemStack stack)
    {
        List<ItemStack> items = new ArrayList<>(this.items);

        while (items.size() <= slot)
        {
            items.add(ItemStack.EMPTY);
        }

        items.set(slot, stack == null ? ItemStack.EMPTY : stack);

        return new PovSlotSet(items);
    }

    /** The same set cut or padded to a size */
    public PovSlotSet resized(int size)
    {
        List<ItemStack> items = new ArrayList<>(size);

        for (int i = 0; i < size; i++)
        {
            items.add(this.get(i));
        }

        return new PovSlotSet(items);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof PovSlotSet set) || set.items.size() != this.items.size())
        {
            return false;
        }

        for (int i = 0; i < this.items.size(); i++)
        {
            if (!ItemStack.areEqual(this.items.get(i), set.items.get(i)))
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int hash = this.items.size();

        for (ItemStack stack : this.items)
        {
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 17 + stack.getCount());
        }

        return hash;
    }
}
