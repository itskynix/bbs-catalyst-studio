package mchorse.bbs_mod.nav;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.structure.StructureWand;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.ClientCompat;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

/**
 * The navigation wand: a plain item whose whole behaviour lives here, on the client, like the
 * structure wand's. Two modes, changed with Shift and the wheel:
 *
 * <ul>
 * <li><b>Box:</b> the left button places corner A, the right one corner B, the wheel on a face
 * pushes it and Alt and the wheel slides the whole box - the same hands as the structure wand.</li>
 * <li><b>Path:</b> the right button paints the block under the crosshair as a block of a path, held
 * down it paints along as the mouse moves; blocks in neighbouring columns join, so lines that meet
 * are a junction. The left button erases a block of the path.</li>
 * </ul>
 *
 * <p>Alt and the right button saves what is selected and painted into the scene - the film open in
 * the editor, or the world - and Alt and the left button drops it without saving. The hint above
 * the hotbar names what the buttons do in the mode the wand is in.</p>
 */
public final class NavigationWandController
{
    public enum Mode
    {
        BOX, PATH
    }

    private static final String[] TOOLTIP = {
        "item.bbs.navigation_wand.tooltip.box",
        "item.bbs.navigation_wand.tooltip.path",
        "item.bbs.navigation_wand.tooltip.save"
    };

    private static Mode mode = Mode.BOX;

    /** Set by a save, cleared when the right button is let go */
    private static boolean saveLatched;

    /** What just happened, shown on top of the hint for a moment */
    private static String notice = "";
    private static long noticeTime = Long.MIN_VALUE;
    private static final long NOTICE_MILLIS = 2500L;

    /** The face of the box under the crosshair this frame: what the wheel acts on */
    private static Direction face;

    /** The block the next box click lands on */
    private static BlockPos pick;

    private NavigationWandController()
    {}

    public static Mode getMode()
    {
        return mode;
    }

    public static Direction getFace()
    {
        return face;
    }

    public static BlockPos getPick()
    {
        return pick;
    }

    static void setView(Direction faceUnderCrosshair, BlockPos pickedBlock)
    {
        face = faceUnderCrosshair;
        pick = pickedBlock;
    }

    public static void register()
    {
        /* A held right button repeats the use every few ticks: Alt and it saves once per press, not again on an empty selection */
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register((mc) ->
        {
            if (!mc.options.useKey.isPressed())
            {
                saveLatched = false;
            }
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> isHolding(player, hand) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> isHolding(player, hand) ? ActionResult.FAIL : ActionResult.PASS);

        //? if >=1.21
        /*ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->*/
        //? if <1.21
        ItemTooltipCallback.EVENT.register((stack, context, lines) ->
        {
            if (stack.isOf(BBSMod.NAVIGATION_WAND_ITEM))
            {
                for (String line : TOOLTIP)
                {
                    lines.add(Text.translatable(line).formatted(Formatting.GRAY));
                }
            }
        });
    }

    /* Input */

    /** The left button: corner A, an erased path block, or with Alt everything dropped. True when the click was the wand's */
    public static boolean onAttack()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive(mc))
        {
            return false;
        }

        if (Window.isAltPressed())
        {
            /* The draft goes first; with no draft, or with Shift held too, what is saved goes */
            if (!NavigationSelection.isEmpty() && !Window.isShiftPressed())
            {
                NavigationSelection.clear();
                message(UIKeys.NAV_WAND_CLEARED.get());
            }
            else
            {
                deleteSaved();
            }
        }
        else if (mode == Mode.BOX)
        {
            NavigationSelection.setA(pickBox(mc));
        }
        else
        {
            erase(mc);
        }

        mc.player.swingHand(getHand(mc.player));

