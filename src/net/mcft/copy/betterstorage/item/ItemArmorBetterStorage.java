package net.mcft.copy.betterstorage.item;

import net.mcft.copy.betterstorage.BetterStorage;
import net.mcft.copy.betterstorage.misc.Constants;
import net.mcft.copy.betterstorage.utils.MiscUtils;
import net.minecraft.item.EnumArmorMaterial;
import net.minecraft.item.ItemArmor;
import cpw.mods.fml.common.registry.GameRegistry;

public abstract class ItemArmorBetterStorage extends ItemArmor {
	
	private String name;
	
	public ItemArmorBetterStorage(int id, EnumArmorMaterial material, int renderSlot, int slot) {
		
		super(id, material, renderSlot, slot);
		
		setCreativeTab(BetterStorage.creativeTab);
		
		setUnlocalizedName(Constants.modId + "." + getItemName());
		if (!isItemBlock()) GameRegistry.registerItem(this, getItemName());
		
	}
	
	public boolean isItemBlock() { return false; }
	
	/** Returns the name of this item, for example "drinkingHelmet". */
	public String getItemName() {
		return ((name != null) ? name : (name = MiscUtils.getName(this)));
	}
	
}
