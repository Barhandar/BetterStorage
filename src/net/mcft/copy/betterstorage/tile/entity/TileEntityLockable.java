package net.mcft.copy.betterstorage.tile.entity;

import java.security.InvalidParameterException;

import net.mcft.copy.betterstorage.api.lock.EnumLockInteraction;
import net.mcft.copy.betterstorage.api.lock.ILock;
import net.mcft.copy.betterstorage.api.lock.ILockable;
import net.mcft.copy.betterstorage.attachment.Attachments;
import net.mcft.copy.betterstorage.attachment.IHasAttachments;
import net.mcft.copy.betterstorage.attachment.LockAttachment;
import net.mcft.copy.betterstorage.tile.ContainerMaterial;
import net.mcft.copy.betterstorage.utils.WorldUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class TileEntityLockable extends TileEntityConnectable
                                         implements ILockable, IHasAttachments {
	
	private boolean powered;
	
	public ContainerMaterial material;
	public LockAttachment lockAttachment;
	
	protected Attachments attachments = new Attachments(this);
	
	protected ItemStack getLockInternal() { return (canHaveLock() ? lockAttachment.getItem() : null); }
	protected void setLockInternal(ItemStack lock) { lockAttachment.setItem(lock); }
	
	public TileEntityLockable() {
		if (!canHaveLock()) return;
		lockAttachment = attachments.add(LockAttachment.class);
		lockAttachment.setScale(0.5F, 1.5F);
		setAttachmentPosition();
	}
	
	public ContainerMaterial getMaterial() {
		if (!canHaveMaterial()) return null;
		if (material != null) return material;
		return material = ((worldObj != null) ? ContainerMaterial.get(getBlockMetadata()) : ContainerMaterial.iron);
	}
	
	public boolean canHaveMaterial() { return true; }
	public boolean canHaveLock() { return true; }
	
	public abstract void setAttachmentPosition();
	
	// Attachment points
	
	@Override
	public Attachments getAttachments() {
		return ((TileEntityLockable)getMainTileEntity()).attachments;
	}
	
	@Override
	public void setOrientation(ForgeDirection orientation) {
		super.setOrientation(orientation);
		if (canHaveLock())
			lockAttachment.setDirection(orientation);
	}
	@Override
	public void setConnected(ForgeDirection connected) {
		super.setConnected(connected);
		if (canHaveLock())
			setAttachmentPosition();
	}
	
	@Override
	public void updateEntity() {
		super.updateEntity();
		attachments.update();
	}
	
	// TileEntityContainer stuff
	
	@Override
	public boolean onBlockActivated(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if (!worldObj.isRemote && canHaveLock() &&!canPlayerUseContainer(player))
			((ILock)getLock().getItem()).applyEffects(getLock(), this, player, EnumLockInteraction.OPEN);
		return super.onBlockActivated(player, side, hitX, hitY, hitZ);
	}
	
	@Override
	public boolean canPlayerUseContainer(EntityPlayer player) {
		return (super.canPlayerUseContainer(player) &&
		        ((getLock() == null) || canUse(player)));
	}
	
	@Override
	protected void onBlockPlacedBeforeCheckingConnections(EntityLivingBase player, ItemStack stack) {
		super.onBlockPlacedBeforeCheckingConnections(player, stack);
		if (canHaveMaterial())
			material = ContainerMaterial.getMaterial(stack, ContainerMaterial.iron);
	}
	
	@Override
	public ItemStack onPickBlock(ItemStack block, MovingObjectPosition target) {
		if (!canHaveMaterial()) return block;
		return getMaterial().setMaterial(block);
	}
	
	@Override
	public void dropContents() {
		super.dropContents();
		if (!canHaveLock()) return;
		WorldUtils.dropStackFromBlock(this, getLock());
		setLock(null);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void onBlockRenderAsItem(ItemStack stack) {
		super.onBlockRenderAsItem(stack);
		if (canHaveMaterial())
			material = ContainerMaterial.getMaterial(stack, ContainerMaterial.iron);
	}
	
	// TileEntityConnactable stuff
	
	@Override
	protected boolean isAccessible() { return (getLock() == null); }
	
	@Override
	public boolean canConnect(TileEntityConnectable connectable) {
		if (!(connectable instanceof TileEntityLockable)) return false;
		TileEntityLockable lockable = (TileEntityLockable)connectable;
		return (super.canConnect(connectable) && (material == lockable.material) &&
		        (getLock() == null) && (lockable.getLock() == null));
	}
	
	// ILockable implementation
	
	@Override
	public ItemStack getLock() {
		return ((TileEntityLockable)getMainTileEntity()).getLockInternal();
	}
	
	@Override
	public boolean isLockValid(ItemStack lock) {
		return ((lock == null) || ((lock.getItem() instanceof ILock) && canHaveLock()));
	}
	
	@Override
	public void setLock(ItemStack lock) {
		if (!isLockValid(lock))
			throw new InvalidParameterException("Can't set lock to " + lock + ".");
		
		TileEntityLockable main = (TileEntityLockable)getMainTileEntity();
		main.setLockInternal(lock);
		main.markForUpdate();
		onInventoryChanged();
	}
	
	@Override
	public boolean canUse(EntityPlayer player) {
		return (getMainTileEntity().getPlayersUsing() > 0);
	}
	
	@Override
	public void useUnlocked(EntityPlayer player) {
		openGui(player);
	}
	
	@Override
	public void applyTrigger() { setPowered(true); }
	
	// Trigger enchantment related
	
	/** Returns if the chest is emitting redstone. */
	public boolean isPowered() {
		return ((TileEntityLockable)getMainTileEntity()).powered;
	}
	
	/** Sets if the chest is emitting redstone.
	 *  Updates all nearby blocks to make sure they notice it. */
	public void setPowered(boolean powered) {
		
		TileEntityLockable chest = ((TileEntityLockable)getMainTileEntity());
		if (chest != this) { chest.setPowered(powered); return; }
		
		if (this.powered == powered) return;
		this.powered = powered;
		
		int id = getBlockType().blockID;
		// Schedule a block update to turn the redstone signal back off.
		if (powered) worldObj.scheduleBlockUpdate(xCoord, yCoord, zCoord, id, 10);
		
		// Notify nearby blocks
		worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, id);
		worldObj.notifyBlocksOfNeighborChange(xCoord + 1, yCoord, zCoord, id);
		worldObj.notifyBlocksOfNeighborChange(xCoord - 1, yCoord, zCoord, id);
		worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord + 1, zCoord, id);
		worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord - 1, zCoord, id);
		worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord + 1, id);
		worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord - 1, id);
		
		// Notify nearby blocks of adjacent chest
		if (isConnected() && (getConnected() == ForgeDirection.EAST)) {
			worldObj.notifyBlocksOfNeighborChange(xCoord + 2, yCoord, zCoord, id);
			worldObj.notifyBlocksOfNeighborChange(xCoord + 1, yCoord + 1, zCoord, id);
			worldObj.notifyBlocksOfNeighborChange(xCoord + 1, yCoord - 1, zCoord, id);
			worldObj.notifyBlocksOfNeighborChange(xCoord + 1, yCoord, zCoord + 1, id);
			worldObj.notifyBlocksOfNeighborChange(xCoord + 1, yCoord, zCoord - 1, id);
		}
		if (isConnected() && (getConnected() == ForgeDirection.SOUTH)) {
			worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord + 2, id);
			worldObj.notifyBlocksOfNeighborChange(xCoord + 1, yCoord, zCoord + 1, id);
			worldObj.notifyBlocksOfNeighborChange(xCoord - 1, yCoord, zCoord + 1, id);
			worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord + 1, zCoord + 1, id);
			worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord - 1, zCoord + 1, id);
		}
		
	}
	
	// TileEntity synchronization
	
	@Override
	public Packet getDescriptionPacket() {
		Packet132TileEntityData packet = (Packet132TileEntityData)super.getDescriptionPacket();
		if (canHaveMaterial())
			packet.data.setString(ContainerMaterial.TAG_NAME, getMaterial().name);
		if (canHaveLock()) {
			ItemStack lock = getLockInternal();
			if (lock != null) packet.data.setCompoundTag("lock", lock.writeToNBT(new NBTTagCompound()));
		}
        return packet;
	}
	@Override
	public void onDataPacket(INetworkManager net, Packet132TileEntityData packet) {
		super.onDataPacket(net, packet);
		if (canHaveMaterial())
			material = ContainerMaterial.get(packet.data.getString(ContainerMaterial.TAG_NAME));
		if (canHaveLock()) {
			if (!packet.data.hasKey("lock")) setLockInternal(null);
			else setLockInternal(ItemStack.loadItemStackFromNBT(packet.data.getCompoundTag("lock")));
		}
	}
	
	// Reading from / writing to NBT
	
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		if (canHaveMaterial())
			material = ContainerMaterial.get(compound.getString(ContainerMaterial.TAG_NAME));
		if (canHaveLock() && compound.hasKey("lock"))
			setLockInternal(ItemStack.loadItemStackFromNBT(compound.getCompoundTag("lock")));
	}
	@Override
	public void writeToNBT(NBTTagCompound compound) {
		super.writeToNBT(compound);
		if (canHaveMaterial())
			compound.setString(ContainerMaterial.TAG_NAME, getMaterial().name);
		if (canHaveLock()) {
			ItemStack lock = getLockInternal();
			if (lock != null)
				compound.setCompoundTag("lock", lock.writeToNBT(new NBTTagCompound("")));
		}
	}
	
}
