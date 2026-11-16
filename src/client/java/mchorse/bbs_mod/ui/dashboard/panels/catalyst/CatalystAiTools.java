package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;

/**
 * The smart editing tools of the Tools menu. Every one of them works on what is already on the
 * machine: the samples of a sound, or the pictures of a video decoded by the ffmpeg the mod already
 * uses. Nothing goes over the network. Slow reading happens on a thread of its own; the changes to the
 * timeline are made back on the render thread, under one undo step.
 */
public class CatalystAiTools
{
    private final UICatalystPanel panel;

    public CatalystAiTools(UICatalystPanel panel)
    {
        this.panel = panel;
    }

    /* ── Helpers ── */

    private CatalystComposition comp()
    {
        return this.panel.getActiveComposition();
    }

    private UINleTimeline timeline()
    {
        return this.panel.catalystTimeline;
    }

    private int fps(CatalystComposition comp)
    {
        return comp.fps > 0 ? comp.fps : 60;
    }

    private List<CatalystLayer> selected()
    {
        return new ArrayList<>(this.timeline().getSelectedLayers());
    }

    private void error(IKey message)
    {
        this.panel.getContext().notifyError(message);
    }

    private void result(IKey what, int count)
    {
        this.panel.getContext().notifySuccess(IKey.raw(what.get() + ": " + count));
    }

    /** The frame of the timeline a moment of the clip's source sound falls on */
    private int toFrame(CatalystLayer layer, double sourceSeconds, int fps)
    {
        return layer.startFrame + (int) Math.round(sourceSeconds * fps - (layer.mediaOffset + layer.audioOffset));
    }

    private double fromSeconds(CatalystLayer layer, int fps)
    {
        return (layer.mediaOffset + layer.audioOffset) / (double) fps;
    }

    /** A clip with a sound of its own to read: a sound clip, or a video that plays its own */
    private boolean hasSource(CatalystLayer layer)
    {
        return (layer.layerType == CatalystLayer.LayerType.AUDIO || (layer.layerType == CatalystLayer.LayerType.VIDEO && !layer.muteOwnAudio))
            && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty() && !CatalystMedia.isOffline(layer);
    }

    /** The clip and the clips linked to it: they are cut together */
    private List<CatalystLayer> group(CatalystComposition comp, CatalystLayer layer)
    {
        List<CatalystLayer> group = new ArrayList<>();

        group.add(layer);

        if (!layer.linkId.isEmpty())
        {
            for (CatalystLayer other : comp.layers)
            {
                if (other != layer && layer.linkId.equals(other.linkId))
                {
                    group.add(other);
                }
            }
        }

        return group;
    }

    /** Cuts every clip of the group at a frame; the second halves come back, and each linked pair of them gets a link of its own */
    private List<CatalystLayer> split(CatalystComposition comp, List<CatalystLayer> group, int frame, Map<String, String> links)
    {
        List<CatalystLayer> seconds = new ArrayList<>();

        for (CatalystLayer layer : group)
        {
            if (frame <= layer.startFrame || frame >= layer.startFrame + layer.duration)
            {
                continue;
            }

            CatalystLayer second = layer.splitAt(frame);

            if (second == null)
            {
                continue;
            }

            if (!second.linkId.isEmpty())
            {
                second.linkId = links.computeIfAbsent(second.linkId, (id) -> java.util.UUID.randomUUID().toString().substring(0, 8));
            }

            comp.layers.add(comp.layers.indexOf(layer) + 1, second);
            seconds.add(second);
        }

        return seconds;
    }

    /** Whether a clip, where it is now, sits on another clip of its track */
    private boolean collides(CatalystComposition comp, CatalystLayer layer, Set<CatalystLayer> ignore)
    {
        for (CatalystLayer other : comp.layers)
        {
            if (other != layer && !ignore.contains(other) && other.isAudioClip() == layer.isAudioClip() && other.track == layer.track
                && layer.startFrame < other.startFrame + other.duration && other.startFrame < layer.startFrame + layer.duration)
            {
                return true;
            }
        }

        return false;
    }

