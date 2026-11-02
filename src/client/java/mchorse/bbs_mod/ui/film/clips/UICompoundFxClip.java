package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.fx.CompoundFxClip;
import mchorse.bbs_mod.camera.clips.fx.CompoundFxPresets;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIClipTypeMenu;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.IFactory;

import java.util.List;

/**
 * Compound FX Clip UI: Davinci Resolve / Premiere Pro style compound clip & nesting container editor.
 * Displays embedded sub-clips, allows adding/editing/removing nested FX clips,
 * opening sub-clip editors in overlays, decomposing back to timeline, and presets.
 */
public class UICompoundFxClip extends UIFxClip<CompoundFxClip>
{
    /* Presets */
    public UIButton terminalNight;
    public UIButton nisyanLobby;
    public UIButton mentalBreakdown;
    public UIButton loadPreset;
    public UIButton savePreset;

    /* Nesting controls */
    public UIButton decomposeButton;
    public UIButton addChildButton;
    public UIElement nestedContainer;


    public UICompoundFxClip(CompoundFxClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        /* Presets */
        this.terminalNight = new UIButton(UIKeys.CAMERA_CLIPS_COMPOUND_FX_PRESET_TERMINAL_NIGHT, (b) ->
        {
            CompoundFxPresets.applyTerminalNight(this.clip);
            this.rebuildChildrenList();
        });

        this.nisyanLobby = new UIButton(UIKeys.CAMERA_CLIPS_COMPOUND_FX_PRESET_NISYAN_LOBBY, (b) ->
        {
            CompoundFxPresets.applyNisyanLobbySickYellow(this.clip);
            this.rebuildChildrenList();
        });

        this.mentalBreakdown = new UIButton(UIKeys.CAMERA_CLIPS_COMPOUND_FX_PRESET_MENTAL_BREAKDOWN, (b) ->
        {
            CompoundFxPresets.applyMentalBreakdownRed(this.clip);
            this.rebuildChildrenList();
        });

        this.loadPreset = new UIButton(UIKeys.CAMERA_CLIPS_COMPOUND_FX_LOAD_PRESET, (b) -> this.openLoadPresetOverlay());
        this.savePreset = new UIButton(UIKeys.CAMERA_CLIPS_COMPOUND_FX_SAVE_PRESET, (b) -> this.openSavePresetOverlay());

        /* Nesting buttons */
        this.decomposeButton = new UIButton(UIKeys.CAMERA_CLIPS_COMPOUND_FX_DECOMPOSE, (b) -> this.decompose());
        this.addChildButton = new UIButton(UIKeys.CAMERA_CLIPS_COMPOUND_FX_ADD_CHILD, (b) -> this.openAddChildMenu());

        this.nestedContainer = UI.column();

    }

    @Override
    protected boolean hasKeyframesSection()
    {
        return false;
    }

    @Override
    protected void addFxPanels()
    {
        /* 1. Presets section */
        this.panels.add(this.section(
            UIKeys.CAMERA_CLIPS_COMPOUND_FX_PRESETS,
            this.terminalNight,
            this.nisyanLobby,
            this.mentalBreakdown,
            UI.row(this.loadPreset, this.savePreset).marginTop(UIConstants.SECTION_GAP)
        ));

        /* 2. Nesting container section (Compound Clip Children) */
        this.panels.add(this.section(
            UIKeys.CAMERA_CLIPS_COMPOUND_FX_NESTED_CLIPS,
            UI.row(this.addChildButton, this.decomposeButton),
            this.nestedContainer
        ));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.rebuildChildrenList();
    }

