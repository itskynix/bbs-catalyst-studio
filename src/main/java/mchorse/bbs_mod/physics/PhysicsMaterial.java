package mchorse.bbs_mod.physics;

/** The ready-made materials of the physics studio: what a body weighs by volume, how it grips and how it bounces */
public enum PhysicsMaterial
{
    FEATHER("feather", 20F, 0.4F, 0.3F),
    WOOD("wood", 600F, 0.5F, 0.25F),
    STONE("stone", 2400F, 0.7F, 0.1F),
    METAL("metal", 7800F, 0.4F, 0.15F),
    HEAVY("heavy", 12000F, 0.8F, 0.02F);

    public final String id;
    /** Kilograms per cubic metre; a body's mass is this times the volume of its collision box, unless an author sets one */
    public final float density;
    public final float friction;
    public final float restitution;

    PhysicsMaterial(String id, float density, float friction, float restitution)
    {
        this.id = id;
        this.density = density;
        this.friction = friction;
        this.restitution = restitution;
    }

    public static PhysicsMaterial byId(String id)
    {
        for (PhysicsMaterial material : values())
        {
            if (material.id.equals(id))
            {
                return material;
            }
        }

        return WOOD;
    }
}
