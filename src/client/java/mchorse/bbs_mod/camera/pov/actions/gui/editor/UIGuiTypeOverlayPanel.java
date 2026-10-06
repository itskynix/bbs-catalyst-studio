/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.framework.elements.IFocusedUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.list.UIList
 *  mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel
 */
package mchorse.bbs_mod.camera.pov.actions.gui.editor;

import mchorse.bbs_mod.camera.pov.actions.gui.GuiTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.gui.editor.UIGuiTypeList;
import java.util.function.Consumer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.IFocusedUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;

public class UIGuiTypeOverlayPanel
extends UIOverlayPanel {
    public final UISearchList<GuiTypeEntry> list;
    private final Consumer<GuiTypeEntry> callback;

    public UIGuiTypeOverlayPanel(Consumer<GuiTypeEntry> callback, String currentId) {
        super(IKey.constant((String)"GUI Type"));
        this.callback = callback;
        UIGuiTypeList typeList = new UIGuiTypeList(selected -> {
            if (selected != null && !selected.isEmpty() && this.callback != null) {
                this.callback.accept((GuiTypeEntry)selected.get(0));
                this.close();
            }
        });
        this.list = new UISearchList((UIList)typeList);
        this.list.label(IKey.constant((String)"Search..."));
        this.list.list.background();
        this.list.list.add(GuiTypeEntry.getAll());
        GuiTypeEntry current = GuiTypeEntry.findById(currentId);
        if (current != null) {
            this.list.list.setCurrentScroll(current);
        }
        this.list.relative(this.content).xy(6, 6).w(1.0f, -12).h(1.0f, -12);
        this.content.add(this.list);
    }

    protected void onAdd(UIElement parent) {
        super.onAdd(parent);
        if (this.getContext() != null && this.list != null && this.list.search != null) {
            this.getContext().focus((IFocusedUIElement)this.list.search);
        }
    }
}

