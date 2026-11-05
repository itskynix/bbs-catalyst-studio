package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.ui.replays.LightTrackLayout;

@Mixin(value = UIKeyframeSheet.class, remap = false)
public abstract class UIKeyframeSheetMixin
{
    @Shadow public UIKeyframeSheet.Section section;

    @Inject(method = "<init>(Lmchorse/bbs_mod/film/replays/tracks/TrackDescriptor;)V", at = @At("TAIL"))
    private void irlite$lightSection(TrackDescriptor track, CallbackInfo ci)
    {
        UIKeyframeSheet.Section section = LightTrackLayout.sectionFor(track);

        if (section != null)
        {
            this.section = section;
        }
    }
}

