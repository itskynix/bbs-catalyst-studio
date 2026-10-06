/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.DeathScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.SleepingChatScreen
 *  net.minecraft.client.gui.widget.ButtonWidget
 *  net.minecraft.client.gui.widget.GridWidget
 *  net.minecraft.client.gui.widget.GridWidget$Adder
 *  net.minecraft.client.gui.widget.SimplePositioningWidget
 *  net.minecraft.client.gui.widget.Widget
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.render.RenderLayer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Text
 *  net.minecraft.util.Formatting
 */
package mchorse.bbs_mod.camera.pov.actions.menu.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.MenuPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.menu.render.MenuSleepOverlay;
import mchorse.bbs_mod.camera.pov.actions.menu.schema.MenuTypeResolver;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.DeathScreenPovAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MenuActionRenderer {
    private static final int DEATH_TOP = 0x60500000;
    private static final int DEATH_BOTTOM = -1602211792;
    private static final int PAUSE_TOP = -1072689136;
    private static final int PAUSE_BOTTOM = -804253680;
    private static final MenuPovActionClip LIVE_CLIP = new MenuPovActionClip();
    private static final Transform LIVE_CURSOR = new Transform();
    private static GridWidget pauseGrid;
    private static int pauseWidth;
    private static int pauseHeight;

    private MenuActionRenderer() {
    }

    public static void render(MatrixStack matrices, Batcher2D batcher, RecordedPovActions actions, float tick, int screenWidth, int screenHeight) {
        if (actions == null) {
            return;
        }
        MenuPovActionClip clip = actions.getActiveMenu(tick);
        if (clip == null) {
            return;
        }
        MenuActionRenderer.renderClip(batcher, clip, clip.getLocalTick(tick), screenWidth, screenHeight);
    }

    public static boolean renderLive(Batcher2D batcher, int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        String type = MenuTypeResolver.resolveLive(client.currentScreen, client.player);
        if (type == null) {
            return false;
        }
        MenuActionRenderer.fillLiveClip(type, client.currentScreen, client.player, screenWidth, screenHeight);
        MenuActionRenderer.renderClip(batcher, LIVE_CLIP, 0.0f, screenWidth, screenHeight);
        return true;
    }

    public static void renderClip(Batcher2D batcher, MenuPovActionClip clip, float local, int screenWidth, int screenHeight) {
        boolean cursorVisible;
        String type;
        String string = type = clip.state.isEmpty() ? "game_menu" : (String)clip.state.interpolate(local, "game_menu");
        if (type == null || type.isBlank()) {
            type = "game_menu";
        }
        float mouseX = (float)screenWidth / 2.0f;
        float mouseY = (float)screenHeight / 2.0f;
        boolean bl = cursorVisible = clip.cursorVisible.isEmpty() || Boolean.TRUE.equals(clip.cursorVisible.interpolate(local, true));
        if (cursorVisible && !clip.cursorLayout.isEmpty()) {
            Transform cursor = (Transform)clip.cursorLayout.interpolate(local, new Transform());
            mouseX = (float)screenWidth / 2.0f + cursor.translate.x * 2.0f;
            mouseY = (float)screenHeight / 2.0f - cursor.translate.y * 2.0f;
        }
        DrawContext context = batcher.getContext();
        batcher.flush();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
        MenuActionRenderer.overlayState();
        switch (type) {
            case "death": {
                MenuActionRenderer.renderDeath(context, batcher, clip, local, screenWidth, screenHeight, mouseX, mouseY);
                break;
            }
            case "sleep": {
                MenuActionRenderer.renderSleep(context, batcher, clip, local, screenWidth, screenHeight, mouseX, mouseY);
                break;
            }
            default: {
                MenuActionRenderer.renderGameMenu(context, batcher, clip, local, screenWidth, screenHeight, mouseX, mouseY);
            }
        }
        MenuActionRenderer.overlayState();
        batcher.flush();
    }

    private static void fillLiveClip(String type, Screen screen, ClientPlayerEntity player, int width, int height) {
        MenuActionRenderer.LIVE_CLIP.tick.set(0);
        MenuActionRenderer.LIVE_CLIP.duration.set(100);
        MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.state, type);
        MinecraftClient client = MinecraftClient.getInstance();
        double mouseX = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
        MenuActionRenderer.LIVE_CURSOR.translate.set((float)((mouseX - (double)width / 2.0) / 2.0), (float)(((double)height / 2.0 - mouseY) / 2.0), 0.0f);
        MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.cursorLayout, LIVE_CURSOR);
        boolean leaveBed = screen instanceof SleepingChatScreen;
        MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.leaveBed, leaveBed);
        MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.cursorVisible, !"sleep".equals(type) || leaveBed);
        if ("death".equals(type) && screen instanceof DeathScreen) {
            String message = "";
            String score = "Score: 0";
            boolean active = false;
            if (screen instanceof DeathScreenPovAccess) {
                DeathScreenPovAccess deathAccess = (DeathScreenPovAccess)screen;
                Text deathMessage = deathAccess.bbsPov$getDeathMessage();
                Text scoreText = deathAccess.bbsPov$getScoreText();
                if (deathMessage != null) {
                    message = deathMessage.getString();
                }
                if (scoreText != null) {
                    score = scoreText.getString();
                }
                active = deathAccess.bbsPov$getTicksSinceDeath() >= 20;
            }
            MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.deathMessage, message);
            MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.score, score);
            MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.bgOpacity, Float.valueOf(1.0f));
            MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.buttonsActive, active);
        } else if ("sleep".equals(type)) {
            int timer = player == null ? 0 : player.getSleepTimer();
            MenuActionRenderer.setChannel(MenuActionRenderer.LIVE_CLIP.opacity, Float.valueOf(MenuSleepOverlay.progress(timer)));
        }
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

    private static void renderGameMenu(DrawContext context, Batcher2D batcher, MenuPovActionClip clip, float local, int width, int height, float mouseX, float mouseY) {
        float bg = clip != null && !clip.bgOpacity.isEmpty() ? ((Float)clip.bgOpacity.interpolate(local, Float.valueOf(1.0f))).floatValue() : 1.0f;
        bg = Math.max(0.0f, Math.min(1.0f, bg));
        context.fillGradient(0, 0, width, height, MenuActionRenderer.scaleAlpha(-1072689136, bg), MenuActionRenderer.scaleAlpha(-804253680, bg));
        MenuActionRenderer.overlayState();
        TextRenderer texts = MinecraftClient.getInstance().textRenderer;
        context.drawCenteredTextWithShadow(texts, (Text)Text.translatable((String)"menu.game"), width / 2, 40, 0xFFFFFF);
        GridWidget grid = MenuActionRenderer.pauseButtons(width, height);
        grid.forEachChild(child -> {
            MenuActionRenderer.overlayState();
            child.render(context, (int)mouseX, (int)mouseY, 0.0f);
        });
        MenuActionRenderer.overlayState();
        batcher.flush();
    }

    private static GridWidget pauseButtons(int width, int height) {
        if (pauseGrid != null && pauseWidth == width && pauseHeight == height) {
            return pauseGrid;
        }
        GridWidget grid = new GridWidget();
        grid.getMainPositioner().margin(4, 4, 4, 0);
        GridWidget.Adder adder = grid.createAdder(2);
        adder.add((Widget)ButtonWidget.builder((Text)Text.translatable((String)"menu.returnToGame"), b -> {}).width(204).build(), 2, grid.copyPositioner().marginTop(50));
        adder.add((Widget)MenuActionRenderer.narrowButton("gui.advancements"));
        adder.add((Widget)MenuActionRenderer.narrowButton("gui.stats"));
        adder.add((Widget)MenuActionRenderer.narrowButton("menu.sendFeedback"));
        adder.add((Widget)MenuActionRenderer.narrowButton("menu.reportBugs"));
        adder.add((Widget)MenuActionRenderer.narrowButton("menu.options"));
        adder.add((Widget)MenuActionRenderer.narrowButton("menu.shareToLan"));
        adder.add((Widget)ButtonWidget.builder((Text)Text.translatable((String)"menu.returnToMenu"), b -> {}).width(204).build(), 2);
        grid.refreshPositions();
        SimplePositioningWidget.setPos((Widget)grid, (int)0, (int)0, (int)width, (int)height, (float)0.5f, (float)0.25f);
        pauseGrid = grid;
        pauseWidth = width;
        pauseHeight = height;
        return grid;
    }

    private static ButtonWidget narrowButton(String key) {
        return ButtonWidget.builder((Text)Text.translatable((String)key), b -> {}).width(98).build();
    }

    private static void renderDeath(DrawContext context, Batcher2D batcher, MenuPovActionClip clip, float local, int width, int height, float mouseX, float mouseY) {
        String score;
        String message;
        float bg = clip.bgOpacity.isEmpty() ? 1.0f : ((Float)clip.bgOpacity.interpolate(local, Float.valueOf(1.0f))).floatValue();
        bg = Math.max(0.0f, Math.min(1.0f, bg));
        context.fillGradient(0, 0, width, height, MenuActionRenderer.scaleAlpha(0x60500000, bg), MenuActionRenderer.scaleAlpha(-1602211792, bg));
        MenuActionRenderer.overlayState();
        boolean buttonsActive = clip.buttonsActive.isEmpty() ? local >= 20.0f : Boolean.TRUE.equals(clip.buttonsActive.interpolate(local, false));
        int buttonX = width / 2 - 100;
        MenuActionRenderer.drawButton(context, mouseX, mouseY, buttonX, height / 4 + 72, 200, 20, (Text)Text.translatable((String)"deathScreen.respawn"), buttonsActive);
        MenuActionRenderer.drawButton(context, mouseX, mouseY, buttonX, height / 4 + 96, 200, 20, (Text)Text.translatable((String)"deathScreen.titleScreen"), buttonsActive);
        TextRenderer texts = MinecraftClient.getInstance().textRenderer;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.scale(2.0f, 2.0f, 2.0f);
        context.drawCenteredTextWithShadow(texts, (Text)Text.translatable((String)"deathScreen.title"), width / 2 / 2, 30, 0xFFFFFF);
        matrices.pop();
        String string = message = clip.deathMessage.isEmpty() ? "" : (String)clip.deathMessage.interpolate(local, "");
        if (message != null && !message.isBlank()) {
            context.drawCenteredTextWithShadow(texts, (Text)Text.literal((String)message), width / 2, 85, 0xFFFFFF);
        }
        String string2 = score = clip.score.isEmpty() ? "Score: 0" : (String)clip.score.interpolate(local, "Score: 0");
        if (score.regionMatches(true, 0, "Score:", 0, 6)) {
            String value = score.substring(6).trim();
            MutableText scoreLabel = Text.translatable((String)"deathScreen.score").append(": ");
            int labelWidth = texts.getWidth((StringVisitable)scoreLabel);
            int valueWidth = texts.getWidth(value);
            int x = width / 2 - (labelWidth + valueWidth) / 2;
            context.drawTextWithShadow(texts, (Text)scoreLabel, x, 100, 0xFFFFFF);
            context.drawTextWithShadow(texts, (Text)Text.literal((String)value).formatted(Formatting.YELLOW), x + labelWidth, 100, 0xFFFF55);
        } else {
            context.drawCenteredTextWithShadow(texts, (Text)Text.literal((String)score), width / 2, 100, 0xFFFFFF);
        }
        MenuActionRenderer.overlayState();
        batcher.flush();
    }

    private static void renderSleep(DrawContext context, Batcher2D batcher, MenuPovActionClip clip, float local, int width, int height, float mouseX, float mouseY) {
        boolean showLeaveBed;
        float progress = clip.opacity.isEmpty() ? 1.0f : ((Float)clip.opacity.interpolate(local, Float.valueOf(1.0f))).floatValue();
        int color = MenuSleepOverlay.color(progress);
        if (color >>> 24 > 0) {
            context.fill(RenderLayer.getGuiOverlay(), 0, 0, width, height, color);
        }
        boolean bl = showLeaveBed = clip.leaveBed.isEmpty() || Boolean.TRUE.equals(clip.leaveBed.interpolate(local, true));
        if (showLeaveBed) {
            MenuActionRenderer.drawButton(context, mouseX, mouseY, width / 2 - 100, height - 40, 200, 20, (Text)Text.translatable((String)"multiplayer.stopSleeping"), true);
        }
        MenuActionRenderer.overlayState();
        batcher.flush();
    }

    private static void drawButton(DrawContext context, float mouseX, float mouseY, int x, int y, int w, int h, Text label, boolean active) {
        ButtonWidget button = ButtonWidget.builder((Text)label, b -> {}).dimensions(x, y, w, h).build();
        button.active = active;
        MenuActionRenderer.overlayState();
        button.render(context, (int)mouseX, (int)mouseY, 0.0f);
        MenuActionRenderer.overlayState();
    }

    private static int scaleAlpha(int argb, float factor) {
        int alpha = Math.round((float)(argb >>> 24 & 0xFF) * factor);
        return alpha << 24 | argb & 0xFFFFFF;
    }

    private static void overlayState() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static boolean hidesCursor(RecordedPovActions actions, float tick) {
        String type;
        if (actions == null) {
            return false;
        }
        MenuPovActionClip clip = actions.getActiveMenu(tick);
        if (clip == null) {
            return false;
        }
        float local = clip.getLocalTick(tick);
        String string = type = clip.state.isEmpty() ? "game_menu" : (String)clip.state.interpolate(local, "game_menu");
        if ("sleep".equals(type)) {
            boolean showLeaveBed;
            boolean bl = showLeaveBed = clip.leaveBed.isEmpty() || Boolean.TRUE.equals(clip.leaveBed.interpolate(local, true));
            if (!showLeaveBed) {
                return true;
            }
        }
        return !clip.cursorVisible.isEmpty() && !Boolean.TRUE.equals(clip.cursorVisible.interpolate(local, true));
    }

    public static boolean blocksCrosshair(RecordedPovActions actions, float tick) {
        String type;
        if (actions == null) {
            return false;
        }
        MenuPovActionClip clip = actions.getActiveMenu(tick);
        if (clip == null) {
            return false;
        }
        float local = clip.getLocalTick(tick);
        String string = type = clip.state.isEmpty() ? "game_menu" : (String)clip.state.interpolate(local, "game_menu");
        return !"sleep".equals(type) || !MenuActionRenderer.hidesCursor(actions, tick);
    }
}

