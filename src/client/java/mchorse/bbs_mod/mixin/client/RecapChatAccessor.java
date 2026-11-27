package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Reads the client chat history so Recap can record messages for first-person playback. */
@Mixin(ChatHud.class)
public interface RecapChatAccessor
{
    @Accessor("messages")
    List<ChatHudLine> bbs$getMessages();
}