    /** Reads on a thread of its own and hands the result back to the render thread */
    private <T> void background(IKey name, List<CatalystLayer> sweep, Function<DoubleConsumer, T> work, Consumer<T> done)
    {
        int from = Integer.MAX_VALUE;
        int to = 0;

        for (CatalystLayer layer : sweep)
        {
            from = Math.min(from, layer.startFrame);
            to = Math.max(to, layer.startFrame + layer.duration);
        }

        this.panel.beginAi(name, from == Integer.MAX_VALUE ? 0 : from, to);

        Thread thread = new Thread(() ->
        {
            T result = null;

            try
            {
                /* Asking for progress is also where a stop is noticed: it ends the reading with an exception */
                result = work.apply((p) ->
                {
                    if (this.panel.isAiCancelled())
                    {
                        throw new java.util.concurrent.CancellationException();
                    }

                    this.panel.setAiProgress(p);
                });
            }
            catch (Throwable ignored)
            {}

            T finished = result;

            this.panel.runLater(() ->
            {
                boolean stopped = this.panel.isAiCancelled();

                this.panel.endAi();

                if (stopped)
                {
                    this.panel.getContext().notifyInfo(UIKeys.CATALYST_NLE_AI_CANCELLED);
                }
                else
                {
                    done.accept(finished);
                }
            });
        }, "CatalystAiTool");

        thread.setDaemon(true);
        thread.start();
    }

    /* ── 1. Auto cut: the silences go ── */

    public void autoCut()
    {
        if (this.panel.isAiBusy())
        {
            return;
        }

        CatalystComposition comp = this.comp();
        List<CatalystLayer> sources = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (CatalystLayer layer : this.selected())
        {
            if (this.hasSource(layer) && !this.timeline().isClipLocked(layer) && (layer.linkId.isEmpty() || seen.add(layer.linkId)))
            {
                sources.add(layer);
            }
        }

        if (comp == null || sources.isEmpty())
        {
            this.error(UIKeys.CATALYST_NLE_AI_NEED_SOUND);

            return;
        }

        UIAiToolOverlayPanel.open(this.panel.getContext(), UIKeys.CATALYST_NLE_AI_AUTO_CUT, List.of(
            UIAiToolOverlayPanel.Field.number(UIKeys.CATALYST_NLE_AI_THRESHOLD_DB, -80D, -10D, -38D),
            UIAiToolOverlayPanel.Field.number(UIKeys.CATALYST_NLE_AI_MIN_SILENCE, 0.05D, 5D, 0.35D)
        ), (values) ->
        {
            int fps = this.fps(comp);

            this.background(UIKeys.CATALYST_NLE_AI_AUTO_CUT, sources, (progress) ->
            {
                /* For each clip, its silences as stretches of the timeline's frames, the last first */
                Map<CatalystLayer, List<int[]>> found = new HashMap<>();

                for (int at = 0; at < sources.size(); at++)
                {
                    CatalystLayer layer = sources.get(at);
                    int turn = at;
                    CatalystAudioAnalysis.Envelope envelope = CatalystAudioAnalysis.envelope(layer.resourcePath, (p) -> progress.accept((turn + p) / sources.size()));

                    if (envelope == null)
                    {
                        continue;
                    }

                    double from = this.fromSeconds(layer, fps);
                    List<int[]> frames = new ArrayList<>();

                    for (double[] silence : CatalystAudioAnalysis.silences(envelope, from, from + layer.duration / (double) fps, values[0], values[1]))
                    {
                        frames.add(new int[] {this.toFrame(layer, silence[0], fps), this.toFrame(layer, silence[1], fps)});
                    }

                    Collections.reverse(frames);
                    found.put(layer, frames);
                }

                return found;
            }, (found) ->
            {
                if (found == null)
                {
                    return;
                }

                this.panel.pushUndo();

                int cuts = 0;

                for (Map.Entry<CatalystLayer, List<int[]>> entry : found.entrySet())
                {
                    List<CatalystLayer> group = this.group(comp, entry.getKey());

                    for (int[] silence : entry.getValue())
                    {
                        cuts += this.cutOut(comp, group, silence[0], silence[1]) ? 1 : 0;
                    }
                }

                this.timeline().clearSelection();
                this.panel.toolsDone();
                this.result(UIKeys.CATALYST_NLE_AI_RESULT_CUTS, cuts);
            });
        });
    }

