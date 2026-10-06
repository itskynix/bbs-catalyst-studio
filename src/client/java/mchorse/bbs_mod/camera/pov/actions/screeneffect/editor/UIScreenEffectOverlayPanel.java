/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.input.list.UIList
 *  mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.ui.framework.elements.utils.RowStyle
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect.editor;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectPresetEntry;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectPresets;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public class UIScreenEffectOverlayPanel
extends UIOverlayPanel {
    public UISearchList<ScreenEffectPresetEntry> searchList;

    public UIScreenEffectOverlayPanel(IKey title, Consumer<ScreenEffectPresetEntry> callback) {
        super(title);
        UIScreenEffectList list = new UIScreenEffectList(selected -> {
            if (callback != null && selected != null && !selected.isEmpty()) {
                callback.accept((ScreenEffectPresetEntry)selected.get(0));
                this.close();
            }
        });
        list.add(ScreenEffectPresets.getAll());
        this.searchList = new UISearchList((UIList)list);
        this.searchList.relative(this.content).xy(6, 6).w(1.0f, -12).h(1.0f, -6);
        this.content.add(this.searchList);
    }

    public static class UIScreenEffectList
    extends UIList<ScreenEffectPresetEntry> {
        public UIScreenEffectList(Consumer<List<ScreenEffectPresetEntry>> callback) {
            super(callback);
            this.scroll.scrollItemSize = 26;
        }

        public void renderListElement(UIContext context, ScreenEffectPresetEntry element, int i, int x, int y, boolean hover, boolean selected) {
            int h = this.scroll.scrollItemSize;
            RowStyle.row((Batcher2D)context.batcher, (int)x, (int)y, (int)this.area.w, (int)h, (int)this.rowColor(element), (boolean)this.isHeader(element), (boolean)hover, (boolean)selected);
            this.renderElementPart(context, element, i, x, y, hover, selected);
        }

        protected void renderElementPart(UIContext context, ScreenEffectPresetEntry element, int i, int x, int y, boolean hover, boolean selected) {
            DrawContext dc = context.batcher.getContext();
            if (dc != null) {
                ItemStack stack = element.createIconStack();
                RenderSystem.enableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc((int)515);
                RenderSystem.depthMask((boolean)true);
                dc.drawItem(stack, x + 4, y + 5);
                RenderSystem.disableDepthTest();
            }
            int textX = x + 24;
            context.batcher.text(element.name, (float)textX, (float)(y + 3), -1, false);
            context.batcher.text(element.description, (float)textX, (float)(y + 13), -5592406, false);
        }
    }
}

