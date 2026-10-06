/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.BaseFilmController
 *  mchorse.bbs_mod.film.Films
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.integration.access.bbs.FilmsPovAccess;
import java.util.List;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={Films.class}, remap=false)
public interface FilmsPovAccessor
extends FilmsPovAccess {
    @Override
    @Accessor(value="controllers")
    public List<BaseFilmController> bbsPov$getControllers();
}

