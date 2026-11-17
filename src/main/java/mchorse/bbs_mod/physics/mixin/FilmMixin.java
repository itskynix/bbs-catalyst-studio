package mchorse.bbs_mod.physics.mixin;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import mchorse.bbs_mod.physics.engine.FilmPhysics;
import mchorse.bbs_mod.physics.engine.PhysicsFilm;

/** Gives every film its physics knobs, saved, loaded and undone with the rest of the film. */
@Mixin(Film.class)
public class FilmMixin implements PhysicsFilm
{
    @Unique private final FilmPhysics bbs_physics$physics = new FilmPhysics();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbs_physics$values(CallbackInfo ci)
    {
        ((ValueGroup) (Object) this).add(this.bbs_physics$physics);
    }

    @Override
    public FilmPhysics bbs_physics$physics()
    {
        return this.bbs_physics$physics;
    }
}
