package net.mcft.copy.betterstorage.inventory;

import java.util.Arrays;

import net.mcft.copy.betterstorage.api.crafting.BetterStorageCrafting;
import net.mcft.copy.betterstorage.api.crafting.CraftingSourceStation;
import net.mcft.copy.betterstorage.api.crafting.IRecipeInput;
import net.mcft.copy.betterstorage.api.crafting.IStationRecipe;
import net.mcft.copy.betterstorage.config.GlobalConfig;
import net.mcft.copy.betterstorage.item.recipe.VanillaStationRecipe;
import net.mcft.copy.betterstorage.tile.entity.TileEntityCraftingStation;
import net.mcft.copy.betterstorage.utils.StackUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;

public class InventoryCraftingStation extends InventoryBetterStorage {
	
	public TileEntityCraftingStation entity = null;
	
	public final ItemStack[] crafting;
	public final ItemStack[] output;
	public final ItemStack[] contents;
	
	public final ItemStack[] lastOutput;
	
	public IStationRecipe currentRecipe = null;
	public boolean outputIsReal = false;
	public int progress = 0;
	public int craftingTime = 0;
	public int experience = 0;
	
	private IRecipeInput[] requiredInput = new IRecipeInput[9];
	
	private boolean hasRequirements = false;
	private boolean checkHasRequirements = true;
	
	public InventoryCraftingStation(TileEntityCraftingStation entity) {
		this("", entity.crafting, entity.output, entity.contents);
		this.entity = entity;
	}
	public InventoryCraftingStation(String name) {
		this(name, new ItemStack[9], new ItemStack[9], new ItemStack[18]);
	}
	private InventoryCraftingStation(String name, ItemStack[] crafting, ItemStack[] output, ItemStack[] contents) {
		super(name);
		this.crafting = crafting;
		this.output = output;
		this.contents = contents;
		
		lastOutput = new ItemStack[output.length];
		updateLastOutput();
		
		onInventoryChanged();
	}
	
	public void update() {
		if (!outputIsReal && (currentRecipe != null) &&
		    ((progress < craftingTime ||
		     (progress < GlobalConfig.stationAutocraftDelaySetting.getValue())))) progress++; 
	}
	
	/** Checks if the recipe changed and updates everything accordingly. */
	public void checkRecipe() {
		IStationRecipe previous = currentRecipe;
		if ((currentRecipe == null) || !currentRecipe.matches(crafting)) {
			currentRecipe = BetterStorageCrafting.findMatchingRecipe(crafting);
			if (currentRecipe == null)
				currentRecipe = VanillaStationRecipe.findVanillaRecipe(this);
		}
		if ((previous != currentRecipe) || !recipeOutputMatches()) {
			progress = 0;
			craftingTime = ((currentRecipe != null) ? currentRecipe.getCraftingTime(crafting) : 0);
			experience = ((currentRecipe != null) ? currentRecipe.getExperienceDisplay(crafting) : 0);
			if (!outputIsReal)
				for (int i = 0; i < output.length; i++)
					output[i] = null;
		}
		Arrays.fill(requiredInput, null);
		if (currentRecipe != null)
			currentRecipe.getCraftRequirements(crafting, requiredInput);
		updateLastOutput();
	}
	private boolean recipeOutputMatches() {
		if (!outputIsReal || (currentRecipe == null)) return true;
		ItemStack[] recipeOutput = currentRecipe.getOutput(crafting);
		for (int i = 0; i < output.length; i++)
			if (!ItemStack.areItemStacksEqual(((i < recipeOutput.length) ? recipeOutput[i] : null), output[i]))
				return false;
		return true;
	}
	
