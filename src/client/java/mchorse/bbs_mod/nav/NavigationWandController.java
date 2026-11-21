package mchorse.bbs_mod.nav;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
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
            NavigationSelection.clear();
            message(UIKeys.NAV_WAND_CLEARED.get());
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

        if (Window.isAltPressed())
        {
            save();
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

        Film film = NavigationNetworks.activeFilm();
        NavigationNetwork network = NavigationNetworks.get(film);

        if (network.path.remove(pos))
        {
            NavigationNetworks.save(film, network);
            NavigationNetworks.invalidate();
            NavigationWandRenderer.refresh();
            message(UIKeys.NAV_WAND_ERASED.get());
        }
    }

    /** Alt and the right button: the box and the painted blocks go into the network of the scene, and the selection is cleared */
    private static void save()
    {
        int blocks = NavigationSelection.getPath().size();
        int boxes = NavigationSelection.isReady() ? 1 : 0;

        if (blocks == 0 && boxes == 0)
        {
            message(UIKeys.NAV_WAND_NOTHING.get());

            return;
        }

        Film film = NavigationNetworks.activeFilm();
        NavigationNetwork network = NavigationNetworks.get(film).copy();

        if (boxes > 0)
        {
            BlockPos min = NavigationSelection.getMin();
            Vec3i size = NavigationSelection.getSize();

            network.boxes.add(new NavigationNetwork.Box(min.getX(), min.getY(), min.getZ(), size.getX(), size.getY(), size.getZ()));
        }

        network.path.addAll(NavigationSelection.getPath());

        NavigationNetworks.save(film, network);
        NavigationNetworks.invalidate();
        NavigationSelection.clear();
        NavigationWandRenderer.refresh();
        message(UIKeys.NAV_WAND_SAVED.format(String.valueOf(blocks), String.valueOf(boxes)).get());
    }

    private static void message(String text)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null)
        {
            mc.player.sendMessage(Text.literal(text), true);
        }
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

    private static final int ROW = 11;

    /** The hint above the hotbar: the mode, what is selected, and what the buttons do in this mode */
    public static void renderHud(Batcher2D batcher)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden || !isHolding(mc.player))
        {
            return;
        }

        FontRenderer font = batcher.getFont();
        String[] lines;

        if (mode == Mode.BOX)
        {
            lines = new String[] {
                UIKeys.NAV_WAND_HINT_CORNER_A.get(),
                UIKeys.NAV_WAND_HINT_CORNER_B.get(),
                UIKeys.NAV_WAND_HINT_PUSH.get(),
                UIKeys.NAV_WAND_HINT_SAVE.get(),
                UIKeys.NAV_WAND_HINT_MODE.get()
            };
        }
        else
        {
            lines = new String[] {
                UIKeys.NAV_WAND_HINT_PAINT.get(),
                UIKeys.NAV_WAND_HINT_ERASE.get(),
                UIKeys.NAV_WAND_HINT_SAVE.get(),
                UIKeys.NAV_WAND_HINT_MODE.get()
            };
        }

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        int y = height - 54 - lines.length * ROW;

        String status = modeName(mode);

        if (mode == Mode.BOX && NavigationSelection.isReady())
        {
            Vec3i size = NavigationSelection.getSize();

            status += "   " + size.getX() + " × " + size.getY() + " × " + size.getZ();
        }
        else if (mode == Mode.PATH)
        {
            status += "   " + UIKeys.NAV_WAND_STATUS_BLOCKS.format(String.valueOf(NavigationSelection.getPath().size())).get();
        }

        batcher.textShadow(status, (width - font.getWidth(status)) / 2F, y - 13, mode == Mode.BOX ? 0xff4fb4ff : 0xff4ade80);

        for (int i = 0; i < lines.length; i++)
        {
            batcher.textShadow(lines[i], (width - font.getWidth(lines[i])) / 2F, y + i * ROW, i == lines.length - 1 ? Colors.LIGHTER_GRAY : Colors.WHITE);
        }
    }
}
