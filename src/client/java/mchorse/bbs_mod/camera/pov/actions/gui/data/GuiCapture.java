/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.actions.gui.data;

import mchorse.bbs_mod.camera.pov.actions.gui.data.AnvilSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.BeaconSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.BookSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.BrewingSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.CreativeSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.EnchantmentSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.FurnaceSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GamemodeSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GuiSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.LoomSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.MerchantSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.MountSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.RecipeBookSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.data.StonecutterSnapshot;

public final class GuiCapture {
    public final GuiSnapshot snapshot;
    public final AnvilSnapshot anvil;
    public final CreativeSnapshot creative;
    public final LoomSnapshot loom;
    public final StonecutterSnapshot stonecutter;
    public final EnchantmentSnapshot enchantment;
    public final BeaconSnapshot beacon;
    public final RecipeBookSnapshot recipeBook;
    public final MerchantSnapshot merchant;
    public final BookSnapshot book;
    public final MountSnapshot mount;
    public final GamemodeSnapshot gamemode;
    public final FurnaceSnapshot furnace;
    public final BrewingSnapshot brewing;

    public GuiCapture(GuiSnapshot snapshot, AnvilSnapshot anvil, CreativeSnapshot creative, LoomSnapshot loom, StonecutterSnapshot stonecutter, EnchantmentSnapshot enchantment, BeaconSnapshot beacon, RecipeBookSnapshot recipeBook, MerchantSnapshot merchant, BookSnapshot book, MountSnapshot mount, GamemodeSnapshot gamemode, FurnaceSnapshot furnace, BrewingSnapshot brewing) {
        this.snapshot = snapshot;
        this.anvil = anvil;
        this.creative = creative;
        this.loom = loom;
        this.stonecutter = stonecutter;
        this.enchantment = enchantment;
        this.beacon = beacon;
        this.recipeBook = recipeBook;
        this.merchant = merchant;
        this.book = book;
        this.mount = mount;
        this.gamemode = gamemode;
        this.furnace = furnace;
        this.brewing = brewing;
    }
}

