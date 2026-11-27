package mchorse.bbs_mod.recap;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * The recorded player's things lent to the live player for one draw, and given back right after:
 * the hotbar and the picked slot, the off hand and the armor, and, for the interface, the hearts,
 * absorption, food, air and experience. Only the client's own copy is touched, between two of its
 * ticks, so neither the server nor the game's logic ever sees it.
 */
public final class RecapLend
{
    private final int selected;
    private final ItemStack[] hotbar = new ItemStack[9];
    private final ItemStack offHand;
    private final ItemStack[] armor = new ItemStack[4];

    private final boolean vitals;
    private float health;
    private float absorption;
    private int food;
    private int air;
    private int level;
    private float progress;

    private RecapLend(ClientPlayerEntity player, boolean vitals)
    {
        PlayerInventory inventory = player.getInventory();

        this.selected = inventory.selectedSlot;

        for (int i = 0; i < 9; i++)
        {
            this.hotbar[i] = inventory.main.get(i);
        }

        this.offHand = inventory.offHand.get(0);

        for (int i = 0; i < 4; i++)
        {
            this.armor[i] = inventory.armor.get(i);
        }

        this.vitals = vitals;

        if (vitals)
        {
            this.health = player.getHealth();
            this.absorption = player.getAbsorptionAmount();
            this.food = player.getHungerManager().getFoodLevel();
            this.air = player.getAir();
            this.level = player.experienceLevel;
            this.progress = player.experienceProgress;
        }
    }

    /** Lends the recorded things; {@code vitals} also lends the numbers the interface shows. */
    public static RecapLend lend(ClientPlayerEntity player, RecapGear.Frame frame, boolean vitals)
    {
        RecapLend lent = new RecapLend(player, vitals);
        PlayerInventory inventory = player.getInventory();

        inventory.selectedSlot = Math.max(0, Math.min(8, frame.selected));

        for (int i = 0; i < 9; i++)
        {
            inventory.main.set(i, frame.item(i));
        }

        inventory.offHand.set(0, frame.item(RecapGear.OFF_HAND));

        for (int i = 0; i < 4; i++)
        {
            inventory.armor.set(i, frame.item(RecapGear.ARMOR + i));
        }

        if (vitals)
        {
            player.setHealth(frame.health);
            player.setAbsorptionAmount(frame.absorption);
            player.getHungerManager().setFoodLevel(frame.food);
            player.setAir(frame.air);
            player.experienceLevel = frame.level;
            player.experienceProgress = frame.progress;
        }

        return lent;
    }

    /** Gives everything lent back, as it was. */
    public void giveBack(ClientPlayerEntity player)
    {
        PlayerInventory inventory = player.getInventory();

        inventory.selectedSlot = this.selected;

        for (int i = 0; i < 9; i++)
        {
            inventory.main.set(i, this.hotbar[i]);
        }

        inventory.offHand.set(0, this.offHand);

        for (int i = 0; i < 4; i++)
        {
            inventory.armor.set(i, this.armor[i]);
        }

        if (this.vitals)
        {
            player.setHealth(this.health);
            player.setAbsorptionAmount(this.absorption);
            player.getHungerManager().setFoodLevel(this.food);
            player.setAir(this.air);
            player.experienceLevel = this.level;
            player.experienceProgress = this.progress;
        }
    }
}
