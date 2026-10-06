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
package mchorse.bbs_mod.camera.pov.actions.toast.editor;

import mchorse.bbs_mod.camera.pov.actions.toast.ToastPresets;
import mchorse.bbs_mod.camera.pov.actions.toast.ToastTypeEntry;
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

public class UIToastOverlayPanel
extends UIOverlayPanel {
    public UISearchList<ToastTypeEntry> list;
    public Consumer<ToastTypeEntry> callback;

    public UIToastOverlayPanel(IKey title, Consumer<ToastTypeEntry> callback) {
        super(title);
        this.callback = callback;
        UIToastList toastList = new UIToastList(selected -> {
            if (this.callback != null && selected != null && !selected.isEmpty()) {
                this.callback.accept((ToastTypeEntry)selected.get(0));
                this.close();
            }
        });
        toastList.add(ToastPresets.getAll());
        this.list = new UISearchList((UIList)toastList);
        this.list.relative(this.content).xy(6, 6).w(1.0f, -12).h(1.0f, -6);
        this.content.add(this.list);
    }

    public static class UIToastList
    extends UIList<ToastTypeEntry> {
        public UIToastList(Consumer<List<ToastTypeEntry>> callback) {
            super(callback);
            this.scroll.scrollItemSize = 26;
        }

        public void renderListElement(UIContext context, ToastTypeEntry element, int i, int x, int y, boolean hover, boolean selected) {
            int h = this.scroll.scrollItemSize;
            RowStyle.row((Batcher2D)context.batcher, (int)x, (int)y, (int)this.area.w, (int)h, (int)this.rowColor(element), (boolean)this.isHeader(element), (boolean)hover, (boolean)selected);
            this.renderElementPart(context, element, i, x, y, hover, selected);
        }

        protected void renderElementPart(UIContext context, ToastTypeEntry element, int i, int x, int y, boolean hover, boolean selected) {
            int h = this.scroll.scrollItemSize;
            int iconX = x + 4;
            int iconY = y + (h - 16) / 2;
            context.batcher.box((float)(iconX - 1), (float)(iconY - 1), (float)(iconX + 17), (float)(iconY + 17), -872415232);
            DrawContext dc = context.batcher.getContext();
            if (dc != null && element != null) {
                ItemStack stack = element.createIconStack();
                RenderSystem.enableBlend();
                dc.drawItem(stack, iconX, iconY);
            }
            int textX = x + 26;
            if (element != null) {
                String title = element.title;
                String desc = element.description + " (" + element.category + ")";
                context.batcher.text(title, (float)textX, (float)(y + 3), element.getTitleColor(), false);
                context.batcher.text(desc, (float)textX, (float)(y + 13), -5592406, false);
            }
        }

        protected String elementToString(UIContext context, int i, ToastTypeEntry element) {
            if (element == null) {
                return "";
            }
            return element.title + " " + element.description + " " + element.category;
        }
    }
}

