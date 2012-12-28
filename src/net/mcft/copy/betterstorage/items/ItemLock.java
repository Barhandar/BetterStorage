package net.mcft.copy.betterstorage.items;

import cpw.mods.fml.common.registry.LanguageRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class ItemLock extends ItemBetterStorage {
	
	public ItemLock(int id) {
		super(id);
		setIconCoord(1, 0);
		
		setItemName("lock");
		LanguageRegistry.addName(this, "Lock");
		
		setCreativeTab(CreativeTabs.tabMisc);
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
	                         int x, int y, int z, int side, float subX, float subY, float subZ) {
		if (world.isRemote) return false;
		TileEntity entity = world.getBlockTileEntity(x, y, z);
		if (entity != null && entity instanceof ILockable) {
			ILockable lockable = (ILockable)entity;
			if (!lockable.canLock(stack)) return false;
			lockable.setLock(stack);
			// Remove the lock from the player's inventory.
			player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
			return true;
		} else return false;
	}
	
	public static boolean isLock(ItemStack stack) {
		return (stack != null && stack.getItem() instanceof ItemLock);
	}
	public static boolean isLocked(ILockable lockable) {
		return (lockable.getLock() != null);
	}
	public static boolean canKeyOpenLock(ItemStack lock, ItemStack key) {
		if (!ItemKey.isKey(key)) return false;
		return (lock.getItemDamage() == key.getItemDamage());
	}
	
	/** Gets called when a player tries to open a locked container. <br>
	 *  Returns if the container can be opened. */
	public boolean tryOpen(ItemStack lock, EntityPlayer player, ItemStack key) {
		return canKeyOpenLock(lock, key);
	}
	/** Gets called when a player tries to unlock a locked container. <br>
	 *  Returns if the container can be unlocked. */
	public boolean tryUnlock(ItemStack lock, EntityPlayer player, ItemStack key) {
		return canKeyOpenLock(lock, key);
	}
	
	// Static helper methods which just call the above methods:
	
	/** Gets called when a player tries to open a locked container. <br>
	 *  Returns if the container can be opened. */
	public static boolean lockTryOpen(ItemStack lock, EntityPlayer player, ItemStack key) {
		if (!isLock(lock)) return false;
		return ((ItemLock)lock.getItem()).tryOpen(lock, player, key);
	}
	/** Gets called when a player tries to unlock a locked container. <br>
	 *  Returns if the container can be unlocked. */
	public static boolean lockTryUnlock(ItemStack lock, EntityPlayer player, ItemStack key) {
		if (!isLock(lock)) return false;
		return ((ItemLock)lock.getItem()).tryUnlock(lock, player, key);
	}
	
}