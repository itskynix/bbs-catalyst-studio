package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.recap.RecapClips;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.clips.Clips;

/** The properties panels of Recap Studio's own time tools, beside the film studio's clip panels. */
public final class UIRecapToolClips
{
    private static boolean registered;

    private UIRecapToolClips()
    {}

    /** Puts the panels in the clip panel registry, once. */
    public static void setup()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        UIClip.register(RecapClips.SpeedClip.class, UISpeedClip::new);
        UIClip.register(RecapClips.TimelapseClip.class, UITimelapseClip::new);
        UIClip.register(RecapClips.DayTimeClip.class, UIDayTimeClip::new);
        UIClip.register(RecapClips.FreezeClip.class, UIFreezeClip::new);
    }

    /**
     * A time tool whose value can be keyed over the clip: its panel opens the value's own dope sheet
     * in the timeline's place, as the film studio's keyed clips do, with a double click on the clip too.
     */
    public abstract static class UIKeyedToolClip<T extends RecapClips.ToolClip> extends UIClip<T>
    {
        private static final String EMBED = "recap_tool_keys";

        public UIKeyframeEditor keyframes;
        public UIButton editKeyframes;

        public UIKeyedToolClip(T clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        /** The name of the keyed value, the title of its row. */
        protected abstract IKey valueName();

        /** What a first key takes: the plain value. */
        protected abstract double plainValue();

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
            this.keyframes.view.rulerRenderer((context) ->
            {
                UIReplaysEditor.renderRuler(context, this.keyframes.view, this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
            });
            this.keyframes.view.duration(() -> this.clip.duration.get());
            this.keyframes.setUndoId(EMBED);

            this.editKeyframes = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
            {
                this.updateSheets();
                this.editor.embedView(this.keyframes);
                this.keyframes.view.resetView();

                if (this.keyframes.view.getGraph() != null)
                {
                    this.keyframes.view.getGraph().clearSelection();
                }
            });
            this.editKeyframes.keys().register(Keys.FORMS_EDIT, () -> this.editKeyframes.clickItself());
        }

        @Override
        protected void registerPanels()
        {
            super.registerPanels();

            this.panels.add(this.section(UIKeys.CAMERA_PANELS_KEYFRAMES, this.editKeyframes));
        }

        private void updateSheets()
        {
            this.keyframes.view.removeAllSheets();
            this.keyframes.view.addSheet(new UIKeyframeSheet(this.clip.keys().getId(), this.valueName(), 0x3fa7d6, this.clip.keys(), null).seed(this::plainValue));
        }

        @Override
        public boolean openSubEditor()
        {
            return this.openSubEditor(this.editKeyframes);
        }

        @Override
        public void fillData()
        {
            super.fillData();

            this.updateDuration(this.clip.duration.get());
            this.updateSheets();
        }

        @Override
        public void applyUndoData(MapType data)
        {
            super.applyUndoData(data);

            if (data.getString("embed").equals(EMBED))
            {
                this.editor.embedView(this.keyframes);
                this.keyframes.view.resetView();
            }
        }

        @Override
        public void collectUndoData(MapType data)
        {
            super.collectUndoData(data);

            if (this.keyframes.hasParent())
            {
                data.putString("embed", EMBED);
            }
        }
    }

    public static class UISpeedClip extends UIKeyedToolClip<RecapClips.SpeedClip>
    {
        public UITrackpad speed;
        public UITrackpad fade;

        public UISpeedClip(RecapClips.SpeedClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected IKey valueName()
        {
            return UIRecapKeys.key("clip.speed");
        }

        @Override
        protected double plainValue()
        {
            return this.clip.speed.get();
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.speed = this.trackpad(this.clip.speed);
            this.speed.limit(this.clip.speed).tooltip(UIRecapKeys.key("clip.speed_tooltip"));
            this.fade = this.trackpad(this.clip.fade);
            this.fade.limit(this.clip.fade).integer().tooltip(UIRecapKeys.key("clip.fade_tooltip"));
        }

        @Override
        protected void registerPanels()
        {
            super.registerPanels();

            this.panels.add(this.section(UIRecapKeys.key("clip.speed"), this.speed));
            this.panels.add(this.section(UIRecapKeys.key("clip.fade"), this.fade));
        }
    }

    public static class UITimelapseClip extends UIKeyedToolClip<RecapClips.TimelapseClip>
    {
        public UITrackpad rate;

        public UITimelapseClip(RecapClips.TimelapseClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected IKey valueName()
        {
            return UIRecapKeys.key("clip.rate");
        }

        @Override
        protected double plainValue()
        {
            return this.clip.rate.get();
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.rate = this.trackpad(this.clip.rate);
            this.rate.limit(this.clip.rate).tooltip(UIRecapKeys.key("clip.rate_tooltip"));
        }

        @Override
        protected void registerPanels()
        {
            super.registerPanels();

            this.panels.add(this.section(UIRecapKeys.key("clip.rate"), this.rate));
        }
    }

    public static class UIDayTimeClip extends UIKeyedToolClip<RecapClips.DayTimeClip>
    {
        public UITrackpad time;

        public UIDayTimeClip(RecapClips.DayTimeClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected IKey valueName()
        {
            return UIRecapKeys.key("clip.day_time");
        }

        @Override
        protected double plainValue()
        {
            return this.clip.time.get();
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.time = this.trackpad(this.clip.time);
            this.time.limit(this.clip.time).tooltip(UIRecapKeys.key("clip.day_time_tooltip"));
        }

        @Override
        protected void registerPanels()
        {
            super.registerPanels();

            this.panels.add(this.section(UIRecapKeys.key("clip.day_time"), this.time));
        }
    }

    /** Holds the recording still, with an optional smooth ramp in and out. */
    public static class UIFreezeClip extends UIClip<RecapClips.FreezeClip>
    {
        public UITrackpad fade;

        public UIFreezeClip(RecapClips.FreezeClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.fade = this.trackpad(this.clip.fade);
            this.fade.limit(this.clip.fade).integer().tooltip(UIRecapKeys.key("clip.fade_tooltip"));
        }

        @Override
        protected void registerPanels()
        {
            super.registerPanels();

            this.panels.add(this.section(UIRecapKeys.key("clip.fade"), this.fade));
        }
    }
}
