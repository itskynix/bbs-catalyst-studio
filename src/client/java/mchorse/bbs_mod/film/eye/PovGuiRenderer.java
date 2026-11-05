package mchorse.bbs_mod.film.eye;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.film.pov.PovClip;
import mchorse.bbs_mod.film.pov.PovGuiClip;
import mchorse.bbs_mod.film.pov.PovSlotSet;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.mixin.client.PovDeathScreenAccessor;
import mchorse.bbs_mod.mixin.client.PovHandledScreenAccessor;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.BlastFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.BrewingStandScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.FurnaceScreen;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.GrindstoneScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HopperScreen;
import net.minecraft.client.gui.screen.ingame.LoomScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.client.gui.screen.ingame.SmokerScreen;
import net.minecraft.client.gui.screen.ingame.StonecutterScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.BlastFurnaceScreenHandler;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.CartographyTableScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The interfaces of the POV interface clip, drawn into the film's frame.
 *
 * <p>The screens whose look is their slots and a bar or two are the game's own, over a screen
 * handler and an inventory of their own (never the player's), so nothing the film shows can move a
 * real item; their slots and progress are set from the clip every frame, so a scrub lands on the
 * frame's items at once. The screens that carry state of the player's own (the inventory's and the
 * crafting table's recipe book, the creative tabs, the beacon's powers) and the book are drawn here
 * instead, from the clip's channels alone. The menus are the game's menus, and the bed's is its
 * button over a fall into black.</p>
 *
 * <p>Each screen is placed by the clip's layout (moved, scaled and turned about the middle of the
 * frame), the cursor is moved into the screen's own space with it, and what lies under the cursor
 * gets its tooltip; the tooltip waits for the HUD to draw the cursor and what it carries, and goes
 * over them, the topmost of all. As in the game, a cursor carrying an item shows no tooltip.</p>
 */
public final class PovGuiRenderer
{
    private static final Identifier INVENTORY = Ids.of("minecraft", "textures/gui/container/inventory.png");
    private static final Identifier CRAFTING = Ids.of("minecraft", "textures/gui/container/crafting_table.png");
    private static final Identifier BEACON = Ids.of("minecraft", "textures/gui/container/beacon.png");
    private static final Identifier BOOK = Ids.of("minecraft", "textures/gui/book.png");
    private static final Identifier RECIPE_BOOK = Ids.of("minecraft", "textures/gui/recipe_book.png");

    /** The beacon's powers by the pyramid level they need; the regeneration is the secondary's */
    private static final StatusEffect[][] BEACON_POWERS = {
        {StatusEffects.SPEED, StatusEffects.HASTE},
        {StatusEffects.RESISTANCE, StatusEffects.JUMP_BOOST},
        {StatusEffects.STRENGTH}
    };

    private static final int SLOT_HIGHLIGHT = 0x80FFFFFF;

    /** The game's screens made for the clips, kept while the clips are, remade when the type or the size change */
    private static final Map<PovGuiClip, Holder> SCREENS = new WeakHashMap<>();

    /* What the mixins read: that a screen is being drawn here, so the game's own background stays out */
    private static boolean drawing;

    /* The frame's actor (drawn in the inventories' boxes) and whether the cursor carries an item, set by the HUD */
    private static IEntity actor;
    private static boolean carrying;
    /* The tooltip waiting for the cursor to be drawn */
    private static Tooltip pending;

    /* Where the frame's screens answer a click in the film editor's preview, and the screen being drawn's layout and size */
    private static final List<Hotspot> HOTSPOTS = new ArrayList<>();
    private static Transform frameLayout;
    private static int frameW;
    private static int frameH;

    private PovGuiRenderer()
    {}

    public static boolean isDrawing()
    {
        return drawing;
    }

