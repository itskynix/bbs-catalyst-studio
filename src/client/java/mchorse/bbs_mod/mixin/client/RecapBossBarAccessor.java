package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** Reads active client boss bars so Recap can record them for first-person playback. */
@Mixin(BossBarHud.class)
public interface RecapBossBarAccessor
{
    @Accessor("bossBars")
    Map<UUID, ClientBossBar> bbs$getBossBars();
}