	/** Called when an item is removed from the output
	 *  slot while it doesn't store any real items. */
	public void craft(EntityPlayer player) {
		currentRecipe.craft(crafting, new CraftingSourceStation(entity, player));
		for (int i = 0; i < crafting.length; i++) {
			ItemStack stack = crafting[i];
			if (stack == null) continue;
			if (stack.stackSize <= 0) {
				// Item stack is depleted.
			} else if (stack.getItem().isDamageable() && (stack.getItemDamage() > stack.getMaxDamage())) {
				// Item stack is destroyed.
				if (player != null)
					MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, stack));
			} else continue;
			crafting[i] = null;
		}
		pullRequired(requiredInput, true);
		outputIsReal = !outputEmpty();
		progress = 0;
		checkRecipe();
		checkHasRequirements = true;
	}
	
	/** Pull items required for the recipe from the internal inventory.
	 *  Returns if successful. If doPull is false, only checks but doesn't move items. */
	public boolean pullRequired(IRecipeInput[] requiredInput, boolean doPull) {
		ItemStack[] contents = (doPull ? this.contents : this.contents.clone());
		ItemStack[] crafting = (doPull ? this.crafting : this.crafting.clone());
		craftingLoop:
		for (int i = 0; i < crafting.length; i++) {
			ItemStack stack = crafting[i];
			IRecipeInput required = requiredInput[i];
			if (required != null) {
				if ((stack != null) && !required.matches(stack)) return false;
				int currentAmount = ((stack != null) ? stack.stackSize : 0);
				int requiredAmount = (required.getAmount() - currentAmount);
				if (!doPull) requiredAmount += required.getAmount();
				if (requiredAmount < 0) continue;
				for (int j = 0; j < contents.length; j++) {
					ItemStack contentsStack = contents[j];
					if (contentsStack == null) continue;
					if ((stack == null) ? required.matches(contentsStack)
					                    : StackUtils.matches(stack, contentsStack)) {
						int amount = Math.min(contentsStack.stackSize, requiredAmount);
						crafting[i] = stack = StackUtils.copyStack(contentsStack, (currentAmount += amount));
						contents[j] =         StackUtils.copyStack(contentsStack, contentsStack.stackSize - amount);
						if ((requiredAmount -= amount) <= 0)
							continue craftingLoop;
					}
				}
				return false;
			} else if (stack != null)
				return false;
		}
		return true;
	}
	
	/** Returns if items can be taken out of the output slots. */
	public boolean canTake(EntityPlayer player) {
		return (outputIsReal || ((currentRecipe != null) &&
		                         (currentRecipe.canCraft(crafting, new CraftingSourceStation(entity, player))) &&
		                         (progress >= craftingTime) &&
		                          ((player != null) ||
		                           ((progress >= GlobalConfig.stationAutocraftDelaySetting.getValue()) &&
		                            hasRequirements()))));
	}
	
	/** Returns if the crafting station has the items
	 *  required in its inventory to craft the recipe again. */
	private boolean hasRequirements() {
		if (checkHasRequirements) {
			hasRequirements = pullRequired(requiredInput, false);
			checkHasRequirements = false;
		}
		return hasRequirements;
	}
	
	// IInventory implementation
	
	@Override
	public int getSizeInventory() { return (crafting.length + output.length + contents.length); }
	
	@Override
	public ItemStack getStackInSlot(int slot) {
		if (slot < crafting.length) return crafting[slot];
		else if (slot < crafting.length + output.length)
			return output[slot - crafting.length];
		else return contents[slot - (crafting.length + output.length)];
	}
	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		if (slot < crafting.length) crafting[slot] = stack;
		else if (slot < crafting.length + output.length)
			output[slot - crafting.length] = stack;
		else contents[slot - (crafting.length + output.length)] = stack;
	}
	
	@Override
	public boolean isUseableByPlayer(EntityPlayer player) { return true; }
	
	@Override
	public void openChest() {  }
	@Override
	public void closeChest() {  }
	
	@Override
	public void onInventoryChanged() {
		
		boolean updateLastOutput = false;
		
		// See if items were taken out.
		if (outputChanged()) {
			if (!outputIsReal && (currentRecipe != null)) craft(null);
			updateLastOutput = true;
		}
		
		if (outputEmpty()) {
			// Otherwise set the output to not be real.
			outputIsReal = false;
			if (currentRecipe != null) {
				// Fill it with ghost output from the recipe.
				ItemStack[] output = currentRecipe.getOutput(crafting);
				for (int i = 0; i < output.length; i++)
					this.output[i] = ItemStack.copyItemStack(output[i]);
			}
			updateLastOutput = true;
		}
		
		checkHasRequirements = true;
		if (updateLastOutput)
			updateLastOutput();
		
	}
	
	// Utility functions

	private void updateLastOutput() {
		for (int i = 0; i < output.length; i++)
			lastOutput[i] = ItemStack.copyItemStack(output[i]);
	}
	private boolean outputChanged() {
		for (int i = 0; i < output.length; i++)
			if (!ItemStack.areItemStacksEqual(output[i], lastOutput[i]))
				return true;
		return false;
	}
	private boolean outputEmpty() {
		for (int i = 0; i < output.length; i++)
			if (output[i] != null) return false;
		return true;
	}
	
}
