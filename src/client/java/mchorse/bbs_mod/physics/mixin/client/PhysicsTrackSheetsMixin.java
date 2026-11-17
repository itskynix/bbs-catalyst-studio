package mchorse.bbs_mod.physics.mixin.client;

import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import mchorse.bbs_mod.physics.client.forms.PhysicsTracks;

import java.util.List;

/** Groups the physics tracks of the dope sheet once BBS CS has built its sheets. */
@Mixin(value = UIReplaysEditorUtils.class, remap = false)
public class PhysicsTrackSheetsMixin
{
    @Inject(method = "buildSheets", at = @At("RETURN"))
    private static void bbs_physics$group(List<TrackDescriptor> catalog, List<UIKeyframeSheet> sheets, CallbackInfo info)
    {
        PhysicsTracks.group(sheets);
    }
}
