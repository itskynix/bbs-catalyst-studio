package mchorse.bbs_mod.recap;

/**
 * Everything a recap recorded besides its own file, together: the camera, the player's movement and
 * what it had on it, the blocks, the sky, the other entities, the sounds and the particles. Any of
 * them is null for a recap kept before it was recorded.
 */
public class RecapData
{
    public RecapTrack track;
    public RecapMotion motion;
    public RecapEvents events;
    public RecapEnvironment environment;
    public RecapGear gear;
    public RecapEntities entities;
    public RecapSounds sounds;
    public RecapParticles particles;
    public RecapHudStream hud;
    public RecapBaseline baseline;
    public RecapPov pov;

    public static RecapData load(RecapRecord record)
    {
        RecapData data = new RecapData();

        data.track = RecapStore.loadTrack(record);
        data.motion = RecapStore.loadMotion(record);
        data.events = RecapStore.loadEvents(record);
        data.environment = RecapStore.loadEnvironment(record);
        data.gear = RecapStore.loadGear(record);
        data.entities = RecapStore.loadEntities(record);
        data.sounds = RecapStore.loadSounds(record);
        data.particles = RecapStore.loadParticles(record);
        data.hud = RecapStore.loadHud(record);
        data.baseline = RecapStore.loadBaseline(record);
        data.pov = RecapStore.loadPov(record);

        return data;
    }

    public boolean hasEntities()
    {
        return this.entities != null && !this.entities.isEmpty();
    }

    public boolean hasSounds()
    {
        return this.sounds != null;
    }

    public boolean hasParticles()
    {
        return this.particles != null;
    }

    public boolean hasHud()
    {
        return this.hud != null && !this.hud.isEmpty();
    }

    public boolean hasBaseline()
    {
        return this.baseline != null && !this.baseline.isEmpty();
    }
}
