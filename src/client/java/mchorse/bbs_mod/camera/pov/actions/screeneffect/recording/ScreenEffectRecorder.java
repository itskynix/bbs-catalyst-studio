/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.colors.Color
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.block.BlockRenderType
 *  net.minecraft.block.BlockState
 *  net.minecraft.block.Blocks
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.hud.InGameHud
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.EquipmentSlot
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.effect.StatusEffectInstance
 *  net.minecraft.entity.effect.StatusEffectInstance$FactorCalculationData
 *  net.minecraft.entity.effect.StatusEffects
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.registry.Registries
 *  net.minecraft.registry.tag.FluidTags
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.BlockPos$Mutable
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.world.BlockView
 *  net.minecraft.world.border.WorldBorderStage
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.ScreenEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.InGameHudVignettePovAccess;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import net.minecraft.world.border.WorldBorderStage;

public final class ScreenEffectRecorder {
    private ScreenEffectPovActionClip recordingClip;
    private final Set<String> currentlyActiveEffects = new HashSet<String>();
    private boolean wasScoping = false;
    private int spyglassReleaseTicksRemaining = 0;
    private float spyglassLastZoom = 1.0f;
    private static ItemStack pendingTotemItem = null;
    private static boolean pendingTotemFlipped = false;
    private static boolean totemTriggered = false;
    private final Map<String, Float> lastFloatValues = new HashMap<String, Float>();
    private final Map<String, Integer> lastRecordedTicks = new HashMap<String, Integer>();

    public static void onFloatingItem(ItemStack item, boolean flipped) {
        pendingTotemItem = item;
        pendingTotemFlipped = flipped;
        totemTriggered = true;
    }

    public void reset() {
        this.recordingClip = null;
        this.currentlyActiveEffects.clear();
        this.wasScoping = false;
        this.spyglassReleaseTicksRemaining = 0;
        this.spyglassLastZoom = 1.0f;
        this.lastFloatValues.clear();
        this.lastRecordedTicks.clear();
        pendingTotemItem = null;
        pendingTotemFlipped = false;
        totemTriggered = false;
    }