    /** Takes the stretch a to b out of the clips of the group, and the clips after it on their tracks glide left to close it */
    private boolean cutOut(CatalystComposition comp, List<CatalystLayer> group, int a, int b)
    {
        CatalystLayer lead = group.get(0);
        int start = lead.startFrame;
        int end = start + lead.duration;

        a = Math.max(a, start);
        b = Math.min(b, end);

        if (b <= a || (a <= start && b >= end))
        {
            return false;
        }

        Map<String, String> links = new HashMap<>();
        List<CatalystLayer> middle;

        if (b < end)
        {
            this.split(comp, group, b, links);
        }

        middle = a > start ? this.split(comp, group, a, links) : new ArrayList<>(group);

        Set<String> closed = new HashSet<>();

        for (CatalystLayer piece : middle)
        {
            comp.layers.remove(piece);
        }

        for (CatalystLayer piece : middle)
        {
            if (closed.add(piece.isAudioClip() + ":" + piece.track))
            {
                this.timeline().closeGapAt(comp, piece.isAudioClip(), piece.track, b, b - a);
            }
        }

        return true;
    }

    /* ── 2 and 3. J cut and L cut ── */

    /** The clip that starts the cut: the later of two picked clips, or the one picked, or the one starting nearest the playhead */
    private CatalystLayer clipAfterCut(CatalystComposition comp)
    {
        List<CatalystLayer> visual = new ArrayList<>();

        for (CatalystLayer layer : this.selected())
        {
            if (!layer.isAudioClip())
            {
                visual.add(layer);
            }
        }

        if (!visual.isEmpty())
        {
            visual.sort((x, y) -> Integer.compare(x.startFrame, y.startFrame));

            return visual.get(visual.size() - 1);
        }

        CatalystLayer best = null;
        int playhead = this.panel.getCurrentFrame();

        for (CatalystLayer layer : comp.layers)
        {
            if (!layer.isAudioClip() && Math.abs(layer.startFrame - playhead) <= this.fps(comp) && (best == null || Math.abs(layer.startFrame - playhead) < Math.abs(best.startFrame - playhead)))
            {
                best = layer;
            }
        }

        return best;
    }

    /** The clip that ends at the cut: the earlier of two picked clips, or the one picked, or the one ending nearest the playhead */
    private CatalystLayer clipBeforeCut(CatalystComposition comp)
    {
        List<CatalystLayer> visual = new ArrayList<>();

        for (CatalystLayer layer : this.selected())
        {
            if (!layer.isAudioClip())
            {
                visual.add(layer);
            }
        }

        if (!visual.isEmpty())
        {
            visual.sort((x, y) -> Integer.compare(x.startFrame, y.startFrame));

            return visual.get(0);
        }

        CatalystLayer best = null;
        int playhead = this.panel.getCurrentFrame();

        for (CatalystLayer layer : comp.layers)
        {
            int end = layer.startFrame + layer.duration;

            if (!layer.isAudioClip() && Math.abs(end - playhead) <= this.fps(comp) && (best == null || Math.abs(end - playhead) < Math.abs(best.startFrame + best.duration - playhead)))
            {
                best = layer;
            }
        }

        return best;
    }

    /** The sound clip that goes with a picture clip: the clip linked to it, or the clip itself when it is a sound */
    private CatalystLayer soundOf(CatalystComposition comp, CatalystLayer layer)
    {
        if (layer.isAudioClip())
        {
            return layer;
        }

        for (CatalystLayer other : this.group(comp, layer))
        {
            if (other != layer && other.isAudioClip())
            {
                return other;
            }
        }

        return null;
    }

    public void jCut()
    {
        this.extend(true);
    }

