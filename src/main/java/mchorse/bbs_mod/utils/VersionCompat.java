package mchorse.bbs_mod.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Formatting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Optional;
//? if >=1.21 {
/*import net.minecraft.component.type.FoodComponent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
*///?} else {
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.FoodComponent;
//?}

/**
 * Differences of the vanilla API between the supported Minecraft versions (item NBT turned into
 * data components, block entities started to need a registry lookup, enchantments became data).
 */
public class VersionCompat
{
    //? if >=1.21 {
    /*private static RegistryWrapper.WrapperLookup builtin;

    public static RegistryWrapper.WrapperLookup lookup(World world)
    {
        if (world != null)
        {
            return world.getRegistryManager();
        }

        if (builtin == null)
        {
            builtin = BuiltinRegistries.createWrapperLookup();
        }

        return builtin;
    }
    *///?}

    /** The ops that read and write NBT; registry aware since item components can name data driven entries. */
    public static DynamicOps<NbtElement> nbtOps()
    {
        //? if >=1.21 {
        /*return RegistryOps.of(NbtOps.INSTANCE, lookup(null));
        *///?} else {
        return NbtOps.INSTANCE;
        //?}
    }

    public static NbtElement stackToNbt(ItemStack stack)
    {
        //? if >=1.21 {
        /*return ItemStack.OPTIONAL_CODEC.encodeStart(nbtOps(), stack).result().orElse(null);
        *///?} else {
        return ItemStack.CODEC.encodeStart(nbtOps(), stack).result().orElse(null);
        //?}
    }

    public static ItemStack stackFromNbt(NbtElement nbt)
    {
        //? if >=1.21 {
        /*/^ Stacks saved up to 1.20 have a Count byte and a tag. The item and the count carry over, the
         * tag stays as the stack's custom data; components it used to stand for are not rebuilt. ^/
        if (nbt instanceof NbtCompound legacy && legacy.contains("Count") && !legacy.contains("count"))
        {
            NbtCompound upgraded = new NbtCompound();

            upgraded.putString("id", legacy.getString("id"));
            upgraded.putInt("count", Math.max(1, legacy.getInt("Count")));

            if (legacy.contains("tag", 10))
            {
                NbtCompound components = new NbtCompound();

                components.put("minecraft:custom_data", legacy.getCompound("tag").copy());
                upgraded.put("components", components);
            }

            nbt = upgraded;
        }

        return ItemStack.OPTIONAL_CODEC.parse(nbtOps(), nbt).result().orElse(ItemStack.EMPTY);
        *///?} else {
        return ItemStack.CODEC.parse(nbtOps(), nbt).result().orElse(ItemStack.EMPTY);
        //?}
    }

    /* Block entities */

    public static NbtCompound createNbtWithId(BlockEntity entity)
    {
        //? if >=1.21 {
        /*return entity.createNbtWithId(lookup(entity.getWorld()));
        *///?} else {
        return entity.createNbtWithId();
        //?}
    }

    public static BlockEntity createBlockEntity(World world, BlockPos pos, BlockState state, NbtCompound nbt)
    {
        //? if >=1.21 {
        /*return BlockEntity.createFromNbt(pos, state, nbt, lookup(world));
        *///?} else {
        return BlockEntity.createFromNbt(pos, state, nbt);
        //?}
    }

    /* Item stack data */

    /** The mod's own data on the stack: NBT up to 1.20, the custom data component after. May be null. */
    public static NbtCompound getStackData(ItemStack stack)
    {
        //? if >=1.21 {
        /*NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);

        return component == null ? null : component.copyNbt();
        *///?} else {
        return stack.getNbt();
        //?}
    }

    /** Same as {@link #getStackData(ItemStack)}, but never null; write it back with {@link #setStackData(ItemStack, NbtCompound)}. */
    public static NbtCompound getOrCreateStackData(ItemStack stack)
    {
        NbtCompound nbt = getStackData(stack);

        return nbt == null ? new NbtCompound() : nbt;
    }

    public static void setStackData(ItemStack stack, NbtCompound nbt)
    {
        //? if >=1.21 {
        /*stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        *///?} else {
        stack.setNbt(nbt);
        //?}
    }

    /** The block entity data that a placed block item restores (BlockEntityTag before 1.20.5). May be null. */
    public static NbtCompound getBlockEntityData(ItemStack stack)
    {
        //? if >=1.21 {
        /*NbtComponent component = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);

        return component == null ? null : component.copyNbt();
        *///?} else {
        return stack.getSubNbt("BlockEntityTag");
        //?}
    }

    public static void setBlockEntityData(ItemStack stack, NbtCompound nbt)
    {
        //? if >=1.21 {
        /*stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(nbt));
        *///?} else {
        stack.setSubNbt("BlockEntityTag", nbt);
        //?}
    }

    /* Entities */

    public static void setStepHeight(LivingEntity entity, float stepHeight)
    {
        //? if >=1.21 {
        /*EntityAttributeInstance attribute = entity.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);

        if (attribute != null)
        {
            attribute.setBaseValue(stepHeight);
        }
        *///?} else {
        entity.setStepHeight(stepHeight);
        //?}
    }

    /** Ticks the entity has been gliding with an elytra. */
    public static int getGlideTicks(LivingEntity entity)
    {
        //? if >=1.21 {
        /*return entity.getFallFlyingTicks();
        *///?} else {
        return entity.getRoll();
        //?}
    }

    /* Names, glint and the give command */

    public static boolean hasCustomName(ItemStack stack)
    {
        //? if >=1.21 {
        /*return stack.contains(DataComponentTypes.CUSTOM_NAME);
        *///?} else {
        return stack.hasCustomName();
        //?}
    }