        return true;
    }

    /** The right button: corner B, a painted path block, or with Alt everything saved */
    public static boolean onUse()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive(mc))
        {
            return false;
        }

        /* Alt with the right button saves whatever the mode: nothing of the mode's own right click happens */
        if (Window.isAltPressed())
        {
            if (!saveLatched)
            {
                saveLatched = true;
                save();
            }
        }
        else if (mode == Mode.BOX)
        {
            NavigationSelection.setB(pickBox(mc));
            mc.player.swingHand(getHand(mc.player));
        }
        else
        {
            paint(mc);
        }

        return true;
    }

    /** Shift and the wheel changes the mode; in box mode the wheel on a face pushes it, with Alt slides the box. True when the notch was taken */
    public static boolean onScroll(double vertical)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive(mc) || vertical == 0)
        {
            return false;
        }

        if (Window.isShiftPressed())
        {
            mode = mode == Mode.BOX ? Mode.PATH : Mode.BOX;
            message(UIKeys.NAV_WAND_MODE.format(modeName(mode)).get());

            return true;
        }

        if (mode != Mode.BOX || face == null || !NavigationSelection.isReady())
        {
            return false;
        }

        int amount = vertical > 0 ? -1 : 1;

        if (Window.isAltPressed())
        {
            NavigationSelection.move(face, amount);
        }
        else
        {
            NavigationSelection.push(face, amount);
        }

        return true;
    }

    /* Painting and saving */

    private static void paint(MinecraftClient mc)
    {
        BlockPos pos = pickBlock(mc);

        if (pos == null)
        {
            return;
        }

        if (Double.isNaN(NavGrid.standable(mc.world, pos.getX(), pos.getY(), pos.getZ(), 1.8F)))
        {
            message(UIKeys.NAV_WAND_NOT_WALKABLE.get());

            return;
        }

        if (NavigationSelection.getPath().add(pos))
        {
            mc.player.swingHand(getHand(mc.player));
        }
    }

    /** Takes a block out of the path: from the unsaved ones, or else from the saved ones */
    private static void erase(MinecraftClient mc)
    {
        BlockPos pos = pickBlock(mc);

        if (pos == null)
        {
            return;
        }

        if (NavigationSelection.getPath().remove(pos))
        {
            return;
        }

        if (NavigationNetworks.sceneNetwork(NavigationNetworks.activeFilm()).path.contains(pos))
        {
            NavigationNetworks.update((network) -> network.path.remove(pos));
            NavigationWandRenderer.refresh();
            message(UIKeys.NAV_WAND_ERASED.get());
        }
    }

    /**
     * Alt and the right button: whatever is drafted - the box, the painted blocks, or both, in either
     * mode - goes into the network of the scene, in the film and in the world's file alike, and the
     * draft is cleared. What the AI walks on is made again at once.
     */
    private static void save()
    {
        int blocks = NavigationSelection.getPath().size();
        boolean box = NavigationSelection.isReady();

        if (blocks == 0 && !box)
        {
            message(UIKeys.NAV_WAND_NOTHING.get());

            return;
        }

        NavigationNetwork.Box saved = null;

        if (box)
        {
            BlockPos min = NavigationSelection.getMin();
            Vec3i size = NavigationSelection.getSize();

            saved = new NavigationNetwork.Box(min.getX(), min.getY(), min.getZ(), size.getX(), size.getY(), size.getZ());
        }

        NavigationNetwork.Box added = saved;
        java.util.List<BlockPos> painted = new java.util.ArrayList<>(NavigationSelection.getPath());

        NavigationNetworks.update((network) ->
        {
            if (added != null && !network.boxes.contains(added))
            {
                network.boxes.add(added);
            }

            network.path.addAll(painted);
        });

        NavigationSelection.clear();
        NavigationWandRenderer.refresh();
        nodesCounted = Long.MIN_VALUE;
        message(UIKeys.NAV_WAND_SAVED.format(String.valueOf(blocks), String.valueOf(box ? 1 : 0)).get());
    }

    /** Everything saved goes, in the film and in the world's file */
    private static void deleteSaved()
    {
        NavigationNetworks.update((network) ->
        {
            network.boxes.clear();
            network.path.clear();
        });

        NavigationWandRenderer.refresh();
        nodesCounted = Long.MIN_VALUE;
        message(UIKeys.NAV_WAND_NETWORK_DELETED.get());
    }

    private static void message(String text)
    {
        notify(text);
    }

    /** A notice on top of the wand's hint for a moment; shown even with the wand put away, for the preview key */
    public static void notify(String text)
    {
        notice = text == null ? "" : text;
        noticeTime = System.currentTimeMillis();
    }

    private static String notice()
    {
        return System.currentTimeMillis() - noticeTime < NOTICE_MILLIS ? notice : null;
    }

    private static String modeName(Mode mode)
    {
        return (mode == Mode.BOX ? UIKeys.NAV_WAND_MODE_BOX : UIKeys.NAV_WAND_MODE_PATH).get();
    }

    /* What is held and what is looked at */

    private static boolean isActive(MinecraftClient mc)
    {
        return mc.player != null && mc.currentScreen == null && isHolding(mc.player);
    }

    private static boolean isHolding(PlayerEntity player, Hand hand)
    {
        return player.getStackInHand(hand).isOf(BBSMod.NAVIGATION_WAND_ITEM);
    }

    public static boolean isHolding(PlayerEntity player)
    {
        return isHolding(player, Hand.MAIN_HAND) || isHolding(player, Hand.OFF_HAND);
    }

    private static Hand getHand(PlayerEntity player)
    {
        return isHolding(player, Hand.MAIN_HAND) ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    /** The block under the crosshair, or the block at arm's length when it is on nothing: a box can start in the air */
    static BlockPos pickBox(MinecraftClient mc)
    {
        HitResult hit = mc.crosshairTarget;

        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK)
        {
            return blockHit.getBlockPos();
        }

        float tickDelta = mc./*$ gtd*/getTickDelta()/**/;
        Vec3d eye = mc.player.getCameraPosVec(tickDelta);
        Vec3d look = mc.player.getRotationVec(tickDelta);
        double reach = mc.interactionManager == null ? 4.5 : ClientCompat.getReach(mc);

        return BlockPos.ofFloored(eye.add(look.multiply(reach)));
    }

    /** The block under the crosshair, or null when it is on nothing: a path is painted on ground */
    static BlockPos pickBlock(MinecraftClient mc)
    {
        HitResult hit = mc.crosshairTarget;

        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK)
        {
            return blockHit.getBlockPos();
        }

        return null;
    }

    /* HUD */

    /**
     * The hint above the hotbar, drawn the way the structure wand draws its own: the mode and what is
     * selected on the status line, and below it the mouse buttons, the wheel and the key caps with what
     * they do in the mode the wand is in, the bare gestures on the left and the ones with a key held on
     * the right. The status line also says how many floors the AI of the scene has to walk on, so a
     * saved box or path that gives none shows at once.
     */
    public static void renderHud(Batcher2D batcher)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden)
        {
            return;
        }

        if (!isHolding(mc.player))
        {
            String alone = notice();

            if (alone != null)
            {
                StructureWand.renderHints(batcher, new java.util.ArrayList<>(), new java.util.ArrayList<>(), null, 0, alone);
            }

            return;
        }

        java.util.List<StructureWand.Hint> plain = new java.util.ArrayList<>();
        java.util.List<StructureWand.Hint> modified = new java.util.ArrayList<>();
        boolean box = mode == Mode.BOX;

        if (box)
        {
            plain.add(new StructureWand.Hint(UIKeys.STRUCTURE_WAND_CORNER_A.get(), StructureWand.COLOR_A, StructureWand.Glyph.LMB));
            plain.add(new StructureWand.Hint(UIKeys.STRUCTURE_WAND_CORNER_B.get(), StructureWand.COLOR_B, StructureWand.Glyph.RMB));

            if (face != null && NavigationSelection.isReady())
            {
                plain.add(new StructureWand.Hint(UIKeys.STRUCTURE_WAND_PUSH.get(), Colors.WHITE, StructureWand.Glyph.WHEEL));
            }
        }
        else
        {
            plain.add(new StructureWand.Hint(UIKeys.NAV_WAND_LABEL_PAINT.get(), PATH_COLOR, StructureWand.Glyph.RMB));
            plain.add(new StructureWand.Hint(UIKeys.NAV_WAND_LABEL_ERASE.get(), Colors.WHITE, StructureWand.Glyph.LMB));
        }

        modified.add(new StructureWand.Hint(UIKeys.STRUCTURE_WAND_SAVE.get(), Colors.WHITE, StructureWand.Glyph.ALT, StructureWand.Glyph.RMB));
        modified.add(NavigationSelection.isEmpty()
            ? new StructureWand.Hint(UIKeys.NAV_WAND_LABEL_DELETE.get(), 0xff7070, StructureWand.Glyph.ALT, StructureWand.Glyph.LMB)
            : new StructureWand.Hint(UIKeys.STRUCTURE_WAND_CLEAR.get(), Colors.WHITE, StructureWand.Glyph.ALT, StructureWand.Glyph.LMB));

        if (box && face != null && NavigationSelection.isReady())
        {
            modified.add(new StructureWand.Hint(UIKeys.STRUCTURE_WAND_MOVE.get(), Colors.WHITE, StructureWand.Glyph.ALT, StructureWand.Glyph.WHEEL));
        }

        modified.add(new StructureWand.Hint(UIKeys.NAV_WAND_LABEL_MODE.get(), Colors.LIGHTER_GRAY, StructureWand.Glyph.SHIFT, StructureWand.Glyph.WHEEL));

        String status = modeName(mode);

        if (box && NavigationSelection.isReady())
        {
            Vec3i size = NavigationSelection.getSize();

            status += "   " + size.getX() + " × " + size.getY() + " × " + size.getZ();
        }
        else if (!box)
        {
            status += "   " + UIKeys.NAV_WAND_STATUS_BLOCKS.format(String.valueOf(NavigationSelection.getPath().size())).get();
        }

        status += "   ·   " + UIKeys.NAV_WAND_NETWORK_NODES.format(String.valueOf(sceneNodes())).get();

        StructureWand.renderHints(batcher, plain, modified, status, box ? Colors.A100 | StructureWand.COLOR_A : Colors.A100 | PATH_COLOR, notice());
    }

    private static final int PATH_COLOR = 0x4ade80;

    private static int nodes;
    private static long nodesCounted = Long.MIN_VALUE;

    /** How many floors the AI of the scene has to walk on, counted twice a second */
    private static int sceneNodes()
    {
        long now = System.currentTimeMillis();

        if (now - nodesCounted > 500L)
        {
            NavGrid grid = AiSimulation.sceneGrid(NavigationNetworks.activeFilm());

            nodes = grid == null ? 0 : grid.getNodes().size();
            nodesCounted = now;
        }

        return nodes;
    }
}
