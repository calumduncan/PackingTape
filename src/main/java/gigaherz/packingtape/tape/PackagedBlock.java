package gigaherz.packingtape.tape;

import gigaherz.packingtape.Config;
import gigaherz.packingtape.PackingTapeMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.IProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.ChestType;
import net.minecraft.stats.Stats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.*;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

public class PackagedBlock extends Block
{
    public static final BooleanProperty UNPACKING = BooleanProperty.create("unpacking");

    public PackagedBlock(Properties properties)
    {
        super(properties);
        setDefaultState(this.getStateContainer().getBaseState().with(UNPACKING, false));
    }

    @Deprecated
    @Override
    public boolean isReplaceable(BlockState state, BlockItemUseContext useContext)
    {
        return state.get(UNPACKING);
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new PackagedBlockEntity();
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(UNPACKING);
    }

    @Override
    public ItemStack getPickBlock(BlockState state, RayTraceResult target, IBlockReader world, BlockPos pos, PlayerEntity player)
    {
        if (player.abilities.isCreativeMode && Screen.hasControlDown())
            return new ItemStack(asItem(), 1);
        else
            return new ItemStack(PackingTapeMod.Items.TAPE, 1);
    }

    //@Override
    public void getDrops(NonNullList<ItemStack> drops, @Nullable TileEntity teWorld)
    {
        if (teWorld instanceof PackagedBlockEntity)
        {
            // TE exists here thanks to the willHarvest above.
            PackagedBlockEntity packaged = (PackagedBlockEntity) teWorld;
            ItemStack stack = packaged.getPackedStack();

            drops.add(stack);
        }
    }

