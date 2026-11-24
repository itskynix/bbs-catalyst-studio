package mchorse.bbs_mod.triggers;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The values of global and per entity flow variables, saved with the world (in the overworld's
 * data folder, {@code bbs_flow_variables.dat}). Global ones are keyed by the variable's name, so two
 * boxes that both have a global "score" share it; entity ones by the entity's UUID and the name.
 */
public class FlowGlobals extends PersistentState
{
    private static final String ID = "bbs_flow_variables";

    private static final Type<FlowGlobals> TYPE = new Type<>(FlowGlobals::new, FlowGlobals::read, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, String> global = new HashMap<>();
    private final Map<String, String> entity = new HashMap<>();

    public static FlowGlobals get(MinecraftServer server)
    {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    public String getGlobal(String name)
    {
        return this.global.get(name);
    }

    public void setGlobal(String name, String value)
    {
        if (!value.equals(this.global.put(name, value)))
        {
            this.markDirty();
        }
    }

    public String getEntity(UUID uuid, String name)
    {
        return this.entity.get(uuid + "/" + name);
    }

    public void setEntity(UUID uuid, String name, String value)
    {
        if (!value.equals(this.entity.put(uuid + "/" + name, value)))
        {
            this.markDirty();
        }
    }

    private static FlowGlobals read(NbtCompound nbt)
    {
        FlowGlobals globals = new FlowGlobals();
        NbtCompound global = nbt.getCompound("global");
        NbtCompound entity = nbt.getCompound("entity");

        for (String key : global.getKeys()) globals.global.put(key, global.getString(key));
        for (String key : entity.getKeys()) globals.entity.put(key, entity.getString(key));

        return globals;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt)
    {
        NbtCompound global = new NbtCompound();
        NbtCompound entity = new NbtCompound();

        this.global.forEach(global::putString);
        this.entity.forEach(entity::putString);
        nbt.put("global", global);
        nbt.put("entity", entity);

        return nbt;
    }
}
