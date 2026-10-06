/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.graphics.texture.Texture
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel
 *  mchorse.bbs_mod.settings.ui.UIValueFactory
 *  mchorse.bbs_mod.settings.ui.UIValueMap
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.numeric.ValueBoolean
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle
 *  mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel
 *  mchorse.bbs_mod.ui.utils.UI
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  org.joml.Vector4f
 */
package mchorse.bbs_mod.camera.pov.config;

import mchorse.bbs_mod.camera.pov.config.BakeToggleAllValue;
import mchorse.bbs_mod.camera.pov.config.CursorCropValue;
import mchorse.bbs_mod.camera.pov.config.CursorTextureValue;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.config.UICursorCropOverlayPanel;
import java.util.List;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.joml.Vector4f;

public final class UICursorCropSetting {
    private UICursorCropSetting() {
    }

    public static void register() {
        UIValueMap.register(BakeToggleAllValue.class, (value, parent) -> {
            UIToggle toggle = UIValueFactory.booleanUI((ValueBoolean)value, t -> {
                PovSettings.setAllBake(t.getValue());
                if (parent instanceof UISettingsOverlayPanel) {
                    UISettingsOverlayPanel panel = (UISettingsOverlayPanel)parent;
                    panel.refresh();
                }
            });
            toggle.resetFlex();
            toggle.valueBinding(() -> toggle.setValue(PovSettings.areAllBakeEnabled()));
            return List.of(toggle);
        });
        UIValueMap.register(CursorTextureValue.class, (value, parent) -> {
            UIButton pick = new UIButton(IKey.constant((String)"Pick Texture"), button -> UITexturePicker.open((UIContext)parent.getContext(), (Link)((Link)value.get()), link -> {
                Link oldLink = (Link)value.get();
                value.set(link);
                if ((oldLink == null && link != null || oldLink != null && !oldLink.equals(link)) && PovSettings.cursorCrop != null) {
                    PovSettings.cursorCrop.set(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
                }
            }));
            pick.h(20);
            UIIcon clear = new UIIcon(Icons.CLOSE, button -> {
                value.set(null);
                if (PovSettings.cursorCrop != null) {
                    PovSettings.cursorCrop.set(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
                }
            });
            clear.wh(20, 20);
            clear.tooltip(IKey.constant((String)"Remove custom cursor texture"));
            UIElement row = UI.row((int)2, (int)0, (int)20, (UIElement[])new UIElement[]{pick, clear}).h(20);
            row.w(90);
            row.valueBinding(() -> {
                Link link = (Link)value.get();
                if (link != null) {
                    pick.label = IKey.constant((String)("Tex: " + link.path));
                    clear.setVisible(true);
                } else {
                    pick.label = IKey.constant((String)"Pick Texture");
                    clear.setVisible(false);
                }
            });
            return List.of(UIValueFactory.column((UIElement)row, (BaseValue)value));
        });
        UIValueMap.register(CursorCropValue.class, (value, parent) -> {
            UIButton edit = new UIButton(IKey.constant((String)"Edit Crop..."), button -> {
                Link link;
                Link link2 = link = PovSettings.cursorTexture == null ? null : (Link)PovSettings.cursorTexture.get();
                if (link == null) {
                    return;
                }
                Texture texture = BBSModClient.getTextures().getTexture(link);
                if (texture == null || !texture.isValid() || texture.width <= 0 || texture.height <= 0) {
                    return;
                }
                UIOverlay.addOverlay((UIContext)parent.getContext(), (UIOverlayPanel)new UICursorCropOverlayPanel(link, (CursorCropValue)value), (float)0.5f, (float)0.5f);
            });
            edit.w(90);
            return List.of(UIValueFactory.column((UIElement)edit, (BaseValue)value));
        });
    }
}

