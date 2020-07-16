package gigaherz.packingtape.tape;

import gigaherz.packingtape.PackingTapeMod;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.registries.ObjectHolder;

import javax.annotation.Nullable;

public class PackagedBlockEntity extends TileEntity
{
    @ObjectHolder("packingtape:packaged_block")
    public static TileEntityType<PackagedBlockEntity> TYPE;

    private BlockState containedBlockState;
    private CompoundNBT containedTile;
    private Direction preferredDirection;

    public PackagedBlockEntity(TileEntityType<?> tileEntityTypeIn)
    {
        super(tileEntityTypeIn);
    }

    public PackagedBlockEntity()
    {
        super(TYPE);
    }

    @Override
    public CompoundNBT write(CompoundNBT compound)
    {
        compound = super.write(compound);

        if (containedBlockState != null)
        {
            CompoundNBT blockData = NBTUtil.writeBlockState(containedBlockState);
            compound.put("Block", blockData);
            compound.put("BlockEntity", containedTile.copy());
            if (preferredDirection != null)
            {
                compound.putInt("PreferredDirection", preferredDirection.ordinal());
            }
        }

        return compound;
    }

    @Override
    public void read(BlockState state, CompoundNBT compound)
    {
        super.read(state, compound);

        // Old way.
        if (compound.contains("containedBlock", Constants.NBT.TAG_STRING))
        {
            CompoundNBT tempTag = new CompoundNBT();
            tempTag.putString("Name", compound.getString("containedBlock"));
            tempTag.put("Properties", compound.get("containedBlockState"));
            containedBlockState = NBTUtil.readBlockState(tempTag);
            containedTile = compound.getCompound("containedTile").copy();
            if (compound.contains("preferredDirection"))
            {
                preferredDirection = Direction.values()[compound.getInt("preferredDirection")];
            }
        }
        else
        {
            CompoundNBT blockTag = compound.getCompound("Block");
            containedBlockState = NBTUtil.readBlockState(blockTag);
            containedTile = compound.getCompound("BlockEntity").copy();
            if (compound.contains("PreferredDirection"))
            {
                preferredDirection = Direction.byName(compound.getString("PreferredDirection"));
            }
        }
    }

    public BlockState getContainedBlockState()
    {
        return containedBlockState;
    }

    public CompoundNBT getContainedTile()
    {
        return containedTile;
    }

    public void setContents(BlockState state, CompoundNBT tag)
    {
        containedBlockState = state;
        containedTile = tag;
    }

    @Nullable
    public Direction getPreferredDirection()
    {
        return preferredDirection;
    }

    public void setPreferredDirection(Direction preferredDirection)
    {
        this.preferredDirection = preferredDirection;
    }

    public ItemStack getPackedStack()
    {
        ItemStack stack = new ItemStack(PackingTapeMod.PACKAGED_BLOCK.get());

        CompoundNBT tileEntityData = new CompoundNBT();
        write(tileEntityData);
        tileEntityData.remove("x");
        tileEntityData.remove("y");
        tileEntityData.remove("z");

        CompoundNBT stackTag = new CompoundNBT();
        stackTag.put("BlockEntityTag", tileEntityData);
        stack.setTag(stackTag);

        PackingTapeMod.logger.debug(String.format("Created Packed stack with %s", containedBlockState.toString()));

        return stack;
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return write(new CompoundNBT());
    }

    //@Nullable
    //@Override
    //public SPacketUpdateTileEntity getUpdatePacket()
    //{
    //    return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    //}

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag)
    {
        read(state, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt)
    {
        handleUpdateTag(getBlockState(), pkt.getNbtCompound());
    }

    public boolean isEmpty()
    {
        return containedBlockState == null || containedBlockState.isAir();
    }
}