/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.utils.clips.Clip
 *  net.minecraft.advancement.AdvancementDisplay
 *  net.minecraft.advancement.AdvancementEntry
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.toast.AdvancementToast
 *  net.minecraft.client.toast.RecipeToast
 *  net.minecraft.client.toast.SystemToast
 *  net.minecraft.client.toast.Toast
 *  net.minecraft.client.toast.TutorialToast
 *  net.minecraft.client.world.ClientWorld
 *  net.minecraft.item.ItemStack
 *  net.minecraft.recipe.RecipeEntry
 *  net.minecraft.registry.Registries
 */
package mchorse.bbs_mod.camera.pov.actions.toast.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.ToastPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.toast.ToastPresets;
import mchorse.bbs_mod.camera.pov.actions.toast.ToastTypeEntry;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.AdvancementToastPovAccessor;
import java.lang.reflect.Field;
import java.util.List;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.advancement.AdvancementDisplay;
//? if >=1.20.4 {
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.recipe.RecipeEntry;
//?} else {
/*import net.minecraft.advancement.Advancement;
import net.minecraft.recipe.Recipe;
*///?}
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.AdvancementToast;
import net.minecraft.client.toast.RecipeToast;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.TutorialToast;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class ToastRecorder {
    private ToastRecorder() {
    }

    public static void finish(ReplayKeyframesPovAccess access, int endTick) {
        if (access == null || access.bbsPov$getActions() == null) {
            return;
        }
        for (Clip clip : access.bbsPov$getActions().get()) {
            if (!(clip instanceof ToastPovActionClip)) continue;
            ToastPovActionClip toastClip = (ToastPovActionClip)clip;
            toastClip.trimToRecording(endTick);
        }
    }

    public static void onToastAdded(Toast toast, RecordedPovActions actions, int tick) {
        block22: {
            if (actions == null || toast == null || !PovSettings.isBakeToasts()) {
                return;
            }
            int baseLayer = PovActionType.TOASTS.seedLayer();
            boolean[] occupied = new boolean[5];
            for (Clip c : actions.get()) {
                int offset;
                if (!(c instanceof ToastPovActionClip)) continue;
                ToastPovActionClip tc = (ToastPovActionClip)c;
                int start = (Integer)tc.tick.get();
                int end = start + (Integer)tc.duration.get();
                if (tick < start || tick >= end || (offset = (Integer)tc.layer.get() - baseLayer) < 0 || offset >= 5) continue;
                occupied[offset] = true;
            }
            int targetLayer = baseLayer;
            for (int i = 0; i < 5; ++i) {
                if (occupied[i]) continue;
                targetLayer = baseLayer + i;
                break;
            }
            ToastPovActionClip clip = (ToastPovActionClip)actions.add(PovActionType.TOASTS, tick, 100);
            clip.layer.set(targetLayer);
            if (toast instanceof AdvancementToast) {
                AdvancementToast advancementToast = (AdvancementToast)toast;
                //? if >=1.20.4 {
                try {
                    AdvancementEntry entry = null;
                    if (advancementToast instanceof AdvancementToastPovAccessor) {
                        AdvancementToastPovAccessor accessor = (AdvancementToastPovAccessor)advancementToast;
                        entry = accessor.bbsPov$getAdvancement();
                    }
                    if (entry == null) {
                        for (Field f : AdvancementToast.class.getDeclaredFields()) {
                            f.setAccessible(true);
                            Object obj = f.get(advancementToast);
                            if (!(obj instanceof AdvancementEntry)) continue;
                            entry = (AdvancementEntry)obj;
                            break;
                        }
                    }
                    if (entry == null || !entry.value().display().isPresent()) break block22;
                    AdvancementDisplay d = (AdvancementDisplay)entry.value().display().get();
                    String titleText = d.getFrame().getToastText().getString();
                    String descText = d.getTitle().getString();
                    clip.setCustomTitle(titleText);
                    clip.setCustomDescription(descText);
                    clip.title.set(descText);
                    ItemStack iconStack = d.getIcon();
                    if (iconStack != null && !iconStack.isEmpty()) {
                        clip.setCustomIcon(Registries.ITEM.getId(iconStack.getItem()).toString());
                    }
                    clip.setFrameType(d.getFrame().asString());
                    ToastTypeEntry match = ToastPresets.findByTitleOrDesc(descText);
                    if (match != null) {
                        clip.setPresetId(match.id);
                    }
                }
                catch (Exception exception) {}
                //?} else {
                /*try {
                    Advancement entry = null;
                    if (advancementToast instanceof AdvancementToastPovAccessor) {
                        AdvancementToastPovAccessor accessor = (AdvancementToastPovAccessor)advancementToast;
                        entry = accessor.bbsPov$getAdvancement();
                    }
                    if (entry == null) {
                        for (Field f : AdvancementToast.class.getDeclaredFields()) {
                            f.setAccessible(true);
                            Object obj = f.get(advancementToast);
                            if (!(obj instanceof Advancement)) continue;
                            entry = (Advancement)obj;
                            break;
                        }
                    }
                    if (entry == null || entry.getDisplay() == null) break block22;
                    AdvancementDisplay d = entry.getDisplay();
                    String titleText = d.getFrame().getToastText().getString();
                    String descText = d.getTitle().getString();
                    clip.setCustomTitle(titleText);
                    clip.setCustomDescription(descText);
                    clip.title.set(descText);
                    ItemStack iconStack = d.getIcon();
                    if (iconStack != null && !iconStack.isEmpty()) {
                        clip.setCustomIcon(Registries.ITEM.getId(iconStack.getItem()).toString());
                    }
                    clip.setFrameType(d.getFrame().getId());
                    ToastTypeEntry match = ToastPresets.findByTitleOrDesc(descText);
                    if (match != null) {
                        clip.setPresetId(match.id);
                    }
                }
                catch (Exception exception) {}
                *///?}
            } else if (toast instanceof RecipeToast) {
                RecipeToast recipeToast = (RecipeToast)toast;
                clip.setPresetId("rec_crafting_table");
                clip.setFrameType("recipe");
                clip.setCustomTitle("New Recipes Unlocked!");
                clip.setCustomDescription("Check your recipe book");
                clip.title.set("Recipe Unlocked");
                try {
                    for (Field f : RecipeToast.class.getDeclaredFields()) {
                        ItemStack is;
                        List list;
                        f.setAccessible(true);
                        Object val = f.get(recipeToast);
                        if (val instanceof List && !(list = (List)val).isEmpty()) {
                            ItemStack is2;
                            ItemStack is3;
                            Object first = list.get(0);
                            if (first instanceof ItemStack && !(is3 = (ItemStack)first).isEmpty()) {
                                clip.setCustomIcon(Registries.ITEM.getId(is3.getItem()).toString());
                            }
                            //? if >=1.20.4 {
                            if (!(first instanceof RecipeEntry)) continue;
                            RecipeEntry re = (RecipeEntry)first;
                            ClientWorld world = MinecraftClient.getInstance().world;
                            if (world == null || (is2 = re.value().getResult(world.getRegistryManager())) == null || is2.isEmpty()) continue;
                            clip.setCustomIcon(Registries.ITEM.getId(is2.getItem()).toString());
                            //?} else {
                            /*if (!(first instanceof Recipe)) continue;
                            Recipe r = (Recipe)first;
                            ClientWorld world = MinecraftClient.getInstance().world;
                            if (world == null || (is2 = r.getOutput(world.getRegistryManager())) == null || is2.isEmpty()) continue;
                            clip.setCustomIcon(Registries.ITEM.getId(is2.getItem()).toString());
                            *///?}
                        }
                        if (!(val instanceof ItemStack) || (is = (ItemStack)val).isEmpty()) continue;
                        clip.setCustomIcon(Registries.ITEM.getId(is.getItem()).toString());
                    }
                }
                catch (Exception exception) {}
            } else if (toast instanceof TutorialToast) {
                clip.setPresetId("tut_movement");
                clip.setFrameType("tutorial");
                clip.title.set("Tutorial");
            } else if (toast instanceof SystemToast) {
                clip.setPresetId("sys_screenshot");
                clip.setFrameType("system");
                clip.title.set("System Toast");
            }
        }
    }
}

