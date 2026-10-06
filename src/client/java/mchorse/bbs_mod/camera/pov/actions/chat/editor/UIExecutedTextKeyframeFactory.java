/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle
 *  mchorse.bbs_mod.ui.framework.elements.input.UIColor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.ui.utils.UI
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 */
package mchorse.bbs_mod.camera.pov.actions.chat.editor;

import mchorse.bbs_mod.camera.pov.actions.chat.editor.UIFormattedTextarea;
import java.lang.invoke.StringConcatFactory;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIExecutedTextKeyframeFactory
extends UIKeyframeFactory<String> {
    public static final String HIDE_HUD_PREFIX = "\u0001HIDE_HUD\u0001";
    private static final int[][] MC_COLORS = new int[][]{{0, 0}, {170, 1}, {43520, 2}, {43690, 3}, {0xAA0000, 4}, {0xAA00AA, 5}, {0xFFAA00, 6}, {0xAAAAAA, 7}, {0x555555, 8}, {0x5555FF, 9}, {0x55FF55, 10}, {0x55FFFF, 11}, {0xFF5555, 12}, {0xFF55FF, 13}, {0xFFFF55, 14}, {0xFFFFFF, 15}};
    private static final char[] MC_CHARS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    public UIFormattedTextarea textarea;
    public UIColor color;
    public UIButton btnN;
    public UIButton btnB;
    public UIButton btnU;
    public UIButton btnI;
    public UIToggle showOnHud;
    private String lastValue;
    private int lastEquippedColor = -1;

    public static boolean isHiddenFromHud(String value) {
        return value != null && value.startsWith(HIDE_HUD_PREFIX);
    }

    public static String getRawText(String value) {
        if (value != null && value.startsWith(HIDE_HUD_PREFIX)) {
            return value.substring(HIDE_HUD_PREFIX.length());
        }
        return value != null ? value : "";
    }

    public static String formatValue(String rawText, boolean showOnHud) {
        if (!showOnHud) {
            return HIDE_HUD_PREFIX + (rawText != null ? rawText : "");
        }
        return rawText != null ? rawText : "";
    }

    public UIExecutedTextKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor) {
        super(keyframe, editor);
        this.lastValue = (String)keyframe.getValue();
        this.textarea = new UIFormattedTextarea(str -> this.updateKeyframeValue((String)str, this.showOnHud == null || this.showOnHud.getValue()));
        this.textarea.h(80);
        this.textarea.setFormattedText(UIExecutedTextKeyframeFactory.getRawText(this.lastValue));
        this.color = new UIColor(col -> {
            String code = UIExecutedTextKeyframeFactory.getClosestColorCode(col);
            this.textarea.applyFormat(code);
        });
        this.color.h(20);
        this.btnN = new UIButton(IKey.constant((String)"N"), b -> this.textarea.applyNormal());
        this.btnN.tooltip(IKey.constant((String)"Normal / Reset Style (Keep Color)"));
        this.btnB = new UIButton(IKey.constant((String)"B"), b -> this.textarea.applyFormat("\u00a7l"));
        this.btnB.tooltip(IKey.constant((String)"Bold (\u00a7l)"));
        this.btnU = new UIButton(IKey.constant((String)"U"), b -> this.textarea.applyFormat("\u00a7n"));
        this.btnU.tooltip(IKey.constant((String)"Underline (\u00a7n)"));
        this.btnI = new UIButton(IKey.constant((String)"I"), b -> this.textarea.applyFormat("\u00a7o"));
        this.btnI.tooltip(IKey.constant((String)"Italic (\u00a7o)"));
        this.showOnHud = new UIToggle(IKey.constant((String)"Pop up on HUD"), !UIExecutedTextKeyframeFactory.isHiddenFromHud(this.lastValue), toggle -> this.updateKeyframeValue(this.textarea.getText(), toggle.getValue()));
        this.showOnHud.tooltip(IKey.constant((String)"When disabled, the text won't pop up and fade away on the HUD, but will still show in the chat screen history."));
        this.scroll.add(new IUIElement[]{UI.label((IKey)IKey.constant((String)"Executed Text:")), this.textarea, this.color, UI.row((UIElement[])new UIElement[]{this.btnN, this.btnB, this.btnU, this.btnI}), this.showOnHud});
    }

    private void updateKeyframeValue(String rawText, boolean showOnHud) {
        String formatted = UIExecutedTextKeyframeFactory.formatValue(rawText, showOnHud);
        this.setValue(formatted);
        this.lastValue = formatted;
    }

    private static String getClosestColorCode(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int closestIdx = 15;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < MC_COLORS.length; ++i) {
            int cR = MC_COLORS[i][0] >> 16 & 0xFF;
            int cG = MC_COLORS[i][0] >> 8 & 0xFF;
            int cB = MC_COLORS[i][0] & 0xFF;
            double dist = (double)((r - cR) * (r - cR)) * 0.3 + (double)((g - cG) * (g - cG)) * 0.59 + (double)((b - cB) * (b - cB)) * 0.11;
            if (!(dist < minDistance)) continue;
            minDistance = dist;
            closestIdx = i;
        }
        return "\u00a7" + MC_CHARS[closestIdx];
    }

    public void update() {
        super.update();
        String val = (String)this.keyframe.getValue();
        if (val == null ? this.lastValue != null : !val.equals(this.lastValue)) {
            this.lastValue = val;
            this.textarea.setFormattedText(UIExecutedTextKeyframeFactory.getRawText(val));
            if (this.showOnHud != null) {
                this.showOnHud.setValue(!UIExecutedTextKeyframeFactory.isHiddenFromHud(val));
            }
        }
    }

    public void render(UIContext context) {
        int selectedColor;
        context.batcher.box((float)this.area.x, (float)this.area.y, (float)this.area.ex(), (float)this.area.ey(), -15461356);
        if (this.color != null && !this.color.isUserEditing() && this.lastEquippedColor != (selectedColor = this.textarea.getSelectedColor())) {
            this.lastEquippedColor = selectedColor;
            this.color.setColor(selectedColor);
        }
        super.render(context);
    }
}

