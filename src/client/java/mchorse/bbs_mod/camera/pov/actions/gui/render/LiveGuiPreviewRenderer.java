/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.blaze3d.systems.VertexSorter
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.SleepingChatScreen
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.item.ItemStack
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render;

import mchorse.bbs_mod.camera.pov.actions.bossbar.render.BossBarActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.chat.render.ChatActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.chat.render.ChatHistoryRenderer;
import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiRecipeBook;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GuiCapture;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GuiSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.menu.render.MenuActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.menu.schema.MenuTypeResolver;
import mchorse.bbs_mod.camera.pov.hud.HudMount;
import mchorse.bbs_mod.camera.pov.hud.HudState;
import mchorse.bbs_mod.camera.pov.hud.render.HeldItemTooltipRenderer;
import mchorse.bbs_mod.camera.pov.hud.render.HudRenderer;
import mchorse.bbs_mod.camera.pov.render.PovCrosshairRenderer;
import mchorse.bbs_mod.camera.pov.render.PovCursorRenderer;
import mchorse.bbs_mod.camera.pov.render.PovViewportMetrics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import java.util.Map;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class LiveGuiPreviewRenderer {
    private static final GuiPovActionClip LIVE_CLIP = new GuiPovActionClip();
    private static final Transform LIVE_CURSOR_TRANSFORM = new Transform();
    private static boolean isRendering = false;

    public static boolean isRenderingLive() {
        return isRendering;
    }

    private LiveGuiPreviewRenderer() {
    }

    private static <T> void setChannel(KeyframeChannel<T> channel, T value) {
        if (channel == null) {
            return;
        }
        if (channel.isEmpty()) {
            channel.insert(0.0f, value);
        } else {
            ((Keyframe)channel.getKeyframes().get(0)).setValue(value);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void render(Batcher2D batcher, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client.currentScreen;
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        String menuType = MenuTypeResolver.resolveLive(screen, player);
        boolean menuOpen = menuType != null;
        boolean sleepFade = "sleep".equals(menuType) && !(screen instanceof SleepingChatScreen);
        boolean showMenuUi = menuOpen && !sleepFade;
        int screenWidth = PovViewportMetrics.getMinecraftScaledWidth();
        int screenHeight = PovViewportMetrics.getMinecraftScaledHeight();
        GuiCapture captured = !menuOpen && screen != null ? GuiSnapshotCapture.capture(screen, screenWidth, screenHeight) : null;
        GuiSnapshot snapshot = captured == null ? null : captured.snapshot;
        String guiType = snapshot == null ? "" : snapshot.guiType;
        boolean isChatScreen = screen instanceof ChatScreen;
        if (captured != null) {
            LiveGuiPreviewRenderer.LIVE_CLIP.tick.set(0);
            LiveGuiPreviewRenderer.LIVE_CLIP.duration.set(100);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.state, guiType);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getCursorVisible(guiType), snapshot.cursorVisible);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getOpacity(guiType), Float.valueOf(1.0f));
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getDarknessOpacity(guiType), Float.valueOf("gamemode_switcher".equals(guiType) ? 0.0f : 1.0f));
            LiveGuiPreviewRenderer.LIVE_CURSOR_TRANSFORM.translate.set(snapshot.cursorTx, snapshot.cursorTy, 0.0f);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getCursorLayout(guiType), LIVE_CURSOR_TRANSFORM);
            LiveGuiPreviewRenderer.applyCapturedExtras(captured, guiType);
        } else if (showMenuUi || isChatScreen) {
            LiveGuiPreviewRenderer.updateLiveCursorFromMouse(screenWidth, screenHeight);
        }
        Matrix4f previousProjection = new Matrix4f((Matrix4fc)RenderSystem.getProjectionMatrix());
        Matrix4f screenProjection = new Matrix4f().ortho(0.0f, (float)screenWidth, (float)screenHeight, 0.0f, -1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix((Matrix4f)screenProjection, (VertexSorter)VertexSorter.BY_Z);
        isRendering = true;
        try {
            boolean hideHud;
            HudState liveHotbar = LiveGuiPreviewRenderer.createLiveHudState(player, snapshot);
            if (sleepFade && liveHotbar != null) {
                liveHotbar.cursorVisible = false;
            }
            boolean bl = hideHud = "sleep".equals(menuType) && !sleepFade;
            if (liveHotbar != null && !hideHud) {
                HudRenderer.renderHotbar(batcher.getContext().getMatrices(), batcher, liveHotbar, 0, 0, screenWidth, screenHeight);
                batcher.flush();
                if (captured == null && !isChatScreen) {
                    HeldItemTooltipRenderer.renderLive(batcher, liveHotbar, screenWidth, screenHeight);
                }
            }
            if (!isChatScreen && !hideHud) {
                ChatHistoryRenderer.renderLiveHistory(batcher.getContext(), screenWidth, screenHeight);
            }
            if (captured != null) {
                GuiActionRenderer.renderGuiClip(batcher.getContext().getMatrices(), batcher, null, LIVE_CLIP, null, 0.0f, screenWidth, screenHeight);
            }
            BossBarActionRenderer.renderLive(batcher, screenWidth, screenHeight);
            if (menuOpen) {
                MenuActionRenderer.renderLive(batcher, screenWidth, screenHeight);
            }
            if (isChatScreen) {
                ChatActionRenderer.renderLiveChat(batcher, (ChatScreen)screen, screenWidth, screenHeight);
            }
            if (liveHotbar != null && (captured != null || showMenuUi)) {
                PovCursorRenderer.render(batcher, liveHotbar, screenWidth, screenHeight);
            } else if (sleepFade) {
                PovCrosshairRenderer.render(batcher, screenWidth, screenHeight);
            }
        }
        finally {
            isRendering = false;
            RenderSystem.setProjectionMatrix((Matrix4f)previousProjection, (VertexSorter)VertexSorter.BY_DISTANCE);
        }
    }

    private static HudState createLiveHudState(ClientPlayerEntity player, GuiSnapshot snapshot) {
        boolean survivalLike;
        if (player == null) {
            return null;
        }
        HudState state = new HudState();
        state.visible = true;
        state.alpha = 1.0f;
        state.selectedSlot = player.getInventory().selectedSlot;
        state.offhandItem = player.getOffHandStack();
        for (int i = 0; i < 9; ++i) {
            state.items[i] = player.getInventory().getStack(i);
        }
        state.statusBarsVisible = survivalLike = !player.isCreative() && !player.isSpectator();
        if (survivalLike) {
            state.health = player.getHealth();
            state.healthContainer = player.getMaxHealth();
            state.previousHealth = state.health;
            state.lastHealth = state.health;
            state.recentHealthLow = state.health;
            state.recentHealthHigh = state.health;
            state.absorptionContainer = state.absorption = player.getAbsorptionAmount();
            state.armor = player.getArmor();
            state.hunger = player.getHungerManager().getFoodLevel();
            LivingEntity mount = HudMount.jumpingMount((PlayerEntity)player);
            int mountSlots = HudMount.heartSlots(mount);
            state.mountHealthContainer = mountSlots * 2;
            state.mountHealth = mount == null ? 0.0f : mount.getHealth();
            state.air = player.getAir();
            state.experience = player.experienceProgress;
            state.experienceLevel = player.experienceLevel;
            state.hardcore = player.getWorld().getLevelProperties().isHardcore();
        }
        state.cursorVisible = true;
        state.cursorLayout.copy(LIVE_CURSOR_TRANSFORM);
        state.cursorItem = snapshot == null ? ItemStack.EMPTY : snapshot.cursorItem;
        return state;
    }

    private static void updateLiveCursorFromMouse(int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        double mouseX = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
        LiveGuiPreviewRenderer.LIVE_CURSOR_TRANSFORM.translate.set((float)((mouseX - (double)screenWidth / 2.0) / 2.0), (float)(((double)screenHeight / 2.0 - mouseY) / 2.0), 0.0f);
    }

    private static void applyCapturedExtras(GuiCapture captured, String guiType) {
        GuiSnapshot snapshot = captured.snapshot;
        for (Map.Entry<String, ItemStack> slot : snapshot.slots.entrySet()) {
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getGuiSlot(guiType, slot.getKey()), slot.getValue());
        }
        LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getDragSlots(guiType), snapshot.dragging ? snapshot.dragEncoded : "");
        LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getCursorItem(guiType), snapshot.cursorItem);
        if (captured.creative != null) {
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.creativeTab, captured.creative.tab);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.creativePage, captured.creative.page);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.creativeRow, captured.creative.row);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.creativeScroll, Float.valueOf(captured.creative.scroll));
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.creativeSearch, captured.creative.search);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.creativeSearchFocus, captured.creative.searchFocused);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.creativeSearchSelStart, captured.creative.searchSelStart);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.creativeSearchSelEnd, captured.creative.searchSelEnd);
        }
        if (captured.loom != null) {
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.loomRow, captured.loom.row);
        }
        if (captured.stonecutter != null) {
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.stonecutterRow, captured.stonecutter.row);
        }
        if (captured.anvil != null) {
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.anvilName, captured.anvil.name);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.anvilNameFocus, captured.anvil.focused);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.anvilNameSelStart, captured.anvil.selStart);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.anvilNameSelEnd, captured.anvil.selEnd);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.anvilError, captured.anvil.error);
        }
        if (captured.enchantment != null) {
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.enchantOffers, captured.enchantment.offers);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.enchantSeed, captured.enchantment.seed);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.enchantPlayerLevel, captured.enchantment.playerLevel);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.enchantCreative, captured.enchantment.creative);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.enchantBookOpen, Float.valueOf(captured.enchantment.bookOpen));
        }
        if (captured.beacon != null) {
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.beaconLevel, captured.beacon.level);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.beaconPrimary, captured.beacon.primary);
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.beaconSecondary, captured.beacon.secondary);
        }
        if (captured.furnace != null) {
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getFurnaceLit(guiType), Float.valueOf(captured.furnace.lit));
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getFurnaceCook(guiType), Float.valueOf(captured.furnace.cook));
        }
        if (captured.mount != null) {
            if ("horse".equals(guiType)) {
                LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getHorseVariant(guiType), captured.mount.horseVariant);
            }
            if ("donkey".equals(guiType)) {
                LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMountChest(guiType), captured.mount.chestOpen);
            }
        }
        if (captured.brewing != null) {
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBrewProgress(guiType), Float.valueOf(captured.brewing.progress));
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBrewFuel(guiType), Float.valueOf(captured.brewing.fuel));
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBrewBubbles(guiType), captured.brewing.bubbles);
        }
        if (captured.merchant != null) {
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMerchantOffers(guiType), captured.merchant.offers);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMerchantProfession(guiType), captured.merchant.profession);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMerchantLevel(guiType), captured.merchant.level);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMerchantExperience(guiType), captured.merchant.experience);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMerchantSelectedOffer(guiType), captured.merchant.selectedOffer);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMerchantScrollOffset(guiType), captured.merchant.scrollOffset);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMerchantTitle(guiType), captured.merchant.title);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getMerchantCanLevel(guiType), captured.merchant.canLevel);
        }
        if (captured.recipeBook != null) {
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeOpen(guiType), captured.recipeBook.open);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeSearch(guiType), captured.recipeBook.search);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeSearchFocus(guiType), captured.recipeBook.searchFocused);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeSearchSelStart(guiType), captured.recipeBook.searchSelStart);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeSearchSelEnd(guiType), captured.recipeBook.searchSelEnd);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeShowing(guiType), captured.recipeBook.showing);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipePage(guiType), captured.recipeBook.page);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeButton(guiType), captured.recipeBook.buttonSelected);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeSelected(guiType), captured.recipeBook.selected);
            if (GuiRecipeBook.hasCategories(guiType)) {
                LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getRecipeCategory(guiType), captured.recipeBook.category);
            }
        }
        if (captured.book != null) {
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBookWritable(guiType), captured.book.writable);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBookSigning(guiType), captured.book.signing);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBookPage(guiType), captured.book.page);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBookPages(guiType), captured.book.pages);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBookTitle(guiType), captured.book.title);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBookAuthor(guiType), captured.book.author);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBookSelStart(guiType), captured.book.selStart);
            LiveGuiPreviewRenderer.setChannel(LIVE_CLIP.getBookSelEnd(guiType), captured.book.selEnd);
        }
        if (captured.gamemode != null) {
            LiveGuiPreviewRenderer.setChannel(LiveGuiPreviewRenderer.LIVE_CLIP.gamemodeSelection, captured.gamemode.selection);
        }
    }
}

