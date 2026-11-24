package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.TriggerBoxBlock;
import mchorse.bbs_mod.blocks.entities.TriggerBoxBlockEntity;
import mchorse.bbs_mod.client.renderer.TriggerBoxRenderer;
import mchorse.bbs_mod.network.ClientPacketCrusher;
import mchorse.bbs_mod.network.TriggerBoxNetwork;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.triggers.UITriggerBoxMenu;
import net.fabricmc.fabric.impl.client.rendering.BlockEntityRendererRegistryImpl;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;

/** The client half of the trigger box: its renderer, its editor, and sending the editor's save. */
public class TriggerBoxClient
{
    private static final ClientPacketCrusher crusher = new ClientPacketCrusher();

    public static void setup()
    {
        BlockEntityRendererRegistryImpl.register(BBSMod.TRIGGER_BOX_ENTITY, TriggerBoxRenderer::new);

        TriggerBoxBlock.openEditor = (pos) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            BlockEntity be = mc.world == null ? null : mc.world.getBlockEntity(pos);

            if (be instanceof TriggerBoxBlockEntity box && mc.currentScreen == null)
            {
                UIScreen.open(new UITriggerBoxMenu(box));
            }
        };
    }

    public static void save(TriggerBoxBlockEntity box)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null)
        {
            return;
        }

        crusher.send(mc.player, TriggerBoxNetwork.SERVER_TRIGGER_BOX_SAVE, box.toData(), (buf) -> buf.writeBlockPos(box.getPos()));
    }
}
