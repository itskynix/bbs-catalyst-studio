package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapClips;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

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

    public static class UISpeedClip extends UIClip<RecapClips.SpeedClip>
    {
        public UITrackpad speed;

        public UISpeedClip(RecapClips.SpeedClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.speed = this.trackpad(this.clip.speed);
            this.speed.limit(this.clip.speed).tooltip(UIRecapKeys.key("clip.speed_tooltip"));
        }

        @Override
        protected void registerPanels()
        {
            super.registerPanels();

            this.panels.add(this.section(UIRecapKeys.key("clip.speed"), this.speed));
        }
    }

    public static class UITimelapseClip extends UIClip<RecapClips.TimelapseClip>
    {
        public UITrackpad rate;

        public UITimelapseClip(RecapClips.TimelapseClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
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

    public static class UIDayTimeClip extends UIClip<RecapClips.DayTimeClip>
    {
        public UITrackpad time;

        public UIDayTimeClip(RecapClips.DayTimeClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
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

    /** Nothing to set: while it is on, the recording holds still. */
    public static class UIFreezeClip extends UIClip<RecapClips.FreezeClip>
    {
        public UIFreezeClip(RecapClips.FreezeClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }
    }
}