    public void rebuildChildrenList()
    {
        this.nestedContainer.removeAll();

        List<Clip> children = this.clip.children.get();

        if (children.isEmpty())
        {
            this.nestedContainer.add(UI.label(UIKeys.CAMERA_CLIPS_COMPOUND_FX_NO_CHILDREN));
        }
        else
        {
            for (Clip child : children)
            {
                String displayName = this.editor.getClipDisplayName(child);
                String timeInfo = "[" + TimeUtils.toTime(child.tick.get()) + " - " + TimeUtils.toTime(child.tick.get() + child.duration.get()) + "]";

                UIButton editButton = new UIButton(UIKeys.CAMERA_CLIPS_COMPOUND_FX_EDIT_CHILD, (b) -> this.openChildEditor(child));
                editButton.w(50);

                UIIcon removeButton = new UIIcon(Icons.CLOSE, (b) ->
                {
                    this.clip.children.remove(child);
                    this.rebuildChildrenList();
                });
                removeButton.w(16);

                UIElement row = UI.row(
                    UI.label(IKey.constant(displayName + " " + timeInfo)),
                    editButton,
                    removeButton
                ).marginTop(2);

                this.nestedContainer.add(row);
            }
        }

        this.resize();
    }

    public void openChildEditor(Clip child)
    {
        UIChildClipOverlay overlay = new UIChildClipOverlay(child, this.editor);

        UIOverlay.addOverlay(this.getContext(), overlay, 0.45F, 0.75F);
    }

    private void openAddChildMenu()
    {
        IFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();

        this.getContext().replaceContextMenu((menu) ->
        {
            for (Link type : UIClipTypeMenu.effectTypes(factory))
            {
                ClipFactoryData data = factory.getData(type);

                menu.action(data.icon, UIKeys.C_CLIP.get(type), data.color, () -> this.addChild(factory.create(type)));
            }
        });
    }

    private void addChild(Clip newChild)
    {
        newChild.tick.set(0);
        newChild.duration.set(this.clip.duration.get());
        newChild.layer.set(this.clip.children.getTopLayer() + 1);

        this.clip.children.addClip(newChild);
        this.clip.children.sortLayers();
        this.rebuildChildrenList();
    }

    private void decompose()
    {
        if (this.editor instanceof UIClipsPanel panel && panel.clips != null)
        {
            panel.clips.decomposeCompound(this.clip);
        }
    }

    @Override
    public boolean openSubEditor()
    {
        List<Clip> children = this.clip.children.get();

        if (!children.isEmpty())
        {
            this.openChildEditor(children.get(0));

            return true;
        }

        return super.openSubEditor();
    }

    private void openLoadPresetOverlay()
    {
        List<String> presets = CompoundFxPresets.getAvailablePresets();

        UIStringOverlayPanel panel = new UIStringOverlayPanel(
            UIKeys.CAMERA_CLIPS_COMPOUND_FX_LOAD_PRESET,
            false,
            presets,
            (selected) ->
            {
                if (selected != null && !selected.isEmpty())
                {
                    CompoundFxPresets.applyPreset(this.clip, selected);
                    this.rebuildChildrenList();
                }
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel, 220, 260);
    }

    private void openSavePresetOverlay()
    {
        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(
            UIKeys.CAMERA_CLIPS_COMPOUND_FX_SAVE_PRESET,
            UIKeys.CAMERA_CLIPS_COMPOUND_FX_PRESET_NAME,
            (name) ->
            {
                if (name != null && !name.trim().isEmpty())
                {
                    CompoundFxPresets.saveCustomPreset(name.trim(), this.clip);
                }
            }
        );

        UIOverlay.addOverlay(this.getContext(), prompt, 0.4F, 0.5F);
    }

    /**
     * Overlay panel hosting a nested sub-clip's full UI editor panel.
     */
    public static class UIChildClipOverlay extends UIOverlayPanel
    {
        public UIChildClipOverlay(Clip child, IUIClipsDelegate delegate)
        {
            super(IKey.constant(delegate.getClipDisplayName(child)));

            UIClip childPanel = UIClip.createPanel(child, delegate);

            if (childPanel != null)
            {
                childPanel.relative(this.content).w(1F).h(1F);
                this.content.add(childPanel);
                childPanel.fillData();
            }
        }
    }
}
