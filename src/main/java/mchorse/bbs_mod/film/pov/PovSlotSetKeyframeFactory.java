package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyframes of a {@link PovSlotSet}: a group of slots holds until the next keyframe, as a slot's
 * item does (there is nothing in between two inventories). Each item is saved the way a single
 * item keyframe saves it.
 */
public class PovSlotSetKeyframeFactory implements IKeyframeFactory<PovSlotSet>
{
    @Override
    public PovSlotSet fromData(BaseType data)
    {
        if (data == null || !data.isList())
        {
            return PovSlotSet.EMPTY;
        }

        ListType list = data.asList();
        List<ItemStack> items = new ArrayList<>(list.size());

        for (BaseType element : list.elements)
        {
            ItemStack stack = KeyframeFactories.ITEM_STACK.fromData(element);

            items.add(stack == null ? ItemStack.EMPTY : stack);
        }

        return new PovSlotSet(items);
    }

    @Override
    public BaseType toData(PovSlotSet value)
    {
        ListType list = new ListType();

        for (ItemStack stack : value.getItems())
        {
            list.add(KeyframeFactories.ITEM_STACK.toData(stack));
        }

        return list;
    }

    @Override
    public PovSlotSet createEmpty()
    {
        return PovSlotSet.EMPTY;
    }

    @Override
    public boolean compare(Object a, Object b)
    {
        return a instanceof PovSlotSet setA && setA.equals(b);
    }

    @Override
    public PovSlotSet copy(PovSlotSet value)
    {
        /* Never changed in place, so a keyframe may share it */
        return value;
    }

    @Override
    public boolean isStepped()
    {
        return true;
    }

    @Override
    public PovSlotSet interpolate(PovSlotSet preA, PovSlotSet a, PovSlotSet b, PovSlotSet postB, IInterp interpolation, float x)
    {
        return a;
    }

    @Override
    public int contentHash(PovSlotSet value)
    {
        return value.hashCode();
    }
}
