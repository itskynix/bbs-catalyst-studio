package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.utils.VersionCompat;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

import java.util.Optional;

public class ItemStackKeyframeFactory implements IKeyframeFactory<ItemStack>
{
    @Override
    public ItemStack fromData(BaseType data)
    {
        return VersionCompat.stackFromNbt(DataStorageUtils.toNbt(data));
    }

    @Override
    public BaseType toData(ItemStack value)
    {
        NbtElement result = VersionCompat.stackToNbt(value);

        return result == null ? new MapType() : DataStorageUtils.fromNbt(result);
    }

    @Override
    public ItemStack createEmpty()
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean compare(Object a, Object b)
    {
        if (a instanceof ItemStack itemA && b instanceof ItemStack itemB)
        {
            return ItemStack.areEqual(itemA, itemB);
        }

        return false;
    }

    @Override
    public boolean isStepped()
    {
        return true;
    }

    @Override
    public ItemStack copy(ItemStack value)
    {
        return value.copy();
    }

    @Override
    public ItemStack interpolate(ItemStack preA, ItemStack a, ItemStack b, ItemStack postB, IInterp interpolation, float x)
    {
        return a;
    }
}