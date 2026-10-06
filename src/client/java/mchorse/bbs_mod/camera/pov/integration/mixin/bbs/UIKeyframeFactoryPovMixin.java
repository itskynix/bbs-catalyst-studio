/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.item.ItemStack
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.actions.bossbar.editor.UIBossBarLookKeyframeFactory;
import mchorse.bbs_mod.camera.pov.actions.chat.editor.UIChatTextKeyframeFactory;
import mchorse.bbs_mod.camera.pov.actions.chat.editor.UIExecutedTextKeyframeFactory;
import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiSlotSchema;
import mchorse.bbs_mod.camera.pov.actions.gui.editor.UIGuiSlotKeyframeFactory;
import mchorse.bbs_mod.camera.pov.hud.editor.UIConstrainedNumericKeyframeFactory;
import mchorse.bbs_mod.camera.pov.hud.editor.UIHotbarItemKeyframeFactory;
import mchorse.bbs_mod.camera.pov.hud.editor.UIHotbarTransformKeyframeFactory;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIKeyframeFactory.class}, remap=false)
public abstract class UIKeyframeFactoryPovMixin {
    @Inject(method={"createPanel"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$createLayoutPanel(Keyframe keyframe, UIKeyframes editor, CallbackInfoReturnable<UIKeyframeFactory> info) {
        UIKeyframeSheet sheet = null;
        if (editor != null && editor.getGraph() != null) {
            sheet = editor.getGraph().getSheet(keyframe);
        }
        if (sheet == null && editor != null && editor.getDopeSheet() != null) {
            sheet = editor.getDopeSheet().getSheet(keyframe);
        }
        if (sheet == null && editor != null && editor.getDopeSheet() != null) {
            for (UIKeyframeSheet s : editor.getDopeSheet().getSheets()) {
                if (s.channel != keyframe.getParent() && (s.channel == null || !s.channel.getKeyframes().contains(keyframe))) continue;
                sheet = s;
                break;
            }
        }
        if (sheet == null && editor != null && editor.getGraph() != null) {
            for (UIKeyframeSheet s : editor.getGraph().getSheets()) {
                if (s.channel != keyframe.getParent() && (s.channel == null || !s.channel.getKeyframes().contains(keyframe))) continue;
                sheet = s;
                break;
            }
        }
        String id = null;
        if (sheet != null && sheet.id != null) {
            id = sheet.id;
        } else if (keyframe.getParent() != null) {
            id = keyframe.getParent().getId();
        }
        if (keyframe.getValue() instanceof Transform) {
            boolean isLayout;
            boolean bl = isLayout = id != null && (id.contains("layout") || id.contains("cursor")) || sheet != null && sheet.title != null && sheet.title.get() != null && sheet.title.get().toLowerCase().contains("layout");
            if (isLayout) {
                info.setReturnValue(new UIHotbarTransformKeyframeFactory((Keyframe<Transform>)keyframe, editor));
                return;
            }
        }
        if (keyframe.getValue() instanceof ItemStack) {
            info.setReturnValue(new UIHotbarItemKeyframeFactory((Keyframe<ItemStack>)keyframe, editor));
            return;
        }
        Object object = keyframe.getValue();
        if (object instanceof String) {
            String str = (String)object;
            if ("bossbar_color".equals(id) || "color".equals(id)) {
                info.setReturnValue(new UIBossBarLookKeyframeFactory((Keyframe<String>)keyframe, editor, UIBossBarLookKeyframeFactory.Mode.COLOR));
                return;
            }
            if ("bossbar_style".equals(id) || "style".equals(id)) {
                info.setReturnValue(new UIBossBarLookKeyframeFactory((Keyframe<String>)keyframe, editor, UIBossBarLookKeyframeFactory.Mode.STYLE));
                return;
            }
            if ("executed_text".equals(id) || "execution_text".equals(id)) {
                info.setReturnValue(new UIExecutedTextKeyframeFactory((Keyframe<String>)keyframe, editor));
                return;
            }
            if ("chat_text".equals(id)) {
                info.setReturnValue(new UIChatTextKeyframeFactory((Keyframe<String>)keyframe, editor));
                return;
            }
        }
        if (id == null) {
            return;
        }
        if ("crafting_grid".equals(id) || "inventory_slots".equals(id) || "hotbar_inventory_slots".equals(id)) {
            info.setReturnValue(new UIGuiSlotKeyframeFactory((Keyframe<Boolean>)keyframe, editor));
            return;
        }
        if ("gui_slots".equals(id)) {
            String guiId;
            GuiSlotSchema schema;
            BaseValue baseValue;
            UIFilmPanel filmPanel = PovReplaySettings.getFilmPanel();
            GuiPovActionClip guiClip = null;
            if (keyframe.getParent() != null && (baseValue = keyframe.getParent().getParent()) instanceof GuiPovActionClip) {
                GuiPovActionClip clip;
                guiClip = clip = (GuiPovActionClip)baseValue;
            } else if (filmPanel != null && filmPanel.cameraEditor != null && (baseValue = filmPanel.cameraEditor.getClip()) instanceof GuiPovActionClip) {
                GuiPovActionClip clip;
                guiClip = clip = (GuiPovActionClip)baseValue;
            }
            if (guiClip != null && (schema = GuiSlotSchema.get(guiId = guiClip.state.isEmpty() ? "inventory" : (String)guiClip.state.get(0).getValue())).getGroupedSlots().size() > 9) {
                info.setReturnValue(null);
                return;
            }
            info.setReturnValue(new UIGuiSlotKeyframeFactory((Keyframe<Boolean>)keyframe, editor));
            return;
        }
        if ("gui_opacity".equals(id) || "bg_opacity".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.floatPanel(keyframe, editor, 0.0f, 1.0f));
        } else if ("selected_slot".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 8));
        } else if ("hotbar_heart_type".equals(id) || "heart_type".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 4));
        } else if ("hotbar_armor".equals(id) || "hotbar_hunger".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 20));
        } else if ("hotbar_air".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 300));
        } else if ("hotbar_experience_level".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 9999));
        } else if ("hotbar_experience".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.doublePanel(keyframe, editor, 0.0, 1.0));
        } else if ("pov_hand_active_hand".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 2));
        } else if ("book_page".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 99));
        } else if ("horse_variant".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 34));
        } else if ("beacon_primary".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 5));
        } else if ("beacon_level".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 4));
        } else if ("beacon_secondary".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.integerPanel(keyframe, editor, 0, 2));
        } else if ("pov_hand_right_swing_progress".equals(id) || "right_swing_progress".equals(id) || "pov_hand_left_swing_progress".equals(id) || "left_swing_progress".equals(id) || "pov_hand_main_equip".equals(id) || "pov_hand_off_equip".equals(id)) {
            info.setReturnValue(UIKeyframeFactoryPovMixin.floatPanel(keyframe, editor, 0.0f, 1.0f));
        } else if ("bossbar_color".equals(id) || "color".equals(id) && keyframe.getValue() instanceof String) {
            info.setReturnValue(new UIBossBarLookKeyframeFactory((Keyframe<String>)keyframe, editor, UIBossBarLookKeyframeFactory.Mode.COLOR));
        } else if ("bossbar_style".equals(id) || "style".equals(id) && keyframe.getValue() instanceof String) {
            info.setReturnValue(new UIBossBarLookKeyframeFactory((Keyframe<String>)keyframe, editor, UIBossBarLookKeyframeFactory.Mode.STYLE));
        }
    }

    private static UIKeyframeFactory<Integer> integerPanel(Keyframe keyframe, UIKeyframes editor, int minimum, int maximum) {
        return new UIConstrainedNumericKeyframeFactory<Integer>(keyframe, editor, minimum, maximum, true, value -> (int)Math.round(value));
    }

    private static UIKeyframeFactory<Double> doublePanel(Keyframe keyframe, UIKeyframes editor, double minimum, double maximum) {
        return new UIConstrainedNumericKeyframeFactory<Double>(keyframe, editor, minimum, maximum, false, value -> value);
    }

    private static UIKeyframeFactory<Float> floatPanel(Keyframe keyframe, UIKeyframes editor, float minimum, float maximum) {
        return new UIConstrainedNumericKeyframeFactory<Float>(keyframe, editor, minimum, maximum, false, value -> Float.valueOf((float)value));
    }
}