    public static void setCustomName(ItemStack stack, Text name)
    {
        //? if >=1.21 {
        /*stack.set(DataComponentTypes.CUSTOM_NAME, name);
        *///?} else {
        stack.setCustomName(name);
        //?}
    }

    /** Makes the stack shimmer like an enchanted one without enchanting it. */
    public static void setGlint(ItemStack stack)
    {
        //? if >=1.21 {
        /*stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        *///?} else {
        NbtCompound nbt = stack.getOrCreateNbt();

        nbt.put("Enchantments", new NbtList());
        nbt.getList("Enchantments", 10).add(new NbtCompound());
        //?}
    }

    public static boolean hasStoredEnchantments(ItemStack stack)
    {
        //? if >=1.21 {
        /*ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);

        return stored != null && !stored.isEmpty();
        *///?} else {
        NbtCompound nbt = stack.getNbt();

        return nbt != null && nbt.contains("StoredEnchantments", 9) && !nbt.getList("StoredEnchantments", 10).isEmpty();
        //?}
    }

    /** What follows the item id in a give command: NBT up to 1.20.4, the component list after. May be empty. */
    public static String giveSuffix(ItemStack stack)
    {
        //? if >=1.21 {
        /*StringBuilder builder = new StringBuilder();

        for (Map.Entry<ComponentType<?>, Optional<?>> entry : stack.getComponentChanges().entrySet())
        {
            if (builder.length() > 0)
            {
                builder.append(',');
            }

            if (entry.getValue().isEmpty())
            {
                builder.append('!').append(entry.getKey());

                continue;
            }

            DataResult<NbtElement> result = ((Codec<Object>) entry.getKey().getCodec()).encodeStart(nbtOps(), entry.getValue().get());

            if (result.result().isPresent())
            {
                builder.append(entry.getKey()).append('=').append(result.result().get());
            }
        }

        return builder.length() == 0 ? "" : "[" + builder + "]";
        *///?} else {
        NbtCompound tag = stack.getNbt();

        return tag != null && !tag.isEmpty() ? tag.toString() : "";
        //?}
    }

    /* Attributes */

    public interface ModifierVisitor
    {
        /** The operation is 0 for addition, 1 for multiplying the base, 2 for multiplying the total. */
        void accept(int operation, double value);
    }

    /** Visits the attack speed modifiers a stack gives to the main hand. */
    public static void forEachAttackSpeedModifier(ItemStack stack, ModifierVisitor visitor)
    {
        //? if >=1.21 {
        /*AttributeModifiersComponent component = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);

        component.applyModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) ->
        {
            if (attribute == EntityAttributes.GENERIC_ATTACK_SPEED)
            {
                visitor.accept(modifier.operation().ordinal(), modifier.value());
            }
        });
        *///?} else {
        for (EntityAttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_SPEED))
        {
            visitor.accept(modifier.getOperation().ordinal(), modifier.getValue());
        }
        //?}
    }

    public static Formatting rarityFormatting(ItemStack stack)
    {
        //? if >=1.21 {
        /*return stack.getRarity().getFormatting();
        *///?} else {
        return stack.getRarity().formatting;
        //?}
    }

    /* Food */

    public static boolean isFood(ItemStack stack)
    {
        //? if >=1.21 {
        /*return stack.contains(DataComponentTypes.FOOD);
        *///?} else {
        return stack.getItem().getFoodComponent() != null;
        //?}
    }

    /** Food that is eaten in half the usual time. */
    public static boolean isSnack(ItemStack stack)
    {
        //? if >=1.21 {
        /*FoodComponent food = stack.get(DataComponentTypes.FOOD);

        return food != null && food.getEatTicks() < 32;
        *///?} else {
        FoodComponent food = stack.getItem().getFoodComponent();

        return food != null && food.isSnack();
        //?}
    }

    /* Items in use */

    public static int getMaxUseTime(ItemStack stack, LivingEntity user)
    {
        //? if >=1.21 {
        /*return stack.getMaxUseTime(user);
        *///?} else {
        return stack.getMaxUseTime();
        //?}
    }

    /** The user may be null when none is at hand; the enchantments that shorten the pull are ignored then. */
    public static int getPullTime(ItemStack stack, LivingEntity user)
    {
        //? if >=1.21 {
        /*return user == null ? 25 : CrossbowItem.getPullTime(stack, user);
        *///?} else {
        return CrossbowItem.getPullTime(stack);
        //?}
    }

    /** Riptide level of the stack, 0 when it has none. */
    public static int getRiptideLevel(ItemStack stack, LivingEntity user)
    {
        //? if >=1.21 {
        /*float strength = EnchantmentHelper.getTridentSpinAttackStrength(stack, user);

        return strength <= 0F ? 0 : (strength >= 3F ? 3 : (strength >= 2.25F ? 2 : 1));
        *///?} else {
        return EnchantmentHelper.getRiptide(stack);
        //?}
    }

    public static SoundEvent getRiptideSound(int level)
    {
        //? if >=1.21 {
        /*return (level >= 3 ? SoundEvents.ITEM_TRIDENT_RIPTIDE_3 : (level == 2 ? SoundEvents.ITEM_TRIDENT_RIPTIDE_2 : SoundEvents.ITEM_TRIDENT_RIPTIDE_1)).value();
        *///?} else {
        return level >= 3 ? SoundEvents.ITEM_TRIDENT_RIPTIDE_3 : (level == 2 ? SoundEvents.ITEM_TRIDENT_RIPTIDE_2 : SoundEvents.ITEM_TRIDENT_RIPTIDE_1);
        //?}
    }
}
