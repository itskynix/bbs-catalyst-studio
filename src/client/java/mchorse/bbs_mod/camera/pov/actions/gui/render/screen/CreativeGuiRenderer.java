/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.Element
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.widget.ClickableWidget
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.search.SearchManager
 *  net.minecraft.client.search.SearchProvider
 *  net.minecraft.item.ItemGroup
 *  net.minecraft.item.ItemGroup$Type
 *  net.minecraft.item.ItemGroups
 *  net.minecraft.item.ItemStack
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.CreativeInventoryTabs;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiSlotSchema;
import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSlotDragPreview;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.LiveGuiPreviewRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiEntityPreviewRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiEquipmentRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiItemRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiTextRenderer;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.search.SearchManager;
import net.minecraft.client.search.SearchProvider;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class CreativeGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final CreativeGuiRenderer INSTANCE = new CreativeGuiRenderer();
    private static String cachedSearchQuery = null;
    private static List<ItemStack> cachedSearchResults = List.of();

    private CreativeGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawBackground(GuiRenderContext ctx) {
        ItemGroup group = CreativeGuiRenderer.getSelectedGroup(ctx.clip, ctx.localTick);
        int page = MathHelper.clamp((int)((Integer)ctx.clip.creativePage.interpolate(ctx.localTick, 0)), (int)0, (int)CreativeInventoryTabs.maxPage());
        CreativeGuiRenderer.drawTabBackgrounds(ctx.batcher, group, page, false);
        ctx.batcher.flush();
        Object tabName = "tab_items.png";
        if (group != null) {
            if (group.getType() == ItemGroup.Type.INVENTORY) {
                tabName = "tab_inventory.png";
            } else if (group.getType() == ItemGroup.Type.SEARCH) {
                tabName = "tab_item_search.png";
            } else {
                String raw = group.getTexture();
                if (raw != null && !raw.isEmpty()) {
                    Object object = tabName = raw.startsWith("tab_") ? raw : "tab_" + raw;
                }
            }
        }
        if (!((String)tabName).endsWith(".png")) {
            tabName = (String)tabName + ".png";
        }
        Identifier texture = new Identifier("minecraft", "textures/gui/container/creative_inventory/" + (String)tabName);
        ctx.batcher.getContext().drawTexture(texture, 0, 0, 0.0f, 0.0f, 195, 136, 256, 256);
        ctx.batcher.getContext().draw();
        CreativeGuiRenderer.drawTabBackgrounds(ctx.batcher, group, page, true);
        ctx.batcher.getContext().draw();
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        CreativeGuiRenderer.drawPaginationButtons(ctx.batcher, ctx.clip, ctx.localTick, page, cursorX, cursorY);
        ctx.batcher.getContext().draw();
    }

    @Override
    public ItemStack renderItems(GuiRenderContext ctx) {
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        return CreativeGuiRenderer.drawInventory(ctx.batcher, ctx.clip, ctx.replayKeyframes, ctx.localTick, ctx.globalTick, cursorX, cursorY, ctx.hover, ctx.cursorHasItem);
    }

    @Override
    public void drawPreview(GuiRenderContext ctx) {
        boolean creativeSurvival;
        ItemGroup group = CreativeGuiRenderer.getSelectedGroup(ctx.clip, ctx.localTick);
        boolean bl = creativeSurvival = group != null && group.getType() == ItemGroup.Type.INVENTORY;
        if (creativeSurvival) {
            GuiEntityPreviewRenderer.drawInventoryPreview(ctx, true);
        }
    }

    public static ItemGroup getSelectedGroup(GuiPovActionClip clip, float localTick) {
        GuiItemRenderer.ensureItemGroupsPopulated();
        List<ItemGroup> groups = CreativeInventoryTabs.groups();
        if (groups.isEmpty()) {
            return null;
        }
        int index = MathHelper.clamp((int)((Integer)clip.creativeTab.interpolate(localTick, 0)), (int)0, (int)(groups.size() - 1));
        return groups.get(index);
    }

    private static void drawPaginationButtons(Batcher2D batcher, GuiPovActionClip clip, float localTick, int page, float cursorX, float cursorY) {
        int maxPage = CreativeInventoryTabs.maxPage();
        if (maxPage <= 0) {
            return;
        }
        if (CreativeInventoryTabs.isForge()) {
            Element focused;
            boolean prevHover = GuiTextRenderer.inBounds(cursorX, cursorY, 0, -50, 20, 20);
            int prevTextColor = prevHover ? -96 : -1;
            batcher.getContext().drawGuiTexture(prevHover ? new Identifier("widget/button_highlighted") : new Identifier("widget/button"), 0, -50, 20, 20);
            int prevW = MinecraftClient.getInstance().textRenderer.getWidth("<");
            batcher.getContext().drawTextWithShadow(MinecraftClient.getInstance().textRenderer, (Text)Text.literal((String)"<"), 0 + (20 - prevW) / 2, -44, prevTextColor);
            boolean nextHover = GuiTextRenderer.inBounds(cursorX, cursorY, 175, -50, 20, 20);
            int nextTextColor = nextHover ? -96 : -1;
            batcher.getContext().drawGuiTexture(nextHover ? new Identifier("widget/button_highlighted") : new Identifier("widget/button"), 175, -50, 20, 20);
            int nextW = MinecraftClient.getInstance().textRenderer.getWidth(">");
            batcher.getContext().drawTextWithShadow(MinecraftClient.getInstance().textRenderer, (Text)Text.literal((String)">"), 175 + (20 - nextW) / 2, -44, nextTextColor);
            String pageStr = page + 1 + " / " + (maxPage + 1);
            int pageW = MinecraftClient.getInstance().textRenderer.getWidth(pageStr);
            batcher.getContext().drawTextWithShadow(MinecraftClient.getInstance().textRenderer, (Text)Text.literal((String)pageStr), 97 - pageW / 2, -44, -1);
            boolean focusPrev = false;
            boolean focusNext = false;
            if (LiveGuiPreviewRenderer.isRenderingLive() && MinecraftClient.getInstance().currentScreen != null && (focused = MinecraftClient.getInstance().currentScreen.getFocused()) instanceof ClickableWidget) {
                String msg;
                ClickableWidget cw = (ClickableWidget)focused;
                String string = msg = cw.getMessage() != null ? cw.getMessage().getString() : "";
                if ("<".equals(msg)) {
                    focusPrev = true;
                } else if (">".equals(msg)) {
                    focusNext = true;
                }
            }
            if (focusPrev) {
                batcher.box(0.0f, -50.0f, 20.0f, -49.0f, -1);
                batcher.box(0.0f, -31.0f, 20.0f, -30.0f, -1);
                batcher.box(0.0f, -49.0f, 1.0f, -31.0f, -1);
                batcher.box(19.0f, -49.0f, 20.0f, -31.0f, -1);
            } else if (focusNext) {
                batcher.box(175.0f, -50.0f, 195.0f, -49.0f, -1);
                batcher.box(175.0f, -31.0f, 195.0f, -30.0f, -1);
                batcher.box(175.0f, -49.0f, 176.0f, -31.0f, -1);
                batcher.box(194.0f, -49.0f, 195.0f, -31.0f, -1);
            }
        } else {
            Identifier buttons = Identifier.of((String)"fabric", (String)"textures/gui/creative_buttons.png");
            int prevU = page > 0 && GuiTextRenderer.inBounds(cursorX, cursorY, 170, 4, 11, 12) ? 22 : 0;
            int prevV = page > 0 ? 0 : 12;
            batcher.getContext().drawTexture(buttons, 170, 4, (float)prevU, (float)prevV, 11, 12, 256, 256);
            int nextU = 11 + (page < maxPage && GuiTextRenderer.inBounds(cursorX, cursorY, 181, 4, 11, 12) ? 22 : 0);
            int nextV = page < maxPage ? 0 : 12;
            batcher.getContext().drawTexture(buttons, 181, 4, (float)nextU, (float)nextV, 11, 12, 256, 256);
        }
    }

    private static ItemStack drawInventory(Batcher2D batcher, GuiPovActionClip clip, ReplayKeyframes replayKeyframes, float localTick, float globalTick, float cursorX, float cursorY, GuiPointerHover hover, boolean cursorHasItem) {
        int i;
        String query;
        String search;
        List list;
        Collection source;
        ItemGroup selected = CreativeGuiRenderer.getSelectedGroup(clip, localTick);
        if (selected == null) {
            return null;
        }
        List<ItemGroup> groups = CreativeInventoryTabs.groups();
        ItemStack hovered = null;
        Text hoverText = null;
        int page = MathHelper.clamp((int)((Integer)clip.creativePage.interpolate(localTick, 0)), (int)0, (int)CreativeInventoryTabs.maxPage());
        CreativeGuiRenderer.drawTabIcons(batcher, page);
        for (ItemGroup group : groups) {
            int hoverY;
            if (!CreativeInventoryTabs.visibleOnPage(group, page)) continue;
            boolean top = CreativeInventoryTabs.isTop(group, page);
            int column = CreativeInventoryTabs.getColumn(group, page);
            int tabX = CreativeInventoryTabs.isSpecial(group, page) ? 195 - 27 * (7 - column) + 1 : 27 * column;
            int n = hoverY = top ? -32 : 136;
            if (!(cursorX >= (float)tabX) || !(cursorX <= (float)(tabX + 26)) || !(cursorY >= (float)hoverY) || !(cursorY <= (float)(hoverY + 32))) continue;
            hoverText = group.getDisplayName();
        }
        int maxPage = CreativeInventoryTabs.maxPage();
        if (maxPage > 0 && !CreativeInventoryTabs.isForge() && cursorX >= 170.0f && cursorX <= 192.0f && cursorY >= 4.0f && cursorY <= 16.0f) {
            hoverText = Text.translatable((String)"fabric.gui.creativeTabPage", (Object[])new Object[]{page + 1, maxPage + 1});
        }
        source = selected.getType() == ItemGroup.Type.SEARCH ? selected.getSearchTabStacks() : selected.getDisplayStacks();
        if (source instanceof List) {
            List list2 = (List)source;
            list = list2;
        } else {
            list = new ArrayList(source);
        }
        List<ItemStack> items = list;
        String string = search = clip.creativeSearch.isEmpty() ? "" : (String)clip.creativeSearch.interpolate(localTick, "");
        if (search == null) {
            search = "";
        }
        if (!(query = search.trim()).isEmpty() && selected.getType() == ItemGroup.Type.SEARCH) {
            items = CreativeGuiRenderer.searchItems(query);
        }
        if (selected.getType() == ItemGroup.Type.SEARCH) {
            boolean searchHover = GuiTextRenderer.inBounds(cursorX, cursorY, 82, 6, 80, 9);
            boolean focused = GuiTextRenderer.isSearchFocused(clip.creativeSearchFocus, localTick, search, searchHover);
            GuiTextRenderer.drawSearchField(batcher, search, 82, 6, 80, 9, focused, false, 1.0f, GuiTextRenderer.sampleInt(clip.creativeSearchSelStart, localTick, search.length()), GuiTextRenderer.sampleInt(clip.creativeSearchSelEnd, localTick, search.length()));
        }
        RecordedHudData hudData = null;
        if (replayKeyframes instanceof ReplayKeyframesPovAccess) {
            ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
            hudData = access.bbsPov$getHud();
        }
        Set<String> dragKeys = GuiSlotRenderer.dragPreviewKeys(clip, "creative_inventory", localTick);
        String encoded = GuiSlotRenderer.dragPreviewEncoded(clip, "creative_inventory", localTick);
        ItemStack paint = GuiSlotRenderer.dragPaintItem(clip, hudData, globalTick, "creative_inventory", localTick, encoded, dragKeys);
        if (selected.getType() != ItemGroup.Type.INVENTORY) {
            int rows = Math.max(5, (items.size() + 8) / 9);
            int maxFirstRow = Math.max(0, rows - 5);
            int firstRow = MathHelper.clamp((int)((Integer)clip.creativeRow.interpolate(localTick, 0)), (int)0, (int)maxFirstRow);
            int firstItem = firstRow * 9;
            for (int i2 = 0; i2 < 45 && firstItem + i2 < items.size(); ++i2) {
                int x = 9 + i2 % 9 * 18;
                int y = 18 + i2 / 9 * 18;
                ItemStack stack = (ItemStack)items.get(firstItem + i2);
                GuiSlotRenderer.drawSlotItem(batcher, stack, x, y);
                if (!(cursorX >= (float)x) || !(cursorX <= (float)(x + 16)) || !(cursorY >= (float)y) || !(cursorY <= (float)(y + 16))) continue;
                GuiSlotRenderer.drawSlotHighlight(batcher, x, y);
                hovered = stack;
                hover.itemGroup = false;
            }
            if (selected.hasScrollbar()) {
                float scroll = maxFirstRow == 0 ? 0.0f : (float)firstRow / (float)maxFirstRow;
                int scrollY = 18 + Math.round(scroll * 95.0f);
                batcher.getContext().drawGuiTexture(new Identifier("container/creative_inventory/scroller"), 175, scrollY, 12, 15);
            }
        } else {
            GuiEquipmentRenderer.renderEmptyEquipmentSlots(batcher, clip, "creative_inventory", localTick);
            for (GuiSlotSchema.Slot slot : GuiSlotSchema.get((String)"creative_inventory").slots) {
                ItemStack stack = GuiSlotRenderer.processSlot(batcher, clip.getGuiSlot("creative_inventory", slot.id()), localTick, slot.x(), slot.y(), cursorX, cursorY, dragKeys.contains(slot.id()));
                if (stack == null) continue;
                hovered = stack;
                hover.itemGroup = false;
            }
            for (i = 0; i < 27; ++i) {
                int x = 9 + i % 9 * 18;
                int y = 54 + i / 9 * 18;
                ItemStack stack = ItemStack.EMPTY;
                if (hudData != null && i < hudData.inventory.size()) {
                    KeyframeChannel<ItemStack> ch = hudData.inventory.get(i);
                    stack = ch != null && !ch.isEmpty() ? (ItemStack)ch.interpolate(globalTick, ItemStack.EMPTY) : ItemStack.EMPTY;
                } else if (LiveGuiPreviewRenderer.isRenderingLive() && MinecraftClient.getInstance().player != null) {
                    stack = (ItemStack)MinecraftClient.getInstance().player.getInventory().main.get(9 + i);
                }
                boolean isHover = cursorX >= (float)x && cursorX <= (float)(x + 16) && cursorY >= (float)y && cursorY <= (float)(y + 16);
                boolean preview = dragKeys.contains("inv_" + i);
                if (preview && paint != null && !paint.isEmpty()) {
                    int existing = stack == null || stack.isEmpty() ? 0 : stack.getCount();
                    stack = paint.copyWithCount(GuiSlotDragPreview.previewCount(GuiSlotDragPreview.decodeButton(encoded), GuiSlotDragPreview.decodeOriginalCount(encoded), dragKeys.size(), paint.getMaxCount(), existing));
                }
                ItemStack shown = GuiSlotRenderer.drawSlotContents(batcher, stack, x, y, preview, isHover);
                if (!isHover || shown == null || shown.isEmpty()) continue;
                hovered = shown;
                hover.itemGroup = true;
            }
        }
        for (i = 0; i < 9; ++i) {
            int x = 9 + i * 18;
            int y = 112;
            ItemStack stack = ItemStack.EMPTY;
            if (replayKeyframes != null) {
                KeyframeChannel ch = (KeyframeChannel)replayKeyframes.hotbar.get(i);
                stack = ch != null && !ch.isEmpty() ? (ItemStack)ch.interpolate(globalTick, ItemStack.EMPTY) : ItemStack.EMPTY;
            } else if (LiveGuiPreviewRenderer.isRenderingLive() && MinecraftClient.getInstance().player != null) {
                stack = MinecraftClient.getInstance().player.getInventory().getStack(i);
            }
            boolean preview = dragKeys.contains("hotbar_" + i);
            if (preview && paint != null && !paint.isEmpty()) {
                int existing = stack == null || stack.isEmpty() ? 0 : stack.getCount();
                stack = paint.copyWithCount(GuiSlotDragPreview.previewCount(GuiSlotDragPreview.decodeButton(encoded), GuiSlotDragPreview.decodeOriginalCount(encoded), dragKeys.size(), paint.getMaxCount(), existing));
            }
            boolean isHover = cursorX >= (float)x && cursorX <= (float)(x + 16) && cursorY >= (float)y && cursorY <= (float)(y + 16);
            ItemStack shown = GuiSlotRenderer.drawSlotContents(batcher, stack, x, y, preview, isHover);
            if (!isHover || shown == null || shown.isEmpty()) continue;
            hovered = shown;
            hover.itemGroup = true;
        }
        if (selected.shouldRenderName()) {
            batcher.text(selected.getDisplayName().getString(), 8.0f, 6.0f, -12566464, false);
        }
        if (hoverText != null && !cursorHasItem) {
            batcher.flush();
            batcher.getContext().drawTooltip(MinecraftClient.getInstance().textRenderer, (Text)hoverText, Math.round(cursorX), Math.round(cursorY));
        }
        return hovered;
    }

    private static void drawTabBackgrounds(Batcher2D batcher, ItemGroup selected, int page, boolean selectedOnly) {
        for (ItemGroup group : CreativeInventoryTabs.groups()) {
            boolean active;
            boolean bl = active = group == selected;
            if (!CreativeInventoryTabs.visibleOnPage(group, page) || active != selectedOnly) continue;
            boolean top = CreativeInventoryTabs.isTop(group, page);
            int column = CreativeInventoryTabs.getColumn(group, page);
            int tabX = CreativeInventoryTabs.isSpecial(group, page) ? 195 - 27 * (7 - column) + 1 : 27 * column;
            int tabY = top ? -28 : 132;
            String sprite = "container/creative_inventory/tab_" + (top ? "top_" : "bottom_") + (active ? "selected_" : "unselected_") + (column + 1);
            batcher.getContext().drawGuiTexture(new Identifier(sprite), tabX, tabY, 26, 32);
        }
    }

    private static void drawTabIcons(Batcher2D batcher, int page) {
        DiffuseLighting.enableGuiDepthLighting();
        for (ItemGroup group : CreativeInventoryTabs.groups()) {
            if (!CreativeInventoryTabs.visibleOnPage(group, page)) continue;
            boolean top = CreativeInventoryTabs.isTop(group, page);
            int column = CreativeInventoryTabs.getColumn(group, page);
            int tabX = CreativeInventoryTabs.isSpecial(group, page) ? 195 - 27 * (7 - column) + 1 : 27 * column;
            int tabY = top ? -28 : 132;
            batcher.getContext().getMatrices().push();
            batcher.getContext().getMatrices().translate(0.0f, 0.0f, 100.0f);
            batcher.getContext().drawItem(group.getIcon(), tabX + 5, tabY + 8 + (top ? 1 : -1));
            batcher.getContext().getMatrices().pop();
        }
        batcher.getContext().draw();
        DiffuseLighting.disableGuiDepthLighting();
    }

    private static List<ItemStack> searchItems(String query) {
        if (query != null && query.equals(cachedSearchQuery)) {
            return cachedSearchResults;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        String lowered = (query == null ? "" : query).toLowerCase(Locale.ROOT);
        List<ItemStack> results = null;
        try {
            if (lowered.startsWith("#")) {
                SearchProvider<ItemStack> tags = client.getSearchProvider(SearchManager.ITEM_TAG);
                results = tags == null ? List.of() : new ArrayList<ItemStack>(tags.findAll(lowered.substring(1)));
            } else {
                SearchProvider<ItemStack> tooltips = client.getSearchProvider(SearchManager.ITEM_TOOLTIP);
                if (tooltips != null) {
                    results = new ArrayList<ItemStack>(tooltips.findAll(lowered));
                }
            }
        }
        catch (Exception tooltips) {
            // empty catch block
        }
        if (results == null) {
            ArrayList<ItemStack> fallback = new ArrayList<ItemStack>();
            GuiItemRenderer.ensureItemGroupsPopulated();
            ItemGroup searchTab = ItemGroups.getSearchGroup();
            Collection<ItemStack> source = searchTab == null ? List.of() : searchTab.getSearchTabStacks();
            for (ItemStack stack : source) {
                if (stack == null || stack.isEmpty() || !CreativeGuiRenderer.itemMatchesQuery(stack, lowered)) continue;
                fallback.add(stack);
            }
            results = fallback;
        }
        cachedSearchQuery = query;
        cachedSearchResults = results;
        return results;
    }

    private static boolean itemMatchesQuery(ItemStack stack, String query) {
        if (stack.getName().getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (stack.getItem().toString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        try {
            for (Text line : Screen.getTooltipFromItem((MinecraftClient)MinecraftClient.getInstance(), (ItemStack)stack)) {
                if (!line.getString().toLowerCase(Locale.ROOT).contains(query)) continue;
                return true;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return false;
    }
}

