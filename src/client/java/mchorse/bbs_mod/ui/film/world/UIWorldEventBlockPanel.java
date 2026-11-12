package mchorse.bbs_mod.ui.film.world;

import mchorse.bbs.compat.mtr.MtrCompatHelper;
import mchorse.bbs_mod.film.world.MtrDispatchTrack;
import mchorse.bbs_mod.film.world.WorldEventBlock;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Right-hand inspector panel for editing a selected {@link WorldEventBlock} and its {@link MtrDispatchTrack}.
 */
public class UIWorldEventBlockPanel extends UIElement
{
    public final UIWorldTimelinePanel editor;
    public final WorldEventBlock block;

    public UITextbox title;
    public UIToggle enabled;
    public UITrackpad startTick;
    public UITrackpad duration;
    public UITrackpad layer;

    public UITextbox sidingId;
    public UIButton pickVehicle;
    public UITrackpad targetStationId;
    public UITrackpad dwellTimeTicks;

    public UITrackpad dispatchTick;
    public UITrackpad openDoorsTick;
    public UITrackpad closeDoorsTick;
    public UITrackpad hornTick;
    public UIButton openSubDopeSheet;

    public UIWorldEventBlockPanel(UIWorldTimelinePanel editor, WorldEventBlock block)
    {
        super();

        this.editor = editor;
        this.block = block;

        this.column(5).vertical().stretch().scroll().padding(10);

        this.title = new UITextbox(120, (text) ->
        {
            this.editor.timeline.markDirtyBeforeEdit();
            this.block.title = text;
            this.editor.timeline.notifyEdited();
        });

        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            this.editor.timeline.markDirtyBeforeEdit();
            this.block.enabled = b.getValue();
            this.editor.timeline.notifyEdited();
        });

        this.startTick = new UITrackpad((v) ->
        {
            this.editor.timeline.markDirtyBeforeEdit();
            this.block.startTick = Math.max(0, v.intValue());
            this.editor.timeline.notifyEdited();
        }).integer().limit(0);

        this.duration = new UITrackpad((v) ->
        {
            this.editor.timeline.markDirtyBeforeEdit();
            this.block.duration = Math.max(1, v.intValue());
            this.editor.timeline.notifyEdited();
        }).integer().limit(1);

        this.layer = new UITrackpad((v) ->
        {
            this.editor.timeline.markDirtyBeforeEdit();
            this.block.layer = Math.max(0, v.intValue());
            this.editor.timeline.updateLayers();
            this.editor.timeline.notifyEdited();
        }).integer().limit(0);

        this.sidingId = new UITextbox(120, (text) ->
        {
            this.editor.timeline.markDirtyBeforeEdit();
            this.block.subDopeSheet.getPrimaryTrack().sidingId = text.trim();
            this.editor.timeline.notifyEdited();
        });

        this.pickVehicle = new UIButton(UIKeys.WORLD_TIMELINE_PANEL_PICK_VEHICLE, (b) ->
        {
            String detected = MtrCompatHelper.detectNearestVehicleOrSidingId();

            if (detected != null && !detected.isEmpty())
            {
                this.editor.timeline.markDirtyBeforeEdit();
                this.block.subDopeSheet.getPrimaryTrack().sidingId = detected;
                this.sidingId.setText(detected);
                this.editor.timeline.notifyEdited();
            }
        });

        this.targetStationId = new UITrackpad((v) ->
        {
            this.editor.timeline.markDirtyBeforeEdit();
            this.block.subDopeSheet.getPrimaryTrack().targetStationId = v.longValue();
            this.editor.timeline.notifyEdited();
        }).integer();

        this.dwellTimeTicks = new UITrackpad((v) ->
        {
            MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

            this.editor.timeline.markDirtyBeforeEdit();
            track.setDwellTimeTicks(v.intValue());
            this.closeDoorsTick.setValue(track.closeDoorsTick);
            this.editor.timeline.notifyEdited();
        }).integer().limit(1);

        this.dispatchTick = new UITrackpad((v) ->
        {
            MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

            this.editor.timeline.markDirtyBeforeEdit();
            track.setDispatchTick(v.intValue());
            this.fillData();
            this.editor.timeline.notifyEdited();
        }).integer().limit(0);

        this.openDoorsTick = new UITrackpad((v) ->
        {
            MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

            this.editor.timeline.markDirtyBeforeEdit();
            track.setOpenDoorsTick(v.intValue());
            this.fillData();
            this.editor.timeline.notifyEdited();
        }).integer().limit(0);

        this.closeDoorsTick = new UITrackpad((v) ->
        {
            MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

            this.editor.timeline.markDirtyBeforeEdit();
            track.setCloseDoorsTick(v.intValue());
            this.fillData();
            this.editor.timeline.notifyEdited();
        }).integer().limit(1);

        this.hornTick = new UITrackpad((v) ->
        {
            MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

            this.editor.timeline.markDirtyBeforeEdit();
            track.setHornTick(v.intValue());
            this.fillData();
            this.editor.timeline.notifyEdited();
        }).integer().limit(-1);

        this.openSubDopeSheet = new UIButton(UIKeys.WORLD_TIMELINE_CONTEXT_EDIT_SUB_DOPE, (b) ->
        {
            this.editor.openSubDopeSheet(this.block);
        });

        this.add(UI.label(UIKeys.CAMERA_PANELS_TITLE), this.title);
        this.add(this.enabled);
        this.add(UI.label(UIKeys.CAMERA_PANELS_TICK), this.startTick);
        this.add(UI.label(UIKeys.CAMERA_PANELS_DURATION), this.duration);
        this.add(UI.label(UIKeys.CAMERA_PANELS_LAYER), this.layer);

        this.add(UI.label(UIKeys.WORLD_TIMELINE_PANEL_SIDING_ID).marginTop(6), this.sidingId, this.pickVehicle);
        this.add(UI.label(UIKeys.WORLD_TIMELINE_PANEL_TARGET_STATION), this.targetStationId);
        this.add(UI.label(UIKeys.WORLD_TIMELINE_PANEL_DWELL_TIME), this.dwellTimeTicks);

        this.add(UI.label(UIKeys.WORLD_TIMELINE_PANEL_KF_DISPATCH).marginTop(6), this.dispatchTick);
        this.add(UI.label(UIKeys.WORLD_TIMELINE_PANEL_KF_OPEN_DOORS), this.openDoorsTick);
        this.add(UI.label(UIKeys.WORLD_TIMELINE_PANEL_KF_CLOSE_DOORS), this.closeDoorsTick);
        this.add(UI.label(UIKeys.WORLD_TIMELINE_PANEL_KF_HORN), this.hornTick);
        this.add(this.openSubDopeSheet.marginTop(6));

        this.fillData();
    }

    public void fillData()
    {
        if (this.block == null)
        {
            return;
        }

        MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

        this.title.setText(this.block.title == null ? "" : this.block.title);
        this.enabled.setValue(this.block.enabled);
        this.startTick.setValue(this.block.startTick);
        this.duration.setValue(this.block.duration);
        this.layer.setValue(this.block.layer);

        this.sidingId.setText(track.sidingId == null ? "" : track.sidingId);
        this.targetStationId.setValue((double) track.targetStationId);
        this.dwellTimeTicks.setValue(track.dwellTimeTicks);

        this.dispatchTick.setValue(track.dispatchTick);
        this.openDoorsTick.setValue(track.openDoorsTick);
        this.closeDoorsTick.setValue(track.closeDoorsTick);
        this.hornTick.setValue(track.hornTick);
    }
}