    public void lCut()
    {
        this.extend(false);
    }

    /**
     * J cut ({@code early}): the sound of the clip after the cut starts before the picture does, fading in.
     * L cut: the sound of the clip before the cut goes on after the picture has changed, fading out.
     */
    private void extend(boolean early)
    {
        if (this.panel.isAiBusy())
        {
            return;
        }

        CatalystComposition comp = this.comp();

        if (comp == null)
        {
            return;
        }

        CatalystLayer picture = early ? this.clipAfterCut(comp) : this.clipBeforeCut(comp);

        if (picture == null)
        {
            this.error(UIKeys.CATALYST_NLE_AI_NEED_CUT);

            return;
        }

        CatalystLayer sound = this.soundOf(comp, picture);

        if (sound == null)
        {
            this.error(UIKeys.CATALYST_NLE_AI_NO_SOUND_CLIP);

            return;
        }

        int fps = this.fps(comp);

        UIAiToolOverlayPanel.open(this.panel.getContext(), early ? UIKeys.CATALYST_NLE_AI_J_CUT : UIKeys.CATALYST_NLE_AI_L_CUT, List.of(
            UIAiToolOverlayPanel.Field.number(UIKeys.CATALYST_NLE_AI_FRAMES, 1D, 600D, Math.max(1, fps / 2))
        ), (values) ->
        {
            int frames = (int) Math.round(values[0]);

            /* How much of the source there is to show: before its start, or after its end */
            if (early)
            {
                frames = Math.min(frames, Math.max(0, sound.mediaOffset));
            }
            else if (sound.mediaDuration > 0)
            {
                frames = Math.min(frames, Math.max(0, sound.mediaDuration - sound.mediaOffset - sound.duration));
            }

            if (frames <= 0)
            {
                this.error(UIKeys.CATALYST_NLE_AI_NO_SOURCE);

                return;
            }

            this.panel.pushUndo();

            /* It may no longer fit on its track: the lowest track that has room takes it */
            comp.layers.remove(sound);

            int oldStart = sound.startFrame;
            int oldDuration = sound.duration;

            if (early)
            {
                sound.startFrame -= frames;
                sound.mediaOffset -= frames;
            }

            sound.duration += frames;

            if (this.collides(comp, sound, Collections.emptySet()))
            {
                sound.track = this.timeline().freeTrack(comp, true, sound.startFrame, sound.duration);
            }

            comp.layers.add(sound);

            /* The soft ramp over the stretch that was added */
            float volume = sound.volume;

            sound.animVolume = true;

            if (early)
            {
                sound.channelVolume.insert(sound.startFrame, 0F);
                sound.channelVolume.insert(oldStart, volume);
            }
            else
            {
                sound.channelVolume.insert(oldStart + oldDuration, volume);
                sound.channelVolume.insert(sound.startFrame + sound.duration, 0F);
            }

            this.panel.toolsDone();
        });
    }

    /* ── 4. Beat sync ── */

