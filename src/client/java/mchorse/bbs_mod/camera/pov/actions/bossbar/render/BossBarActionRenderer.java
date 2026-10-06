/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.hud.BossBarHud
 *  net.minecraft.client.gui.hud.ClientBossBar
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.bossbar.render;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.bossbar.BossBarLooks;
import mchorse.bbs_mod.camera.pov.actions.clip.BossBarPovActionClip;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BossBarHudPovAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class BossBarActionRenderer {
    private static final int WIDTH = 182;
    private static final int HEIGHT = 5;
    private static final int BAR_Y = 12;

    private BossBarActionRenderer() {
    }

    public static void render(Batcher2D batcher, RecordedPovActions actions, float tick, int width, int height) {
        if (actions == null) {
            return;
        }
        List<BossBarPovActionClip> activeClips = actions.getActiveBossBars(tick);
        if (activeClips.isEmpty()) {
            return;
        }
        int baseLayer = PovActionType.BOSS_BARS.seedLayer();
        int maxSlots = 9;
        for (int i = 0; i < Math.min(maxSlots, activeClips.size()); ++i) {
            BossBarPovActionClip clip = activeClips.get(i);
            float local = clip.getLocalTick(tick);
            int slot = Math.max(0, Math.min(8, (Integer)clip.layer.get() - baseLayer));
            if (slot < 0 || slot >= maxSlots) {
                slot = i;
            }
            int y = 12 + slot * 19;
            BossBarActionRenderer.renderBar(batcher, clip.name.interpolate(local, "Ender Dragon"), clip.percent.interpolate(local, 1.0f), clip.color.interpolate(local, "pink"), clip.style.interpolate(local, "progress"), width, y);
        }
    }

    public static void renderLive(Batcher2D batcher, int width, int height) {
        BossBarHud hud;
        MinecraftClient client = MinecraftClient.getInstance();
        BossBarHud bossBarHud = hud = client.inGameHud == null ? null : client.inGameHud.getBossBarHud();
        if (!(hud instanceof BossBarHudPovAccess)) {
            return;
        }
        BossBarHudPovAccess access = (BossBarHudPovAccess)hud;
        Map<UUID, ClientBossBar> bars = access.bbsPov$getBossBars();
        if (bars == null || bars.isEmpty()) {
            return;
        }
        int slot = 0;
        for (ClientBossBar bar : bars.values()) {
            if (bar == null) continue;
            int y = 12 + slot * 19;
            BossBarActionRenderer.renderBar(batcher, bar.getName().getString(), bar.getPercent(), BossBarLooks.colorId(bar.getColor()), BossBarLooks.styleId(bar.getStyle()), width, y);
            if (++slot < 9) continue;
            break;
        }
    }

    private static void renderBar(Batcher2D batcher, String name, float percent, String color, String style, int screenWidth, int y) {
        int x = screenWidth / 2 - 91;
        int fill = (int)(MathHelper.clamp((float)percent, (float)0.0f, (float)1.0f) * 182.0f);
        DrawContext context = batcher.getContext();
        batcher.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        BossBarActionRenderer.drawSprite(context, BossBarLooks.background(color), x, y, 182);
        BossBarActionRenderer.drawNotched(context, BossBarLooks.notchedBackground(style), x, y, 182);
        if (fill > 0) {
            BossBarActionRenderer.drawSprite(context, BossBarLooks.progress(color), x, y, fill);
            BossBarActionRenderer.drawNotched(context, BossBarLooks.notchedProgress(style), x, y, fill);
        }
        TextRenderer texts = MinecraftClient.getInstance().textRenderer;
        MutableText title = name == null || name.isEmpty() ? Text.empty() : ("Ender Dragon".equalsIgnoreCase(name) ? Text.translatable((String)"entity.minecraft.ender_dragon") : ("Wither".equalsIgnoreCase(name) ? Text.translatable((String)"entity.minecraft.wither") : ("Raid".equalsIgnoreCase(name) ? Text.translatable((String)"event.minecraft.raid") : Text.literal((String)name))));
        int textX = screenWidth / 2 - texts.getWidth((StringVisitable)title) / 2;
        context.drawTextWithShadow(texts, (Text)title, textX, y - 9, 0xFFFFFF);
        context.draw();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        batcher.flush();
    }

    private static void drawNotched(DrawContext context, Identifier texture, int x, int y, int width) {
        if (texture == null) {
            return;
        }
        RenderSystem.enableBlend();
        BossBarActionRenderer.drawSprite(context, texture, x, y, width);
        RenderSystem.disableBlend();
    }

    private static void drawSprite(DrawContext context, Identifier texture, int x, int y, int width) {
        mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, texture, 182, 5, 0, 0, x, y, width, 5);
    }
}