    private static int alpha(int color, float factor)
    {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0F, Math.min(1F, factor)));

        return (a << 24) | (color & 0xFFFFFF);
    }

    private static Identifier sprite(String path)
    {
        return Ids.of("minecraft", path);
    }

    /** Before the frame's screens: whose screens they are, and whether the cursor carries an item (which keeps the tooltips away) */
    public static void beginFrame(IEntity entity, boolean carried)
    {
        actor = entity;
        carrying = carried;
        pending = null;
    }

    /** A new frame: nothing of the last one's screens can be clicked any more */
    public static void clearHotspots()
    {
        HOTSPOTS.clear();
    }

    /**
     * A click in the film editor's preview, at a place of the frame from 0 to 1 across and down:
     * on the arrows of the recipe book or of the creative tabs' pages, or on a creative tab, it
     * keys the page or the tab on the clip from the clip's tick of the frame on, as clicking it in
     * the game would turn to it. Whether a hotspot took the click.
     */
    public static boolean click(float x, float y)
    {
        for (int i = HOTSPOTS.size() - 1; i >= 0; i--)
        {
            Hotspot hotspot = HOTSPOTS.get(i);
            Vector2f point = toLayout(hotspot.layout(), hotspot.w(), hotspot.h(), (int) (x * hotspot.w()), (int) (y * hotspot.h()));

            if (over((int) point.x, (int) point.y, hotspot.x(), hotspot.y(), hotspot.width(), hotspot.height()))
            {
                hotspot.action().run();

                return true;
            }
        }

        return false;
    }

    /** A box of the screen being drawn that answers a click */
    private static void hotspot(int x, int y, int width, int height, Runnable action)
    {
        HOTSPOTS.add(new Hotspot(frameLayout, frameW, frameH, x, y, width, height, action));
    }

    /**
     * A click's edit: the channel keyed to a value from the clip's tick on and held there, with
     * the value it had kept until then. One undo step.
     */
    private static <T> void keyFrom(KeyframeChannel<T> channel, float rel, T value, T before)
    {
        int tick = Math.max(0, (int) rel);

        BaseValue.edit(channel, (c) ->
        {
            if (c.isEmpty() && tick > 0)
            {
                hold(c, c.insert(0F, before));
            }

            int index = c.insert(tick, value);

            hold(c, index);
            hold(c, index - 1);
        });
    }

    private static void hold(KeyframeChannel<?> channel, int index)
    {
        Keyframe<?> keyframe = index < 0 ? null : channel.get(index);

        if (keyframe != null)
        {
            keyframe.getInterpolation().setInterp(Interpolations.CONST);
        }
    }

    /**
     * After the cursor: the tooltip of what the cursor is over, in the layout of its screen, over
     * the cursor and its item. Ends the frame.
     */
    public static void renderTooltip(DrawContext context)
    {
        Tooltip tooltip = pending;

        pending = null;
        actor = null;

        if (tooltip == null)
        {
            return;
        }

        MatrixStack matrices = context.getMatrices();

        context.draw();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        matrices.push();
        applyLayout(matrices, tooltip.layout(), tooltip.w(), tooltip.h());
        RenderSystem.setShaderColor(1F, 1F, 1F, tooltip.opacity());

        try
        {
            context.drawItemTooltip(MinecraftClient.getInstance().textRenderer, tooltip.stack(), tooltip.x(), tooltip.y());
            context.draw();
        }
        finally
        {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            matrices.pop();
        }
    }

    public static void render(DrawContext context, Batcher2D batcher, PovGuiClip clip, float rel, float factor, Replay replay, float tick, int w, int h, int mouseX, int mouseY)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        String type = clip.screen.get();
        float opacity = (float) PovClip.value(clip.opacity, rel, 1D) * factor;

        if (opacity <= 0F || mc.player == null)
        {
            return;
        }

        /* Over the interface and the hands, as the game puts a screen: what was drawn of them (the
         * hotbar's items stand out of the frame) must not show through the window */
        context.draw();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        /* Behind the screen, over the whole frame whatever the layout: the dimmed world, the death's red */
        float darkness = (float) PovClip.value(clip.darkness, rel, 1D) * factor;

        if (type.equals("death"))
        {
            float red = (float) PovClip.value(clip.redBackground, rel, 1D) * factor;

            context.fillGradient(0, 0, w, h, alpha(0x60500000, red), alpha(0xA0803030, red));
        }
        else if (!type.equals("sleep") && darkness > 0F)
        {
            context.fillGradient(0, 0, w, h, alpha(0xC0101010, darkness), alpha(0xD0101010, darkness));
        }

        Transform layout = PovClip.keyed(clip.layout, rel, null);
        MatrixStack matrices = context.getMatrices();
        Vector2f mouse = toLayout(layout, w, h, mouseX, mouseY);
        int mx = (int) mouse.x;
        int my = (int) mouse.y;

        context.draw();
        matrices.push();
        applyLayout(matrices, layout, w, h);
        RenderSystem.setShaderColor(1F, 1F, 1F, opacity);
        drawing = true;
        frameLayout = layout;
        frameW = w;
        frameH = h;

        try
        {
            Hover hover = new Hover();

            switch (type)
            {
                case "inventory" -> renderInventory(context, batcher, mc, clip, rel, replay, tick, w, h, mx, my, hover);
                case "crafting" -> renderCrafting(context, batcher, mc, clip, rel, replay, tick, w, h, mx, my, hover);
                case "creative" -> renderCreative(context, batcher, mc, clip, rel, replay, tick, w, h, mx, my, hover);
                case "beacon" -> renderBeacon(context, batcher, mc, clip, rel, replay, tick, w, h, mx, my, hover);
                case "book" -> renderBook(context, batcher, mc, clip, rel, tick, w, h, mx, my);
                case "sleep" -> renderSleep(context, clip, rel, w, h, mx, my);
                default -> renderScreen(context, mc, clip, rel, replay, tick, w, h, mx, my, hover);
            }

            /* The tooltip waits for the cursor; the last screen's wins */
            if (!carrying && hover.stack != null && !hover.stack.isEmpty())
            {
                pending = new Tooltip(hover.stack, layout, w, h, mx, my, opacity);
            }

            context.draw();
        }
        catch (Exception e)
        {
            /* A screen that cannot be drawn outside the game leaves the frame as it was */
            SCREENS.remove(clip);
        }
        finally
        {
            drawing = false;
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            matrices.pop();
        }
    }

    /** The layout: moved from the middle of the frame, turned about it and scaled */
    private static void applyLayout(MatrixStack matrices, Transform layout, int w, int h)
    {
        if (layout == null)
        {
            return;
        }

        matrices.translate(w / 2F + layout.translate.x, h / 2F + layout.translate.y, 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(layout.rotate.z));
        matrices.scale(layout.scale.x, layout.scale.y, 1F);
        matrices.translate(-w / 2F, -h / 2F, 0F);
    }

    /** The cursor in the screen's own space: the layout undone */
    private static Vector2f toLayout(Transform layout, int w, int h, int mouseX, int mouseY)
    {
        if (layout == null)
        {
            return new Vector2f(mouseX, mouseY);
        }

        float x = mouseX - w / 2F - layout.translate.x;
        float y = mouseY - h / 2F - layout.translate.y;
        float cos = (float) Math.cos(-layout.rotate.z);
        float sin = (float) Math.sin(-layout.rotate.z);
        float rx = x * cos - y * sin;
        float ry = x * sin + y * cos;
        float sx = Math.abs(layout.scale.x) < 0.0001F ? 1F : layout.scale.x;
        float sy = Math.abs(layout.scale.y) < 0.0001F ? 1F : layout.scale.y;

        return new Vector2f(rx / sx + w / 2F, ry / sy + h / 2F);
    }

    /* The game's screens */

    private static void renderScreen(DrawContext context, MinecraftClient mc, PovGuiClip clip, float rel, Replay replay, float tick, int w, int h, int mouseX, int mouseY, Hover hover)
    {
        String type = clip.screen.get();
        String message = PovClip.keyed(clip.deathMessage, rel, "");
        Holder holder = SCREENS.get(clip);
        String key = type.equals("death") ? type + ":" + message : type;

        if (holder == null || !holder.key.equals(key) || holder.width != w || holder.height != h)
        {
            PlayerInventory inventory = new PlayerInventory(mc.player);
            Screen screen = switch (type)
            {
                case "game_menu" -> new GameMenuScreen(true);
                case "death" -> new DeathScreen(message.isEmpty() ? null : Text.literal(message), PovClip.flag(replay.pov.hardcore, tick, false));
                default -> container(type, inventory);
            };

            if (screen == null)
            {
                return;
            }

            screen.init(mc, w, h);
            holder = new Holder(key, screen, inventory, w, h);
            SCREENS.put(clip, holder);
        }

        if (holder.screen instanceof HandledScreen<?> handled)
        {
            ScreenHandler handler = handled.getScreenHandler();

            fillSlots(handler, holder.inventory, clip, rel, replay, tick);
            fillProgress(handler, clip, rel, type);
        }
        else if (holder.screen instanceof DeathScreen death)
        {
            /* The replay's score and buttons rather than the local player's: the buttons as the clip
             * keys them, or, unkeyed, as the game has them (greyed out for the first second) */
            PovDeathScreenAccessor accessor = (PovDeathScreenAccessor) death;
            String score = PovClip.keyed(clip.score, rel, "");

            accessor.bbs$setScoreText(Text.translatable("deathScreen.score.value", Text.literal(score.isEmpty() ? "0" : score).formatted(Formatting.YELLOW)));
            accessor.bbs$setButtonsActive(PovClip.flag(clip.buttonsActive, rel, rel >= PovGuiClip.DEATH_BUTTONS_DELAY));
        }

        holder.screen.render(context, mouseX, mouseY, mc.getTickDelta());

        /* The game's screen keeps its slot tooltip to itself (see the mixin); the slot under the
         * cursor gets it with the rest, after the cursor */
        if (holder.screen instanceof HandledScreen<?> handled)
        {
            Slot focused = ((PovHandledScreenAccessor) handled).bbs$getFocusedSlot();

            if (focused != null && focused.hasStack())
            {
                hover.stack = focused.getStack();
            }
        }
    }

    private static HandledScreen<?> container(String type, PlayerInventory inventory)
    {
        return switch (type)
        {
            case "furnace" -> new FurnaceScreen(new FurnaceScreenHandler(0, inventory), inventory, Text.translatable("container.furnace"));
            case "blast_furnace" -> new BlastFurnaceScreen(new BlastFurnaceScreenHandler(0, inventory), inventory, Text.translatable("container.blast_furnace"));
            case "smoker" -> new SmokerScreen(new SmokerScreenHandler(0, inventory), inventory, Text.translatable("container.smoker"));
            case "anvil" -> new AnvilScreen(new AnvilScreenHandler(0, inventory), inventory, Text.translatable("container.repair"));
            case "grindstone" -> new GrindstoneScreen(new GrindstoneScreenHandler(0, inventory), inventory, Text.translatable("container.grindstone_title"));
            case "stonecutter" -> new StonecutterScreen(new StonecutterScreenHandler(0, inventory), inventory, Text.translatable("container.stonecutter"));
            case "smithing" -> new SmithingScreen(new SmithingScreenHandler(0, inventory), inventory, Text.translatable("container.upgrade"));
            case "loom" -> new LoomScreen(new LoomScreenHandler(0, inventory), inventory, Text.translatable("container.loom"));
            case "cartography" -> new CartographyTableScreen(new CartographyTableScreenHandler(0, inventory), inventory, Text.translatable("container.cartography_table"));
            case "enchanting" -> new EnchantmentScreen(new EnchantmentScreenHandler(0, inventory), inventory, Text.translatable("container.enchant"));
            case "brewing" -> new BrewingStandScreen(new BrewingStandScreenHandler(0, inventory), inventory, Text.translatable("container.brewing"));
            case "chest" -> new GenericContainerScreen(GenericContainerScreenHandler.createGeneric9x3(0, inventory), inventory, Text.translatable("container.chest"));
            case "large_chest" -> new GenericContainerScreen(GenericContainerScreenHandler.createGeneric9x6(0, inventory), inventory, Text.translatable("container.chestDouble"));
            case "barrel" -> new GenericContainerScreen(GenericContainerScreenHandler.createGeneric9x3(0, inventory), inventory, Text.translatable("container.barrel"));
            case "shulker_box" -> new ShulkerBoxScreen(new ShulkerBoxScreenHandler(0, inventory), inventory, Text.translatable("container.shulkerBox"));
            case "hopper" -> new HopperScreen(new HopperScreenHandler(0, inventory), inventory, Text.translatable("container.hopper"));
            case "dispenser" -> new Generic3x3ContainerScreen(new Generic3x3ContainerScreenHandler(0, inventory), inventory, Text.translatable("container.dispenser"));
            default -> null;
        };
    }

    /**
     * The slots, every frame: the container's own from its slot group (or, for a clip of the first
     * version, from its one-per-slot channels), the player's part from the replay.
     */
    private static void fillSlots(ScreenHandler handler, PlayerInventory inventory, PovGuiClip clip, float rel, Replay replay, float tick)
    {
        PovSlotSet container = PovClip.keyed(clip.containerSlots, rel, PovSlotSet.EMPTY);
        int own = 0;

        for (int i = 0; i < handler.slots.size(); i++)
        {
            Slot slot = handler.slots.get(i);
            ItemStack stack;

            if (slot.inventory == inventory)
            {
                stack = playerItem(clip, rel, replay, slot.getIndex(), tick);
            }
            else
            {
                KeyframeChannel<ItemStack> legacy = clip.slots.slot(i, false);

                stack = legacy != null && !legacy.isEmpty() ? legacy.interpolate(rel) : container.get(own);
                own += 1;
            }

            slot.setStackNoCallbacks(stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }

    /** The progress bars of the furnaces and the brewing stand, through the handler's own properties */
    private static void fillProgress(ScreenHandler handler, PovGuiClip clip, float rel, String type)
    {
        if (PovGuiClip.SMELTING.contains(type))
        {
            double cook = clamp01(PovClip.value(clip.cookProgress, rel, 0D));
            double burn = clamp01(PovClip.value(clip.burnProgress, rel, 0D));

            handler.setProperty(0, (int) Math.round(burn * 100D));
            handler.setProperty(1, 100);
            handler.setProperty(2, (int) Math.round(cook * 200D));
            handler.setProperty(3, 200);
        }
        else if (type.equals("brewing"))
        {
            double brew = clamp01(PovClip.value(clip.brewProgress, rel, 0D));
            double fuel = clamp01(PovClip.value(clip.brewFuel, rel, 0D));

            handler.setProperty(0, brew > 0D ? (int) Math.round(400D * (1D - brew)) : 0);
            handler.setProperty(1, (int) Math.round(fuel * 20D));
        }
    }

    private static double clamp01(double value)
    {
        return Math.max(0D, Math.min(1D, value));
    }

    /**
     * What the replay has in a slot of the player's inventory, by the inventory's own numbering:
     * the hotbar (0 to 8), the inventory above it (9 to 35), the armor (36 feet to 39 head) and
     * the off hand (40), the armor and the off hand the clip's own where it keys them.
     */
    private static ItemStack playerItem(PovGuiClip clip, float rel, Replay replay, int index, float tick)
    {
        ReplayKeyframes keyframes = replay.keyframes;
        KeyframeChannel<ItemStack> own = switch (index)
        {
            case 36 -> clip.armorFeet;
            case 37 -> clip.armorLegs;
            case 38 -> clip.armorChest;
            case 39 -> clip.armorHead;
            case 40 -> clip.offhand;
            default -> null;
        };

        if (own != null && !own.isEmpty())
        {
            return own.interpolate(rel);
        }

        if (index >= 9 && index < 36)
        {
            return PovClip.keyed(replay.pov.inventorySlots, tick, PovSlotSet.EMPTY).get(index - 9);
        }

        KeyframeChannel<ItemStack> channel = null;

        if (index >= 0 && index < ReplayKeyframes.HOTBAR_SIZE && index < keyframes.hotbar.size()) channel = keyframes.hotbar.get(index);
        else if (index == 36) channel = keyframes.getEquipmentChannel(EquipmentSlot.FEET);
        else if (index == 37) channel = keyframes.getEquipmentChannel(EquipmentSlot.LEGS);
        else if (index == 38) channel = keyframes.getEquipmentChannel(EquipmentSlot.CHEST);
        else if (index == 39) channel = keyframes.getEquipmentChannel(EquipmentSlot.HEAD);
        else if (index == 40) channel = keyframes.offHand;

        return channel == null || channel.isEmpty() ? ItemStack.EMPTY : channel.interpolate(tick);
    }

    /* Drawn here */

    /** A slot's item, lit while the cursor is over it, which also makes it the one with the tooltip */
    private static void drawSlot(DrawContext context, MinecraftClient mc, ItemStack stack, int x, int y, int mouseX, int mouseY, Hover hover)
    {
        if (stack != null && !stack.isEmpty())
        {
            context.drawItem(stack, x, y);
            context.drawItemInSlot(mc.textRenderer, stack, x, y);
        }

        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16)
        {
            /* Over the item, as the game lights a slot */
            context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 16, SLOT_HIGHLIGHT, SLOT_HIGHLIGHT, 0);

            if (stack != null && !stack.isEmpty())
            {
                hover.stack = stack;
            }
        }
    }

    /** The player's inventory above the hotbar and the hotbar, at the screen's places for them */
    private static void drawPlayerSlots(DrawContext context, MinecraftClient mc, PovGuiClip clip, float rel, Replay replay, float tick, int x0, int y0, int top, int hotbarTop, int mouseX, int mouseY, Hover hover)
    {
        for (int i = 0; i < 27; i++)
        {
            drawSlot(context, mc, playerItem(clip, rel, replay, 9 + i, tick), x0 + 8 + (i % 9) * 18, y0 + top + (i / 9) * 18, mouseX, mouseY, hover);
        }

        for (int i = 0; i < 9; i++)
        {
            drawSlot(context, mc, playerItem(clip, rel, replay, i, tick), x0 + 8 + i * 18, y0 + hotbarTop, mouseX, mouseY, hover);
        }
    }

    /** Where a 176 wide screen with a recipe book starts: pushed to the right while the book is open */
    private static int bookScreenLeft(boolean open, int w)
    {
        return open ? 177 + (w - 176 - 200) / 2 : (w - 176) / 2;
    }

    /**
     * The inventory: the crafting grid and its result (the 2x2 group, the result first), the armor,
     * the inventory and hotbar, the off hand, the recipe book's button, and the book when open.
     */
    private static void renderInventory(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovGuiClip clip, float rel, Replay replay, float tick, int w, int h, int mouseX, int mouseY, Hover hover)
    {
        boolean open = PovClip.flag(clip.recipeBook, rel, false);
        int x0 = bookScreenLeft(open, w);
        int y0 = (h - 166) / 2;

        if (open)
        {
            renderRecipeBook(context, batcher, mc, clip, rel, w, h, mouseX, mouseY, hover);
        }

        context.drawTexture(INVENTORY, x0, y0, 0, 0, 176, 166);
        drawActor(context, tick, w, h, x0 + 26, y0 + 8, x0 + 75, y0 + 78, 30, mouseX, mouseY);
        context.drawText(mc.textRenderer, Text.translatable("container.crafting"), x0 + 97, y0 + 8, 0x404040, false);

        PovSlotSet crafting = PovClip.keyed(clip.crafting2x2, rel, PovSlotSet.EMPTY);

        drawSlot(context, mc, crafting.get(0), x0 + 154, y0 + 28, mouseX, mouseY, hover);

        for (int i = 0; i < 4; i++)
        {
            drawSlot(context, mc, crafting.get(1 + i), x0 + 98 + (i % 2) * 18, y0 + 18 + (i / 2) * 18, mouseX, mouseY, hover);
        }

        for (int i = 0; i < 4; i++)
        {
            drawSlot(context, mc, playerItem(clip, rel, replay, 39 - i, tick), x0 + 8, y0 + 8 + i * 18, mouseX, mouseY, hover);
        }

        drawSlot(context, mc, playerItem(clip, rel, replay, 40, tick), x0 + 77, y0 + 62, mouseX, mouseY, hover);
        drawPlayerSlots(context, mc, clip, rel, replay, tick, x0, y0, 84, 142, mouseX, mouseY, hover);

        boolean lit = PovClip.flag(clip.recipeButton, rel, false) || over(mouseX, mouseY, x0 + 104, h / 2 - 22, 20, 18);

        batcher.drawGuiTexture(sprite(lit ? "recipe_book/button_highlighted" : "recipe_book/button"), x0 + 104, h / 2 - 22, 20, 18);
    }

    /** The crafting table: its 3x3 group (the result first), the inventory and hotbar, the recipe book */
    private static void renderCrafting(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovGuiClip clip, float rel, Replay replay, float tick, int w, int h, int mouseX, int mouseY, Hover hover)
    {
        boolean open = PovClip.flag(clip.recipeBook, rel, false);
        int x0 = bookScreenLeft(open, w);
        int y0 = (h - 166) / 2;

        if (open)
        {
            renderRecipeBook(context, batcher, mc, clip, rel, w, h, mouseX, mouseY, hover);
        }

        context.drawTexture(CRAFTING, x0, y0, 0, 0, 176, 166);
        context.drawText(mc.textRenderer, Text.translatable("container.crafting"), x0 + 29, y0 + 6, 0x404040, false);
        context.drawText(mc.textRenderer, Text.translatable("container.inventory"), x0 + 8, y0 + 72, 0x404040, false);

        PovSlotSet crafting = PovClip.keyed(clip.crafting3x3, rel, PovSlotSet.EMPTY);

        drawSlot(context, mc, crafting.get(0), x0 + 124, y0 + 35, mouseX, mouseY, hover);

        for (int i = 0; i < 9; i++)
        {
            drawSlot(context, mc, crafting.get(1 + i), x0 + 30 + (i % 3) * 18, y0 + 17 + (i / 3) * 18, mouseX, mouseY, hover);
        }

        drawPlayerSlots(context, mc, clip, rel, replay, tick, x0, y0, 84, 142, mouseX, mouseY, hover);

        boolean lit = PovClip.flag(clip.recipeButton, rel, false) || over(mouseX, mouseY, x0 + 5, h / 2 - 49, 20, 18);

        batcher.drawGuiTexture(sprite(lit ? "recipe_book/button_highlighted" : "recipe_book/button"), x0 + 5, h / 2 - 49, 20, 18);
    }

    private static boolean over(int mouseX, int mouseY, int x, int y, int w, int h)
    {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * The recipe book of the crafting screens, from its channels: the category tabs down its left,
     * the search field, the craftable-only switch, a page of the category's recipes (the player's
     * recipe book's, filtered by the search) with the picked one lit, and the page turns.
     */
    private static void renderRecipeBook(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovGuiClip clip, float rel, int w, int h, int mouseX, int mouseY, Hover hover)
    {
        TextRenderer font = mc.textRenderer;
        int i = (w - 147) / 2 - 86;
        int j = (h - 166) / 2;
        List<RecipeBookGroup> groups = RecipeBookGroup.getGroups(RecipeBookCategory.CRAFTING);
        int selected = Math.max(0, Math.min(groups.size() - 1, PovClip.keyed(clip.recipeCategory, rel, 0)));

        /* The tabs, behind the book's edge */
        for (int n = 0; n < groups.size(); n++)
        {
            boolean picked = n == selected;
            int tx = i - 30 - (picked ? 2 : 0);
            int ty = j + 3 + 27 * n;
            List<ItemStack> icons = groups.get(n).getIcons();

            batcher.drawGuiTexture(sprite(picked ? "recipe_book/tab_selected" : "recipe_book/tab"), tx, ty, 35, 27);

            if (icons.size() == 1)
            {
                context.drawItem(icons.get(0), tx + 9, ty + 5);
            }
            else if (icons.size() >= 2)
            {
                context.drawItem(icons.get(0), tx + 3, ty + 5);
                context.drawItem(icons.get(1), tx + 14, ty + 5);
            }
        }

        context.drawTexture(RECIPE_BOOK, i, j, 1, 1, 147, 166);

        /* The search field: the hint while it is empty and not typed into */
        String search = PovClip.keyed(clip.recipeSearch, rel, "");
        boolean focus = PovClip.flag(clip.recipeSearchFocus, rel, false);

        if (search.isEmpty() && !focus)
        {
            context.drawText(font, Text.translatable("gui.recipebook.search_hint").formatted(Formatting.ITALIC, Formatting.GRAY), i + 25, j + 14, 0xFFFFFF, false);
        }
        else
        {
            boolean blink = focus && (System.currentTimeMillis() / 300L) % 2L == 0L;

            context.drawText(font, search + (blink ? "_" : ""), i + 25, j + 14, 0xFFFFFF, true);
        }

        boolean craftable = PovClip.flag(clip.showingCraftable, rel, false);

        batcher.drawGuiTexture(sprite(craftable ? "recipe_book/filter_enabled" : "recipe_book/filter_disabled"), i + 110, j + 12, 26, 16);

        /* The recipes: the category's, those whose result is named like the search */
        List<ItemStack> results = new ArrayList<>();
        String needle = search.toLowerCase(Locale.ROOT);

        if (mc.player != null && mc.world != null)
        {
            for (RecipeResultCollection collection : mc.player.getRecipeBook().getResultsForGroup(groups.get(selected)))
            {
                List<RecipeEntry<?>> recipes = collection.getAllRecipes();

                if (recipes.isEmpty())
                {
                    continue;
                }

                ItemStack result = recipes.get(0).value().getResult(mc.world.getRegistryManager());

                if (needle.isEmpty() || result.getName().getString().toLowerCase(Locale.ROOT).contains(needle))
                {
                    results.add(result);
                }
            }
        }

        int pages = Math.max(1, (results.size() + 19) / 20);
        int page = Math.max(0, Math.min(pages - 1, PovClip.keyed(clip.recipePage, rel, 0)));
        int picked = PovClip.keyed(clip.selectedRecipe, rel, -1);

        for (int k = 0; k < 20; k++)
        {
            int index = page * 20 + k;

            if (index >= results.size())
            {
                break;
            }

            int x = i + 11 + 25 * (k % 5);
            int y = j + 31 + 25 * (k / 5);
            ItemStack result = results.get(index);

            batcher.drawGuiTexture(sprite("recipe_book/slot_craftable"), x, y, 25, 25);
            context.drawItem(result, x + 4, y + 4);

            if (k == picked || over(mouseX, mouseY, x, y, 25, 25))
            {
                context.fillGradient(RenderLayer.getGuiOverlay(), x + 1, y + 1, x + 24, y + 24, SLOT_HIGHLIGHT, SLOT_HIGHLIGHT, 0);
            }

            if (over(mouseX, mouseY, x, y, 25, 25))
            {
                hover.stack = result;
            }
        }

        if (pages > 1)
        {
            String text = (page + 1) + "/" + pages;

            context.drawText(font, text, i + 73 - font.getWidth(text) / 2, j + 141, 0xFFFFFF, false);

            if (page > 0)
            {
                batcher.drawGuiTexture(sprite(over(mouseX, mouseY, i + 38, j + 137, 12, 17) ? "recipe_book/page_backward_highlighted" : "recipe_book/page_backward"), i + 38, j + 137, 12, 17);
                hotspot(i + 38, j + 137, 12, 17, () -> keyFrom(clip.recipePage, rel, page - 1, page));
            }

            if (page < pages - 1)
            {
                batcher.drawGuiTexture(sprite(over(mouseX, mouseY, i + 93, j + 137, 12, 17) ? "recipe_book/page_forward_highlighted" : "recipe_book/page_forward"), i + 93, j + 137, 12, 17);
                hotspot(i + 93, j + 137, 12, 17, () -> keyFrom(clip.recipePage, rel, page + 1, page));
            }
        }
    }

    /**
     * The creative inventory with the game's own tabs and items: the page's tabs, the picked tab
     * (its items scrolled by the scroll row, the search tab's filtered by the search), or the
     * inventory tab with the armor, the off hand and the inventory. The hotbar under it is the replay's.
     */
    private static void renderCreative(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovGuiClip clip, float rel, Replay replay, float tick, int w, int h, int mouseX, int mouseY, Hover hover)
    {
        if (mc.world != null && mc.player != null)
        {
            ItemGroups.updateDisplayContext(mc.player.networkHandler.getEnabledFeatures(), false, mc.world.getRegistryManager());
        }

        List<ItemGroup> groups = ItemGroups.getGroupsToDisplay();

        if (groups.isEmpty())
        {
            return;
        }

        ItemGroup tab = groups.get(Math.max(0, Math.min(groups.size() - 1, PovClip.keyed(clip.creativeTab, rel, 0))));
        int page = Math.max(0, PovClip.keyed(clip.creativePage, rel, 0));
        TextRenderer font = mc.textRenderer;
        int x0 = (w - 195) / 2;
        int y0 = (h - 136) / 2;

        int tabIndex = groups.indexOf(tab);
        int pages = PovCreativePages.pageCount();

        /* The tabs of the page (mods' tabs go on further pages); the picked one in front, later. A
         * click on one in the editor's preview turns to it */
        for (int g = 0; g < groups.size(); g++)
        {
            ItemGroup group = groups.get(g);
            int picked = g;

            if (PovCreativePages.isOnPage(group, page))
            {
                if (group != tab)
                {
                    drawCreativeTab(context, batcher, mc, group, false, x0, y0);
                }

                hotspot(creativeTabX(group, x0), creativeTabY(group, y0), 26, 32, () -> keyFrom(clip.creativeTab, rel, picked, tabIndex));
            }
        }

        /* The pages of the tabs, when mods bring more than fit: their arrows and which one this is, over the top row */
        if (pages > 1)
        {
            context.drawCenteredTextWithShadow(font, Text.literal((page + 1) + " / " + pages), x0 + 195 / 2, y0 - 44, 0xFFFFFF);

            if (page > 0)
            {
                drawPageButton(context, batcher, false, x0, y0 - 50, mouseX, mouseY);
                hotspot(x0, y0 - 50, 20, 20, () -> turnCreativePage(clip, rel, groups, page, page - 1, tabIndex));
            }

            if (page < pages - 1)
            {
                drawPageButton(context, batcher, true, x0 + 175, y0 - 50, mouseX, mouseY);
                hotspot(x0 + 175, y0 - 50, 20, 20, () -> turnCreativePage(clip, rel, groups, page, page + 1, tabIndex));
            }
        }

        context.drawTexture(Ids.of("minecraft", "textures/gui/container/creative_inventory/tab_" + tab.getTexture()), x0, y0, 0, 0, 195, 136);

        if (tab.getType() == ItemGroup.Type.INVENTORY)
        {
            drawActor(context, tick, w, h, x0 + 73, y0 + 6, x0 + 105, y0 + 49, 20, mouseX, mouseY);

            for (int i = 0; i < 4; i++)
            {
                drawSlot(context, mc, playerItem(clip, rel, replay, 39 - i, tick), x0 + 54 + (i / 2) * 54, y0 + 6 + (i % 2) * 27, mouseX, mouseY, hover);
            }

            drawSlot(context, mc, playerItem(clip, rel, replay, 40, tick), x0 + 35, y0 + 20, mouseX, mouseY, hover);

            for (int i = 0; i < 27; i++)
            {
                drawSlot(context, mc, playerItem(clip, rel, replay, 9 + i, tick), x0 + 9 + (i % 9) * 18, y0 + 54 + (i / 9) * 18, mouseX, mouseY, hover);
            }
        }
        else
        {
            List<ItemStack> stacks = new ArrayList<>();
            String search = PovClip.keyed(clip.creativeSearch, rel, "");

            if (tab.getType() == ItemGroup.Type.SEARCH)
            {
                String needle = search.toLowerCase(Locale.ROOT);

                for (ItemStack stack : tab.getSearchTabStacks())
                {
                    if (needle.isEmpty() || stack.getName().getString().toLowerCase(Locale.ROOT).contains(needle))
                    {
                        stacks.add(stack);
                    }
                }

                boolean focus = PovClip.flag(clip.creativeSearchFocus, rel, false);
                boolean blink = focus && (System.currentTimeMillis() / 300L) % 2L == 0L;

                context.drawText(font, search + (blink ? "_" : ""), x0 + 82, y0 + 6, 0xFFFFFF, false);
            }
            else
            {
                stacks.addAll(tab.getDisplayStacks());
            }

            int rows = (stacks.size() + 8) / 9;
            int maxRow = Math.max(0, rows - 5);
            int row = Math.max(0, Math.min(maxRow, PovClip.keyed(clip.creativeScrollRow, rel, 0)));

            for (int i = 0; i < 45; i++)
            {
                int index = row * 9 + i;
                ItemStack stack = index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;

                drawSlot(context, mc, stack, x0 + 9 + (i % 9) * 18, y0 + 18 + (i / 9) * 18, mouseX, mouseY, hover);
            }

            float scroll = maxRow > 0 ? row / (float) maxRow : 0F;

            batcher.drawGuiTexture(sprite(maxRow > 0 ? "container/creative_inventory/scroller" : "container/creative_inventory/scroller_disabled"),
                x0 + 175, y0 + 18 + (int) ((112 - 18 - 17) * scroll), 12, 15);
        }

        for (int i = 0; i < 9; i++)
        {
            drawSlot(context, mc, playerItem(clip, rel, replay, i, tick), x0 + 9 + i * 18, y0 + 112, mouseX, mouseY, hover);
        }

        drawCreativeTab(context, batcher, mc, tab, true, x0, y0);

        if (tab.shouldRenderName())
        {
            context.drawText(font, tab.getDisplayName(), x0 + 8, y0 + 6, 0x404040, false);
        }
    }

    /** A button of the creative tabs' pages: a button with an arrow, lit under the cursor */
    private static void drawPageButton(DrawContext context, Batcher2D batcher, boolean forward, int x, int y, int mouseX, int mouseY)
    {
        boolean lit = over(mouseX, mouseY, x, y, 20, 20);

        batcher.drawGuiTexture(sprite(lit ? "widget/button_highlighted" : "widget/button"), x, y, 20, 20);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(forward ? ">" : "<"), x + 10, y + 6, lit ? 0xFFFFA0 : 0xFFFFFF);
    }

    /** A turn of the creative tabs' page; the tab goes to the new page's first, as the page's tabs are the ones there are to pick */
    private static void turnCreativePage(PovGuiClip clip, float rel, List<ItemGroup> groups, int page, int next, int tab)
    {
        keyFrom(clip.creativePage, rel, next, page);

        for (int g = 0; g < groups.size(); g++)
        {
            if (groups.get(g).getType() == ItemGroup.Type.CATEGORY && PovCreativePages.isOnPage(groups.get(g), next))
            {
                keyFrom(clip.creativeTab, rel, g, tab);

                return;
            }
        }
    }

    private static int creativeTabX(ItemGroup group, int x0)
    {
        int column = Math.max(0, Math.min(6, group.getColumn()));

        return group.isSpecial() ? x0 + 195 - 27 * (7 - column) + 1 : x0 + 27 * column;
    }

    private static int creativeTabY(ItemGroup group, int y0)
    {
        return group.getRow() == ItemGroup.Row.TOP ? y0 - 28 : y0 + 136 - 4;
    }

    /** A creative tab where the game puts it: in a row above or below, by its column, the special ones from the right */
    private static void drawCreativeTab(DrawContext context, Batcher2D batcher, MinecraftClient mc, ItemGroup group, boolean selected, int x0, int y0)
    {
        boolean top = group.getRow() == ItemGroup.Row.TOP;
        int column = Math.max(0, Math.min(6, group.getColumn()));
        int x = creativeTabX(group, x0);
        int y = creativeTabY(group, y0);
        String sprite = "container/creative_inventory/tab_" + (top ? "top" : "bottom") + "_" + (selected ? "selected" : "unselected") + "_" + (column + 1);

        context.draw();
        batcher.drawGuiTexture(sprite(sprite), x, y, 26, 32);
        context.getMatrices().push();
        context.getMatrices().translate(0F, 0F, 100F);
        context.drawItem(group.getIcon(), x + 5, y + 8 + (top ? 1 : -1));
        context.getMatrices().pop();
    }

    /**
     * The beacon from its channels: the powers it offers by its pyramid level, the primary and the
     * secondary picked (by effect id), the payment in its slot, the inventory and hotbar.
     */
    private static void renderBeacon(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovGuiClip clip, float rel, Replay replay, float tick, int w, int h, int mouseX, int mouseY, Hover hover)
    {
        int x0 = (w - 230) / 2;
        int y0 = (h - 219) / 2;
        int level = Math.max(0, Math.min(4, PovClip.keyed(clip.beaconLevel, rel, 0)));
        StatusEffect primary = effect(PovClip.keyed(clip.primaryPower, rel, ""));
        StatusEffect secondary = effect(PovClip.keyed(clip.secondaryPower, rel, ""));

        context.drawTexture(BEACON, x0, y0, 0, 0, 230, 219);
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("block.minecraft.beacon.primary"), x0 + 62, y0 + 10, 0xE0E0E0);
        context.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("block.minecraft.beacon.secondary"), x0 + 169, y0 + 10, 0xE0E0E0);

        for (int row = 0; row < BEACON_POWERS.length; row++)
        {
            StatusEffect[] powers = BEACON_POWERS[row];
            int width = powers.length * 22 + (powers.length - 1) * 2;

            for (int l = 0; l < powers.length; l++)
            {
                drawBeaconButton(context, batcher, mc, powers[l], x0 + 76 + l * 24 - width / 2, y0 + 22 + row * 25, powers[l] == primary, row >= level, mouseX, mouseY);
            }
        }

        /* The secondary: regeneration, and the primary made stronger */
        boolean secondaryOpen = level >= 4 && primary != null;

        drawBeaconButton(context, batcher, mc, StatusEffects.REGENERATION, x0 + 167 - 23, y0 + 47, secondary == StatusEffects.REGENERATION, !secondaryOpen, mouseX, mouseY);
        drawBeaconButton(context, batcher, mc, primary, x0 + 167 + 24 - 23, y0 + 47, primary != null && secondary == primary, !secondaryOpen, mouseX, mouseY);

        drawBeaconButton(context, batcher, mc, null, x0 + 164, y0 + 107, false, primary == null, mouseX, mouseY);
        batcher.drawGuiTexture(sprite("container/beacon/confirm"), x0 + 164 + 2, y0 + 107 + 2, 18, 18);
        drawBeaconButton(context, batcher, mc, null, x0 + 190, y0 + 107, false, false, mouseX, mouseY);
        batcher.drawGuiTexture(sprite("container/beacon/cancel"), x0 + 190 + 2, y0 + 107 + 2, 18, 18);

        context.drawItem(new ItemStack(Items.NETHERITE_INGOT), x0 + 20, y0 + 109);
        context.drawItem(new ItemStack(Items.EMERALD), x0 + 41, y0 + 109);
        context.drawItem(new ItemStack(Items.DIAMOND), x0 + 41 + 22, y0 + 109);
        context.drawItem(new ItemStack(Items.GOLD_INGOT), x0 + 42 + 44, y0 + 109);
        context.drawItem(new ItemStack(Items.IRON_INGOT), x0 + 42 + 66, y0 + 109);

        drawSlot(context, mc, PovClip.keyed(clip.payment, rel, ItemStack.EMPTY), x0 + 136, y0 + 110, mouseX, mouseY, hover);

        for (int i = 0; i < 27; i++)
        {
            drawSlot(context, mc, playerItem(clip, rel, replay, 9 + i, tick), x0 + 36 + (i % 9) * 18, y0 + 137 + (i / 9) * 18, mouseX, mouseY, hover);
        }

        for (int i = 0; i < 9; i++)
        {
            drawSlot(context, mc, playerItem(clip, rel, replay, i, tick), x0 + 36 + i * 18, y0 + 195, mouseX, mouseY, hover);
        }
    }

    private static StatusEffect effect(String id)
    {
        Identifier identifier = id == null || id.isEmpty() ? null : Identifier.tryParse(id);

        return identifier == null ? null : Registries.STATUS_EFFECT.get(identifier);
    }

    private static void drawBeaconButton(DrawContext context, Batcher2D batcher, MinecraftClient mc, StatusEffect effect, int x, int y, boolean selected, boolean disabled, int mouseX, int mouseY)
    {
        String button = disabled ? "container/beacon/button_disabled"
            : selected ? "container/beacon/button_selected"
            : over(mouseX, mouseY, x, y, 22, 22) ? "container/beacon/button_highlighted"
            : "container/beacon/button";

        batcher.drawGuiTexture(sprite(button), x, y, 22, 22);

        if (effect != null)
        {
            Sprite icon = mc.getStatusEffectSpriteManager().getSprite(effect);

            context.drawSprite(x + 2, y + 2, 0, 18, 18, icon);
        }
    }

    /**
     * The book: a page of its text with the page indicator and the page turns, as a written book
     * (Done) or a book and quill (Sign, Done, the cursor at the end of the text), or the signing
     * page (the title being written, by its author, Sign and Close, Cancel).
     */
    private static void renderBook(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovGuiClip clip, float rel, float tick, int w, int h, int mouseX, int mouseY)
    {
        TextRenderer font = mc.textRenderer;
        boolean writable = PovClip.flag(clip.writable, rel, false);
        boolean signing = writable && PovClip.flag(clip.signing, rel, false);
        boolean blink = (System.currentTimeMillis() / 300L) % 2L == 0L;
        int x0 = (w - 192) / 2;
        int y0 = 2;

        context.drawTexture(BOOK, x0, y0, 0, 0, 192, 192);

        if (signing)
        {
            String title = PovClip.keyed(clip.bookTitle, rel, "");
            String author = PovClip.keyed(clip.bookAuthor, rel, mc.player == null ? "" : mc.player.getName().getString());
            Text edit = Text.translatable("book.editTitle");
            String shown = title + (blink ? "_" : "");
            Text by = Text.translatable("book.byAuthor", author).formatted(Formatting.DARK_GRAY);

            context.drawText(font, edit, x0 + 36 + (114 - font.getWidth(edit)) / 2, 34, 0, false);
            context.drawText(font, shown, x0 + 36 + (114 - font.getWidth(shown)) / 2, 50, 0, false);
            context.drawText(font, by, x0 + 36 + (114 - font.getWidth(by)) / 2, 60, 0, false);
            context.drawTextWrapped(font, Text.translatable("book.finalizeWarning"), x0 + 36, 82, 114, 0);

            button(context, Text.translatable("book.finalizeButton"), w / 2 - 100, 196, 98, mouseX, mouseY);
            button(context, Text.translatable("gui.cancel"), w / 2 + 2, 196, 98, mouseX, mouseY);

            return;
        }

        List<String> pages = PovGuiClip.pages(PovClip.keyed(clip.bookPages, rel, ""));
        int page = Math.max(0, Math.min(pages.size() - 1, PovClip.keyed(clip.bookPage, rel, 0)));
        Text indicator = Text.translatable("book.pageIndicator", page + 1, pages.size());
        String text = pages.get(page) + (writable && blink ? "_" : "");
        List<OrderedText> lines = font.wrapLines(StringVisitable.plain(text), 114);

        context.drawText(font, indicator, x0 + 192 - 44 - font.getWidth(indicator), 18, 0, false);

        for (int i = 0; i < lines.size() && i < 14; i++)
        {
            context.drawText(font, lines.get(i), x0 + 36, 32 + i * 9, 0, false);
        }

        if (page < pages.size() - 1 || writable)
        {
            batcher.drawGuiTexture(sprite(over(mouseX, mouseY, x0 + 116, 159, 23, 13) ? "widget/page_forward_highlighted" : "widget/page_forward"), x0 + 116, 159, 23, 13);
        }

        if (page > 0)
        {
            batcher.drawGuiTexture(sprite(over(mouseX, mouseY, x0 + 43, 159, 23, 13) ? "widget/page_backward_highlighted" : "widget/page_backward"), x0 + 43, 159, 23, 13);
        }

        if (writable)
        {
            button(context, Text.translatable("book.signButton"), w / 2 - 100, 196, 98, mouseX, mouseY);
            button(context, Text.translatable("gui.done"), w / 2 + 2, 196, 98, mouseX, mouseY);
        }
        else
        {
            button(context, Text.translatable("gui.done"), w / 2 - 100, 196, 200, mouseX, mouseY);
        }
    }

    /** One of the game's buttons, as a picture: lit under the cursor, pressing does nothing */
    private static void button(DrawContext context, Text label, int x, int y, int width, int mouseX, int mouseY)
    {
        ButtonWidget.builder(label, (b) -> {}).dimensions(x, y, width, 20).build().render(context, mouseX, mouseY, 0F);
    }

    /** The bed: the world going black, and the button to get out of it */
    private static void renderSleep(DrawContext context, PovGuiClip clip, float rel, int w, int h, int mouseX, int mouseY)
    {
        float fall = (float) PovClip.value(clip.sleepDarkness, rel, Math.min(rel / 100F, 1F));

        if (fall > 0F)
        {
            context.fill(0, 0, w, h, alpha(0xCC101020, fall));
        }

        if (PovClip.flag(clip.leaveBed, rel, true))
        {
            button(context, Text.translatable("multiplayer.stopSleeping"), w / 2 - 100, h - 40, 200, mouseX, mouseY);
        }
    }

    /**
     * The actor in an inventory's black box, as the game draws the player there: standing in the
     * middle of the box with its feet a little under it, the body turned a little after the cursor
     * and the head more, lit as the game lights an entity in a screen and cut to the box. The
     * actor's own form, through the same path the world draws it by, so its pose and parts are
     * the film's; its turns are lent for the draw and given back after it.
     */
    private static void drawActor(DrawContext context, float tick, int w, int h, int x1, int y1, int x2, int y2, int size, int mouseX, int mouseY)
    {
        IEntity entity = actor;
        Form form = entity == null ? null : entity.getForm();

        if (form == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack matrices = context.getMatrices();
        float cx = (x1 + x2) / 2F;
        float cy = (y1 + y2) / 2F;
        float turn = (float) Math.atan((cx - mouseX) / 40F);
        float tilt = (float) Math.atan((cy - mouseY) / 40F);
        float bodyYaw = 180F + turn * 20F;
        float lookYaw = 180F + turn * 40F;
        float lookPitch = -tilt * 20F;
        AABB box = entity.getPickingHitbox();
        float height = box != null && box.h > 0.1D && box.h < 4D ? (float) box.h : 1.8F;
        float[] color = RenderSystem.getShaderColor().clone();
        float[] turns = {
            entity.getYaw(), entity.getPrevYaw(), entity.getHeadYaw(), entity.getPrevHeadYaw(),
            entity.getBodyYaw(), entity.getPrevBodyYaw(), entity.getPitch(), entity.getPrevPitch()
        };

        context.draw();
        scissor(matrices, w, h, x1, y1, x2, y2);

        entity.setYaw(lookYaw);
        entity.setPrevYaw(lookYaw);
        entity.setHeadYaw(lookYaw);
        entity.setPrevHeadYaw(lookYaw);
        entity.setBodyYaw(bodyYaw);
        entity.setPrevBodyYaw(bodyYaw);
        entity.setPitch(lookPitch);
        entity.setPrevPitch(lookPitch);

        matrices.push();

        try
        {
            /* The game's placement in the box, then the world's turn of a body, which the form's own draw leaves to its caller */
            matrices.translate(cx, cy, 50F);
            matrices.multiplyPositionMatrix(new Matrix4f().scaling(size, size, -size));
            matrices.translate(0F, height / 2F + 0.0625F, 0F);
            matrices.multiply(new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateX(tilt * 20F * (float) Math.PI / 180F)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

            DiffuseLighting.method_34742();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            FormUtilsClient.render(form, new FormRenderingContext()
                .set(FormRenderType.PREVIEW, entity, matrices, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, tick - (int) tick)
                .camera(mc.gameRenderer.getCamera()));
            context.draw();
        }
        finally
        {
            matrices.pop();

            entity.setYaw(turns[0]);
            entity.setPrevYaw(turns[1]);
            entity.setHeadYaw(turns[2]);
            entity.setPrevHeadYaw(turns[3]);
            entity.setBodyYaw(turns[4]);
            entity.setPrevBodyYaw(turns[5]);
            entity.setPitch(turns[6]);
            entity.setPrevPitch(turns[7]);

            DiffuseLighting.enableGuiDepthLighting();
            RenderSystem.disableScissor();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    /** A box of the screen cut out of the frame, wherever the layout has put it (its bounds, when turned) */
    private static void scissor(MatrixStack matrices, int w, int h, int x1, int y1, int x2, int y2)
    {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int i = 0; i < 4; i++)
        {
            Vector3f corner = matrix.transformPosition(new Vector3f(i % 2 == 0 ? x1 : x2, i < 2 ? y1 : y2, 0F));

            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
        }

        float sx = fb.textureWidth / (float) w;
        float sy = fb.textureHeight / (float) h;
        int px = Math.round(minX * sx);
        int py = Math.round(fb.textureHeight - maxY * sy);

        RenderSystem.enableScissor(px, py, Math.max(0, Math.round((maxX - minX) * sx)), Math.max(0, Math.round((maxY - minY) * sy)));
    }

    /** The item under the cursor, for the tooltip drawn after the cursor */
    private static final class Hover
    {
        ItemStack stack;
    }

    /** A box of a screen that answers a click in the editor's preview, in the screen's own space (its layout and frame size kept with it) */
    private record Hotspot(Transform layout, int w, int h, int x, int y, int width, int height, Runnable action)
    {}

    /** A tooltip waiting for the cursor: its stack, the layout and size of its screen, and where */
    private record Tooltip(ItemStack stack, Transform layout, int w, int h, int x, int y, float opacity)
    {}

    private record Holder(String key, Screen screen, PlayerInventory inventory, int width, int height)
    {}
}