    public void beatSync()
    {
        if (this.panel.isAiBusy())
        {
            return;
        }

        CatalystComposition comp = this.comp();
        List<CatalystLayer> music = new ArrayList<>();
        List<CatalystLayer> pictures = new ArrayList<>();

        for (CatalystLayer layer : this.selected())
        {
            if (layer.layerType == CatalystLayer.LayerType.AUDIO && this.hasSource(layer))
            {
                music.add(layer);
            }
            else if (!layer.isAudioClip())
            {
                pictures.add(layer);
            }
        }

        if (comp == null || music.isEmpty())
        {
            this.error(UIKeys.CATALYST_NLE_AI_NEED_MUSIC);

            return;
        }

        UIAiToolOverlayPanel.open(this.panel.getContext(), UIKeys.CATALYST_NLE_AI_BEAT_SYNC, List.of(
            UIAiToolOverlayPanel.Field.number(UIKeys.CATALYST_NLE_AI_SENSITIVITY, 1D, 4D, 1.6D),
            UIAiToolOverlayPanel.Field.toggle(UIKeys.CATALYST_NLE_AI_ALIGN, !pictures.isEmpty())
        ), (values) ->
        {
            int fps = this.fps(comp);

            this.background(UIKeys.CATALYST_NLE_AI_BEAT_SYNC, music, (progress) ->
            {
                List<Integer> beats = new ArrayList<>();

                for (int at = 0; at < music.size(); at++)
                {
                    CatalystLayer layer = music.get(at);
                    int turn = at;
                    CatalystAudioAnalysis.Envelope envelope = CatalystAudioAnalysis.envelope(layer.resourcePath, (p) -> progress.accept((turn + p) / music.size()));

                    if (envelope == null)
                    {
                        continue;
                    }

                    double from = this.fromSeconds(layer, fps);

                    for (double beat : CatalystAudioAnalysis.beats(envelope, from, from + layer.duration / (double) fps, values[0]))
                    {
                        int frame = this.toFrame(layer, beat, fps);

                        if (frame >= layer.startFrame && frame < layer.startFrame + layer.duration)
                        {
                            beats.add(frame);
                        }
                    }
                }

                Collections.sort(beats);

                return beats;
            }, (beats) ->
            {
                if (beats == null || beats.isEmpty())
                {
                    this.error(UIKeys.CATALYST_NLE_AI_RESULT_NONE);

                    return;
                }

                this.panel.pushUndo();

                int added = 0;

                for (int frame : beats)
                {
                    boolean there = false;

                    for (CatalystComposition.CatalystMarker marker : comp.markers)
                    {
                        there |= Math.abs(marker.frame - frame) <= 1;
                    }

                    if (!there)
                    {
                        comp.markers.add(new CatalystComposition.CatalystMarker(frame, UIKeys.CATALYST_NLE_AI_BEAT_LABEL.get(), 0xFF58B4FF));
                        added += 1;
                    }
                }

                /* The picked pictures start on the beat nearest to them, when it is no more than half a second away and nothing is in the way */
                if (values[1] > 0D)
                {
                    for (CatalystLayer layer : pictures)
                    {
                        if (this.timeline().isClipLocked(layer))
                        {
                            continue;
                        }

                        int nearest = beats.get(0);

                        for (int frame : beats)
                        {
                            nearest = Math.abs(frame - layer.startFrame) < Math.abs(nearest - layer.startFrame) ? frame : nearest;
                        }

                        int delta = nearest - layer.startFrame;

                        if (delta == 0 || Math.abs(delta) > fps / 2 || layer.startFrame + delta < 0)
                        {
                            continue;
                        }

                        Set<CatalystLayer> moved = new HashSet<>(this.group(comp, layer));

                        this.timeline().shiftClip(layer, delta);

                        if (this.collides(comp, layer, moved))
                        {
                            this.timeline().shiftClip(layer, -delta);
                        }
                        else
                        {
                            for (CatalystLayer partner : moved)
                            {
                                if (partner != layer)
                                {
                                    this.timeline().shiftClip(partner, delta);

                                    if (this.collides(comp, partner, moved))
                                    {
                                        this.timeline().shiftClip(partner, -delta);
                                    }
                                }
                            }
                        }
                    }
                }

                this.panel.toolsDone();
                this.result(UIKeys.CATALYST_NLE_AI_RESULT_BEATS, added);
            });
        });
    }

    /* ── 5. Ducking ── */

