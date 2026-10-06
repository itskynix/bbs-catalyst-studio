/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.film.IUIClipsDelegate
 *  mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack
 *  mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.UITrackpad
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel
 *  mchorse.bbs_mod.ui.utils.UIUtils
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiSlotSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class UIGuiSlotEditor
extends UIElement {
    public static final int SLOT_SIZE = 23;
    public static final int SLOT_GAP = 2;
    public static final int PICKER_HEIGHT = 23;
    private final GuiPovActionClip clip;
    private final IUIClipsDelegate editor;
    private List<SlotView> slots = List.of();
    private int contentWidth;
    private int contentHeight;
    private float editTick;
    private int selectedSlotIndex = 0;
    private long lastClickTime = 0L;
    private int lastClickedSlotKey = -1;
    private int poseIconX = -1;
    private int poseIconY = -1;
    public final UIItemStack itemWidget;
    public final UITrackpad countTrackpad;

    public UIGuiSlotEditor(GuiPovActionClip clip, IUIClipsDelegate editor) {
        this.clip = clip;
        this.editor = editor;
        this.itemWidget = new UIItemStack(this::updateCurrentSlotStack);
        this.countTrackpad = new UITrackpad(value -> {
            ItemStack current = this.getCurrentSlotStack();
            if (current.isEmpty()) {
                return;
            }
            ItemStack copy = current.copy();
            copy.setCount(Math.max(1, Math.min(64, value.intValue())));
            this.updateCurrentSlotStack(copy);
        });
        this.countTrackpad.limit(1.0, 64.0, true);
        this.add(new IUIElement[]{this.itemWidget, this.countTrackpad});
        if (this.clip != null) {
            this.configure("inventory", Mode.GUI, 0.0f);
        }
    }

    public void configure(String guiId, boolean guiSlots) {
        this.configure(guiId, Mode.GUI, 0.0f);
    }

    public void configure(String guiId, boolean guiSlots, float editTick) {
        this.configure(guiId, Mode.GUI, editTick);
    }

    public void configure(List<KeyframeChannel<ItemStack>> inventory, KeyframeChannel<Boolean> anchor, float editTick) {
        ArrayList<SlotView> views = new ArrayList<SlotView>();
        this.poseIconX = -1;
        this.poseIconY = -1;
        this.addGrid(views, inventory, anchor, 9, 2, 2);
        this.slots = List.copyOf(views);
        this.editTick = editTick;
        this.selectedSlotIndex = 0;
        this.contentWidth = 0;
        this.contentHeight = 0;
        for (SlotView slot : this.slots) {
            this.contentWidth = Math.max(this.contentWidth, slot.x() + 23);
            this.contentHeight = Math.max(this.contentHeight, slot.y() + 23);
        }
        this.h(this.contentHeight + 8 + 54);
        this.updateControlValues();
        this.resize();
    }

    public void configure(String guiId, Mode mode, float editTick) {
        if (this.clip == null) {
            return;
        }
        GuiSlotSchema schema = GuiSlotSchema.get(guiId);
        ArrayList<SlotView> views = new ArrayList<SlotView>();
        int preferredSelection = 0;
        this.poseIconX = -1;
        this.poseIconY = -1;
        if (mode == Mode.CRAFTING) {
            int columns = "inventory".equals(guiId) ? 2 : 3;
            ArrayList<KeyframeChannel<ItemStack>> inputs = new ArrayList<KeyframeChannel<ItemStack>>();
            KeyframeChannel<ItemStack> result = null;
            for (GuiSlotSchema.Slot slot : schema.getCraftingSlots()) {
                KeyframeChannel<ItemStack> channel = this.clip.getGuiSlot(guiId, slot.id());
                if ("craft_result".equals(slot.id())) {
                    result = channel;
                    continue;
                }
                if (channel == null) continue;
                inputs.add(channel);
            }
            KeyframeChannel<Boolean> craftingAnchor = this.clip.getCraftingSlotAnchor(guiId);
            this.addGrid(views, inputs, craftingAnchor, columns, 2, 2);
            int craftingRows = Math.max(1, (inputs.size() + columns - 1) / columns);
            int step = 25;
            int gridRight = 2 + columns * step;
            int resultX = gridRight + 22;
            int resultY = 2 + Math.max(0, (craftingRows - 1) * step / 2);
            this.poseIconX = gridRight + 2;
            this.poseIconY = resultY + 3;
            if (result != null) {
                views.add(new SlotView(result, craftingAnchor, resultX, resultY));
            }
        } else {
            this.addGrid(views, schema.groupedSlots ? this.clip.getGroupedGuiSlots(guiId) : this.clip.getGuiSlots(guiId), this.clip.getPrimarySlotAnchor(guiId), Math.max(1, schema.groupColumns), 2, 2);
        }
        this.slots = List.copyOf(views);
        this.editTick = editTick;
        this.selectedSlotIndex = Math.min(preferredSelection, Math.max(0, this.slots.size() - 1));
        this.contentWidth = 0;
        this.contentHeight = 0;
        for (SlotView slot : this.slots) {
            this.contentWidth = Math.max(this.contentWidth, slot.x() + 23);
            this.contentHeight = Math.max(this.contentHeight, slot.y() + 23);
        }
        this.h(this.contentHeight + 8 + 54);
        this.updateControlValues();
        this.resize();
    }

    public void setEditTick(float editTick) {
        if (Math.abs(this.editTick - editTick) > 1.0E-4f) {
            this.editTick = editTick;
            this.updateControlValues();
        }
    }

    private void addGrid(List<SlotView> views, List<KeyframeChannel<ItemStack>> channels, KeyframeChannel<Boolean> anchor, int columns, int x, int y) {
        int step = 25;
        for (int i = 0; i < channels.size(); ++i) {
            if (channels.get(i) == null) continue;
            views.add(new SlotView(channels.get(i), anchor, x + i % columns * step, y + i / columns * step));
        }
    }

    private KeyframeChannel<ItemStack> getSelectedChannel() {
        return this.selectedSlotIndex >= 0 && this.selectedSlotIndex < this.slots.size() ? this.slots.get(this.selectedSlotIndex).channel() : null;
    }

    public ItemStack getCurrentSlotStack() {
        KeyframeChannel<ItemStack> ch = this.getSelectedChannel();
        if (ch == null || ch.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return (ItemStack)ch.interpolate(this.editTick, ItemStack.EMPTY);
    }

    public void updateCurrentSlotStack(ItemStack stack) {
        SlotView selected;
        KeyframeChannel<ItemStack> ch = this.getSelectedChannel();
        if (ch == null) {
            return;
        }
        Consumer<KeyframeChannel<ItemStack>> mutator = channel -> {
            ItemStack value;
            Keyframe<ItemStack> existing = null;
            for (Keyframe<ItemStack> keyframe : channel.getKeyframes()) {
                if (!(Math.abs(keyframe.getTick() - this.editTick) < 1.0E-4f)) continue;
                existing = keyframe;
                break;
            }
            ItemStack itemStack = value = stack == null ? ItemStack.EMPTY : stack.copy();
            if (existing == null) {
                channel.insert(this.editTick, value);
            } else {
                existing.setValue(value);
            }
        };
        if (this.editor != null && this.editor.getClip() != null) {
            try {
                this.editor.editMultiple(ch, mutator);
            }
            catch (Throwable t) {
                mutator.accept(ch);
            }
        } else {
            mutator.accept(ch);
        }
        selected = this.selectedSlotIndex >= 0 && this.selectedSlotIndex < this.slots.size() ? this.slots.get(this.selectedSlotIndex) : null;
        if (selected != null && selected.anchor() != null) {
            Consumer<KeyframeChannel<Boolean>> anchorMutator = anchor -> {
                for (Keyframe<Boolean> keyframe : anchor.getKeyframes()) {
                    if (!(Math.abs(keyframe.getTick() - this.editTick) < 1.0E-4f)) continue;
                    keyframe.setValue(true);
                    return;
                }
                anchor.insert(this.editTick, true);
            };
            if (this.editor != null && this.editor.getClip() != null) {
                try {
                    this.editor.editMultiple(selected.anchor(), anchorMutator);
                }
                catch (Throwable t) {
                    anchorMutator.accept(selected.anchor());
                }
            } else {
                anchorMutator.accept(selected.anchor());
            }
        }
        this.updateControlValues();
    }

    public void updateControlValues() {
        ItemStack stack = this.getCurrentSlotStack();
        this.itemWidget.setStack(stack);
        this.countTrackpad.setValue(stack.isEmpty() ? 1.0 : (double)Math.max(1, stack.getCount()));
        this.countTrackpad.setEnabled(!stack.isEmpty());
        int controlWidth = Math.max(120, this.contentWidth - 4);
        int itemY = this.contentHeight + 8;
        int countY = itemY + 23 + 4;
        this.itemWidget.relative((UIElement)this).x(2).y(itemY).w(controlWidth).h(23);
        this.countTrackpad.relative((UIElement)this).x(2).y(countY).w(controlWidth).h(23);
        this.itemWidget.resize();
        this.countTrackpad.resize();
    }

    private int pickerWidth() {
        ItemStack stack = this.getCurrentSlotStack();
        String label = stack == null || stack.isEmpty() ? "Pick an item..." : stack.getName().getString();
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(label);
        return 28 + textWidth + 8;
    }

    public void openItemPicker(UIContext context) {
        ItemStack current = this.getCurrentSlotStack();
        UIUnifiedPickOverlayPanel panel = UIUnifiedPickOverlayPanel.forItem(stack -> this.updateCurrentSlotStack((ItemStack)stack), (ItemStack)current);
        UIOverlay.addOverlay((UIContext)context, (UIOverlayPanel)panel, (float)0.9f, (float)0.5f);
        UIUtils.playClick();
    }

    public void resize() {
        this.h(this.contentHeight + 8 + 54);
        super.resize();
        int controlWidth = Math.max(120, this.contentWidth - 4);
        int itemY = this.contentHeight + 8;
        int countY = itemY + 23 + 4;
        this.itemWidget.relative((UIElement)this).x(2).y(itemY).w(controlWidth).h(23);
        this.countTrackpad.relative((UIElement)this).x(2).y(countY).w(controlWidth).h(23);
        this.itemWidget.resize();
        this.countTrackpad.resize();
    }

    protected boolean subMouseClicked(UIContext context) {
        if (context.mouseButton == 0 || context.mouseButton == 1) {
            for (int i = 0; i < this.slots.size(); ++i) {
                SlotView slot = this.slots.get(i);
                int sx = this.area.x + slot.x();
                int sy = this.area.y + slot.y();
                if (context.mouseX < sx || context.mouseX > sx + 23 || context.mouseY < sy || context.mouseY > sy + 23) continue;
                this.onSlotClicked(i, context);
                return true;
            }
        }
        return super.subMouseClicked(context);
    }

    private void onSlotClicked(int index, UIContext context) {
        this.selectedSlotIndex = index;
        this.updateControlValues();
        long now = System.currentTimeMillis();
        if (context.mouseButton == 1) {
            this.updateCurrentSlotStack(ItemStack.EMPTY);
        } else if (index == this.lastClickedSlotKey && now - this.lastClickTime < 350L) {
            this.openItemPicker(context);
        }
        this.lastClickTime = now;
        this.lastClickedSlotKey = index;
    }

    public void render(UIContext context) {
        for (int i = 0; i < this.slots.size(); ++i) {
            SlotView slot = this.slots.get(i);
            int sx = this.area.x + slot.x();
            int sy = this.area.y + slot.y();
            boolean isSel = this.selectedSlotIndex == i;
            boolean isHover = context.mouseX >= sx && context.mouseX <= sx + 23 && context.mouseY >= sy && context.mouseY <= sy + 23;
            this.renderSlotBox(context, sx, sy, slot.channel(), isSel, isHover);
        }
        if (this.poseIconX >= 0) {
            context.batcher.icon(Icons.ARROW_RIGHT, -1, (float)(this.area.x + this.poseIconX), (float)(this.area.y + this.poseIconY));
        }
        super.render(context);
    }

    private void renderSlotBox(UIContext context, int x, int y, KeyframeChannel<ItemStack> channel, boolean selected, boolean hover) {
        ItemStack stack;
        int bg;
        int n = bg = hover ? -2008791996 : -2011028958;
        int outline = selected ? -256 : (hover ? -1 : 0x44FFFFFF);
        context.batcher.box((float)x, (float)y, (float)(x + 23), (float)(y + 23), bg);
        context.batcher.outline((float)x, (float)y, (float)(x + 23), (float)(y + 23), outline);
        if (channel != null && !channel.isEmpty() && (stack = (ItemStack)channel.interpolate(this.editTick, ItemStack.EMPTY)) != null && !stack.isEmpty()) {
            try {
                int offset = 3;
                context.batcher.getContext().drawItem(stack, x + offset, y + offset);
                context.batcher.getContext().drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x + offset, y + offset);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    public static enum Mode {
        GUI,
        CRAFTING;

    }

    private record SlotView(KeyframeChannel<ItemStack> channel, KeyframeChannel<Boolean> anchor, int x, int y) {
    }
}

