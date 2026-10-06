/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.render.BufferBuilder
 *  net.minecraft.client.render.BufferBuilder$BuiltBuffer
 *  net.minecraft.client.render.BufferRenderer
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.render.GameRenderer
 *  net.minecraft.client.render.Tessellator
 *  net.minecraft.client.render.VertexFormat$DrawMode
 *  net.minecraft.client.render.VertexFormats
 *  net.minecraft.client.texture.Sprite
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.math.RotationAxis
 *  org.joml.Matrix4f
 *  org.joml.Vector3f
 */
package mchorse.bbs_mod.camera.pov.hud.render;

import mchorse.bbs_mod.camera.pov.hud.HudState;
import mchorse.bbs_mod.camera.pov.render.PovViewportMetrics;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Random;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class HudRenderer {
    private static final int HUD_GREEN = 8453920;
    private static final int BAR_ICON_Y = -17;
    private static final int EXPERIENCE_BAR_Y = -7;
    private static final int EXPERIENCE_TEXT_Y = -13;
    private static final float SCALE_PIVOT_X = 91.0f;
    private static final float SCALE_PIVOT_Y = 0.5f;
    private static final int MAX_HEALTH_ROWS = 60;
    private static final float MAX_HEALTH_CONTAINER = 1200.0f;
    private static final Identifier HOTBAR = Identifier.of((String)"minecraft", (String)"hud/hotbar");
    private static final Identifier HOTBAR_SELECTION = Identifier.of((String)"minecraft", (String)"hud/hotbar_selection");
    private static final Identifier HOTBAR_OFFHAND_LEFT = Identifier.of((String)"minecraft", (String)"hud/hotbar_offhand_left");
    private static final Identifier HEART_CONTAINER = Identifier.of((String)"minecraft", (String)"hud/heart/container");
    private static final Identifier HEART_HARDCORE_CONTAINER = Identifier.of((String)"minecraft", (String)"hud/heart/container_hardcore");
    private static final Identifier HEART_CONTAINER_BLINKING = Identifier.of((String)"minecraft", (String)"hud/heart/container_blinking");
    private static final Identifier HEART_HARDCORE_CONTAINER_BLINKING = Identifier.of((String)"minecraft", (String)"hud/heart/container_hardcore_blinking");
    private static final Identifier[][] HEART_HALVES = new Identifier[][]{{Identifier.of((String)"minecraft", (String)"hud/heart/half"), Identifier.of((String)"minecraft", (String)"hud/heart/hardcore_half")}, {Identifier.of((String)"minecraft", (String)"hud/heart/poisoned_half"), Identifier.of((String)"minecraft", (String)"hud/heart/poisoned_hardcore_half")}, {Identifier.of((String)"minecraft", (String)"hud/heart/withered_half"), Identifier.of((String)"minecraft", (String)"hud/heart/withered_hardcore_half")}, {Identifier.of((String)"minecraft", (String)"hud/heart/absorbing_half"), Identifier.of((String)"minecraft", (String)"hud/heart/absorbing_hardcore_half")}, {Identifier.of((String)"minecraft", (String)"hud/heart/frozen_half"), Identifier.of((String)"minecraft", (String)"hud/heart/frozen_hardcore_half")}};
    private static final Identifier[][] HEART_FULLS = new Identifier[][]{{Identifier.of((String)"minecraft", (String)"hud/heart/full"), Identifier.of((String)"minecraft", (String)"hud/heart/hardcore_full")}, {Identifier.of((String)"minecraft", (String)"hud/heart/poisoned_full"), Identifier.of((String)"minecraft", (String)"hud/heart/poisoned_hardcore_full")}, {Identifier.of((String)"minecraft", (String)"hud/heart/withered_full"), Identifier.of((String)"minecraft", (String)"hud/heart/withered_hardcore_full")}, {Identifier.of((String)"minecraft", (String)"hud/heart/absorbing_full"), Identifier.of((String)"minecraft", (String)"hud/heart/absorbing_hardcore_full")}, {Identifier.of((String)"minecraft", (String)"hud/heart/frozen_full"), Identifier.of((String)"minecraft", (String)"hud/heart/frozen_hardcore_full")}};
    private static final Identifier[][] HEART_HALVES_BLINKING = new Identifier[][]{{Identifier.of((String)"minecraft", (String)"hud/heart/half_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/hardcore_half_blinking")}, {Identifier.of((String)"minecraft", (String)"hud/heart/poisoned_half_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/poisoned_hardcore_half_blinking")}, {Identifier.of((String)"minecraft", (String)"hud/heart/withered_half_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/withered_hardcore_half_blinking")}, {Identifier.of((String)"minecraft", (String)"hud/heart/absorbing_half_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/absorbing_hardcore_half_blinking")}, {Identifier.of((String)"minecraft", (String)"hud/heart/frozen_half_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/frozen_hardcore_half_blinking")}};
    private static final Identifier[][] HEART_FULLS_BLINKING = new Identifier[][]{{Identifier.of((String)"minecraft", (String)"hud/heart/full_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/hardcore_full_blinking")}, {Identifier.of((String)"minecraft", (String)"hud/heart/poisoned_full_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/poisoned_hardcore_full_blinking")}, {Identifier.of((String)"minecraft", (String)"hud/heart/withered_full_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/withered_hardcore_full_blinking")}, {Identifier.of((String)"minecraft", (String)"hud/heart/absorbing_full_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/absorbing_hardcore_full_blinking")}, {Identifier.of((String)"minecraft", (String)"hud/heart/frozen_full_blinking"), Identifier.of((String)"minecraft", (String)"hud/heart/frozen_hardcore_full_blinking")}};
    private static final Identifier ARMOR_EMPTY = Identifier.of((String)"minecraft", (String)"hud/armor_empty");
    private static final Identifier ARMOR_FULL = Identifier.of((String)"minecraft", (String)"hud/armor_full");
    private static final Identifier ARMOR_HALF = Identifier.of((String)"minecraft", (String)"hud/armor_half");
    private static final Identifier FOOD_EMPTY = Identifier.of((String)"minecraft", (String)"hud/food_empty");
    private static final Identifier FOOD_FULL = Identifier.of((String)"minecraft", (String)"hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.of((String)"minecraft", (String)"hud/food_half");
    private static final Identifier FOOD_EMPTY_HUNGER = Identifier.of((String)"minecraft", (String)"hud/food_empty_hunger");
    private static final Identifier FOOD_FULL_HUNGER = Identifier.of((String)"minecraft", (String)"hud/food_full_hunger");
    private static final Identifier FOOD_HALF_HUNGER = Identifier.of((String)"minecraft", (String)"hud/food_half_hunger");
    private static final Identifier VEHICLE_CONTAINER = Identifier.of((String)"minecraft", (String)"hud/heart/vehicle_container");
    private static final Identifier VEHICLE_FULL = Identifier.of((String)"minecraft", (String)"hud/heart/vehicle_full");
    private static final Identifier VEHICLE_HALF = Identifier.of((String)"minecraft", (String)"hud/heart/vehicle_half");
    private static final Identifier AIR = Identifier.of((String)"minecraft", (String)"hud/air");
    private static final Identifier AIR_BURSTING = Identifier.of((String)"minecraft", (String)"hud/air_bursting");
    private static final Identifier EXPERIENCE_BAR_BACKGROUND_TEXTURE = Identifier.of((String)"minecraft", (String)"textures/gui/sprites/hud/experience_bar_background.png");
    private static final Identifier EXPERIENCE_BAR_PROGRESS_TEXTURE = Identifier.of((String)"minecraft", (String)"textures/gui/sprites/hud/experience_bar_progress.png");
    private static boolean wasHeartRegenerationEnabled;
    private static long heartRegenerationStartTick;

    public static void renderHotbars(MatrixStack stack, Batcher2D batcher, List<HudState> hotbars) {
        if (hotbars == null || hotbars.isEmpty()) {
            return;
        }
        int width = PovViewportMetrics.getFilmScaledWidth();
        int height = PovViewportMetrics.getFilmScaledHeight();
        HudRenderer.renderHotbars(stack, batcher, hotbars, 0, 0, width, height);
    }

    public static void renderHotbars(MatrixStack stack, Batcher2D batcher, List<HudState> hotbars, int originX, int originY, int width, int height) {
        if (hotbars == null || hotbars.isEmpty()) {
            return;
        }
        for (HudState hotbar : hotbars) {
            HudRenderer.renderHotbar(stack, batcher, hotbar, originX, originY, width, height);
        }
    }

    public static void renderHotbar(MatrixStack stack, Batcher2D batcher, HudState hotbar, int originX, int originY, int width, int height) {
        boolean hasOffhandItem;
        float alpha = MathHelper.clamp((float)hotbar.alpha, (float)0.0f, (float)1.0f);
        if (alpha <= 0.0f) {
            return;
        }
        Transform transform = hotbar.layout;
        float scaleX = HudRenderer.safeScale(transform.scale.x);
        float scaleY = HudRenderer.safeScale(transform.scale.y);
        int hotbarWidth = 182;
        float x = (float)originX + (float)width / 2.0f + transform.translate.x * 2.0f - (float)hotbarWidth / 2.0f;
        float bottom = (float)(originY + height) - transform.translate.y * 2.0f;
        float y = bottom - 0.5f - 21.5f * scaleY;
        batcher.flush();
        stack.push();
        stack.translate(x, y, 0.0f);
        stack.translate(91.0f, 0.5f, 0.0f);
        if (transform.rotate.z != 0.0f) {
            stack.multiply(RotationAxis.POSITIVE_Z.rotation(transform.rotate.z));
        }
        stack.scale(scaleX, scaleY, 1.0f);
        stack.translate(-91.0f, -0.5f, 0.0f);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        batcher.getContext().setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)alpha);
        stack.push();
        HudRenderer.applyLayoutTransform(stack, hotbar.slotsLayout, 91.0f, 11.0f);
        HudRenderer.drawGuiSprite(batcher.getContext(), HOTBAR, 0, 0, 182, 22);
        boolean bl = hasOffhandItem = hotbar.offhandItem != null && !hotbar.offhandItem.isEmpty();
        if (hasOffhandItem) {
            HudRenderer.drawGuiSprite(batcher.getContext(), HOTBAR_OFFHAND_LEFT, -29, -1, 29, 24);
        }
        int selectedSlot = MathHelper.clamp((int)hotbar.selectedSlot, (int)0, (int)8);
        HudRenderer.drawGuiSprite(batcher.getContext(), HOTBAR_SELECTION, selectedSlot * 20 - 1, -1, 24, 23);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        Vector3f light0 = new Vector3f(0.85f, 0.85f, -1.0f).normalize();
        Vector3f light1 = new Vector3f(-0.85f, 0.85f, 1.0f).normalize();
        RenderSystem.setupGui3DDiffuseLighting((Vector3f)light0, (Vector3f)light1);
        for (int i = 0; i < 9; ++i) {
            ItemStack stackItem = hotbar.items[i];
            if (stackItem == null || stackItem.isEmpty()) continue;
            int itemX = 3 + i * 20;
            int itemY = 3;
            batcher.getContext().drawItem(stackItem, itemX, itemY);
            batcher.getContext().drawItemInSlot(batcher.getFont().getRenderer(), stackItem, itemX, itemY);
        }
        if (hasOffhandItem) {
            int offhandX = -26;
            int offhandY = 3;
            batcher.getContext().drawItem(hotbar.offhandItem, offhandX, offhandY);
            batcher.getContext().drawItemInSlot(batcher.getFont().getRenderer(), hotbar.offhandItem, offhandX, offhandY);
        }
        batcher.getContext().draw();
        DiffuseLighting.disableGuiDepthLighting();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        batcher.getContext().setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)alpha);
        stack.pop();
        if (hotbar.statusBarsVisible) {
            boolean sharedOutlineBlinking;
            int barsY = -17;
            int heartType = MathHelper.clamp((int)hotbar.heartType, (int)0, (int)4);
            int hardcore = hotbar.hardcore ? 1 : 0;
            Identifier container = hotbar.hardcore ? HEART_HARDCORE_CONTAINER : HEART_CONTAINER;
            Identifier heartHalf = HEART_HALVES[heartType][hardcore];
            Identifier heartFull = HEART_FULLS[heartType][hardcore];
            Identifier containerBlinking = hotbar.hardcore ? HEART_HARDCORE_CONTAINER_BLINKING : HEART_CONTAINER_BLINKING;
            Identifier heartHalfBlinking = HEART_HALVES_BLINKING[heartType][hardcore];
            Identifier heartFullBlinking = HEART_FULLS_BLINKING[heartType][hardcore];
            int absorptionType = heartType == 2 ? 2 : 3;
            Identifier absorptionHalf = HEART_HALVES[absorptionType][hardcore];
            Identifier absorptionFull = HEART_FULLS[absorptionType][hardcore];
            Identifier absorptionHalfBlinking = HEART_HALVES_BLINKING[absorptionType][hardcore];
            Identifier absorptionFullBlinking = HEART_FULLS_BLINKING[absorptionType][hardcore];
            int healthSlots = MathHelper.ceil((float)(MathHelper.clamp((float)hotbar.healthContainer, (float)0.0f, (float)1200.0f) / 2.0f));
            healthSlots = MathHelper.clamp((int)healthSlots, (int)0, (int)600);
            int healthRows = Math.max(1, Math.min(60, (healthSlots + 9) / 10));
            int absorptionSlots = MathHelper.ceil((float)(MathHelper.clamp((float)hotbar.absorptionContainer, (float)0.0f, (float)1200.0f) / 2.0f));
            absorptionSlots = MathHelper.clamp((int)absorptionSlots, (int)0, (int)600);
            int absorptionRows = absorptionSlots <= 0 ? 0 : Math.max(1, Math.min(60, (absorptionSlots + 9) / 10));
            Random heartShakeRandom = hotbar.health <= 4.0f ? new Random(HudRenderer.thisTickSeed()) : null;
            Random hungerShakeRandom = hotbar.hunger <= 6.0f ? new Random(HudRenderer.thisTickSeed() + 17L) : null;
            int regenerationHeartIndex = -1;
            long hudTick = HudRenderer.currentHudTick();
            long healthFlashAge = (long)Math.floor(hotbar.healthFlashAge);
            boolean bl2 = sharedOutlineBlinking = hotbar.heartFlash && Math.floorMod(healthFlashAge / 3L, 2L) == 0L;
            if (hotbar.heartRegeneration && healthSlots > 0 && hotbar.health > 0.0f) {
                if (!wasHeartRegenerationEnabled) {
                    heartRegenerationStartTick = hudTick;
                }
                wasHeartRegenerationEnabled = true;
                int cycleLength = healthSlots + 5;
                int cycleIndex = cycleLength <= 0 ? 0 : Math.floorMod(hudTick - heartRegenerationStartTick, cycleLength);
                regenerationHeartIndex = cycleIndex < healthSlots ? cycleIndex : -1;
            } else if (wasHeartRegenerationEnabled) {
                wasHeartRegenerationEnabled = false;
            }
            stack.push();
            HudRenderer.applyLayoutTransform(stack, hotbar.heartsLayout, 40.5f, (float)barsY + 4.5f);
            HudRenderer.renderHealthBar(batcher, hotbar.health, hotbar.previousHealth, hotbar.heartFlash, container, containerBlinking, heartHalf, heartFull, heartHalfBlinking, heartFullBlinking, 0, barsY, healthSlots, heartShakeRandom, regenerationHeartIndex, healthFlashAge);
            if (absorptionSlots > 0) {
                HudRenderer.renderBar(batcher, hotbar.absorption, hotbar.recentAbsorptionLow, hotbar.recentAbsorptionHigh, hotbar.absorptionFlash, sharedOutlineBlinking, container, absorptionHalf, absorptionFull, containerBlinking, absorptionHalfBlinking, absorptionFullBlinking, 0, barsY - healthRows * 10, absorptionSlots, heartShakeRandom, -1, hudTick);
            }
            if (hotbar.armor > 0.0f) {
                HudRenderer.renderBar(batcher, hotbar.armor, ARMOR_EMPTY, ARMOR_HALF, ARMOR_FULL, 0, barsY - (healthRows + absorptionRows) * 10, 10, null, -1);
            }
            stack.pop();
            stack.push();
            HudRenderer.applyLayoutTransform(stack, hotbar.foodLayout, 141.5f, (float)barsY + 4.5f);
            Identifier foodEmpty = hotbar.hungerEffect ? FOOD_EMPTY_HUNGER : FOOD_EMPTY;
            Identifier foodHalf = hotbar.hungerEffect ? FOOD_HALF_HUNGER : FOOD_HALF;
            Identifier foodFull = hotbar.hungerEffect ? FOOD_FULL_HUNGER : FOOD_FULL;
            int mountSlots = MathHelper.ceil((float)(MathHelper.clamp((float)hotbar.mountHealthContainer, (float)0.0f, (float)60.0f) / 2.0f));
            if (mountSlots > 0) {
                HudRenderer.renderBarReverse(batcher, hotbar.mountHealth, VEHICLE_CONTAINER, VEHICLE_HALF, VEHICLE_FULL, 173, barsY, mountSlots, null);
            } else {
                HudRenderer.renderBarReverse(batcher, hotbar.hunger, foodEmpty, foodHalf, foodFull, 173, barsY, 10, hungerShakeRandom);
            }
            HudRenderer.renderAirBar(batcher, hotbar.air, 173, barsY - 10);
            stack.pop();
            stack.push();
            HudRenderer.applyLayoutTransform(stack, hotbar.expLayout, 91.0f, -4.5f);
            float experience = MathHelper.clamp((float)hotbar.experience, (float)0.0f, (float)1.0f);
            int xpPixels = MathHelper.ceil((float)(experience * 182.0f));
            batcher.getContext().drawTexture(EXPERIENCE_BAR_BACKGROUND_TEXTURE, 0, -7, 0.0f, 0.0f, 182, 5, 182, 5);
            if (xpPixels > 0) {
                batcher.getContext().drawTexture(EXPERIENCE_BAR_PROGRESS_TEXTURE, 0, -7, 0.0f, 0.0f, xpPixels, 5, 182, 5);
            }
            if (hotbar.experienceLevel > 0) {
                String level = Integer.toString(hotbar.experienceLevel);
                int levelX = (182 - batcher.getFont().getWidth(level)) / 2;
                int outlineColor = HudRenderer.applyAlpha(0, alpha);
                int levelColor = HudRenderer.applyAlpha(8453920, alpha);
                batcher.text(level, (float)(levelX - 1), -13.0f, outlineColor, false);
                batcher.text(level, (float)(levelX + 1), -13.0f, outlineColor, false);
                batcher.text(level, (float)levelX, -14.0f, outlineColor, false);
                batcher.text(level, (float)levelX, -12.0f, outlineColor, false);
                batcher.text(level, (float)levelX, -13.0f, levelColor, false);
            }
            stack.pop();
        }
        batcher.getContext().setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        stack.pop();
        batcher.flush();
    }

    private static void applyLayoutTransform(MatrixStack stack, Transform t, float pivotX, float pivotY) {
        if (t == null) {
            return;
        }
        float tx = t.translate.x * 2.0f;
        float ty = -t.translate.y * 2.0f;
        float sx = HudRenderer.safeScale(t.scale.x);
        float sy = HudRenderer.safeScale(t.scale.y);
        float rz = t.rotate.z;
        stack.translate(tx, ty, 0.0f);
        if (sx != 1.0f || sy != 1.0f || rz != 0.0f) {
            stack.translate(pivotX, pivotY, 0.0f);
            if (rz != 0.0f) {
                stack.multiply(RotationAxis.POSITIVE_Z.rotation(rz));
            }
            if (sx != 1.0f || sy != 1.0f) {
                stack.scale(sx, sy, 1.0f);
            }
            stack.translate(-pivotX, -pivotY, 0.0f);
        }
    }

    private static float safeScale(float value) {
        if (!Float.isFinite(value)) {
            return 1.0f;
        }
        if (Math.abs(value) < 0.05f) {
            return value < 0.0f ? -0.05f : 0.05f;
        }
        return value;
    }

    private static void renderHealthBar(Batcher2D batcher, float health, float previousHealth, boolean healthFlash, Identifier container, Identifier containerBlinking, Identifier half, Identifier full, Identifier halfBlinking, Identifier fullBlinking, int x, int y, int slots, Random lowHealthShakeRandom, int regenerationHeartIndex, long healthFlashAge) {
        if (slots <= 0) {
            return;
        }
        int flashTicksPerPhase = 3;
        float current = MathHelper.clamp((float)health, (float)0.0f, (float)((float)slots * 2.0f)) / 2.0f;
        float previous = MathHelper.clamp((float)previousHealth, (float)0.0f, (float)((float)slots * 2.0f)) / 2.0f;
        boolean showBlinkingPhase = healthFlash && Math.floorMod(healthFlashAge / 3L, 2L) == 0L;
        boolean showPaleLayer = showBlinkingPhase && current < previous;
        for (int i = 0; i < slots; ++i) {
            int row = i / 10;
            int col = i % 10;
            int iconX = x + col * 8;
            int iconY = y - row * 10;
            if (lowHealthShakeRandom != null) {
                iconY += lowHealthShakeRandom.nextInt(2);
            }
            if (i == regenerationHeartIndex) {
                iconY -= 2;
            }
            Identifier containerToDraw = showBlinkingPhase ? containerBlinking : container;
            HudRenderer.drawGuiSprite(batcher.getContext(), containerToDraw, iconX, iconY, 9, 9);
            if (showPaleLayer) {
                HudRenderer.drawHeartFill(batcher, previous - (float)i, halfBlinking, fullBlinking, iconX, iconY);
            }
            HudRenderer.drawHeartFill(batcher, current - (float)i, half, full, iconX, iconY);
        }
    }

    private static void drawHeartFill(Batcher2D batcher, float amount, Identifier half, Identifier full, int x, int y) {
        if (amount >= 1.0f) {
            HudRenderer.drawGuiSprite(batcher.getContext(), full, x, y, 9, 9);
        } else if (amount >= 0.5f) {
            HudRenderer.drawGuiSprite(batcher.getContext(), half, x, y, 9, 9);
        }
    }

    private static void renderBar(Batcher2D batcher, float value, Identifier empty, Identifier half, Identifier full, int x, int y, int slots, Random lowHealthShakeRandom, int regenerationHeartIndex) {
        HudRenderer.renderBar(batcher, value, value, value, false, false, empty, half, full, empty, half, full, x, y, slots, lowHealthShakeRandom, regenerationHeartIndex, 0L);
    }

    private static void renderBar(Batcher2D batcher, float value, float recentHealthLow, float recentHealthHigh, boolean heartFlash, boolean sharedOutlineBlinking, Identifier empty, Identifier half, Identifier full, Identifier emptyBlinking, Identifier halfBlinking, Identifier fullBlinking, int x, int y, int slots, Random lowHealthShakeRandom, int regenerationHeartIndex, long hudTick) {
        if (slots <= 0) {
            return;
        }
        int FLASH_TICKS_PER_PHASE = 3;
        float normalized = MathHelper.clamp((float)value, (float)0.0f, (float)((float)slots * 2.0f)) / 2.0f;
        boolean recentlyIncreased = recentHealthHigh - recentHealthLow > 0.05f && value >= recentHealthHigh - 0.05f;
        boolean heartAffected = heartFlash || recentlyIncreased;
        boolean flashPhaseOn = hudTick / 3L % 2L == 0L;
        boolean outlineBlinking = sharedOutlineBlinking || heartAffected && !flashPhaseOn;
        for (int i = 0; i < slots; ++i) {
            int row = i / 10;
            int col = i % 10;
            int iconX = x + col * 8;
            int iconY = y - row * 10;
            if (lowHealthShakeRandom != null) {
                iconY += lowHealthShakeRandom.nextInt(2);
            }
            if (i == regenerationHeartIndex) {
                iconY -= 2;
            }
            Identifier emptyToDraw = outlineBlinking ? emptyBlinking : empty;
            Identifier fullToDraw = full;
            Identifier halfToDraw = half;
            HudRenderer.drawGuiSprite(batcher.getContext(), emptyToDraw, iconX, iconY, 9, 9);
            float current = normalized - (float)i;
            if (current >= 1.0f) {
                HudRenderer.drawGuiSprite(batcher.getContext(), fullToDraw, iconX, iconY, 9, 9);
                continue;
            }
            if (!(current >= 0.5f)) continue;
            HudRenderer.drawGuiSprite(batcher.getContext(), halfToDraw, iconX, iconY, 9, 9);
        }
    }

    private static long thisTickSeed() {
        return HudRenderer.currentHudTick() * 312871L;
    }

    private static long currentHudTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null ? mc.world.getTime() : System.currentTimeMillis() / 50L;
    }

    private static void renderBarReverse(Batcher2D batcher, float value, Identifier empty, Identifier half, Identifier full, int x, int y, int slots, Random lowHungerShakeRandom) {
        if (slots <= 0) {
            return;
        }
        float normalized = MathHelper.clamp((float)value, (float)0.0f, (float)((float)slots * 2.0f)) / 2.0f;
        for (int i = 0; i < slots; ++i) {
            int row = i / 10;
            int col = i % 10;
            int iconX = x - col * 8;
            int iconY = y - row * 10;
            if (lowHungerShakeRandom != null) {
                iconY += lowHungerShakeRandom.nextInt(2);
            }
            HudRenderer.drawGuiSprite(batcher.getContext(), empty, iconX, iconY, 9, 9);
            float current = normalized - (float)i;
            if (current >= 1.0f) {
                HudRenderer.drawGuiSprite(batcher.getContext(), full, iconX, iconY, 9, 9);
                continue;
            }
            if (!(current >= 0.5f)) continue;
            HudRenderer.drawGuiSprite(batcher.getContext(), half, iconX, iconY, 9, 9);
        }
    }

    private static int applyAlpha(int color, float alpha) {
        int a = MathHelper.clamp((int)Math.round(MathHelper.clamp((float)alpha, (float)0.0f, (float)1.0f) * 255.0f), (int)0, (int)255);
        return a << 24 | color & 0xFFFFFF;
    }

    private static void renderAirBar(Batcher2D batcher, float air, int x, int y) {
        if (air >= 300.0f) {
            return;
        }
        int full = MathHelper.ceil((float)((air - 2.0f) * 10.0f / 300.0f));
        int popping = MathHelper.ceil((float)(air * 10.0f / 300.0f)) - full;
        full = MathHelper.clamp((int)full, (int)0, (int)10);
        popping = MathHelper.clamp((int)popping, (int)0, (int)(10 - full));
        for (int i = 0; i < full + popping; ++i) {
            int iconX = x - i * 8;
            Identifier icon = i < full ? AIR : AIR_BURSTING;
            HudRenderer.drawGuiSprite(batcher.getContext(), icon, iconX, y, 9, 9);
        }
    }

    public static void drawGuiSprite(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        Sprite sprite = MinecraftClient.getInstance().getGuiAtlasManager().getSprite(texture);
        if (sprite == null) {
            return;
        }
        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();
        float uSpan = maxU - minU;
        float vSpan = maxV - minV;
        int spriteW = sprite.getContents() != null ? sprite.getContents().getWidth() : 0;
        int spriteH = sprite.getContents() != null ? sprite.getContents().getHeight() : 0;
        float epsU = spriteW > 0 ? uSpan / (float)spriteW * 0.05f : 0.0f;
        float epsV = spriteH > 0 ? vSpan / (float)spriteH * 0.05f : 0.0f;
        float u1 = minU + epsU;
        float u2 = maxU - epsU;
        float v1 = minV + epsV;
        float v2 = maxV - epsV;
        RenderSystem.setShaderTexture((int)0, (Identifier)sprite.getAtlasId());
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bufferBuilder.vertex(matrix, (float)x, (float)y, 0.0f).texture(u1, v1).next();
        bufferBuilder.vertex(matrix, (float)x, (float)(y + height), 0.0f).texture(u1, v2).next();
        bufferBuilder.vertex(matrix, (float)(x + width), (float)(y + height), 0.0f).texture(u2, v2).next();
        bufferBuilder.vertex(matrix, (float)(x + width), (float)y, 0.0f).texture(u2, v1).next();
        BufferRenderer.drawWithGlobalProgram((BufferBuilder.BuiltBuffer)bufferBuilder.end());
    }
}

