/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.graphics.texture.Texture
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.ui.film.IUIClipsDelegate
 *  mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon
 *  mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker
 *  mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel
 *  mchorse.bbs_mod.ui.utils.UI
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.ToastPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.toast.ToastPresets;
import mchorse.bbs_mod.camera.pov.actions.toast.ToastTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.toast.editor.UIToastOverlayPanel;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class UIToastActionClip
extends UIPovActionClip<ToastPovActionClip> {
    public UIButton pickToast;
    public UIElement previewCard;
    public UITextbox customTitle;
    public UITextbox customDescription;
    public UIButton pickItem;
    public UIIcon clearItem;
    public UIButton pickTexture;
    public UIIcon clearTexture;

    public UIToastActionClip(ToastPovActionClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void registerUI() {
        super.registerUI();
        this.pickToast = new UIButton(IKey.constant((String)"Pick Toast Type"), b -> this.openToastPicker());
        this.pickToast.tooltip(IKey.constant((String)"Choose a preset toast from Advancements, Recipes, Tutorials, or System notifications"));
        this.previewCard = new UIElement(){

            public void render(UIContext context) {
                int x = this.area.x;
                int y = this.area.y;
                int w = this.area.w;
                int h = this.area.h;
                context.batcher.box((float)x, (float)y, (float)(x + w), (float)(y + h), -870836200);
                context.batcher.outline((float)x, (float)y, (float)(x + w), (float)(y + h), 0x33FFFFFF);
                DrawContext dc = context.batcher.getContext();
                if (dc != null) {
                    ToastTypeEntry entry = ToastPresets.getById(((ToastPovActionClip)UIToastActionClip.this.clip).getPresetId());
                    Link customTex = ((ToastPovActionClip)UIToastActionClip.this.clip).getCustomTexture();
                    if (customTex != null) {
                        Texture tex = BBSModClient.getTextures().getTexture(customTex);
                        if (tex != null && tex.isValid() && tex.id > 0) {
                            context.batcher.fullTexturedBox(tex, (float)(x + 6), (float)(y + (h - 16) / 2), 16.0f, 16.0f);
                        }
                    } else {
                        String iconId = ((ToastPovActionClip)UIToastActionClip.this.clip).getEffectiveIcon();
                        ItemStack stack = UIToastActionClip.createItemStack(iconId);
                        RenderSystem.enableBlend();
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc((int)515);
                        RenderSystem.depthMask((boolean)true);
                        if ("recipe".equalsIgnoreCase(((ToastPovActionClip)UIToastActionClip.this.clip).getEffectiveFrameType())) {
                            dc.getMatrices().push();
                            dc.getMatrices().translate((float)(x + 2), (float)(y + (h - 16) / 2 - 2), 0.0f);
                            dc.getMatrices().scale(0.6f, 0.6f, 1.0f);
                            dc.drawItem(new ItemStack((ItemConvertible)Items.CRAFTING_TABLE), 0, 0);
                            dc.getMatrices().pop();
                        }
                        dc.drawItem(stack, x + 6, y + (h - 16) / 2);
                        RenderSystem.disableDepthTest();
                    }
                    String title = ((ToastPovActionClip)UIToastActionClip.this.clip).getEffectiveTitle();
                    String desc = ((ToastPovActionClip)UIToastActionClip.this.clip).getEffectiveDescription();
                    int maxW = Math.max(10, w - 34);
                    TextRenderer tr = context.batcher.getFont().getRenderer();
                    String limitedTitle = tr != null ? tr.trimToWidth(title, maxW) : title;
                    String limitedDesc = tr != null ? tr.trimToWidth(desc, maxW) : desc;
                    int descColor = "recipe".equalsIgnoreCase(((ToastPovActionClip)UIToastActionClip.this.clip).getEffectiveFrameType()) ? -13421773 : -5592406;
                    context.batcher.text(limitedTitle, (float)(x + 28), (float)(y + 4), entry.getTitleColor(), false);
                    context.batcher.text(limitedDesc, (float)(x + 28), (float)(y + 14), descColor, false);
                }
                super.render(context);
            }
        };
        this.previewCard.h(28);
        this.customTitle = new UITextbox(100, text -> ((ToastPovActionClip)this.clip).setCustomTitle((String)text));
        this.customTitle.tooltip(IKey.constant((String)"Optional custom title override (leave empty to use preset title)"));
        this.customDescription = new UITextbox(100, text -> ((ToastPovActionClip)this.clip).setCustomDescription((String)text));
        this.customDescription.tooltip(IKey.constant((String)"Optional custom description override (leave empty to use preset description)"));
        this.pickItem = new UIButton(IKey.constant((String)"Pick Item"), b -> this.openItemPicker());
        this.pickItem.tooltip(IKey.constant((String)"Select custom item icon for this toast"));
        this.pickItem.h(20);
        this.clearItem = new UIIcon(Icons.CLOSE, b -> {
            ((ToastPovActionClip)this.clip).setCustomIcon("");
            this.fillData();
            this.editor.fillData();
        });
        this.clearItem.tooltip(IKey.constant((String)"Reset custom item"));
        this.clearItem.wh(20, 20);
        this.pickTexture = new UIButton(IKey.constant((String)"Pick Texture"), b -> this.openTexturePicker());
        this.pickTexture.tooltip(IKey.constant((String)"Choose a custom texture (ignores item icon if selected)"));
        this.pickTexture.h(20);
        this.clearTexture = new UIIcon(Icons.CLOSE, b -> {
            ((ToastPovActionClip)this.clip).setCustomTexture(null);
            this.fillData();
            this.editor.fillData();
        });
        this.clearTexture.tooltip(IKey.constant((String)"Remove custom texture"));
        this.clearTexture.wh(20, 20);
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add((IUIElement)this.section(IKey.constant((String)"Toast Preset"), new UIElement[]{this.previewCard, this.pickToast}));
        UIElement itemRow = UI.row((int)2, (int)0, (int)20, (UIElement[])new UIElement[]{this.pickItem, this.clearItem}).h(20);
        UIElement textureRow = UI.row((int)2, (int)0, (int)20, (UIElement[])new UIElement[]{this.pickTexture, this.clearTexture}).h(20);
        this.panels.add((IUIElement)this.section(IKey.constant((String)"Custom Overrides"), new UIElement[]{this.customTitle, this.customDescription, itemRow, textureRow}));
    }

    @Override
    public void fillData() {
        super.fillData();
        if (!this.customTitle.isFocused()) {
            this.customTitle.setText(((ToastPovActionClip)this.clip).getCustomTitle());
        }
        this.customTitle.placeholder(IKey.constant((String)((ToastPovActionClip)this.clip).getEffectiveTitle()));
        if (!this.customDescription.isFocused()) {
            this.customDescription.setText(((ToastPovActionClip)this.clip).getCustomDescription());
        }
        this.customDescription.placeholder(IKey.constant((String)((ToastPovActionClip)this.clip).getEffectiveDescription()));
        String iconId = ((ToastPovActionClip)this.clip).getCustomIcon();
        if (iconId != null && !iconId.isEmpty()) {
            String shortName = iconId.replace("minecraft:", "");
            this.pickItem.label = IKey.constant((String)("Item: " + shortName));
            this.clearItem.setVisible(true);
        } else {
            this.pickItem.label = IKey.constant((String)"Pick Item");
            this.clearItem.setVisible(false);
        }
        Link texture = ((ToastPovActionClip)this.clip).getCustomTexture();
        if (texture != null) {
            this.pickTexture.label = IKey.constant((String)("Tex: " + texture.path));
            this.clearTexture.setVisible(true);
        } else {
            this.pickTexture.label = IKey.constant((String)"Pick Texture");
            this.clearTexture.setVisible(false);
        }
    }

    private void openToastPicker() {
        UIContext context = this.getContext();
        if (context == null) {
            return;
        }
        UIToastOverlayPanel panel = new UIToastOverlayPanel(IKey.constant((String)"Pick Toast"), entry -> {
            ((ToastPovActionClip)this.clip).applyPreset((ToastTypeEntry)entry);
            this.fillData();
            this.editor.fillData();
        });
        UIOverlay.addOverlay((UIContext)context, (UIOverlayPanel)panel, (int)240, (int)200);
    }

    private void openItemPicker() {
        UIContext context = this.getContext();
        if (context == null) {
            return;
        }
        ItemStack currentStack = UIToastActionClip.createItemStack(((ToastPovActionClip)this.clip).getEffectiveIcon());
        UIUnifiedPickOverlayPanel panel = UIUnifiedPickOverlayPanel.forItem(stack -> {
            if (stack != null && !stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).toString();
                ((ToastPovActionClip)this.clip).setCustomIcon(id);
                this.fillData();
                this.editor.fillData();
            }
        }, (ItemStack)currentStack);
        UIOverlay.addOverlay((UIContext)context, (UIOverlayPanel)panel, (int)280, (int)240);
    }

    private void openTexturePicker() {
        UIContext context = this.getContext();
        if (context == null) {
            return;
        }
        UITexturePicker.open((UIContext)context, (Link)((ToastPovActionClip)this.clip).getCustomTexture(), link -> {
            ((ToastPovActionClip)this.clip).setCustomTexture((Link)link);
            this.fillData();
            this.editor.fillData();
        });
    }

    private static ItemStack createItemStack(String iconId) {
        if (iconId != null && !iconId.isEmpty()) {
            try {
                Item item = (Item)Registries.ITEM.get(new Identifier(iconId));
                if (item != null && item != Items.AIR) {
                    return new ItemStack((ItemConvertible)item);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return new ItemStack((ItemConvertible)Items.DIAMOND);
    }
}

