package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.TriggerBoxBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The trigger box: a block nobody walks into or sees, only a creative player can aim at, and whose
 * block entity holds the volume and the flow. Right click opens its editor on the client.
 */
public class TriggerBoxBlock extends Block implements BlockEntityProvider
{
    /** Set by the client: opens the editor of the box at the position. */
    public static Consumer<BlockPos> openEditor = (pos) -> {};

    public TriggerBoxBlock(Settings settings)
    {
        super(settings);
    }

    private static boolean canEdit(PlayerEntity player)
    {
        return player != null && (player.isCreative() || player.hasPermissionLevel(2));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        if (context instanceof EntityShapeContext entity && entity.getEntity() instanceof PlayerEntity player && player.isCreative())
        {
            return VoxelShapes.fullCube();
        }

        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos)
    {
        return true;
    }

    //? if >=1.21 {
    /*@Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit)
    {
        return this.use(world, pos, player);
    }
    *///?} else {
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        return hand == Hand.MAIN_HAND ? this.use(world, pos, player) : ActionResult.PASS;
    }
    //?}

    private ActionResult use(World world, BlockPos pos, PlayerEntity player)
    {
        if (!canEdit(player))
        {
            return ActionResult.PASS;
        }

        if (world.isClient())
        {
            openEditor.accept(pos);
        }

        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        if (world.isClient())
        {
            return null;
        }

        return ModelBlock.validateTicker(type, BBSMod.TRIGGER_BOX_ENTITY, TriggerBoxBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new TriggerBoxBlockEntity(pos, state);
    }
}
