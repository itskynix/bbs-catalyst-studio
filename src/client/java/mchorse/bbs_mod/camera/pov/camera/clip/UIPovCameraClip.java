package mchorse.bbs_mod.camera.pov.camera.clip;

import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;

public class UIPovCameraClip extends UIClip<PovCameraClip> {
   public UIButton selector;
   public UIToggle hands;
   public UIToggle hud;
   public UIToggle crosshair;
   public UIToggle actions;
   public UIToggle screenEffects;
   public UIToggle cursor;
   public UIToggle cameraShake;
   public UIToggle headLook;
   public UIToggle hardcoreLook;
   public UIToggle blockOutline;
   public UIButton perspective;
   public UITrackpad fov;

   public UIPovCameraClip(PovCameraClip clip, IUIClipsDelegate editor) {
      super(clip, editor);
   }

   protected void registerUI() {
      super.registerUI();
      this.selector = new UIButton(L10n.lang("bbs.pov.clip.selector", "Replay Source"), button -> {
         UIFilmPanel panel = (UIFilmPanel)this.getParent(UIFilmPanel.class);
         if (panel != null) {
            Film film = (Film)panel.getData();
            List<Replay> replays = film != null ? film.replays.getList() : List.of();
            int currentIndex = (Integer)((PovCameraClip)this.clip).selector.get();
            String currentId = currentIndex >= 0 && currentIndex < replays.size() ? replays.get(currentIndex).getId() : "";
            UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), currentId, selectedId -> {
               int foundIndex = -1;

               for (int i = 0; i < replays.size(); i++) {
                  if (replays.get(i).getId().equals(selectedId)) {
                     foundIndex = i;
                     break;
                  }
               }

               int finalIndex = foundIndex;
               this.editor.editMultiple(((PovCameraClip)this.clip).selector, value -> value.set(finalIndex));
            });
         }
      });
      this.selector.tooltip(L10n.lang("bbs.pov.clip.selector.tooltip", "Choose the replay that supplies this Point of View clip"));
      this.hands = new UIToggle(
         L10n.lang("bbs.pov.clip.hands", "Hands"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).hands, value -> value.set(toggle.getValue()))
      );
      this.hands.tooltip(L10n.lang("bbs.pov.clip.hands.tooltip", "Render this replay's POV hands. Click-selection and hand gizmos still require POV Camera Mode."));
      this.hud = new UIToggle(L10n.lang("bbs.pov.clip.hud", "HUD"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).hud, value -> value.set(toggle.getValue())));
      this.crosshair = new UIToggle(
         L10n.lang("bbs.pov.clip.crosshair", "Crosshair"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).crosshair, value -> value.set(toggle.getValue()))
      );
      this.crosshair.tooltip(L10n.lang("bbs.pov.clip.crosshair.tooltip", "Render the replay's recorded crosshair independently from the HUD"));
      this.actions = new UIToggle(
         L10n.lang("bbs.pov.clip.actions", "POV Actions"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).actions, value -> value.set(toggle.getValue()))
      );
      this.screenEffects = new UIToggle(
         L10n.lang("bbs.pov.clip.screen_effects", "Screen Effects"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).screenEffects, value -> value.set(toggle.getValue()))
      );
      this.screenEffects.tooltip(L10n.lang("bbs.pov.clip.screen_effects.tooltip", "Render recorded screen effects (vignettes, spyglass, etc.)"));
      this.cursor = new UIToggle(
         L10n.lang("bbs.pov.clip.cursor", "Cursor"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).cursor, value -> value.set(toggle.getValue()))
      );
      this.cursor.tooltip(L10n.lang("bbs.pov.clip.cursor.tooltip", "Render the recorded GUI cursor and its hover behavior"));
      this.cameraShake = new UIToggle(
         L10n.lang("bbs.pov.clip.camera_shake", "Camera Shake"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).cameraShake, value -> value.set(toggle.getValue()))
      );
      this.cameraShake.tooltip(L10n.lang("bbs.pov.clip.camera_shake.tooltip", "Apply baked Camera Shake action clips while this POV camera clip is active"));
      this.headLook = new UIToggle(L10n.lang("bbs.pov.clip.head_look", "Head Look"), toggle -> {
         this.editor.editMultiple(((PovCameraClip)this.clip).headLook, value -> value.set(toggle.getValue()));
         if (this.hardcoreLook != null) {
            this.hardcoreLook.setEnabled(toggle.getValue());
         }

         if (this.blockOutline != null) {
            this.blockOutline.setEnabled(toggle.getValue());
         }

         if (this.fov != null) {
            this.fov.setEnabled(toggle.getValue());
         }

         if (this.perspective != null) {
            this.perspective.setEnabled(toggle.getValue());
         }
      });
      this.headLook.tooltip(L10n.lang("bbs.pov.clip.head_look.tooltip", "Use the replay's recorded head position and look direction for the camera"));
      this.hardcoreLook = new UIToggle(
         L10n.lang("bbs.pov.clip.hardcore_look", "Hardcore Look"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).hardcoreLook, value -> value.set(toggle.getValue()))
      );
      this.hardcoreLook.tooltip(L10n.lang("bbs.pov.clip.hardcore_look.tooltip", "Follow the actual head bone position and rotation from pose, transform, and overlay keyframes"));
      this.blockOutline = new UIToggle(
         L10n.lang("bbs.pov.clip.block_outline", "Block Outline"), toggle -> this.editor.editMultiple(((PovCameraClip)this.clip).blockOutline, value -> value.set(toggle.getValue()))
      );
      this.blockOutline.tooltip(L10n.lang("bbs.pov.clip.block_outline.tooltip", "Show block outline when looking at a block in POV camera mode"));
      this.perspective = new UIButton(PovCameraClip.getPerspectiveKey((Integer)((PovCameraClip)this.clip).perspective.get()), button -> {
         int next = PovCameraClip.nextPerspective((Integer)((PovCameraClip)this.clip).perspective.get());
         boolean firstPerson = next == 0;
         this.editor.editMultiple(((PovCameraClip)this.clip).perspective, value -> value.set(next));
         this.editor.editMultiple(((PovCameraClip)this.clip).hands, value -> value.set(firstPerson));
         this.editor.editMultiple(((PovCameraClip)this.clip).crosshair, value -> value.set(firstPerson));
         this.updatePerspectiveButton();
         this.hands.setValue(firstPerson);
         this.crosshair.setValue(firstPerson);
      });
      this.perspective.tooltip(L10n.lang("bbs.pov.clip.perspective.tooltip", "Cycle camera view: First Person, Third Person (Back), Third Person (Front)"));
      this.fov = new UITrackpad(value -> this.editor.editMultiple(((PovCameraClip)this.clip).fov, field -> field.set(value.floatValue())));
      this.fov.limit(1.0, 180.0);
      this.fov.tooltip(L10n.lang("bbs.pov.clip.fov.tooltip", "Camera field of view"));
   }

   private void updatePerspectiveButton() {
      if (this.perspective != null) {
         this.perspective.label = PovCameraClip.getPerspectiveKey((Integer)((PovCameraClip)this.clip).perspective.get());
      }
   }

   protected void registerPanels() {
      super.registerPanels();
      this.panels.add(this.section(L10n.lang("bbs.pov.clip.section.replay_source", "Replay Source"), new UIElement[]{this.selector}));
      this.panels
         .add(
            this.section(
               L10n.lang("bbs.pov.clip.section.pov_output", "POV Output"),
               new UIElement[]{this.hands, this.hud, this.crosshair, this.actions, this.screenEffects, this.cursor, this.cameraShake}
            )
         );
      this.panels.add(this.section(L10n.lang("bbs.pov.clip.section.camera", "Camera"), new UIElement[]{this.headLook, this.hardcoreLook, this.blockOutline, this.perspective, this.fov}));
   }

   public void fillData() {
      super.fillData();
      this.hands.setValue((Boolean)((PovCameraClip)this.clip).hands.get());
      this.hud.setValue((Boolean)((PovCameraClip)this.clip).hud.get());
      this.crosshair.setValue((Boolean)((PovCameraClip)this.clip).crosshair.get());
      this.actions.setValue((Boolean)((PovCameraClip)this.clip).actions.get());
      this.screenEffects.setValue((Boolean)((PovCameraClip)this.clip).screenEffects.get());
      this.cursor.setValue((Boolean)((PovCameraClip)this.clip).cursor.get());
      this.cameraShake.setValue((Boolean)((PovCameraClip)this.clip).cameraShake.get());
      this.headLook.setValue((Boolean)((PovCameraClip)this.clip).headLook.get());
      this.hardcoreLook.setValue((Boolean)((PovCameraClip)this.clip).hardcoreLook.get());
      this.hardcoreLook.setEnabled((Boolean)((PovCameraClip)this.clip).headLook.get());
      this.blockOutline.setValue((Boolean)((PovCameraClip)this.clip).blockOutline.get());
      this.blockOutline.setEnabled((Boolean)((PovCameraClip)this.clip).headLook.get());
      this.updatePerspectiveButton();
      this.perspective.setEnabled((Boolean)((PovCameraClip)this.clip).headLook.get());
      this.fov.setValue((double)((Float)((PovCameraClip)this.clip).fov.get()).floatValue());
      this.fov.setEnabled((Boolean)((PovCameraClip)this.clip).headLook.get());
   }
}
