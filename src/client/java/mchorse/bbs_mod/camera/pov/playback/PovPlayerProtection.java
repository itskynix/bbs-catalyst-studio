/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.network.ServerPlayerEntity
 */
package mchorse.bbs_mod.camera.pov.playback;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.network.ServerPlayerEntity;

public final class PovPlayerProtection {
    private static final Map<ServerPlayerEntity, Integer> PLAYERS = new IdentityHashMap<ServerPlayerEntity, Integer>();

    private PovPlayerProtection() {
    }

    public static synchronized void acquire(ServerPlayerEntity player) {
        PLAYERS.merge(player, 1, Integer::sum);
    }

    public static synchronized void release(ServerPlayerEntity player) {
        Integer count = PLAYERS.get(player);
        if (count == null || count <= 1) {
            PLAYERS.remove(player);
        } else {
            PLAYERS.put(player, count - 1);
        }
    }

    public static synchronized boolean contains(ServerPlayerEntity player) {
        return PLAYERS.containsKey(player);
    }
}

