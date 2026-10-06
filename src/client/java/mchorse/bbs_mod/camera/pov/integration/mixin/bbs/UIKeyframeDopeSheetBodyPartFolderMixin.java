/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet
 *  mchorse.bbs_mod.ui.utils.Area
 *  net.minecraft.client.render.BufferBuilder
 *  org.joml.Matrix4f
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.bodypart.PovBodyPartFolderSheet;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIKeyframeDopeSheet.class}, remap=false)
public class UIKeyframeDopeSheetBodyPartFolderMixin {
    @Inject(method={"renderSheet"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$hideFolderTrack(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int y, CallbackInfo info) {
        if (sheet instanceof PovBodyPartFolderSheet) {
            info.cancel();
        }
    }

    @Inject(method={"renderSheetKeyframeShapes"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$hideFolderKeyShapes(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int y, CallbackInfo info) {
        if (sheet instanceof PovBodyPartFolderSheet) {
            info.cancel();
        }
    }

    @Inject(method={"getSheetIndent"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$alignBodyPartChildren(UIKeyframeSheet sheet, CallbackInfoReturnable<Integer> info) {
        if (sheet.id.startsWith("pose_overlay") || sheet.id.matches("[^/]+/(visible|lighting|transform|transform_overlay.*|texture|pose|pose_overlay.*|color|color_overlay.*|actions)")) {
            info.setReturnValue(0);
        } else if (sheet.id.matches("[^/]+/(bone:.*|pose\\.bones\\..*)")) {
            int depth = Math.max(0, sheet.getDepth() - 1);
            info.setReturnValue((4 + depth * 4));
        }
    }

    @Inject(method={"renderSectionKeyframes"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$hideSectionKeyframes(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, CallbackInfo info) {
        info.cancel();
    }
}

