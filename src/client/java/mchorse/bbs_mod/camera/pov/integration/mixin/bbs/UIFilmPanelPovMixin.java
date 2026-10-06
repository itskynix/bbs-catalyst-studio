/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.dashboard.UIDashboard
 *  mchorse.bbs_mod.ui.film.UIClipsPanel
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.replays.UIReplaysListPanel
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
 *  mchorse.bbs_mod.ui.utils.Gizmo
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.Direction
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.CameraShakePovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ToastPovActionClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.RecorderPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIReplayPropertiesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIReplaysListPanelPovAccess;
import mchorse.bbs_mod.camera.pov.recording.ActiveRecordingRange;
import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysListPanel;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIFilmPanel.class}, remap=false)
public abstract class UIFilmPanelPovMixin
implements UIFilmPanelPovAccess {
    @Shadow
    public UIElement main;
    @Shadow
    public UIClipsPanel cameraEditor;
    @Shadow
    private List<UIElement> panels;
    @Shadow
    private UIElement selectedMainEditorPanel;
    @Unique
    private UIPovEditor bbsPov$editor;
    @Unique
    private UIIcon bbsPov$openEditor;
    @Unique
    private UIKeyframeEditor bbsPov$replayKeyframeEditor;

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void bbsPov$installEditor(UIDashboard dashboard, CallbackInfo info) {
        UIFilmPanel panel = (UIFilmPanel)(Object)this;
        this.bbsPov$editor = new UIPovEditor(panel);
        this.bbsPov$replayKeyframeEditor = panel.replayEditor.keyframeEditor;
        this.bbsPov$editor.full((UIElement)this.cameraEditor);
        this.bbsPov$editor.setVisible(false);
        this.bbsPov$openEditor = new UIIcon(Icons.LOOKING, button -> panel.showPanel((UIElement)this.bbsPov$editor));
        this.bbsPov$openEditor.tooltip(L10n.lang("bbs.pov.ui.editor.open", "Open Point of View Editor"), Direction.BOTTOM);
        this.bbsPov$openEditor.setEnabled(false);
        this.main.add((IUIElement)this.bbsPov$editor);
        this.panels.add(this.bbsPov$editor);
        panel.actions().editor(this.bbsPov$openEditor, () -> this.bbsPov$editor != null && this.bbsPov$editor.isVisible());
    }

    @Inject(method={"fillData(Lmchorse/bbs_mod/film/Film;)V"}, at={@At(value="TAIL")})
    private void bbsPov$fillEditor(Film film, CallbackInfo info) {
        if (this.bbsPov$editor == null) {
            return;
        }
        this.bbsPov$openEditor.setEnabled(film != null);
        this.bbsPov$editor.setFilm(film);
        if (film != null) {
            PovCameraClips.ensureLegacyClip(film);
        }
    }

    @Inject(method={"applyRecordedKeyframes"}, at={@At(value="HEAD")})
    private void bbsPov$closeRecordedPovTracks(Recorder recorder, Film film, CallbackInfo info) {
        boolean isOutside = recorder instanceof RecorderPovAccess recAccess && recAccess.bbsPov$isOutside();
        if (isOutside) {
            ActiveRecordingRange.set(recorder.initialTick, recorder.tick);
        } else {
            ActiveRecordingRange.clear();
        }
        ReplayKeyframes replayKeyframes = recorder.keyframes;
        if (replayKeyframes instanceof ReplayKeyframesPovAccess access) {
            ReplayKeyframes replayKeyframes2;
            Replay replay;
            RecordedHandData hand;
            RecordedHudData hud = access.bbsPov$getHud();
            if (hud != null && hud.hasRecordedData()) {
                hud.addRecordingEndKeyframes(recorder.keyframes, recorder.tick);
            }
            if ((hand = access.bbsPov$getHand()) != null && hand.hasRecordedData()) {
                hand.addRecordingEndKeyframes(recorder.keyframes, recorder.tick);
            }
            if (film != null && film.replays.getList().size() > recorder.exception && (replay = (Replay)film.replays.getList().get(recorder.exception)) != null && (replayKeyframes2 = replay.keyframes) instanceof ReplayKeyframesPovAccess) {
                ReplayKeyframesPovAccess targetAccess = (ReplayKeyframesPovAccess)replayKeyframes2;
                RecordedHudData targetHud = targetAccess.bbsPov$getHud();
                RecordedPovActions recActions = access.bbsPov$getActions();
                RecordedPovActions targetActions = targetAccess.bbsPov$getActions();
                if (isOutside) {
                    if (targetHud != null) {
                        targetHud.trimCursorForRecordingRange(recorder.initialTick, recorder.tick);
                    }
                    if (recActions != null && targetActions != null) {
                        targetActions.trimForRecordingRange(recorder.initialTick, recorder.tick);
                        if (PovSettings.isBakeAnyActions()) {
                            for (Clip clip : recActions.get()) {
                                if (clip instanceof GuiPovActionClip) {
                                    GuiPovActionClip guiClip = (GuiPovActionClip)clip;
                                    guiClip.ensureBakingBounds();
                                    continue;
                                }
                                if (!(clip instanceof CameraShakePovActionClip)) continue;
                                CameraShakePovActionClip shakeClip = (CameraShakePovActionClip)clip;
                                shakeClip.ensureBakingBounds();
                            }
                            for (PovActionClip recorded : recActions.takeSessionClips()) {
                                if (recorded instanceof ToastPovActionClip) {
                                    ToastPovActionClip toastClip = (ToastPovActionClip)recorded;
                                    toastClip.trimToRecording(recorder.tick);
                                }
                                targetActions.addClip(recorded.copy());
                            }
                        }
                        targetActions.sync();
                    }
                } else {
                    if (targetHud != null) {
                        targetHud.cursorLayout.removeAll();
                        targetHud.cursorVisible.removeAll();
                        targetHud.cursorItem.removeAll();
                    }
                    if (targetActions != null) {
                        targetActions.clearAll();
                        if (recActions != null && PovSettings.isBakeAnyActions()) {
                            for (Clip clip : recActions.get()) {
                                if (clip instanceof GuiPovActionClip) {
                                    GuiPovActionClip guiClip = (GuiPovActionClip)clip;
                                    guiClip.ensureBakingBounds();
                                    continue;
                                }
                                if (!(clip instanceof CameraShakePovActionClip)) continue;
                                CameraShakePovActionClip shakeClip = (CameraShakePovActionClip)clip;
                                shakeClip.ensureBakingBounds();
                            }
                            for (PovActionClip recorded : recActions.takeSessionClips()) {
                                if (recorded instanceof ToastPovActionClip) {
                                    ToastPovActionClip toastClip = (ToastPovActionClip)recorded;
                                    toastClip.trimToRecording(recorder.tick);
                                }
                                targetActions.addClip(recorded.copy());
                            }
                        }
                        targetActions.sync();
                    }
                    for (KeyframeChannel channel : replay.keyframes.getChannels()) {
                        if (UIFilmPanelPovMixin.bbsPov$isAuthoredChannel(channel)) continue;
                        channel.removeAll();
                    }
                }
                if (this.bbsPov$editor != null) {
                    this.bbsPov$editor.reloadActions();
                }
            }
        }
    }

    @Inject(method={"applyRecordedKeyframes"}, at={@At(value="TAIL")})
    private void bbsPov$clearActiveRecordingRange(Recorder recorder, Film film, CallbackInfo info) {
        ReplayKeyframesPovAccess targetAccess;
        RecordedHudData targetHud;
        ReplayKeyframes replayKeyframes;
        Replay replay;
        RecorderPovAccess access;
        boolean isOutside;
        ActiveRecordingRange.clear();
        boolean bl = isOutside = recorder instanceof RecorderPovAccess && (access = (RecorderPovAccess)recorder).bbsPov$isOutside();
        if (!isOutside && film != null && film.replays.getList().size() > recorder.exception && (replay = (Replay)film.replays.getList().get(recorder.exception)) != null && (replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess && (targetHud = (targetAccess = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getHud()) != null) {
            targetHud.ensureNativeSlotDefaults(replay.keyframes);
        }
    }

    @Unique
    private static boolean bbsPov$isAuthoredChannel(KeyframeChannel<?> channel) {
        String id = channel.getId();
        if (id == null) {
            return false;
        }
        return id.equals("hotbar_absorption_flash") || id.equals("hotbar_layout") || id.equals("hotbar_visible") || id.equals("hotbar_status_bars_visible") || id.equals("hotbar_crosshair") || id.equals("pov_hand_visible") || id.equals("pov_hand_model") || id.equals("pov_hand_texture") || id.equals("pov_hand_color") || id.equals("pov_hand_camera_offset") || id.equals("pov_hand_pose") || id.equals("pov_hand_item_pose") || id.equals("pov_hand_right_hand_visible") || id.equals("pov_hand_left_hand_visible") || id.equals("pov_hand_right_pose") || id.equals("pov_hand_left_pose") || id.equals("pov_hand_main_arm");
    }

    @Inject(method={"applyRecordedKeyframes"}, at={@At(value="INVOKE", target="Lmchorse/bbs_mod/film/replays/ReplayKeyframes;compressItemChannels()V", shift=At.Shift.AFTER)})
    private void bbsPov$closeNativeItemTracksAfterCompression(Recorder recorder, Film film, CallbackInfo info) {
        ReplayKeyframesPovAccess access;
        RecordedHudData hud;
        ReplayKeyframes replayKeyframes = recorder.keyframes;
        if (replayKeyframes instanceof ReplayKeyframesPovAccess && (hud = (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getHud()) != null) {
            hud.addNativeItemBoundaryKeyframes(recorder.keyframes, recorder.initialTick, recorder.tick);
        }
    }

    @Inject(method={"updateMainEditorVisibility"}, at={@At(value="TAIL")})
    private void bbsPov$updateEditorVisibility(boolean enabled, CallbackInfo info) {
        if (this.bbsPov$editor == null) {
            return;
        }
        boolean active = enabled && this.selectedMainEditorPanel == this.bbsPov$editor;
        this.bbsPov$editor.setVisible(active);
        this.bbsPov$editor.setTimelineVisible(active);
        this.bbsPov$editor.setPropertiesVisible(active);
        this.bbsPov$openEditor.active(active);
        this.bbsPov$syncNativeKeyframeEditor(active);
        this.bbsPov$syncPovMode(active);
    }

    @Inject(method={"showPanel(Lmchorse/bbs_mod/ui/framework/elements/UIElement;)V"}, at={@At(value="TAIL")})
    private void bbsPov$updateButton(UIElement panel, CallbackInfo info) {
        boolean povActive;
        boolean bl = povActive = panel == this.bbsPov$editor;
        if (this.bbsPov$openEditor != null) {
            this.bbsPov$openEditor.active(povActive);
        }
        this.bbsPov$syncNativeKeyframeEditor(povActive);
        this.bbsPov$syncPovMode(povActive);
    }

    @Inject(method={"showPanel(Lmchorse/bbs_mod/ui/framework/elements/UIElement;)V"}, at={@At(value="HEAD")})
    private void bbsPov$stopGizmoBeforeEditorSwitch(UIElement panel, CallbackInfo info) {
        UIFilmPanel filmPanel = (UIFilmPanel)(Object)this;
        if (this.bbsPov$editor != null && (panel == this.bbsPov$editor || this.bbsPov$editor.isVisible())) {
            filmPanel.getController().stopGizmoInteraction();
            Gizmo.INSTANCE.stop();
        }
    }

    @Unique
    private void bbsPov$syncNativeKeyframeEditor(boolean povActive) {
        if (this.bbsPov$editor == null || this.bbsPov$replayKeyframeEditor == null) {
            return;
        }
        UIFilmPanel panel = (UIFilmPanel)(Object)this;
        panel.replayEditor.keyframeEditor = povActive ? this.bbsPov$editor.keyframeEditor : this.bbsPov$replayKeyframeEditor;
    }

    @Override
    public UIPovEditor bbsPov$getEditor() {
        return this.bbsPov$editor;
    }

    @Override
    public boolean bbsPov$isPovActive() {
        return this.bbsPov$editor != null && this.selectedMainEditorPanel == this.bbsPov$editor && this.bbsPov$editor.isVisible();
    }

    @Unique
    private void bbsPov$syncPovMode(boolean povActive) {
        UIFilmPanel panel = (UIFilmPanel)(Object)this;
        if (panel.replayEditor != null) {
            if (panel.replayEditor.replaysList instanceof UIReplaysListPanelPovAccess access) {
                access.bbsPov$setPovMode(povActive);
            }
            if (panel.replayEditor.replayProperties instanceof UIReplayPropertiesPovAccess access) {
                access.bbsPov$setPovMode(povActive);
            }
        }
    }

    @Inject(method={"togglePlayback"}, at={@At(value="TAIL")})
    private void bbsPov$shiftPovTimelineOnPlay(CallbackInfo info) {
        UIFilmPanel panel = (UIFilmPanel)(Object)this;
        if (panel.isRunning() && this.bbsPov$editor != null && this.bbsPov$isPovActive()) {
            if (this.bbsPov$editor.actionTimeline != null && this.bbsPov$editor.actionTimeline.clips != null) {
                this.bbsPov$editor.actionTimeline.clips.getXAxis().shiftIntoMiddle((double)panel.getCursor());
            }
            if (this.bbsPov$editor.keyframeEditor != null && this.bbsPov$editor.keyframeEditor.view != null) {
                this.bbsPov$editor.keyframeEditor.view.getXAxis().shiftIntoMiddle((double)panel.getCursor());
            }
        }
    }
}

