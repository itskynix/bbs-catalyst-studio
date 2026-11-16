package mchorse.bbs_mod.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The physics studio's two tools as items a player can hold: the physics gun, which grabs, carries, turns and
 * freezes, and the gravity gun, which pulls and launches. What they act on is the film editor's simulation,
 * and in the world they hold entities, blocks and model blocks (see PhysicsToolServer).
 */
public class PhysicsToolItem extends Item
{
    public enum Kind
    {
        PHYSICS("physics_gun"),
        GRAVITY("gravity_gun");

        public final String id;

        Kind(String id)
        {
            this.id = id;
        }
    }

    public final Kind kind;

    public PhysicsToolItem(Kind kind, Settings settings)
    {
        super(settings);

        this.kind = kind;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand)
    {
        /* The clicks are the tool's own, taken before the game turns them into anything (see InWorldPhysicsTool) */
        return TypedActionResult.pass(user.getStackInHand(hand));
    }
}