    private static BlockState getSuffocatingBlockState(ClientPlayerEntity player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int i = 0; i < 8; ++i) {
            double d = player.getX() + (double)(((float)((i >> 0) % 2) - 0.5f) * player.getWidth() * 0.8f);
            double e = player.getEyeY() + (double)(((float)((i >> 1) % 2) - 0.5f) * 0.1f);
            double f = player.getZ() + (double)(((float)((i >> 2) % 2) - 0.5f) * player.getWidth() * 0.8f);
            mutable.set(d, e, f);
            BlockState blockState = player.getWorld().getBlockState((BlockPos)mutable);
            if (blockState.getRenderType() == BlockRenderType.INVISIBLE || !blockState.shouldSuffocate((BlockView)player.getWorld(), (BlockPos)mutable)) continue;
            return blockState;
        }
        return null;
    }

    public void record(ReplayKeyframesPovAccess access, Recorder recorder) {
        boolean totemPopThisTick;
        boolean isWorldBorderWarning;
        boolean isScoping;
        boolean inPortal;
        if (access == null || recorder == null || !PovSettings.isBakeScreenEffects()) {
            return;
        }
        RecordedPovActions actions = access.bbsPov$getActions();
        if (actions == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        int tick = recorder.tick;
        float vignetteDarkness = 0.0f;
        InGameHud inGameHud = client.inGameHud;
        if (inGameHud instanceof InGameHudVignettePovAccess) {
            InGameHudVignettePovAccess accessHud = (InGameHudVignettePovAccess)inGameHud;
            vignetteDarkness = accessHud.bbsPov$getVignetteDarkness();
        }
        HashSet<String> activeNow = new HashSet<String>();
        BlockState suffocationState = ScreenEffectRecorder.getSuffocatingBlockState(player);
        if (player.isOnFire() || player.isInLava() || player.isSubmergedIn(FluidTags.LAVA)) {
            activeNow.add("fire");
        }
        if (player.getFreezingScale() > 0.05f) {
            activeNow.add("frost");
        }
        boolean inPortalBlock = player.getWorld() != null && player.getWorld().getBlockState(player.getBlockPos()).isOf(Blocks.NETHER_PORTAL);
        boolean bl = inPortal = inPortalBlock || player.nauseaIntensity > 0.001f && !player.hasStatusEffect(StatusEffects.NAUSEA);
        if (inPortal) {
            activeNow.add("portal");
        }
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN)) {
            activeNow.add("pumpkin");
        }
        boolean bl2 = isScoping = player.isUsingSpyglass() || player.isUsingItem() && player.getActiveItem().isOf(Items.SPYGLASS);
        if (isScoping) {
            activeNow.add("spyglass");
        }
        if (suffocationState != null || player.isInsideWall()) {
            activeNow.add("suffocation");
        }
        if (player.hasStatusEffect(StatusEffects.NAUSEA) || player.nauseaIntensity > 0.001f || inPortalBlock) {
            activeNow.add("nausea");
        }
        if (player.hasStatusEffect(StatusEffects.DARKNESS)) {
            activeNow.add("darkness");
        }
        if (player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            activeNow.add("blindness");
        }
        if (player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
            activeNow.add("night_vision");
        }
        if (player.isSubmergedIn(FluidTags.WATER) || player.isSubmergedInWater()) {
            activeNow.add("underwater");
        }
        boolean bl3 = isWorldBorderWarning = player.getWorld() != null && (player.getWorld().getWorldBorder().getDistanceInsideBorder((Entity)player) < 15.0 || player.getWorld().getWorldBorder().getStage() == WorldBorderStage.SHRINKING);
        if (isWorldBorderWarning || vignetteDarkness >= 0.95f) {
            activeNow.add("vignette");
        }
        boolean bl4 = totemPopThisTick = totemTriggered && pendingTotemItem != null;
        if (totemPopThisTick) {
            activeNow.add("totem");
        }
        if (this.recordingClip == null) {
            if (activeNow.isEmpty()) {
                return;
            }
            this.recordingClip = (ScreenEffectPovActionClip)actions.add(PovActionType.SCREEN_EFFECT, tick, 1);
            this.recordingClip.title.set("Screen Effects");
            this.currentlyActiveEffects.clear();
        }
        int localTick = tick - (Integer)this.recordingClip.tick.get();
        if (!activeNow.isEmpty()) {
            this.recordingClip.duration.set(Math.max((Integer)this.recordingClip.duration.get(), localTick + 1));
            for (String effectId : activeNow) {
                boolean isNewlyStarted;
                boolean bl5 = isNewlyStarted = !this.currentlyActiveEffects.contains(effectId);
                if (isNewlyStarted) {
                    this.recordingClip.addEffect(effectId, false);
                    if (effectId.equals("totem")) {
                        String itemId = Registries.ITEM.getId(pendingTotemItem.getItem()).toString();
                        this.recordingClip.totemItem.set(itemId);
                        if (localTick > 0) {
                            if (this.recordingClip.totemVisible.isEmpty()) {
                                this.recordingClip.totemVisible.insert(0.0f, false);
                                if (localTick > 1) {
                                    this.recordingClip.totemVisible.insert((float)(localTick - 1), false);
                                }
                            }
                            if (this.recordingClip.totemParticles.isEmpty()) {
                                this.recordingClip.totemParticles.insert(0.0f, false);
                                if (localTick > 1) {
                                    this.recordingClip.totemParticles.insert((float)(localTick - 1), false);
                                }
                            }
                            if (this.recordingClip.totemProgress.isEmpty()) {
                                this.recordingClip.totemProgress.insert(0.0f, Float.valueOf(0.0f));
                                if (localTick > 1) {
                                    this.recordingClip.totemProgress.insert((float)(localTick - 1), Float.valueOf(0.0f));
                                }
                            }
                            if (this.recordingClip.totemFlipped.isEmpty()) {
                                this.recordingClip.totemFlipped.insert(0.0f, false);
                                if (localTick > 1) {
                                    this.recordingClip.totemFlipped.insert((float)(localTick - 1), false);
                                }
                            }
                        }
                        this.recordingClip.totemVisible.insert((float)localTick, true);
                        this.recordingClip.totemProgress.insert((float)localTick, Float.valueOf(0.0f));
                        this.recordingClip.totemProgress.insert((float)(localTick + 40), Float.valueOf(1.0f));
                        this.recordingClip.totemFlipped.insert((float)localTick, pendingTotemFlipped);
                        this.recordingClip.totemFlipped.insert((float)(localTick + 40), false);
                        this.recordingClip.totemParticles.insert((float)localTick, true);
                        this.recordingClip.totemParticles.insert((float)(localTick + 30), false);
                        this.recordingClip.totemVisible.insert((float)(localTick + 40), false);
                        this.recordingClip.duration.set(Math.max((Integer)this.recordingClip.duration.get(), localTick + 40));
                        totemTriggered = false;
                        pendingTotemItem = null;
                        pendingTotemFlipped = false;
                    } else {
                        ScreenEffectRecorder.recordVisible(this.recordingClip, effectId, localTick, true);
                    }
                }
                if (effectId.equals("totem")) continue;
                this.recordEffectKeyframes(this.recordingClip, effectId, localTick, player, vignetteDarkness, suffocationState, inPortalBlock, isNewlyStarted);
            }
            for (String effectId : this.currentlyActiveEffects) {
                if (activeNow.contains(effectId) || effectId.equals("totem")) continue;
                ScreenEffectRecorder.recordVisible(this.recordingClip, effectId, localTick, false);
                this.recordEffectEnd(this.recordingClip, effectId, localTick);
            }
            this.currentlyActiveEffects.clear();
            this.currentlyActiveEffects.addAll(activeNow);
            this.currentlyActiveEffects.remove("totem");
        } else if (!this.currentlyActiveEffects.isEmpty()) {
            for (String effectId : this.currentlyActiveEffects) {
                ScreenEffectRecorder.recordVisible(this.recordingClip, effectId, localTick, false);
                this.recordEffectEnd(this.recordingClip, effectId, localTick);
            }
            this.currentlyActiveEffects.clear();
            this.recordingClip.duration.set(Math.max((Integer)this.recordingClip.duration.get(), localTick + 1));
        }
    }

    private static void recordVisible(ScreenEffectPovActionClip clip, String effectId, int localTick, boolean visible) {
        KeyframeChannel<Boolean> channel = ScreenEffectRecorder.getVisibleChannel(clip, effectId);
        if (channel != null) {
            if (visible && localTick > 0) {
                if (channel.isEmpty()) {
                    channel.insert(0.0f, false);
                }
                channel.insert((float)(localTick - 1), false);
            }
            channel.insert((float)localTick, visible);
        }
    }

    private static KeyframeChannel<Boolean> getVisibleChannel(ScreenEffectPovActionClip clip, String effectId) {
        return switch (effectId) {
            case "vignette" -> clip.vignetteVisible;
            case "fire" -> clip.fireVisible;
            case "frost" -> clip.frostVisible;
            case "portal" -> clip.portalVisible;
            case "pumpkin" -> clip.pumpkinVisible;
            case "spyglass" -> clip.spyglassVisible;
            case "suffocation" -> clip.suffocationVisible;
            case "night_vision" -> clip.nightVisionVisible;
            case "blindness" -> clip.blindnessVisible;
            case "totem" -> clip.totemVisible;
            case "nausea" -> clip.nauseaVisible;
            case "darkness" -> clip.darknessVisible;
            case "underwater" -> clip.underwaterVisible;
            default -> null;
        };
    }

    private void recordFloatChannel(KeyframeChannel<Float> channel, String channelKey, int localTick, float value, float defaultInactiveVal, float deltaThreshold, boolean isNewlyStarted) {
        if (isNewlyStarted || channel.isEmpty()) {
            if (localTick > 0) {
                if (channel.isEmpty()) {
                    channel.insert(0.0f, Float.valueOf(defaultInactiveVal));
                }
                channel.insert((float)(localTick - 1), Float.valueOf(defaultInactiveVal));
            }
            channel.insert((float)localTick, Float.valueOf(value));
            this.lastFloatValues.put(channelKey, Float.valueOf(value));
            this.lastRecordedTicks.put(channelKey, localTick);
        } else {
            Float lastVal = this.lastFloatValues.get(channelKey);
            Integer lastTick = this.lastRecordedTicks.get(channelKey);
            if (lastVal == null) {
                lastVal = (Float)channel.interpolate((float)localTick);
            }
            if (lastTick == null) {
                lastTick = localTick;
            }
            if (Math.abs(value - lastVal.floatValue()) >= deltaThreshold) {
                channel.insert((float)localTick, Float.valueOf(value));
                this.lastFloatValues.put(channelKey, Float.valueOf(value));
                this.lastRecordedTicks.put(channelKey, localTick);
            }
        }
    }

    private void recordFloatEnd(KeyframeChannel<Float> channel, String channelKey, int localTick, float defaultInactiveVal) {
        if (!channel.isEmpty()) {
            Float lastVal = this.lastFloatValues.get(channelKey);
            Integer lastTick = this.lastRecordedTicks.get(channelKey);
            if (lastVal != null && lastTick != null && localTick - lastTick > 1) {
                channel.insert((float)(localTick - 1), lastVal);
            }
            channel.insert((float)localTick, Float.valueOf(defaultInactiveVal));
            this.lastFloatValues.remove(channelKey);
            this.lastRecordedTicks.remove(channelKey);
        }
    }

    private void recordEffectEnd(ScreenEffectPovActionClip clip, String effectId, int localTick) {
        switch (effectId) {
            case "vignette": {
                this.recordFloatEnd(clip.vignetteOpacity, "vignetteOpacity", localTick, 0.0f);
                break;
            }
            case "nausea": {
                this.recordFloatEnd(clip.nauseaDistortion, "nauseaDistortion", localTick, 0.0f);
                this.recordFloatEnd(clip.nauseaOpacity, "nauseaOpacity", localTick, 0.0f);
                break;
            }
            case "frost": {
                this.recordFloatEnd(clip.frostProgress, "frostProgress", localTick, 0.0f);
                this.recordFloatEnd(clip.frostZoom, "frostZoom", localTick, 1.0f);
                break;
            }
            case "portal": {
                this.recordFloatEnd(clip.portalOpacity, "portalOpacity", localTick, 0.0f);
                break;
            }
            case "pumpkin": {
                this.recordFloatEnd(clip.pumpkinOpacity, "pumpkinOpacity", localTick, 0.0f);
                break;
            }
            case "spyglass": {
                if (!clip.spyglassZoom.isEmpty()) {
                    Integer lastTick = this.lastRecordedTicks.get("spyglassZoom");
                    if (lastTick == null || localTick - 1 > lastTick) {
                        clip.spyglassZoom.insert((float)(localTick - 1), Float.valueOf(this.spyglassLastZoom));
                    }
                    for (int i = 0; i < 4; ++i) {
                        float progress = (float)(i + 1) / 4.0f;
                        float eased = (float)Math.sin((double)MathHelper.clamp((float)progress, (float)0.0f, (float)1.0f) * Math.PI / 2.0);
                        float zoom = MathHelper.lerp((float)eased, (float)this.spyglassLastZoom, (float)1.0f);
                        clip.spyglassZoom.insert((float)(localTick + i), Float.valueOf(zoom));
                    }
                    clip.duration.set(Math.max((Integer)clip.duration.get(), localTick + 4));
                    this.lastFloatValues.remove("spyglassZoom");
                    this.lastRecordedTicks.remove("spyglassZoom");
                }
                this.recordFloatEnd(clip.spyglassScale, "spyglassScale", localTick, 1.12f);
                break;
            }
            case "suffocation": {
                this.recordFloatEnd(clip.suffocationOpacity, "suffocationOpacity", localTick, 0.0f);
                break;
            }
            case "darkness": {
                this.recordFloatEnd(clip.darknessOpacity, "darknessOpacity", localTick, 0.0f);
                this.recordFloatEnd(clip.darknessRadius, "darknessRadius", localTick, 15.0f);
                break;
            }
            case "blindness": {
                this.recordFloatEnd(clip.blindnessOpacity, "blindnessOpacity", localTick, 0.0f);
                this.recordFloatEnd(clip.blindnessRadius, "blindnessRadius", localTick, 5.0f);
                break;
            }
            case "night_vision": {
                this.recordFloatEnd(clip.nightVisionOpacity, "nightVisionOpacity", localTick, 0.0f);
                break;
            }
            case "underwater": {
                this.recordFloatEnd(clip.underwaterOpacity, "underwaterOpacity", localTick, 0.0f);
            }
        }
    }

    private void recordEffectKeyframes(ScreenEffectPovActionClip clip, String effectId, int localTick, ClientPlayerEntity player, float vignetteDarkness, BlockState suffocationState, boolean inPortalBlock, boolean isNewlyStarted) {
        switch (effectId) {
            case "vignette": {
                this.recordFloatChannel(clip.vignetteOpacity, "vignetteOpacity", localTick, vignetteDarkness, 0.0f, 0.01f, isNewlyStarted);
                if (!isNewlyStarted && !clip.vignetteColor.isEmpty()) break;
                if (localTick > 0 && clip.vignetteColor.isEmpty()) {
                    clip.vignetteColor.insert(0.0f, new Color(0.0f, 0.0f, 0.0f, 1.0f));
                    if (localTick > 1) {
                        clip.vignetteColor.insert((float)localTick - 1.0f, new Color(0.0f, 0.0f, 0.0f, 1.0f));
                    }
                }
                clip.vignetteColor.insert((float)localTick, new Color(0.0f, 0.0f, 0.0f, 1.0f));
                break;
            }
            case "nausea": {
                float intensity = player.nauseaIntensity;
                this.recordFloatChannel(clip.nauseaDistortion, "nauseaDistortion", localTick, intensity, 0.0f, 0.01f, isNewlyStarted);
                this.recordFloatChannel(clip.nauseaOpacity, "nauseaOpacity", localTick, 1.0f, 0.0f, 0.01f, isNewlyStarted);
                break;
            }
            case "frost": {
                float freeze = player.getFreezingScale();
                boolean scoping = player.isUsingSpyglass() || player.isUsingItem() && player.getActiveItem().isOf(Items.SPYGLASS);
                Float lastFrostZoom = this.lastFloatValues.get("frostZoom");
                float fovMult = scoping ? (lastFrostZoom != null ? lastFrostZoom.floatValue() : 1.0f) : player.getFovMultiplier();
                this.recordFloatChannel(clip.frostProgress, "frostProgress", localTick, freeze, 0.0f, 0.01f, isNewlyStarted);
                this.recordFloatChannel(clip.frostZoom, "frostZoom", localTick, fovMult, 1.0f, 0.005f, isNewlyStarted);
                break;
            }
            case "portal": {
                float portalVal = inPortalBlock && player.nauseaIntensity <= 0.001f ? 1.0f : player.nauseaIntensity;
                this.recordFloatChannel(clip.portalOpacity, "portalOpacity", localTick, portalVal, 0.0f, 0.01f, isNewlyStarted);
                break;
            }
            case "fire": {
                break;
            }
            case "pumpkin": {
                this.recordFloatChannel(clip.pumpkinOpacity, "pumpkinOpacity", localTick, 1.0f, 0.0f, 0.01f, isNewlyStarted);
                break;
            }
            case "spyglass": {
                InGameHudVignettePovAccess accessHud;
                float hudScale;
                float liveScale = 1.12f;
                MinecraftClient client = MinecraftClient.getInstance();
                InGameHud fovMult = client.inGameHud;
                if (fovMult instanceof InGameHudVignettePovAccess && (hudScale = (accessHud = (InGameHudVignettePovAccess)fovMult).bbsPov$getSpyglassScale()) > 0.001f) {
                    liveScale = hudScale;
                }
                this.recordFloatChannel(clip.spyglassScale, "spyglassScale", localTick, liveScale, 0.5f, 0.005f, isNewlyStarted);
                int useTime = player.getItemUseTime();
                float zoom = (float)(0.102 + 0.898 * Math.pow(0.5, Math.max(0, useTime)));
                if (zoom <= 0.1025f) {
                    zoom = 0.102f;
                }
                this.spyglassLastZoom = zoom = MathHelper.clamp((float)zoom, (float)0.102f, (float)1.0f);
                if (isNewlyStarted) {
                    if (localTick > 0) {
                        if (clip.spyglassZoom.isEmpty()) {
                            clip.spyglassZoom.insert(0.0f, Float.valueOf(1.0f));
                        }
                        clip.spyglassZoom.insert((float)(localTick - 1), Float.valueOf(1.0f));
                    }
                    clip.spyglassZoom.insert((float)localTick, Float.valueOf(zoom));
                    this.lastFloatValues.put("spyglassZoom", Float.valueOf(zoom));
                    this.lastRecordedTicks.put("spyglassZoom", localTick);
                    break;
                }
                Float lastVal = this.lastFloatValues.get("spyglassZoom");
                if (lastVal != null && !(Math.abs(zoom - lastVal.floatValue()) >= 0.005f) && (zoom != 0.102f || !(lastVal.floatValue() > 0.102f))) break;
                clip.spyglassZoom.insert((float)localTick, Float.valueOf(zoom));
                this.lastFloatValues.put("spyglassZoom", Float.valueOf(zoom));
                this.lastRecordedTicks.put("spyglassZoom", localTick);
                break;
            }
            case "suffocation": {
                Identifier id;
                String blockId = "minecraft:stone";
                if (suffocationState != null && (id = Registries.BLOCK.getId(suffocationState.getBlock())) != null) {
                    blockId = id.toString();
                }
                this.recordFloatChannel(clip.suffocationOpacity, "suffocationOpacity", localTick, 1.0f, 0.0f, 0.02f, isNewlyStarted);
                if (!isNewlyStarted && !clip.suffocationBlock.isEmpty()) break;
                if (localTick > 0 && clip.suffocationBlock.isEmpty()) {
                    clip.suffocationBlock.insert(0.0f, blockId);
                    if (localTick > 1) {
                        clip.suffocationBlock.insert((float)localTick - 1.0f, blockId);
                    }
                }
                clip.suffocationBlock.insert((float)localTick, blockId);
                break;
            }
            case "darkness": {
                StatusEffectInstance statusInst;
                float darkFactor = 1.0f;
                if (player.hasStatusEffect(StatusEffects.DARKNESS) && (statusInst = player.getStatusEffect(StatusEffects.DARKNESS)) != null && statusInst.getFactorCalculationData().isPresent()) {
                    darkFactor = ((StatusEffectInstance.FactorCalculationData)statusInst.getFactorCalculationData().get()).lerp((LivingEntity)player, 1.0f);
                }
                this.recordFloatChannel(clip.darknessOpacity, "darknessOpacity", localTick, darkFactor, 0.0f, 0.02f, isNewlyStarted);
                this.recordFloatChannel(clip.darknessRadius, "darknessRadius", localTick, 15.0f, 15.0f, 0.1f, isNewlyStarted);
                break;
            }
            case "blindness": {
                this.recordFloatChannel(clip.blindnessOpacity, "blindnessOpacity", localTick, 1.0f, 0.0f, 0.02f, isNewlyStarted);
                this.recordFloatChannel(clip.blindnessRadius, "blindnessRadius", localTick, 5.0f, 5.0f, 0.1f, isNewlyStarted);
                break;
            }
            case "night_vision": {
                StatusEffectInstance statusInst;
                float strength = 1.0f;
                if (player.hasStatusEffect(StatusEffects.NIGHT_VISION) && (statusInst = player.getStatusEffect(StatusEffects.NIGHT_VISION)) != null && statusInst.getDuration() <= 200) {
                    int dur = statusInst.getDuration();
                    float f = 0.7f + MathHelper.sin((float)(((float)dur - 1.0f) * (float)Math.PI * 0.2f)) * 0.3f;
                    strength = MathHelper.clamp((float)f, (float)0.0f, (float)1.0f);
                }
                this.recordFloatChannel(clip.nightVisionOpacity, "nightVisionOpacity", localTick, strength, 0.0f, 0.02f, isNewlyStarted);
                break;
            }
            case "underwater": {
                this.recordFloatChannel(clip.underwaterOpacity, "underwaterOpacity", localTick, 0.1f, 0.0f, 0.02f, isNewlyStarted);
            }
        }
    }

    public void finish(ReplayKeyframesPovAccess access, int endTick) {
        if (this.recordingClip != null) {
            if (!this.currentlyActiveEffects.isEmpty()) {
                int localTick = endTick - (Integer)this.recordingClip.tick.get();
                for (String effectId : this.currentlyActiveEffects) {
                    ScreenEffectRecorder.recordVisible(this.recordingClip, effectId, localTick, false);
                    this.recordEffectEnd(this.recordingClip, effectId, localTick);
                }
                this.currentlyActiveEffects.clear();
                this.recordingClip.duration.set(Math.max((Integer)this.recordingClip.duration.get(), localTick + 1));
            }
            this.recordingClip.ensureBakingBounds();
            this.recordingClip = null;
            this.wasScoping = false;
            this.spyglassReleaseTicksRemaining = 0;
            this.spyglassLastZoom = 1.0f;
            this.lastFloatValues.clear();
            this.lastRecordedTicks.clear();
        }
        if (access == null || access.bbsPov$getActions() == null) {
            return;
        }
        for (Clip clip : access.bbsPov$getActions().get()) {
            if (!(clip instanceof ScreenEffectPovActionClip)) continue;
            ScreenEffectPovActionClip effectClip = (ScreenEffectPovActionClip)clip;
            effectClip.trimToRecording(endTick);
            effectClip.ensureBakingBounds();
        }
    }
}