    public void ducking()
    {
        if (this.panel.isAiBusy())
        {
            return;
        }

        CatalystComposition comp = this.comp();
        List<CatalystLayer> speech = new ArrayList<>();
        Set<CatalystLayer> speechSet = new HashSet<>();

        for (CatalystLayer layer : this.selected())
        {
            if (layer.layerType == CatalystLayer.LayerType.AUDIO && this.hasSource(layer))
            {
                speech.add(layer);
                speechSet.add(layer);
            }
        }

        List<CatalystLayer> music = new ArrayList<>();

        if (comp != null)
        {
            for (CatalystLayer layer : comp.layers)
            {
                if (layer.layerType == CatalystLayer.LayerType.AUDIO && !speechSet.contains(layer) && !layer.muted && !this.timeline().isClipLocked(layer))
                {
                    music.add(layer);
                }
            }
        }

        if (speech.isEmpty() || music.isEmpty())
        {
            this.error(UIKeys.CATALYST_NLE_AI_NEED_SPEECH);

            return;
        }

        UIAiToolOverlayPanel.open(this.panel.getContext(), UIKeys.CATALYST_NLE_AI_DUCKING, List.of(
            UIAiToolOverlayPanel.Field.number(UIKeys.CATALYST_NLE_AI_DUCK_DB, -30D, -3D, -12D),
            UIAiToolOverlayPanel.Field.number(UIKeys.CATALYST_NLE_AI_SPEECH_DB, -60D, -20D, -42D)
        ), (values) ->
        {
            int fps = this.fps(comp);

            this.background(UIKeys.CATALYST_NLE_AI_DUCKING, speech, (progress) ->
            {
                /* Where somebody speaks, as frames of the timeline */
                List<int[]> spans = new ArrayList<>();

                for (int at = 0; at < speech.size(); at++)
                {
                    CatalystLayer layer = speech.get(at);
                    int turn = at;
                    CatalystAudioAnalysis.Envelope envelope = CatalystAudioAnalysis.envelope(layer.resourcePath, (p) -> progress.accept((turn + p) / speech.size()));

                    if (envelope == null)
                    {
                        continue;
                    }

                    double from = this.fromSeconds(layer, fps);

                    for (double[] span : CatalystAudioAnalysis.activity(envelope, from, from + layer.duration / (double) fps, values[1], 0.4D))
                    {
                        spans.add(new int[] {Math.max(layer.startFrame, this.toFrame(layer, span[0], fps)), Math.min(layer.startFrame + layer.duration, this.toFrame(layer, span[1], fps))});
                    }
                }

                return spans;
            }, (spans) ->
            {
                if (spans == null || spans.isEmpty())
                {
                    this.error(UIKeys.CATALYST_NLE_AI_RESULT_NONE);

                    return;
                }

                this.panel.pushUndo();

                int ramp = Math.max(1, Math.round(0.25F * fps));
                float duck = (float) Math.pow(10D, values[0] / 20D);

                /* Speech that comes close after speech is one stretch, so the music does not bounce between them */
                spans.sort((x, y) -> Integer.compare(x[0], y[0]));

                List<int[]> merged = new ArrayList<>();

                for (int[] span : spans)
                {
                    if (!merged.isEmpty() && span[0] - merged.get(merged.size() - 1)[1] < ramp * 2)
                    {
                        merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], span[1]);
                    }
                    else
                    {
                        merged.add(new int[] {span[0], span[1]});
                    }
                }

                int touched = 0;

                for (CatalystLayer layer : music)
                {
                    int start = layer.startFrame;
                    int end = layer.startFrame + layer.duration;
                    float volume = layer.volume;
                    boolean any = false;

                    layer.channelVolume.removeAll();

                    for (int[] span : merged)
                    {
                        int s = Math.max(start, span[0]);
                        int e = Math.min(end, span[1]);

                        if (e <= s)
                        {
                            continue;
                        }

                        any = true;
                        layer.channelVolume.insert(Math.max(start, s - ramp), volume);
                        layer.channelVolume.insert(s, volume * duck);
                        layer.channelVolume.insert(e, volume * duck);
                        layer.channelVolume.insert(Math.min(end, e + ramp), volume);
                    }

                    layer.animVolume = any;
                    touched += any ? 1 : 0;
                }

                this.panel.toolsDone();
                this.result(UIKeys.CATALYST_NLE_AI_RESULT_DUCKED, touched);
            });
        });
    }

    /* ── 6. Scene cut detection ── */

    public void sceneCuts()
    {
        if (this.panel.isAiBusy())
        {
            return;
        }

        CatalystComposition comp = this.comp();
        List<CatalystLayer> videos = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (CatalystLayer layer : this.selected())
        {
            if (layer.layerType == CatalystLayer.LayerType.VIDEO && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty()
                && !CatalystMedia.isOffline(layer) && !this.timeline().isClipLocked(layer) && (layer.linkId.isEmpty() || seen.add(layer.linkId)))
            {
                videos.add(layer);
            }
        }

        if (comp == null || videos.isEmpty())
        {
            this.error(UIKeys.CATALYST_NLE_AI_NEED_VIDEO);

            return;
        }

        UIAiToolOverlayPanel.open(this.panel.getContext(), UIKeys.CATALYST_NLE_AI_SCENE_CUT, List.of(
            UIAiToolOverlayPanel.Field.number(UIKeys.CATALYST_NLE_AI_CUT_THRESHOLD, 8D, 100D, 28D),
            UIAiToolOverlayPanel.Field.toggle(UIKeys.CATALYST_NLE_AI_MARKERS_ONLY, false)
        ), (values) ->
        {
            int fps = this.fps(comp);

            this.background(UIKeys.CATALYST_NLE_AI_SCENE_CUT, videos, (progress) ->
            {
                Map<CatalystLayer, List<Integer>> found = new HashMap<>();

                for (int at = 0; at < videos.size(); at++)
                {
                    CatalystLayer layer = videos.get(at);
                    int turn = at;

                    found.put(layer, detectScenes(layer.resourcePath.trim(), this.fromSeconds(layer, fps), layer.duration / (double) fps, values[0], layer.startFrame, fps, (p) -> progress.accept((turn + p) / videos.size())));
                }

                return found;
            }, (found) ->
            {
                if (found == null)
                {
                    return;
                }

                this.panel.pushUndo();

                int count = 0;

                for (Map.Entry<CatalystLayer, List<Integer>> entry : found.entrySet())
                {
                    List<Integer> frames = entry.getValue();

                    Collections.reverse(frames);

                    List<CatalystLayer> group = this.group(comp, entry.getKey());

                    for (int frame : frames)
                    {
                        if (values[1] > 0D)
                        {
                            comp.markers.add(new CatalystComposition.CatalystMarker(frame, UIKeys.CATALYST_NLE_AI_SCENE_LABEL.get(), 0xFFE8C43A));
                            count += 1;
                        }
                        else
                        {
                            count += this.split(comp, group, frame, new HashMap<>()).isEmpty() ? 0 : 1;
                        }
                    }
                }

                this.timeline().clearSelection();
                this.panel.toolsDone();
                this.result(UIKeys.CATALYST_NLE_AI_RESULT_SCENES, count);
            });
        });
    }

    /**
     * The frames of the timeline where the picture of a stretch of a video jumps: it is decoded as
     * small grey frames, and a frame is a cut when its brightness or its histogram is far from the one before
     * and far above what the last few frames moved by, so a pan is not taken for a cut.
     */
    private static List<Integer> detectScenes(String path, double from, double length, double threshold, int startFrame, int fps, DoubleConsumer progress)
    {
        List<Integer> frames = new ArrayList<>();
        int width = 48;
        int height = 27;
        int rate = 30;

        try
        {
            List<String> command = new ArrayList<>();

            command.add(FFMpegUtils.getFFMPEG());
            command.add("-nostdin");
            command.add("-loglevel");
            command.add("error");
            command.add("-ss");
            command.add(String.valueOf(from));
            command.add("-t");
            command.add(String.valueOf(length));
            command.add("-i");
            command.add(path);
            command.add("-an");
            command.add("-vf");
            command.add("fps=" + rate + ",scale=" + width + ":" + height + ",format=gray");
            command.add("-f");
            command.add("rawvideo");
            command.add("pipe:1");

            ProcessBuilder builder = new ProcessBuilder(command);

            builder.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process = builder.start();

            try (InputStream stream = process.getInputStream())
            {
                byte[] previous = new byte[width * height];
                byte[] current = new byte[width * height];
                double[] recent = new double[8];
                int index = 0;
                int lastCut = -1000;
                boolean first = true;

                while (readFully(stream, current))
                {
                    if (!first)
                    {
                        double luma = 0D;
                        int[] a = new int[16];
                        int[] b = new int[16];

                        for (int i = 0; i < current.length; i++)
                        {
                            int p = current[i] & 0xff;
                            int q = previous[i] & 0xff;

                            luma += Math.abs(p - q);
                            a[q >> 4] += 1;
                            b[p >> 4] += 1;
                        }

                        double histogram = 0D;

                        for (int i = 0; i < 16; i++)
                        {
                            histogram += Math.abs(a[i] - b[i]);
                        }

                        double score = Math.max(luma / current.length, histogram / current.length * 60D);
                        double average = 0D;

                        for (double r : recent)
                        {
                            average += r;
                        }

                        average /= recent.length;

                        if (score > threshold && score > average * 2.2D + 4D && index - lastCut > rate * 0.4D)
                        {
                            frames.add(startFrame + (int) Math.round(index / (double) rate * fps));
                            lastCut = index;
                        }

                        recent[index % recent.length] = score;

                        if (index % 15 == 0)
                        {
                            progress.accept(Math.min(1D, index / Math.max(1D, length * rate)));
                        }
                    }

                    byte[] swap = previous;

                    previous = current;
                    current = swap;
                    first = false;
                    index += 1;
                }
            }
            finally
            {
                process.destroy();
            }
        }
        catch (Throwable ignored)
        {}

        return frames;
    }

    private static boolean readFully(InputStream stream, byte[] buffer) throws java.io.IOException
    {
        int read = 0;

        while (read < buffer.length)
        {
            int n = stream.read(buffer, read, buffer.length - read);

            if (n < 0)
            {
                return false;
            }

            read += n;
        }

        return true;
    }

    /* ── 7. Close all gaps ── */

    /**
     * Every track is gone over from the left, and each clip that has a gap before it slides left to meet
     * the clip before it, gliding. A clip's linked partners slide the same way, when they have room;
     * the first clip of a track stays where it is.
     */
    public void closeAllGaps()
    {
        if (this.panel.isAiBusy())
        {
            return;
        }

        CatalystComposition comp = this.comp();

        if (comp == null)
        {
            return;
        }

        this.panel.pushUndo();

        int closed = 0;
        Set<CatalystLayer> done = new HashSet<>();

        for (int kind = 0; kind < 2; kind++)
        {
            boolean audio = kind == 1;

            for (int track = 0; track < comp.tracks(audio).size(); track++)
            {
                if (comp.tracks(audio).get(track).locked)
                {
                    continue;
                }

                List<CatalystLayer> row = new ArrayList<>();

                for (CatalystLayer layer : comp.layers)
                {
                    if (layer.isAudioClip() == audio && layer.track == track)
                    {
                        row.add(layer);
                    }
                }

                row.sort((x, y) -> Integer.compare(x.startFrame, y.startFrame));

                int edge = -1;

                for (CatalystLayer layer : row)
                {
                    if (edge >= 0 && layer.startFrame > edge && !done.contains(layer))
                    {
                        int gap = layer.startFrame - edge;
                        Set<CatalystLayer> together = new HashSet<>(this.group(comp, layer));

                        this.timeline().shiftClip(layer, -gap);
                        done.add(layer);

                        for (CatalystLayer partner : together)
                        {
                            if (partner != layer && !done.contains(partner))
                            {
                                this.timeline().shiftClip(partner, -gap);

                                if (partner.startFrame < 0 || this.collides(comp, partner, together))
                                {
                                    this.timeline().shiftClip(partner, gap);
                                }
                                else
                                {
                                    done.add(partner);
                                }
                            }
                        }

                        closed += 1;
                    }

                    edge = layer.startFrame + layer.duration;
                }
            }
        }

        if (closed == 0)
        {
            this.error(UIKeys.CATALYST_NLE_AI_RESULT_NONE);

            return;
        }

        this.panel.toolsDone();
        this.result(UIKeys.CATALYST_NLE_AI_RESULT_GAPS, closed);
    }
}