    @Override
    public void harvestBlock(World worldIn, PlayerEntity player, BlockPos pos, BlockState state, @Nullable TileEntity te, ItemStack stack)
    {
        player.addStat(Stats.BLOCK_MINED.get(this));
        player.addExhaustion(0.005F);

        NonNullList<ItemStack> items = NonNullList.create();
        getDrops(items, te);
        boolean isSilkTouch = EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, stack) > 0;
        net.minecraftforge.event.ForgeEventFactory.fireBlockHarvesting(items, worldIn, pos, state, 0, 1.0f, isSilkTouch, player);
        items.forEach(e -> spawnAsEntity(worldIn, pos, e));
    }



    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
        if (!placer.func_225608_bj_() && placer instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity) placer;
            PackagedBlockEntity te = (PackagedBlockEntity) worldIn.getTileEntity(pos);
            assert te != null;
            te.setPreferredDirection(Direction.fromAngle(player.getRotationYawHead()).getOpposite());
        }
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
    }

    @Deprecated
    @Override
    public ActionResultType func_225533_a_(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rayTraceResult)
    {
        if (worldIn.isRemote)
            return ActionResultType.SUCCESS;

        TileEntity te = worldIn.getTileEntity(pos);

        if (!(te instanceof PackagedBlockEntity))
            return ActionResultType.FAIL;

        PackagedBlockEntity packagedBlock = (PackagedBlockEntity)te;

        BlockState newState = packagedBlock.getContainedBlockState();
        CompoundNBT entityData = packagedBlock.getContainedTile();
        Direction preferred = packagedBlock.getPreferredDirection();


        EnumProperty<Direction> facing = null;
        for (IProperty<?> prop : newState.getProperties())
        {
            if (prop.getName().equalsIgnoreCase("facing") || prop.getName().equalsIgnoreCase("rotation"))
            {
                if (prop instanceof EnumProperty && prop.getValueClass() == Direction.class)
                {
                    //noinspection unchecked
                    facing = (EnumProperty<Direction>) prop;
                    break;
                }
            }
        }

        if (preferred != null && facing != null)
        {
            if (facing.getAllowedValues().contains(preferred))
            {
                newState = newState.with(facing, preferred);
            }
        }

        if (facing != null
                && !player.func_225608_bj_()
                && newState.getBlock() instanceof ChestBlock)
        {
            if (newState.getProperties().contains(ChestBlock.TYPE))
            {
                Direction chestFacing = newState.get(facing);

                Direction left = chestFacing.rotateY();
                Direction right = chestFacing.rotateYCCW();

                // test left side connection
                BlockState leftState = worldIn.getBlockState(pos.offset(left));
                if (leftState.getBlock() == newState.getBlock() && leftState.get(ChestBlock.TYPE) == ChestType.SINGLE)
                {
                    worldIn.setBlockState(pos.offset(left), leftState.with(ChestBlock.TYPE, ChestType.RIGHT));
                    newState = newState.with(ChestBlock.TYPE, ChestType.LEFT);
                }
                else
                {
                    // test right side connection
                    BlockState rightState = worldIn.getBlockState(pos.offset(right));
                    if (rightState.getBlock() == newState.getBlock() && rightState.get(ChestBlock.TYPE) == ChestType.SINGLE)
                    {
                        worldIn.setBlockState(pos.offset(left), rightState.with(ChestBlock.TYPE, ChestType.LEFT));
                        newState = newState.with(ChestBlock.TYPE, ChestType.RIGHT);
                    }
                }
            }
        }

        worldIn.removeTileEntity(pos);
        worldIn.setBlockState(pos, newState);

        setTileEntityNBT(worldIn, pos, entityData, player);

        return ActionResultType.SUCCESS;
    }

    public static void setTileEntityNBT(World worldIn, BlockPos pos,
                                           @Nullable CompoundNBT tag,
                                           @Nullable PlayerEntity playerIn)
    {
        MinecraftServer minecraftserver = worldIn.getServer();
        if (minecraftserver == null)
        {
            return;
        }

        if (tag != null)
        {
            TileEntity tileentity = worldIn.getTileEntity(pos);

            if (tileentity != null)
            {
                if (!Config.isTileEntityAllowed(tileentity) && (playerIn == null || !playerIn.canUseCommandBlock()))
                {
                    return;
                }

                CompoundNBT merged = new CompoundNBT();
                CompoundNBT empty = merged.copy();
                tileentity.write(merged);
                merged.merge(tag);
                merged.putInt("x", pos.getX());
                merged.putInt("y", pos.getY());
                merged.putInt("z", pos.getZ());

                if (!merged.equals(empty))
                {
                    tileentity.read(merged);
                    tileentity.markDirty();
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable IBlockReader worldIn, List<ITextComponent> tooltip, ITooltipFlag advanced)
    {
        super.addInformation(stack, worldIn, tooltip, advanced);

        CompoundNBT tag = stack.getTag();
        if (tag == null)
        {
            tooltip.add(new StringTextComponent("Missing data (no nbt)!"));
            return;
        }

        CompoundNBT info = (CompoundNBT) tag.get("BlockEntityTag");
        if (info == null)
        {
            tooltip.add(new StringTextComponent("Missing data (no tag)!"));
            return;
        }

        if (!info.contains("Block") || !info.contains("BlockEntity"))
        {
            tooltip.add(new StringTextComponent("Missing data (no block info)!"));
            return;
        }

        String blockName = info.getCompound("Block").getString("Name");

        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
        if (block == Blocks.AIR)
        {
            tooltip.add(new StringTextComponent("Unknown block:"));
            tooltip.add(new StringTextComponent("  " + blockName));
            return;
        }

        Item item = block.asItem();
        if (item == Items.AIR)
        {
            item = ForgeRegistries.ITEMS.getValue(block.getRegistryName());
            if (item == Items.AIR)
            {
                tooltip.add(new StringTextComponent("Can't find item for:"));
                tooltip.add(new StringTextComponent("  " + blockName));
                return;
            }
        }

        ItemStack stack1 = new ItemStack(item, 1);
        tooltip.add(new TranslationTextComponent("text.packingtape.packaged.contains", stack1.getDisplayName()));
    }
}
