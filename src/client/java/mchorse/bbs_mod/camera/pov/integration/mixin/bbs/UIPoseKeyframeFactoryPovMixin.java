package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.PovItemPose;
import mchorse.bbs_mod.camera.pov.hand.editor.HandBoneUtils;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import java.util.LinkedHashSet;
import java.util.Set;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory.UIPoseFactoryEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {UIPoseKeyframeFactory.class},
   remap = false
)
public class UIPoseKeyframeFactoryPovMixin {
   @Shadow
   public UIPoseFactoryEditor poseEditor;

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void bbsPov$filterHandBones(Keyframe<Pose> keyframe, UIKeyframes editor, CallbackInfo info) {
      UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);
      if (sheet != null && sheet.channel != null && "pov_hand_item_pose".equals(sheet.channel.getId())) {
         this.poseEditor.setPose((Pose)keyframe.getValue(), "");
         this.poseEditor.fillGroups(PovItemPose.BONES, false);
      } else if (sheet != null && sheet.form instanceof ModelForm form) {
         if (sheet.id == null || !sheet.id.contains("/")) {
            String channelId = sheet.channel != null && sheet.channel.getId() != null ? sheet.channel.getId() : "";
            boolean isHandPose = false;
            if (UIPovHandEditor.isActive()) {
               isHandPose = true;
            } else if (channelId.startsWith("pov_hand_")) {
               isHandPose = true;
            } else if (PovReplaySettings.getFilmPanel() instanceof UIFilmPanelPovAccess access && access.bbsPov$getEditor() != null) {
               UIPovEditor povEditor = access.bbsPov$getEditor();
               if (form == povEditor.getHandEditorForm() || editor == povEditor.keyframeEditor.view && povEditor.isHandSection()) {
                  isHandPose = true;
               }
            }

            if (isHandPose) {
               if (FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer) {
                  ModelInstance model = renderer.getModel();
                  HandBoneUtils.HandBones var18 = HandBoneUtils.collect(model);
                  if (model != null && !var18.depths().isEmpty()) {
                     Set<String> hidden = new LinkedHashSet<>();

                     for (String bone : model.getModel().getGroupKeysInHierarchyOrder()) {
                        if (!var18.contains(bone)) {
                           hidden.add(bone);
                        }
                     }

                     this.poseEditor.fillGroups(model.getModel(), model.getFlippedParts(), false, hidden);
                     return;
                  }

                  return;
               }
            }
         }
      }
   }
}
