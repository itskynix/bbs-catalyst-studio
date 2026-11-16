package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.Waveform;
import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.catalyst.CatalystTrack;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The edit page's timeline: video tracks above a divider (V1 lowest, right over it), audio tracks
 * below it (A1 first), each with a header of its badge and its lock, visibility or mute and solo
 * switches. Clips sit side by side on a track and move across tracks of their kind; their edges trim
 * them, the blade cuts them in two where it is clicked, and with snapping on, edges stick to other
 * clips' edges, the playhead and the markers.
 *
 * <p>The ruler on top scrubs the playhead. The wheel scrolls the tracks, Shift+wheel scrolls in time
 * and Ctrl+wheel zooms around the mouse.</p>
 */
public class UINleTimeline extends UIElement
{
    public enum Tool { SELECT, TRIM, BLADE }

    public static final int HEADER_W = 84;
    public static final int RULER_H = 20;
    /** The height of the video and of the audio tracks; Alt + wheel over a track changes its kind's */
    public int videoH = 24;
    public int audioH = 26;
    private static final int MIN_TRACK_H = 18;
    private static final int MAX_TRACK_H = 120;
    public static final int DIVIDER_H = 4;

    /* The header's switches: lock, visibility or mute, solo - 18 pixels each from these x */
    private static final int LOCK_X = 30;
    private static final int MUTE_X = 48;
    private static final int SOLO_X = 66;

    private static final int EDGE_PX = 5;
    private static final int SNAP_PX = 8;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_MOVE = 1;
    private static final int DRAG_TRIM_LEFT = 2;
    private static final int DRAG_TRIM_RIGHT = 3;
    private static final int DRAG_SCRUB = 4;
    private static final int DRAG_PAN = 5;
    private static final int DRAG_MARQUEE = 6;
    private static final int DRAG_MARKER = 7;

    private final Supplier<CatalystComposition> composition;
    private final IntSupplier playhead;
    private final IntConsumer seek;

    /** Before the first change of a gesture, for undo; after it, to save */
    public Runnable onPreModify;
    public Runnable onModified;
    public Runnable onSelectionChanged;

    public Tool tool = Tool.SELECT;
    public boolean snapping = true;
    public boolean linkedSelection = true;

    /** Pixels per frame, and the frame at the left edge of the lanes */
    public double zoom = 3D;
    public double scrollX = 0D;
    private int scrollY = 0;

    private final Set<CatalystLayer> selection = new LinkedHashSet<>();

    /* The gesture in progress */
    private int drag = DRAG_NONE;
    private boolean dragChanged;
    private CatalystLayer grabbed;
    private int grabFrame;
    private int grabTrack;
    private int panStartX;
    private double panStartScroll;
    private int snapLine = -1;
    private final Map<CatalystLayer, int[]> initial = new HashMap<>();

    /* The last move that left every clip clear of the others, where a blocked one stops */
    private int lastValidDelta;
    private int lastValidTrack;

    /** Set by the page: whether a clip's picture or sound is still being made ready; its top edge then shows a throbber */
    public Predicate<CatalystLayer> loading = (layer) -> false;
    /** Set by the page: whether a video clip has its frames buffered; the strip over the ruler is green for it, red when not */
    public Predicate<CatalystLayer> buffered = (layer) -> true;

    /** Set by the page: the selected clips are moved into a timeline of their own and one nested clip stands in */
    public java.util.function.Consumer<Set<CatalystLayer>> onNest;
    /** Set by the page: a double click on a nested clip opens the timeline inside it */
    public java.util.function.Consumer<CatalystLayer> onOpenNested;
    /** Set by the page: the gain window for the picked sound clips */
    public Runnable onGain;
    /** Set by the page: true while a tool is at work and the timeline is not to be touched */
    public java.util.function.BooleanSupplier interactionLocked = () -> false;

    /* The marker on the ruler that is being dragged, and the two clips the right click found that could be one again */
    private CatalystComposition.CatalystMarker dragMarker;
    private CatalystLayer[] joinPair;

    private CatalystLayer lastClickClip;
    private long lastClickAt;

    /* The gap between two clips of a track that is selected: its track kind (0 video, 1 audio), the track (-1 none) and its frames */
    private int gapAudio;
    private int gapTrack = -1;
    private int gapStart;
    private int gapEnd;

    /* Clips that were pulled left by a ripple draw where they were and glide to where they are: {frames behind, start in ms} */
    private final Map<CatalystLayer, double[]> glide = new HashMap<>();
    private static final double GLIDE_MS = 170D;

    /* The box drawn over empty space: where it began and where the mouse is now, in screen space */
    private int marqueeX;
    private int marqueeY;
    private int marqueeToX;
    private int marqueeToY;
    private boolean marqueeMoved;
    private final Set<CatalystLayer> marqueeBase = new LinkedHashSet<>();

    public UINleTimeline(Supplier<CatalystComposition> composition, IntSupplier playhead, IntConsumer seek)
    {
        this.composition = composition;
        this.playhead = playhead;
        this.seek = seek;
    }

    /* ── Geometry ── */

    public int lanesLeft()
    {
        return this.area.x + HEADER_W;
    }

    public int toGraphX(int frame)
    {
        return this.lanesLeft() + (int) Math.round((frame - this.scrollX) * this.zoom);
    }

    public int fromGraphTick(int x)
    {
        return (int) Math.round((x - this.lanesLeft()) / this.zoom + this.scrollX);
    }

    private int lanesTop()
    {
        return this.area.y + RULER_H - this.scrollY;
    }

    private int contentHeight(CatalystComposition comp)
    {
        return comp.videoTracks.size() * this.videoH + DIVIDER_H + comp.audioTracks.size() * this.audioH;
    }

    /** The top of a track's row on screen */
    public int rowY(CatalystComposition comp, boolean audio, int track)
    {
        if (audio)
        {
            return this.lanesTop() + comp.videoTracks.size() * this.videoH + DIVIDER_H + track * this.audioH;
        }

        return this.lanesTop() + (comp.videoTracks.size() - 1 - track) * this.videoH;
    }

    public int rowH(boolean audio)
    {
        return audio ? this.audioH : this.videoH;
    }

    /** The track under a y on screen: {audio ? 1 : 0, index}, or null over the divider or past the rows */
    private int[] rowAt(CatalystComposition comp, int y)
    {
        int top = this.lanesTop();
        int videoEnd = top + comp.videoTracks.size() * this.videoH;

        if (y >= top && y < videoEnd)
        {
            return new int[] {0, comp.videoTracks.size() - 1 - (y - top) / this.videoH};
        }

        int audioTop = videoEnd + DIVIDER_H;

        if (y >= audioTop && y < audioTop + comp.audioTracks.size() * this.audioH)
        {
            return new int[] {1, (y - audioTop) / this.audioH};
        }

        return null;
    }

    private CatalystLayer clipAt(CatalystComposition comp, int x, int y)
    {
        int[] row = this.rowAt(comp, y);

        if (row == null || x < this.lanesLeft())
        {
            return null;
        }

        int frame = this.fromGraphTickExact(x);

        /* Later clips are drawn over earlier ones, so they are hit first */
        for (int i = comp.layers.size() - 1; i >= 0; i--)
        {
            CatalystLayer layer = comp.layers.get(i);

            if ((layer.isAudioClip() ? 1 : 0) == row[0] && layer.track == row[1] && frame >= layer.startFrame && frame < layer.startFrame + layer.duration)
            {
                return layer;
            }
        }

        return null;
    }

    private int fromGraphTickExact(int x)
    {
        return (int) Math.floor((x - this.lanesLeft()) / this.zoom + this.scrollX);
    }

    /* ── Selection ── */

    public Set<CatalystLayer> getSelectedLayers()
    {
        return this.selection;
    }

    public CatalystLayer getFirstSelectedLayer()
    {
        return this.selection.isEmpty() ? null : this.selection.iterator().next();
    }

    public boolean isSelected(CatalystLayer layer)
    {
        return this.selection.contains(layer);
    }

    public void setSelected(CatalystLayer layer)
    {
        this.selection.clear();

        if (layer != null)
        {
            this.selection.add(layer);
            this.addLinked(layer);
        }

        this.selectionChanged();
    }

    public void clearSelection()
    {
        if (!this.selection.isEmpty())
        {
            this.selection.clear();
            this.selectionChanged();
        }
    }

    /** Linked selection: the clips on other tracks that start and end with this one go along */
    private void addLinked(CatalystLayer layer)
    {
        CatalystComposition comp = this.composition.get();

        if (!this.linkedSelection || comp == null)
        {
            return;
        }

        this.selection.addAll(this.linkedTo(comp, layer));
    }

    /** The clips linked to this one: the audio made from a video's sound, and the other way round */
    private List<CatalystLayer> linkedTo(CatalystComposition comp, CatalystLayer layer)
    {
        List<CatalystLayer> linked = new ArrayList<>();

        if (layer.linkId.isEmpty())
        {
            return linked;
        }

        for (CatalystLayer other : comp.layers)
        {
            if (other != layer && layer.linkId.equals(other.linkId))
            {
                linked.add(other);
            }
        }

        return linked;
    }

    private void selectionChanged()
    {
        this.gapTrack = -1;

        if (this.onSelectionChanged != null)
        {
            this.onSelectionChanged.run();
        }
    }

    /* ── Edits ── */

    private void preModify()
    {
        if (!this.dragChanged)
        {
            this.dragChanged = true;

            if (this.onPreModify != null)
            {
                this.onPreModify.run();
            }
        }
    }

    private void modified()
    {
        if (this.onModified != null)
        {
            this.onModified.run();
        }
    }

    private boolean isLocked(CatalystComposition comp, CatalystLayer layer)
    {
        CatalystTrack track = comp.trackOf(layer);

        return layer.locked || (track != null && track.locked);
    }

    /** Cuts the clips under the playhead in two there: the selected ones, or with none selected every unlocked one */
    public void cutSelected()
    {
        this.splitAt(this.playhead.getAsInt(), this.selection.isEmpty() ? null : this.selection);
    }

    private void splitAt(int frame, Set<CatalystLayer> only)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return;
        }

        List<CatalystLayer> made = new ArrayList<>();
        Map<String, String> links = new HashMap<>();
        boolean before = false;

        for (CatalystLayer layer : new ArrayList<>(comp.layers))
        {
            if ((only != null && !only.contains(layer)) || this.isLocked(comp, layer) || frame <= layer.startFrame || frame >= layer.startFrame + layer.duration)
            {
                continue;
            }

            if (!before)
            {
                before = true;

                if (this.onPreModify != null)
                {
                    this.onPreModify.run();
                }
            }

            CatalystLayer second = layer.splitAt(frame);

            if (second != null)
            {
                if (!second.linkId.isEmpty())
                {
                    second.linkId = links.computeIfAbsent(second.linkId, (id) -> java.util.UUID.randomUUID().toString().substring(0, 8));
                }

                comp.layers.add(comp.layers.indexOf(layer) + 1, second);
                made.add(second);
            }
        }

        if (!made.isEmpty())
        {
            this.selection.clear();
            this.selection.addAll(made);
            this.selectionChanged();
            this.modified();
        }
    }

    public void deleteSelected()
    {
        this.deleteSelected(false);
    }

    /** Deletes the selected clips; a ripple delete also pulls what is after each one on its track into the gap */
    public void deleteSelected(boolean ripple)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null || this.selection.isEmpty())
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        /* Last first, so a gap closed further left doesn't move the ones still to go */
        List<CatalystLayer> doomed = new ArrayList<>(this.selection);

        doomed.sort((a, b) -> Integer.compare(b.startFrame, a.startFrame));

        for (CatalystLayer layer : doomed)
        {
            if (this.isLocked(comp, layer))
            {
                continue;
            }

            comp.layers.remove(layer);

            if (ripple)
            {
                this.closeGap(comp, layer.isAudioClip(), layer.track, layer.startFrame + layer.duration, layer.duration);
            }
        }

        this.selection.clear();
        this.selectionChanged();
        this.modified();
    }

    /** Moves every clip of a track that starts at or after {@code from} left by {@code amount} */
    private void closeGap(CatalystComposition comp, boolean audio, int track, int from, int amount)
    {
        for (CatalystLayer other : comp.layers)
        {
            if (other.isAudioClip() == audio && other.track == track && other.startFrame >= from)
            {
                int moved = Math.min(amount, other.startFrame);

                other.moveKeyframes(-moved);
                other.startFrame -= moved;
                this.glideBy(other, moved);
            }
        }
    }

    /** The clip was just moved {@code frames} to the left: it is drawn that far to the right and eases back */
    private void glideBy(CatalystLayer layer, int frames)
    {
        if (frames > 0)
        {
            this.glide.put(layer, new double[] {this.glideOffset(layer) + frames, System.currentTimeMillis()});
        }
    }

    /** How many frames to the right of its place a clip is drawn right now */
    private double glideOffset(CatalystLayer layer)
    {
        double[] g = this.glide.get(layer);

        if (g == null)
        {
            return 0D;
        }

        double t = (System.currentTimeMillis() - g[1]) / GLIDE_MS;

        if (t >= 1D)
        {
            this.glide.remove(layer);

            return 0D;
        }

        return g[0] * Math.pow(1D - t, 3D);
    }

    /** The gap between clips on a track at a frame: {start, end}, or null when there is no clip after it or the track is locked */
    private int[] gapAt(CatalystComposition comp, boolean audio, int track, int frame)
    {
        List<CatalystTrack> tracks = comp.tracks(audio);

        if (track < 0 || track >= tracks.size() || tracks.get(track).locked)
        {
            return null;
        }

        int before = 0;
        int after = Integer.MAX_VALUE;

        for (CatalystLayer layer : comp.layers)
        {
            if (layer.isAudioClip() != audio || layer.track != track)
            {
                continue;
            }

            int end = layer.startFrame + layer.duration;

            if (end <= frame)
            {
                before = Math.max(before, end);
            }
            else if (layer.startFrame > frame)
            {
                after = Math.min(after, layer.startFrame);
            }
            else
            {
                return null;
            }
        }

        return after == Integer.MAX_VALUE || after <= before ? null : new int[] {before, after};
    }

    /** Whether a gap is selected */
    public boolean hasGapSelected()
    {
        return this.gapTrack >= 0;
    }

    /** Closes the selected gap: the clips after it on its track slide left, softly. True if there was one */
    public boolean closeSelectedGap()
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null || this.gapTrack < 0)
        {
            return false;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        this.closeGap(comp, this.gapAudio == 1, this.gapTrack, this.gapEnd, this.gapEnd - this.gapStart);
        this.gapTrack = -1;
        this.modified();

        return true;
    }

    /**
     * The ripple trims at the playhead, on the clips under it (the selected ones, or with none
     * selected every unlocked one): {@code start} cuts away what is before the playhead and pulls
     * the clip and everything after it back to where it began; otherwise what is after the playhead
     * goes and everything after the clip closes up.
     */
    public void rippleTrim(boolean start)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return;
        }

        int frame = this.playhead.getAsInt();
        List<CatalystLayer> under = new ArrayList<>();

        for (CatalystLayer layer : comp.layers)
        {
            if ((this.selection.isEmpty() || this.selection.contains(layer)) && !this.isLocked(comp, layer)
                && frame > layer.startFrame && frame < layer.startFrame + layer.duration)
            {
                under.add(layer);
            }
        }

        if (under.isEmpty())
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        for (CatalystLayer layer : under)
        {
            int begin = layer.startFrame;
            int end = layer.startFrame + layer.duration;

            if (start)
            {
                /* The part after the playhead is what stays: it takes the clip's place and its start */
                CatalystLayer kept = layer.splitAt(frame);

                if (kept == null)
                {
                    continue;
                }

                kept.id = layer.id;
                kept.linkId = layer.linkId;
                kept.transitionIn = layer.transitionIn;
                kept.moveKeyframes(begin - kept.startFrame);
                kept.startFrame = begin;
                comp.layers.set(comp.layers.indexOf(layer), kept);

                if (this.selection.remove(layer))
                {
                    this.selection.add(kept);
                }

                this.closeGap(comp, kept.isAudioClip(), kept.track, end, frame - begin);
            }
            else
            {
                layer.duration = frame - begin;
                this.closeGap(comp, layer.isAudioClip(), layer.track, end, end - frame);
            }
        }

        this.selectionChanged();
        this.modified();
    }

    public void duplicateSelected()
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null || this.selection.isEmpty())
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        List<CatalystLayer> copies = new ArrayList<>();

        for (CatalystLayer layer : this.selection)
        {
            CatalystLayer copy = layer.duplicate();
            int end = layer.startFrame + layer.duration;

            copy.startFrame = end;
            copy.track = this.overlaps(comp, copy, layer.track, end, copy.duration, java.util.Collections.emptySet())
                ? this.freeTrack(comp, copy.isAudioClip(), end, copy.duration) : layer.track;
            comp.layers.add(copy);
            copies.add(copy);
        }

        this.selection.clear();
        this.selection.addAll(copies);
        this.selectionChanged();
        this.modified();
    }

    public void addMarkerAtPlayhead()
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return;
        }

        int frame = this.playhead.getAsInt();

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        comp.markers.removeIf((marker) -> marker.frame == frame);
        comp.markers.add(new CatalystComposition.CatalystMarker(frame, "", 0xFFE8C43A));
        this.modified();
    }

    /** Adds a track of a kind on top of its kind's tracks */
    public void addTrack(boolean audio)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        comp.tracks(audio).add(new CatalystTrack());
        this.modified();
    }

    /** Removes the last track of a kind when nothing is on it and at least one is left */
    public void removeTrack(boolean audio)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null || comp.tracks(audio).size() <= 1)
        {
            return;
        }

        int last = comp.tracks(audio).size() - 1;

        for (CatalystLayer layer : comp.layers)
        {
            if (layer.isAudioClip() == audio && layer.track == last)
            {
                return;
            }
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        comp.tracks(audio).remove(last);
        this.modified();
    }

    /** The first track of the clip's kind with room for it over [start, start + duration), adding one when they are all taken */
    public int freeTrack(CatalystComposition comp, boolean audio, int start, int duration)
    {
        List<CatalystTrack> tracks = comp.tracks(audio);

        for (int t = 0; t < tracks.size(); t++)
        {
            boolean free = !tracks.get(t).locked;

            for (CatalystLayer layer : comp.layers)
            {
                if (layer.isAudioClip() == audio && layer.track == t && start < layer.startFrame + layer.duration && layer.startFrame < start + duration)
                {
                    free = false;

                    break;
                }
            }

            if (free)
            {
                return t;
            }
        }

        tracks.add(new CatalystTrack());

        return tracks.size() - 1;
    }

    /* ── Snapping ── */

    public int snapTick(int frame)
    {
        int[] snapped = this.snap(frame, null);

        return snapped[0];
    }

    /** {snapped frame, the frame it snapped to or -1} - to clip edges (but the moving ones), the playhead, markers and 0 */
    private int[] snap(int frame, Set<CatalystLayer> skip)
    {
        CatalystComposition comp = this.composition.get();

        if (!this.snapping || comp == null)
        {
            return new int[] {frame, -1};
        }

        double reach = SNAP_PX / Math.max(this.zoom, 0.0001D);
        int best = frame;
        double bestDistance = reach + 1;

        List<Integer> points = new ArrayList<>();

        points.add(0);
        points.add(this.playhead.getAsInt());

        for (CatalystComposition.CatalystMarker marker : comp.markers)
        {
            points.add(marker.frame);
        }

        for (CatalystLayer layer : comp.layers)
        {
            if (skip == null || !skip.contains(layer))
            {
                points.add(layer.startFrame);
                points.add(layer.startFrame + layer.duration);
            }
        }

        for (int point : points)
        {
            double distance = Math.abs(point - frame);

            if (distance <= reach && distance < bestDistance)
            {
                bestDistance = distance;
                best = point;
            }
        }

        return new int[] {best, bestDistance <= reach ? best : -1};
    }

    /* ── Input ── */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null || !this.area.isInside(context))
        {
            return false;
        }

        if (this.interactionLocked.getAsBoolean())
        {
            return true;
        }

        int mx = context.mouseX;
        int my = context.mouseY;

        if (context.mouseButton == 2)
        {
            this.drag = DRAG_PAN;
            this.panStartX = mx;
            this.panStartScroll = this.scrollX;

            return true;
        }

        /* The ruler: a marker is dragged and has its settings on a right click, an empty place scrubs and offers a new marker */
        if (my < this.area.y + RULER_H)
        {
            if (mx >= this.lanesLeft())
            {
                CatalystComposition.CatalystMarker marker = this.markerAt(comp, mx, my);

                if (context.mouseButton == 1)
                {
                    if (marker != null)
                    {
                        this.openMarkerSettings(context, comp, marker);
                    }
                    else
                    {
                        int at = Math.max(0, this.fromGraphTickExact(mx));

                        context.replaceContextMenu((menu) -> menu.action(Icons.BOOKMARK, UIKeys.CATALYST_NLE_ADD_MARKER, () -> this.addMarkerAt(at)));
                    }
                }
                else if (context.mouseButton == 0)
                {
                    if (marker != null)
                    {
                        this.drag = DRAG_MARKER;
                        this.dragMarker = marker;
                        this.dragChanged = false;
                    }
                    else
                    {
                        this.drag = DRAG_SCRUB;
                        this.seek.accept(Math.max(0, this.fromGraphTickExact(mx)));
                    }
                }
            }

            return true;
        }

        int[] row = this.rowAt(comp, my);

        /* Track headers */
        if (mx < this.lanesLeft())
        {
            if (row != null)
            {
                this.headerClick(context, comp, row[0] == 1, row[1], mx, my);
            }
            else if (context.mouseButton == 1)
            {
                this.trackMenu(context);
            }

            return true;
        }

        CatalystLayer clip = this.clipAt(comp, mx, my);

        if (context.mouseButton == 1)
        {
            if (clip != null && !this.selection.contains(clip))
            {
                this.setSelected(clip);
            }
            else if (clip == null && row != null)
            {
                /* Right click on a gap selects it, so the menu can close it */
                int[] gap = this.gapAt(comp, row[0] == 1, row[1], this.fromGraphTickExact(mx));

                this.clearSelection();
                this.gapTrack = -1;

                if (gap != null)
                {
                    this.gapAudio = row[0];
                    this.gapTrack = row[1];
                    this.gapStart = gap[0];
                    this.gapEnd = gap[1];
                }
            }

            this.joinPair = this.joinPairAt(comp, row, mx);
            this.clipMenu(context, comp, row, this.fromGraphTickExact(mx));

            return true;
        }

        if (context.mouseButton != 0)
        {
            return true;
        }

        if (clip == null)
        {
            /* Empty space: a box that selects what it touches, adding to the selection with Ctrl or Shift */
            boolean add = Window.isCtrlPressed() || Window.isShiftPressed();

            this.marqueeBase.clear();

            if (add)
            {
                this.marqueeBase.addAll(this.selection);
            }
            else
            {
                this.clearSelection();
            }

            /* The empty stretch between two clips of a track is selected, to be closed up */
            this.gapTrack = -1;

            if (row != null)
            {
                int[] gap = this.gapAt(comp, row[0] == 1, row[1], this.fromGraphTickExact(mx));

                if (gap != null)
                {
                    this.gapAudio = row[0];
                    this.gapTrack = row[1];
                    this.gapStart = gap[0];
                    this.gapEnd = gap[1];
                }
            }

            if (this.tool != Tool.BLADE)
            {
                this.drag = DRAG_MARQUEE;
                this.marqueeX = mx;
                this.marqueeY = my;
                this.marqueeToX = mx;
                this.marqueeToY = my;
                this.marqueeMoved = false;
                this.dragChanged = false;
            }

            return true;
        }

        if (this.tool == Tool.BLADE)
        {
            int frame = this.snap(this.fromGraphTickExact(mx), null)[0];
            Set<CatalystLayer> only = new LinkedHashSet<>();

            only.add(clip);

            if (this.linkedSelection)
            {
                only.addAll(this.linkedTo(comp, clip));
            }

            this.splitAt(frame, only);

            return true;
        }

        /* A double click on a nested clip opens the timeline inside it */
        long clickedAt = System.currentTimeMillis();

        if (clip.isNested() && clip == this.lastClickClip && clickedAt - this.lastClickAt < 350L && this.onOpenNested != null)
        {
            this.lastClickClip = null;
            this.onOpenNested.accept(clip);

            return true;
        }

        this.lastClickClip = clip;
        this.lastClickAt = clickedAt;

        if (Window.isShiftPressed() || Window.isCtrlPressed())
        {
            if (!this.selection.remove(clip))
            {
                this.selection.add(clip);
            }

            this.selectionChanged();
        }
        else if (!this.selection.contains(clip))
        {
            this.setSelected(clip);
        }

        if (this.isLocked(comp, clip))
        {
            return true;
        }

        /* Which part of the clip: an edge trims it, the body moves it. The trim tool trims from either half */
        int x1 = this.toGraphX(clip.startFrame);
        int x2 = this.toGraphX(clip.startFrame + clip.duration);
        int edge = this.tool == Tool.TRIM ? Math.max(EDGE_PX, (x2 - x1) / 2) : EDGE_PX;

        if (mx - x1 <= edge)
        {
            this.drag = DRAG_TRIM_LEFT;
        }
        else if (x2 - mx <= edge)
        {
            this.drag = DRAG_TRIM_RIGHT;
        }
        else
        {
            this.drag = DRAG_MOVE;
        }

        this.dragChanged = false;
        this.grabbed = clip;
        this.grabFrame = this.fromGraphTickExact(mx);
        this.grabTrack = clip.track;
        this.initial.clear();
        this.lastValidDelta = 0;
        this.lastValidTrack = 0;

        Set<CatalystLayer> moving = this.drag == DRAG_MOVE ? this.selection : java.util.Collections.singleton(clip);

        for (CatalystLayer layer : moving)
        {
            this.initial.put(layer, new int[] {layer.startFrame, layer.duration, layer.mediaOffset, layer.track});
        }

        if (this.drag != DRAG_MOVE && this.linkedSelection)
        {
            for (CatalystLayer partner : this.linkedTo(comp, clip))
            {
                if (!this.isLocked(comp, partner))
                {
                    this.initial.put(partner, new int[] {partner.startFrame, partner.duration, partner.mediaOffset, partner.track});
                }
            }
        }

        return true;
    }

    private void headerClick(UIContext context, CatalystComposition comp, boolean audio, int index, int mx, int my)
    {
        CatalystTrack track = comp.tracks(audio).get(index);

        if (context.mouseButton == 1)
        {
            this.trackMenu(context);

            return;
        }

        int x = mx - this.area.x;
        Runnable change = null;

        if (x >= LOCK_X && x < LOCK_X + 18)
        {
            change = () -> track.locked = !track.locked;
        }
        else if (x >= MUTE_X && x < MUTE_X + 18)
        {
            change = () -> track.muted = !track.muted;
        }
        else if (x >= SOLO_X && x < SOLO_X + 18)
        {
            change = () -> track.solo = !track.solo;
        }

        if (change != null)
        {
            if (this.onPreModify != null)
            {
                this.onPreModify.run();
            }

            change.run();
            this.modified();
        }
    }

    private void trackMenu(UIContext context)
    {
        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.CATALYST_NLE_ADD_VIDEO_TRACK, () -> this.addTrack(false));
            menu.action(Icons.ADD, UIKeys.CATALYST_NLE_ADD_AUDIO_TRACK, () -> this.addTrack(true));
            menu.action(Icons.REMOVE, UIKeys.CATALYST_NLE_REMOVE_VIDEO_TRACK, () -> this.removeTrack(false));
            menu.action(Icons.REMOVE, UIKeys.CATALYST_NLE_REMOVE_AUDIO_TRACK, () -> this.removeTrack(true));
        });
    }

    private void clipMenu(UIContext context, CatalystComposition comp, int[] row, int frame)
    {
        boolean audio = row != null && row[0] == 1;
        int track = row == null ? 0 : row[1];
        int at = Math.max(0, frame);

        context.replaceContextMenu((menu) ->
        {
            if (!this.selection.isEmpty())
            {
                menu.action(Icons.CUT, UIKeys.CATALYST_NLE_SPLIT, this::cutSelected);
                menu.action(Icons.DUPE, UIKeys.CATALYST_NLE_DUPLICATE, this::duplicateSelected);

                if (this.onNest != null)
                {
                    menu.action(Icons.FOLDER, UIKeys.CATALYST_NLE_NEST, () -> this.onNest.accept(new LinkedHashSet<>(this.selection)));
                }

                if (this.onGain != null && this.soundSelected())
                {
                    menu.action(Icons.SOUND, UIKeys.CATALYST_NLE_GAIN, this.onGain);
                }
                menu.action(Icons.TRASH, UIKeys.CATALYST_NLE_DELETE, () -> this.deleteSelected(false));
                menu.action(Icons.SHIFT_BACKWARD, UIKeys.CATALYST_NLE_RIPPLE_DELETE, () -> this.deleteSelected(true));

                if (this.selectionLinked())
                {
                    menu.action(Icons.LINK, UIKeys.CATALYST_NLE_UNLINK, this::unlinkSelected);
                }
            }

            if (!audio)
            {
                menu.action(Icons.FONT, UIKeys.CATALYST_NLE_ADD_TEXT, () -> this.addClip(CatalystLayer.LayerType.TEXT, at, track));
                menu.action(Icons.SPARKLES, UIKeys.CATALYST_NLE_ADD_ADJUSTMENT, () -> this.addClip(CatalystLayer.LayerType.ADJUSTMENT, at, track));
                menu.action(Icons.SQUARE, UIKeys.CATALYST_NLE_ADD_SOLID, () -> this.addClip(CatalystLayer.LayerType.SOLID, at, track));
            }

            if (this.joinPair != null)
            {
                CatalystLayer[] pair = this.joinPair;

                menu.action(Icons.LINK, UIKeys.CATALYST_NLE_JOIN_CUT, () -> this.joinClips(pair[0], pair[1]));
            }

            if (this.gapTrack >= 0)
            {
                menu.action(Icons.SHIFT_BACKWARD, UIKeys.CATALYST_NLE_CLOSE_GAP, this::closeSelectedGap);
            }

            menu.action(Icons.BOOKMARK, UIKeys.CATALYST_NLE_ADD_MARKER, this::addMarkerAtPlayhead);
            menu.action(Icons.ADD, UIKeys.CATALYST_NLE_ADD_VIDEO_TRACK, () -> this.addTrack(false));
            menu.action(Icons.ADD, UIKeys.CATALYST_NLE_ADD_AUDIO_TRACK, () -> this.addTrack(true));
        });
    }

    /** A new generated clip (text, adjustment, solid) at a frame, on the track if it has room there */
    public CatalystLayer addClip(CatalystLayer.LayerType type, int frame, int track)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return null;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        CatalystLayer layer = new CatalystLayer();
        int duration = Math.max(1, (comp.fps > 0 ? comp.fps : 60) * 5);

        layer.layerType = type;
        layer.color = type.defaultColor();
        layer.name = (type == CatalystLayer.LayerType.TEXT ? UIKeys.CATALYST_NLE_CLIP_TEXT
            : type == CatalystLayer.LayerType.ADJUSTMENT ? UIKeys.CATALYST_NLE_CLIP_ADJUSTMENT
            : UIKeys.CATALYST_NLE_CLIP_SOLID).get();
        layer.startFrame = frame;
        layer.duration = duration;

        if (type == CatalystLayer.LayerType.TEXT)
        {
            layer.resourcePath = UIKeys.CATALYST_NLE_CLIP_TEXT.get();
            layer.fontSize = 48;
        }

        boolean free = track >= 0 && track < comp.videoTracks.size() && !comp.videoTracks.get(track).locked;

        for (CatalystLayer other : comp.layers)
        {
            if (free && !other.isAudioClip() && other.track == track && frame < other.startFrame + other.duration && other.startFrame < frame + duration)
            {
                free = false;
            }
        }

        layer.track = free ? track : this.freeTrack(comp, false, frame, duration);
        comp.layers.add(layer);
        this.setSelected(layer);
        this.modified();

        return layer;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        boolean was = this.drag != DRAG_NONE;

        if (this.dragChanged)
        {
            this.modified();
        }

        this.drag = DRAG_NONE;
        this.dragChanged = false;
        this.grabbed = null;
        this.dragMarker = null;
        this.snapLine = -1;
        this.initial.clear();

        return was;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null || !this.area.isInside(context))
        {
            return false;
        }

        double wheel = context.mouseWheel;

        if (Window.isAltPressed())
        {
            /* The height of the tracks of the kind under the mouse: more room for waveforms and names */
            int[] row = this.rowAt(comp, context.mouseY);
            boolean audio = row != null ? row[0] == 1 : context.mouseY > this.lanesTop() + comp.videoTracks.size() * this.videoH;
            int step = wheel > 0 ? 4 : -4;

            if (audio)
            {
                this.audioH = Math.max(MIN_TRACK_H, Math.min(MAX_TRACK_H, this.audioH + step));
            }
            else
            {
                this.videoH = Math.max(MIN_TRACK_H, Math.min(MAX_TRACK_H, this.videoH + step));
            }

            return true;
        }

        if (Window.isCtrlPressed())
        {
            /* Zoom around the frame under the mouse */
            double anchor = (context.mouseX - this.lanesLeft()) / this.zoom + this.scrollX;

            this.setZoom(this.zoom * (wheel > 0 ? 1.2D : 1D / 1.2D));
            this.scrollX = Math.max(0D, anchor - (context.mouseX - this.lanesLeft()) / this.zoom);
        }
        else if (Window.isShiftPressed() || context.mouseWheelHorizontal != 0)
        {
            double amount = context.mouseWheelHorizontal != 0 ? context.mouseWheelHorizontal : wheel;

            this.scrollX = Math.max(0D, this.scrollX - amount * 40D / this.zoom);
        }
        else
        {
            int max = Math.max(0, this.contentHeight(comp) - (this.area.h - RULER_H));

            this.scrollY = Math.max(0, Math.min(max, this.scrollY - (int) (wheel * 24)));
        }

        return true;
    }

    public void setZoom(double zoom)
    {
        this.zoom = Math.max(0.05D, Math.min(40D, zoom));
    }

    /** The zoom as 0 to 1 along a logarithmic scale, for the toolbar's slider */
    public double getZoomFactor()
    {
        return (Math.log(this.zoom) - Math.log(0.05D)) / (Math.log(40D) - Math.log(0.05D));
    }

    public void setZoomFactor(double factor)
    {
        this.setZoom(Math.exp(Math.log(0.05D) + Math.max(0D, Math.min(1D, factor)) * (Math.log(40D) - Math.log(0.05D))));
    }

    /** Steps the playhead to the previous (-1) or next (1) clip edge */
    public void jumpToEdge(int direction)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return;
        }

        int frame = this.playhead.getAsInt();
        int best = direction < 0 ? 0 : Integer.MAX_VALUE;

        for (CatalystLayer layer : comp.layers)
        {
            for (int edge : new int[] {layer.startFrame, layer.startFrame + layer.duration})
            {
                if (direction < 0 && edge < frame && edge > best) best = edge;
                if (direction > 0 && edge > frame && edge < best) best = edge;
            }
        }

        if (best != Integer.MAX_VALUE)
        {
            this.seek.accept(best);
        }
    }

    /* ── Dragging ── */

    private void updateDrag(UIContext context, CatalystComposition comp)
    {
        if (this.drag == DRAG_NONE)
        {
            return;
        }

        if (!Window.isMouseButtonPressed(this.drag == DRAG_PAN ? 2 : 0))
        {
            this.subMouseReleased(context);

            return;
        }

        int mx = context.mouseX;

        if (this.drag == DRAG_SCRUB)
        {
            this.seek.accept(Math.max(0, this.fromGraphTickExact(mx)));

            return;
        }

        if (this.drag == DRAG_PAN)
        {
            this.scrollX = Math.max(0D, this.panStartScroll - (mx - this.panStartX) / this.zoom);

            return;
        }

        if (this.drag == DRAG_MARKER && this.dragMarker != null)
        {
            int frame = Math.max(0, this.fromGraphTickExact(mx));
            int old = this.dragMarker.frame;

            /* Out of the way of its own snap */
            this.dragMarker.frame = -1000000;
            this.snapLine = -1;

            if (this.snapping)
            {
                int[] snapped = this.snap(frame, java.util.Collections.emptySet());

                if (snapped[1] >= 0)
                {
                    frame = Math.max(0, snapped[0]);
                    this.snapLine = snapped[1];
                }
            }

            this.dragMarker.frame = old;

            if (frame != old)
            {
                this.preModify();
                this.dragMarker.frame = frame;
            }

            return;
        }

        if (this.drag == DRAG_MARQUEE)
        {
            this.marqueeToX = mx;
            this.marqueeToY = context.mouseY;

            /* A click that barely moves is a click on nothing, not a box */
            if (this.marqueeMoved || Math.abs(mx - this.marqueeX) + Math.abs(context.mouseY - this.marqueeY) >= 4)
            {
                this.marqueeMoved = true;
                this.updateMarquee(comp);
            }

            return;
        }

        if (this.grabbed == null || !this.initial.containsKey(this.grabbed))
        {
            return;
        }

        int[] start = this.initial.get(this.grabbed);
        int delta = this.fromGraphTickExact(mx) - this.grabFrame;

        if (this.drag == DRAG_MOVE)
        {
            this.moveClips(context, comp, start, delta);
        }
        else
        {
            this.trimClip(comp, start, delta);
        }
    }

    private void moveClips(UIContext context, CatalystComposition comp, int[] start, int delta)
    {
        /* Snap the grabbed clip's start or end, whichever is closer to something */
        int newStart = start[0] + delta;
        int[] snapStart = this.snap(newStart, this.initial.keySet());
        int[] snapEnd = this.snap(newStart + start[1], this.initial.keySet());

        this.snapLine = -1;

        if (snapStart[1] >= 0 && (snapEnd[1] < 0 || Math.abs(snapStart[0] - newStart) <= Math.abs(snapEnd[0] - newStart - start[1])))
        {
            delta += snapStart[0] - newStart;
            this.snapLine = snapStart[1];
        }
        else if (snapEnd[1] >= 0)
        {
            delta += snapEnd[0] - (newStart + start[1]);
            this.snapLine = snapEnd[1];
        }

        /* No clip goes before the start of the timeline */
        for (int[] values : this.initial.values())
        {
            delta = Math.max(delta, -values[0]);
        }

        /* Across tracks: the same number of tracks for every moving clip, if they all have one to go to */
        int[] row = this.rowAt(comp, context.mouseY);
        int trackDelta = 0;

        if (row != null && (row[0] == 1) == this.grabbed.isAudioClip())
        {
            trackDelta = row[1] - this.grabTrack;

            for (Map.Entry<CatalystLayer, int[]> entry : this.initial.entrySet())
            {
                List<CatalystTrack> tracks = comp.tracks(entry.getKey().isAudioClip());
                int target = entry.getValue()[3] + trackDelta;

                if (target < 0 || target >= tracks.size() || tracks.get(target).locked)
                {
                    trackDelta = 0;

                    break;
                }
            }
        }

        /* Clips don't go over other clips on their track: a blocked move stops at the neighbour's edge,
         * or stays on its track when the other one is taken there */
        if (this.movedCollides(comp, delta, trackDelta))
        {
            if (trackDelta != this.lastValidTrack && !this.movedCollides(comp, delta, this.lastValidTrack))
            {
                trackDelta = this.lastValidTrack;
            }
            else
            {
                int track = trackDelta != this.lastValidTrack && this.movedCollides(comp, this.lastValidDelta, trackDelta) ? this.lastValidTrack : trackDelta;
                int step = delta > this.lastValidDelta ? -1 : 1;
                int found = this.lastValidDelta;

                for (int d = delta; d != this.lastValidDelta; d += step)
                {
                    if (!this.movedCollides(comp, d, track))
                    {
                        found = d;

                        break;
                    }
                }

                delta = found;
                trackDelta = track;
                this.snapLine = -1;
            }
        }

        this.lastValidDelta = delta;
        this.lastValidTrack = trackDelta;

        boolean changed = false;

        for (Map.Entry<CatalystLayer, int[]> entry : this.initial.entrySet())
        {
            CatalystLayer layer = entry.getKey();
            int[] values = entry.getValue();

            if (layer.startFrame != values[0] + delta || layer.track != values[3] + trackDelta)
            {
                changed = true;
            }
        }

        if (!changed)
        {
            return;
        }

        this.preModify();

        for (Map.Entry<CatalystLayer, int[]> entry : this.initial.entrySet())
        {
            int target = entry.getValue()[0] + delta;

            entry.getKey().moveKeyframes(target - entry.getKey().startFrame);
            entry.getKey().startFrame = target;
            entry.getKey().track = entry.getValue()[3] + trackDelta;
        }
    }

    /** The grabbed clip's linked partners take the same trim: their start moved by one amount, their end by the other */
    private void trimPartners(int startDelta, int endDelta)
    {
        for (Map.Entry<CatalystLayer, int[]> entry : this.initial.entrySet())
        {
            CatalystLayer partner = entry.getKey();
            int[] values = entry.getValue();

            if (partner == this.grabbed || values[1] - startDelta + endDelta < 1 || values[0] + startDelta < 0)
            {
                continue;
            }

            partner.startFrame = values[0] + startDelta;
            partner.duration = values[1] - startDelta + endDelta;
            partner.mediaOffset = values[2] + startDelta;
        }
    }

    /** Whether the moving clips, moved by {@code delta} frames and {@code trackDelta} tracks, would lie over a clip that stays */
    private boolean movedCollides(CatalystComposition comp, int delta, int trackDelta)
    {
        for (Map.Entry<CatalystLayer, int[]> entry : this.initial.entrySet())
        {
            int[] values = entry.getValue();

            if (this.overlaps(comp, entry.getKey(), values[3] + trackDelta, values[0] + delta, values[1], this.initial.keySet()))
            {
                return true;
            }
        }

        return false;
    }

    /** Whether [start, start + duration) on a track of the clip's kind meets a clip other than the ignored ones */
    private boolean overlaps(CatalystComposition comp, CatalystLayer layer, int track, int start, int duration, Set<CatalystLayer> ignore)
    {
        for (CatalystLayer other : comp.layers)
        {
            if (other == layer || ignore.contains(other) || other.isAudioClip() != layer.isAudioClip() || other.track != track)
            {
                continue;
            }

            if (start < other.startFrame + other.duration && other.startFrame < start + duration)
            {
                return true;
            }
        }

        return false;
    }

    private void trimClip(CatalystComposition comp, int[] start, int delta)
    {
        CatalystLayer layer = this.grabbed;

        /* The neighbours on the track: a trim stops at their edges */
        int previousEnd = 0;
        int nextStart = Integer.MAX_VALUE;

        for (CatalystLayer other : comp.layers)
        {
            if (other == layer || other.isAudioClip() != layer.isAudioClip() || other.track != layer.track)
            {
                continue;
            }

            if (other.startFrame + other.duration <= start[0])
            {
                previousEnd = Math.max(previousEnd, other.startFrame + other.duration);
            }
            else if (other.startFrame >= start[0] + start[1])
            {
                nextStart = Math.min(nextStart, other.startFrame);
            }
        }
        boolean media = layer.mediaDuration > 0 && (layer.layerType == CatalystLayer.LayerType.VIDEO || layer.layerType == CatalystLayer.LayerType.AUDIO);
        int end = start[0] + start[1];

        this.snapLine = -1;

        if (this.drag == DRAG_TRIM_LEFT)
        {
            int[] snapped = this.snap(start[0] + delta, this.initial.keySet());
            int newStart = snapped[0];
            /* Media can't be pulled out before its first frame */
            int earliest = media ? start[0] - start[2] : 0;

            newStart = Math.max(Math.max(previousEnd, earliest), Math.min(end - 1, newStart));
            this.snapLine = snapped[1] >= 0 && snapped[0] == newStart ? snapped[1] : -1;

            if (newStart != layer.startFrame)
            {
                this.preModify();
                layer.startFrame = newStart;
                layer.duration = end - newStart;
                layer.mediaOffset = start[2] + (newStart - start[0]);
                this.trimPartners(newStart - start[0], 0);
            }
        }
        else
        {
            int[] snapped = this.snap(end + delta, this.initial.keySet());
            int newEnd = Math.max(start[0] + 1, Math.min(nextStart, snapped[0]));

            /* Nor past its last one */
            if (media)
            {
                newEnd = Math.min(newEnd, start[0] + layer.mediaDuration - start[2]);
            }

            this.snapLine = snapped[1] >= 0 && snapped[0] == newEnd ? snapped[1] : -1;

            if (newEnd - start[0] != layer.duration)
            {
                this.preModify();
                layer.duration = newEnd - start[0];
                this.trimPartners(0, newEnd - (start[0] + start[1]));
            }
        }
    }

    /* ── Rendering ── */

    @Override
    public void render(UIContext context)
    {
        CatalystComposition comp = this.composition.get();

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff12161e);

        if (comp == null)
        {
            super.render(context);

            return;
        }

        this.updateDrag(context, comp);

        int max = Math.max(0, this.contentHeight(comp) - (this.area.h - RULER_H));

        this.scrollY = Math.max(0, Math.min(max, this.scrollY));

        this.updateCursor(context, comp);

        context.batcher.clip(this.area.x, this.area.y + RULER_H, this.area.w, this.area.h - RULER_H, context);
        this.renderLanes(context, comp);
        this.renderClips(context, comp);
        this.renderHeaders(context, comp);
        context.batcher.unclip(context);

        this.renderRuler(context, comp);
        this.renderPlayhead(context, comp);
        this.renderMarquee(context);

        super.render(context);

        if (this.interactionLocked.getAsBoolean())
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x88080B10);
            context.batcher.icon(Icons.LOCKED, 0xccffffff, this.area.mx() - 8, this.area.my() - 8);
        }
    }

    private void updateCursor(UIContext context, CatalystComposition comp)
    {
        if (!this.area.isInside(context) || context.mouseX < this.lanesLeft() || context.mouseY < this.area.y + RULER_H)
        {
            return;
        }

        if (this.tool == Tool.BLADE)
        {
            context.requestCursor(GLFW.GLFW_CROSSHAIR_CURSOR);

            return;
        }

        if (this.drag == DRAG_TRIM_LEFT || this.drag == DRAG_TRIM_RIGHT)
        {
            context.requestCursor(GLFW.GLFW_HRESIZE_CURSOR);

            return;
        }

        CatalystLayer clip = this.clipAt(comp, context.mouseX, context.mouseY);

        if (clip != null)
        {
            int x1 = this.toGraphX(clip.startFrame);
            int x2 = this.toGraphX(clip.startFrame + clip.duration);
            int edge = this.tool == Tool.TRIM ? Math.max(EDGE_PX, (x2 - x1) / 2) : EDGE_PX;

            if (context.mouseX - x1 <= edge || x2 - context.mouseX <= edge)
            {
                context.requestCursor(GLFW.GLFW_HRESIZE_CURSOR);
            }
        }
    }

    private void renderLanes(UIContext context, CatalystComposition comp)
    {
        for (int audio = 0; audio < 2; audio++)
        {
            boolean a = audio == 1;

            for (int t = 0; t < comp.tracks(a).size(); t++)
            {
                int y = this.rowY(comp, a, t);
                int h = rowH(a);

                context.batcher.box(this.lanesLeft(), y, this.area.ex(), y + h, t % 2 == 0 ? 0xff161b24 : 0xff141820);
                context.batcher.box(this.lanesLeft(), y + h - 1, this.area.ex(), y + h, 0xff1e2430);
            }
        }

        /* The divider between the video and the audio tracks */
        int divider = this.lanesTop() + comp.videoTracks.size() * this.videoH;

        context.batcher.box(this.area.x, divider, this.area.ex(), divider + DIVIDER_H, 0xff0b0e13);
        context.batcher.box(this.area.x, divider + DIVIDER_H / 2, this.area.ex(), divider + DIVIDER_H / 2 + 1, Colors.setA(BBSSettings.primaryColor.get(), 0.45F));
    }

    private void renderClips(UIContext context, CatalystComposition comp)
    {
        FontRenderer font = context.batcher.getFont();
        int fps = comp.fps > 0 ? comp.fps : 60;
        int accent = Colors.A100 | BBSSettings.primaryColor.get();

        context.batcher.clip(this.lanesLeft(), this.area.y + RULER_H, this.area.ex() - this.lanesLeft(), this.area.h - RULER_H, context);

        for (CatalystLayer layer : comp.layers)
        {
            boolean audio = layer.isAudioClip();
            List<CatalystTrack> tracks = comp.tracks(audio);

            if (layer.track < 0 || layer.track >= tracks.size())
            {
                continue;
            }

            int glidePx = (int) Math.round(this.glideOffset(layer) * this.zoom);
            int x1 = this.toGraphX(layer.startFrame) + glidePx;
            int x2 = Math.max(x1 + 2, this.toGraphX(layer.startFrame + layer.duration) + glidePx);

            if (x2 < this.lanesLeft() || x1 > this.area.ex())
            {
                continue;
            }

            int y = this.rowY(comp, audio, layer.track) + 2;
            int h = rowH(audio) - 4;
            CatalystTrack track = tracks.get(layer.track);
            boolean selected = this.selection.contains(layer);
            int base = Colors.A100 | (layer.color & Colors.RGB);
            boolean offline = CatalystMedia.isOffline(layer);
            int fill = offline ? 0xff5a1218 : (track.muted || !layer.visible ? Colors.mulRGB(base, 0.45F) : Colors.mulRGB(base, 0.75F));

            RoundedDraw.fill(context.batcher, x1, y, x2, y + h, 3, RoundedDraw.ALL, fill);
            RoundedDraw.band(context.batcher, x1, y, x2, y + h, 3, RoundedDraw.ALL, y, y + 3, Colors.setA(Colors.WHITE, 0.12F));

            if (audio)
            {
                if (layer.cachedWaveform instanceof Waveform waveform && waveform.isCreated())
                {
                    float from = (float) (layer.mediaOffset + layer.audioOffset) / fps;
                    float to = (float) (layer.mediaOffset + layer.audioOffset + layer.duration) / fps;

                    try
                    {
                        /* The gain scales the wave: twice as loud draws twice as tall, kept inside the clip */
                        int waveH = Math.max(2, Math.round((h - 4) * Math.min(2.5F, layer.gainFactor())));

                        context.batcher.clip(x1, y, x2 - x1, h, context);
                        waveform.render(context.batcher, 0x88ffffff, x1, y + h / 2 - waveH / 2, x2 - x1, waveH, from, to);
                        context.batcher.unclip(context);
                    }
                    catch (Exception ignored)
                    {}
                }
                else
                {
                    UICatalystTimeline.ensureWaveformLoaded(layer);
                }
            }

            /* The transition in, as a wedge over the start */
            if (!layer.transitionIn.isEmpty())
            {
                int tw = Math.min(x2 - x1, (int) Math.round(layer.transitionFrames * this.zoom));

                for (int i = 0; i < tw; i++)
                {
                    int cut = (int) ((1F - i / (float) Math.max(1, tw)) * h);

                    context.batcher.box(x1 + i, y, x1 + i + 1, y + cut, 0x40ffffff);
                }
            }

            /* How far this clip sits from the clip it is linked to, when they have come apart */
            int shift = this.syncShift(comp, layer);
            String badge = shift == 0 ? null : (shift > 0 ? "+" : "") + shift;
            int badgeW = badge == null ? 0 : font.getWidth(badge) + 8;
            int textX = Math.max(x1, this.lanesLeft()) + 5 + (badgeW > 0 ? badgeW + 3 : 0);
            String label = font.limitToWidth(offline ? UIKeys.CATALYST_NLE_OFFLINE.get() + "  " + layer.name : layer.name, Math.max(0, x2 - textX - 4));

            if (!label.isEmpty())
            {
                context.batcher.text(label, textX, y + (h - 8) / 2, offline ? 0xffff8a8a : Colors.WHITE, true);
            }

            if (badge != null)
            {
                int bx = Math.max(x1, this.lanesLeft()) + 2;

                RoundedDraw.fill(context.batcher, bx, y + 2, bx + badgeW, y + 13, 3, RoundedDraw.ALL, 0xffd8322e);
                context.batcher.text(badge, bx + 4, y + 4, Colors.WHITE, false);
            }

            if (!offline && this.loading.test(layer))
            {
                this.renderThrobber(context, font, x1, x2, y);
            }

            if (!layer.effects.getAllTyped().isEmpty() && x2 - x1 > 30)
            {
                context.batcher.icon(Icons.SPARKLES, 0xccffffff, x2 - 18, y + h / 2 - 8);
            }

            RoundedDraw.outline(context.batcher, x1, y, x2, y + h, 3, RoundedDraw.ALL, selected ? accent : 0x40000000);

            if (selected)
            {
                RoundedDraw.outline(context.batcher, x1 + 1, y + 1, x2 - 1, y + h - 1, 2, RoundedDraw.ALL, accent);
            }
        }

        /* The selected gap: a hatched, outlined stretch where the clips will close up */
        if (this.gapTrack >= 0 && this.gapTrack < comp.tracks(this.gapAudio == 1).size())
        {
            int gx1 = this.toGraphX(this.gapStart);
            int gx2 = this.toGraphX(this.gapEnd);
            int gy = this.rowY(comp, this.gapAudio == 1, this.gapTrack) + 2;
            int gh = this.rowH(this.gapAudio == 1) - 4;

            RoundedDraw.fill(context.batcher, gx1, gy, gx2, gy + gh, 3, RoundedDraw.ALL, 0x26ffffff);

            for (int sx = gx1 - gh; sx < gx2; sx += 10)
            {
                for (int i = 0; i < gh; i += 2)
                {
                    int hx1 = Math.max(gx1 + 1, sx + i);
                    int hx2 = Math.min(gx2 - 1, sx + i + 2);

                    if (hx2 > hx1)
                    {
                        context.batcher.box(hx1, gy + gh - 1 - i, hx2, gy + gh - i, 0x40ffffff);
                    }
                }
            }

            RoundedDraw.outline(context.batcher, gx1, gy, gx2, gy + gh, 3, RoundedDraw.ALL, Colors.A100 | BBSSettings.primaryColor.get());
        }

        /* A locked track: dimmed and hatched, so it reads as out of reach */
        for (int kind = 0; kind < 2; kind++)
        {
            List<CatalystTrack> tracks = comp.tracks(kind == 1);

            for (int t = 0; t < tracks.size(); t++)
            {
                if (!tracks.get(t).locked)
                {
                    continue;
                }

                int ty = this.rowY(comp, kind == 1, t);
                int th = this.rowH(kind == 1);
                boolean opened = RoundedDraw.begin(context.batcher);

                context.batcher.box(this.lanesLeft(), ty, this.area.ex(), ty + th - 1, 0x66000000);

                for (int sx = this.lanesLeft() - th; sx < this.area.ex(); sx += 14)
                {
                    for (int i = 0; i < th - 1; i += 2)
                    {
                        context.batcher.box(sx + i, ty + th - 3 - i, sx + i + 2, ty + th - 1 - i, 0x30ffffff);
                    }
                }

                RoundedDraw.end(context.batcher, opened);
            }
        }

        if (this.snapLine >= 0)
        {
            int sx = this.toGraphX(this.snapLine);

            context.batcher.box(sx, this.area.y + RULER_H, sx + 1, this.area.ey(), 0xffffd84a);
        }

        context.batcher.unclip(context);
    }

    private void renderHeaders(UIContext context, CatalystComposition comp)
    {
        FontRenderer font = context.batcher.getFont();
        int accent = Colors.A100 | BBSSettings.primaryColor.get();

        context.batcher.box(this.area.x, this.area.y + RULER_H, this.lanesLeft(), this.area.ey(), 0xff181d27);

        for (int audio = 0; audio < 2; audio++)
        {
            boolean a = audio == 1;
            List<CatalystTrack> tracks = comp.tracks(a);

            for (int t = 0; t < tracks.size(); t++)
            {
                CatalystTrack track = tracks.get(t);
                int y = this.rowY(comp, a, t);
                int h = rowH(a);
                int mid = y + h / 2;
                String badge = (a ? "A" : "V") + (t + 1);
                int badgeColor = a ? 0xff2e9e6a : 0xff3d7fd0;

                RoundedDraw.fill(context.batcher, this.area.x + 4, mid - 7, this.area.x + 27, mid + 7, 4, RoundedDraw.ALL, badgeColor);
                context.batcher.text(badge, this.area.x + 16 - font.getWidth(badge) / 2, mid - 4, Colors.WHITE, false);

                this.headerIcon(context, track.locked ? Icons.LOCKED : Icons.UNLOCKED, this.area.x + LOCK_X, mid, track.locked ? 0xffffc04a : 0x88ffffff);
                this.headerIcon(context, a ? Icons.SOUND : (track.muted ? Icons.INVISIBLE : Icons.VISIBLE), this.area.x + MUTE_X, mid,
                    track.muted ? 0xffe0455a : 0x88ffffff);

                int soloX = this.area.x + SOLO_X;

                if (track.solo)
                {
                    RoundedDraw.fill(context.batcher, soloX + 1, mid - 7, soloX + 17, mid + 7, 3, RoundedDraw.ALL, accent);
                }

                context.batcher.text("S", soloX + 9 - font.getWidth("S") / 2, mid - 4, track.solo ? 0xff10141c : 0x88ffffff, false);

                if (a && track.muted)
                {
                    context.batcher.box(this.area.x + MUTE_X + 2, mid, this.area.x + MUTE_X + 16, mid + 1, 0xffe0455a);
                }

                context.batcher.box(this.area.x, y + h - 1, this.lanesLeft(), y + h, 0xff1e2430);
            }
        }

        context.batcher.box(this.lanesLeft() - 1, this.area.y + RULER_H, this.lanesLeft(), this.area.ey(), 0xff262d3a);
    }

    private void headerIcon(UIContext context, Icon icon, int x, int mid, int color)
    {
        context.batcher.icon(icon, color, x + 1, mid - 8);
    }

    private void renderRuler(UIContext context, CatalystComposition comp)
    {
        FontRenderer font = context.batcher.getFont();
        int fps = comp.fps > 0 ? comp.fps : 60;
        int y = this.area.y;

        context.batcher.box(this.area.x, y, this.area.ex(), y + RULER_H, 0xff1a1f2a);
        context.batcher.box(this.area.x, y + RULER_H - 1, this.area.ex(), y + RULER_H, 0xff262d3a);

        /* A tick step that leaves room for a label: whole seconds when zoomed out, frames when in */
        int[] steps = {1, 2, 5, 10, fps / 2, fps, fps * 2, fps * 5, fps * 10, fps * 30, fps * 60, fps * 300};
        int step = steps[steps.length - 1];

        for (int candidate : steps)
        {
            if (candidate > 0 && candidate * this.zoom >= 70)
            {
                step = candidate;

                break;
            }
        }

        int first = Math.max(0, (int) Math.floor(this.scrollX / step) * step);

        context.batcher.clip(this.lanesLeft(), y, this.area.ex() - this.lanesLeft(), RULER_H, context);

        for (int frame = first; this.toGraphX(frame) < this.area.ex(); frame += step)
        {
            int x = this.toGraphX(frame);

            context.batcher.box(x, y + RULER_H - 8, x + 1, y + RULER_H - 1, 0xff5a6478);
            context.batcher.text(timecode(frame, fps), x + 3, y + 2, 0xff8c96aa, false);

            for (int sub = 1; sub < 5; sub++)
            {
                int sx = this.toGraphX(frame) + (int) Math.round(step * this.zoom * sub / 5D);

                context.batcher.box(sx, y + RULER_H - 4, sx + 1, y + RULER_H - 1, 0xff3a4252);
            }
        }

        for (CatalystComposition.CatalystMarker marker : comp.markers)
        {
            int x = this.toGraphX(marker.frame);

            RoundedDraw.fill(context.batcher, x - 4, y + RULER_H - 10, x + 5, y + RULER_H - 2, 2, RoundedDraw.ALL, Colors.A100 | marker.color);
        }

        /* The render strip along the very top: green where a video clip has its frames buffered, red where it has not
         * (just cut, not played yet, offline) */
        for (CatalystLayer layer : comp.layers)
        {
            if (layer.layerType != CatalystLayer.LayerType.VIDEO)
            {
                continue;
            }

            int sx1 = this.toGraphX(layer.startFrame);
            int sx2 = Math.max(sx1 + 1, this.toGraphX(layer.startFrame + layer.duration));

            if (sx2 < this.lanesLeft() || sx1 > this.area.ex())
            {
                continue;
            }

            boolean ready = !CatalystMedia.isOffline(layer) && this.buffered.test(layer);

            context.batcher.box(sx1, y, sx2, y + 2, ready ? 0x7042d68a : 0xe0e8453c);
        }

        context.batcher.unclip(context);

        /* Corner over the headers: the timeline's name */
        context.batcher.box(this.area.x, y, this.lanesLeft(), y + RULER_H, 0xff181d27);
        context.batcher.text(font.limitToWidth(comp.name, HEADER_W - 10), this.area.x + 6, y + 6, 0xffc8d0dc, false);
    }

    private void renderPlayhead(UIContext context, CatalystComposition comp)
    {
        int x = this.toGraphX(this.playhead.getAsInt());

        if (x < this.lanesLeft() || x > this.area.ex())
        {
            return;
        }

        int color = 0xffe8455a;

        context.batcher.box(x, this.area.y, x + 1, this.area.ey(), color);
        RoundedDraw.fill(context.batcher, x - 5, this.area.y + 2, x + 6, this.area.y + 12, 2, RoundedDraw.ALL, color);
    }

    /** HH:MM:SS:FF */
    /* ── Markers and joining ── */

    /** The marker whose mark on the ruler is under the mouse */
    private CatalystComposition.CatalystMarker markerAt(CatalystComposition comp, int mx, int my)
    {
        if (my < this.area.y + RULER_H - 16)
        {
            return null;
        }

        CatalystComposition.CatalystMarker found = null;

        for (CatalystComposition.CatalystMarker marker : comp.markers)
        {
            if (Math.abs(this.toGraphX(marker.frame) - mx) <= 5)
            {
                found = marker;
            }
        }

        return found;
    }

    public void addMarkerAt(int frame)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        comp.markers.add(new CatalystComposition.CatalystMarker(Math.max(0, frame), "", 0xFFE8C43A));
        this.modified();
    }

    private void openMarkerSettings(UIContext context, CatalystComposition comp, CatalystComposition.CatalystMarker marker)
    {
        UIMarkerOverlayPanel.open(context, marker, () ->
        {
            if (this.onPreModify != null)
            {
                this.onPreModify.run();
            }
        }, this::modified, () ->
        {
            if (this.onPreModify != null)
            {
                this.onPreModify.run();
            }

            comp.markers.remove(marker);
            this.modified();
        });
    }

    /** Two clips that were cut from one stretch of one file, side by side on a track: they play on as they were */
    private boolean joinable(CatalystComposition comp, CatalystLayer a, CatalystLayer b)
    {
        return a != b && (a.layerType == CatalystLayer.LayerType.VIDEO || a.layerType == CatalystLayer.LayerType.AUDIO) && a.layerType == b.layerType
            && a.resourcePath != null && !a.resourcePath.trim().isEmpty() && b.resourcePath != null && a.resourcePath.trim().equals(b.resourcePath.trim())
            && a.track == b.track && a.startFrame + a.duration == b.startFrame && a.mediaOffset + a.duration == b.mediaOffset && a.audioOffset == b.audioOffset
            && !this.isLocked(comp, a) && !this.isLocked(comp, b);
    }

    /** The pair to join: two picked clips, or the two that meet at the cut line under the mouse */
    private CatalystLayer[] joinPairAt(CatalystComposition comp, int[] row, int mx)
    {
        List<CatalystLayer> picked = new ArrayList<>(this.selection);

        picked.sort((x, y) -> Integer.compare(x.startFrame, y.startFrame));

        for (int i = 0; i + 1 < picked.size(); i++)
        {
            if (this.joinable(comp, picked.get(i), picked.get(i + 1)))
            {
                return new CatalystLayer[] {picked.get(i), picked.get(i + 1)};
            }
        }

        if (row == null)
        {
            return null;
        }

        for (CatalystLayer a : comp.layers)
        {
            if (a.isAudioClip() != (row[0] == 1) || a.track != row[1] || Math.abs(this.toGraphX(a.startFrame + a.duration) - mx) > 4)
            {
                continue;
            }

            for (CatalystLayer b : comp.layers)
            {
                if (this.joinable(comp, a, b))
                {
                    return new CatalystLayer[] {a, b};
                }
            }
        }

        return null;
    }

    /** The two become one clip, and so do the sound clips linked to them, keys and all */
    public void joinClips(CatalystLayer a, CatalystLayer b)
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        List<CatalystLayer[]> pairs = new ArrayList<>();

        pairs.add(new CatalystLayer[] {a, b});

        for (CatalystLayer first : this.linkedTo(comp, a))
        {
            for (CatalystLayer second : this.linkedTo(comp, b))
            {
                if (this.joinable(comp, first, second))
                {
                    pairs.add(new CatalystLayer[] {first, second});
                }
            }
        }

        for (CatalystLayer[] pair : pairs)
        {
            pair[0].absorb(pair[1]);
            comp.layers.remove(pair[1]);
            this.selection.remove(pair[1]);
        }

        this.selection.clear();
        this.selection.add(a);
        this.selectionChanged();
        this.modified();
    }

    /* ── For the smart tools and the gain window ── */

    /** Whether a clip is out of reach: locked itself or on a locked track */
    public boolean isClipLocked(CatalystLayer layer)
    {
        CatalystComposition comp = this.composition.get();

        return comp != null && this.isLocked(comp, layer);
    }

    /** Closes a gap on a track from a frame on: the clips after it slide left, gliding */
    public void closeGapAt(CatalystComposition comp, boolean audio, int track, int from, int amount)
    {
        this.closeGap(comp, audio, track, from, amount);
    }

    /** Slides a clip along the timeline with its keys; going left it glides there */
    public void shiftClip(CatalystLayer layer, int delta)
    {
        layer.moveKeyframes(delta);
        layer.startFrame += delta;

        if (delta < 0)
        {
            this.glideBy(layer, -delta);
        }
    }

    /** Whether a sound clip is among the picked */
    private boolean soundSelected()
    {
        for (CatalystLayer layer : this.selection)
        {
            if (layer.layerType == CatalystLayer.LayerType.AUDIO || layer.layerType == CatalystLayer.LayerType.NESTED_AUDIO)
            {
                return true;
            }
        }

        return false;
    }

    /* ── Box selection, loading and sync marks ── */

    /** Selects the clips the box touches, on top of what was selected when it began (with Ctrl or Shift) */
    private void updateMarquee(CatalystComposition comp)
    {
        this.gapTrack = -1;

        int x1 = Math.min(this.marqueeX, this.marqueeToX);
        int x2 = Math.max(this.marqueeX, this.marqueeToX);
        int y1 = Math.min(this.marqueeY, this.marqueeToY);
        int y2 = Math.max(this.marqueeY, this.marqueeToY);
        Set<CatalystLayer> next = new LinkedHashSet<>(this.marqueeBase);

        for (CatalystLayer layer : comp.layers)
        {
            boolean audio = layer.isAudioClip();
            List<CatalystTrack> tracks = comp.tracks(audio);

            if (layer.track < 0 || layer.track >= tracks.size() || this.isLocked(comp, layer))
            {
                continue;
            }

            int cx1 = this.toGraphX(layer.startFrame);
            int cx2 = Math.max(cx1 + 2, this.toGraphX(layer.startFrame + layer.duration));
            int cy1 = this.rowY(comp, audio, layer.track) + 2;
            int cy2 = cy1 + this.rowH(audio) - 4;

            if (cx2 >= x1 && cx1 <= x2 && cy2 >= y1 && cy1 <= y2)
            {
                next.add(layer);

                if (this.linkedSelection)
                {
                    next.addAll(this.linkedTo(comp, layer));
                }
            }
        }

        if (!next.equals(this.selection))
        {
            this.selection.clear();
            this.selection.addAll(next);
            this.selectionChanged();
        }
    }

    /** The glass blue box, over the lanes only */
    private void renderMarquee(UIContext context)
    {
        if (this.drag != DRAG_MARQUEE || !this.marqueeMoved)
        {
            return;
        }

        int left = this.lanesLeft();
        int top = this.area.y + RULER_H;
        int x1 = Math.max(left, Math.min(this.marqueeX, this.marqueeToX));
        int x2 = Math.min(this.area.ex(), Math.max(this.marqueeX, this.marqueeToX));
        int y1 = Math.max(top, Math.min(this.marqueeY, this.marqueeToY));
        int y2 = Math.min(this.area.ey(), Math.max(this.marqueeY, this.marqueeToY));

        if (x2 <= x1 || y2 <= y1)
        {
            return;
        }

        context.batcher.box(x1, y1, x2, y2, 0x2e5aa8ff);
        context.batcher.box(x1, y1, x2, y1 + 1, 0x667cc4ff);
        context.batcher.outline(x1, y1, x2, y2, 0xcc7cc4ff);
    }

    /**
     * How many frames this clip starts after the clip it is linked to (negative: before it), 0 when they
     * start together or it has no link. A clip cut in two gets a fresh link of its own for each half, so
     * the pairs a cut makes start together and show nothing.
     */
    private int syncShift(CatalystComposition comp, CatalystLayer layer)
    {
        if (layer.linkId.isEmpty())
        {
            return 0;
        }

        for (CatalystLayer other : comp.layers)
        {
            if (other != layer && layer.linkId.equals(other.linkId))
            {
                int shift = layer.startFrame - other.startFrame;

                if (shift != 0)
                {
                    return shift;
                }
            }
        }

        return 0;
    }

    /** A thin line along the clip's top edge with a light segment running over it, and the word for it when there is room */
    private void renderThrobber(UIContext context, FontRenderer font, int x1, int x2, int y)
    {
        int width = x2 - x1;
        int segment = Math.max(18, width / 3);
        double phase = (System.currentTimeMillis() % 1100L) / 1100D;
        int from = x1 + (int) Math.round(phase * (width + segment)) - segment;

        context.batcher.box(x1 + 1, y, x2 - 1, y + 2, 0x55ffffff);
        context.batcher.box(Math.max(x1 + 1, from), y, Math.min(x2 - 1, from + segment), y + 2, 0xf0bfe0ff);

        String text = UIKeys.CATALYST_NLE_LOADING.get();
        int textW = font.getWidth(text);

        if (width > textW + 90)
        {
            context.batcher.text(text, x2 - textW - 6, y + 4, 0xffbfe0ff, false);
        }
    }

    /** Whether any selected clip has a link */
    private boolean selectionLinked()
    {
        for (CatalystLayer layer : this.selection)
        {
            if (!layer.linkId.isEmpty())
            {
                return true;
            }
        }

        return false;
    }

    /** Breaks the links of the selected clips, and of the clips they were linked to: they are separate from here on */
    public void unlinkSelected()
    {
        CatalystComposition comp = this.composition.get();

        if (comp == null)
        {
            return;
        }

        Set<String> ids = new LinkedHashSet<>();

        for (CatalystLayer layer : this.selection)
        {
            if (!layer.linkId.isEmpty())
            {
                ids.add(layer.linkId);
            }
        }

        if (ids.isEmpty())
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        for (CatalystLayer layer : comp.layers)
        {
            if (ids.contains(layer.linkId))
            {
                layer.linkId = "";
            }
        }

        this.modified();
    }

    public static String timecode(int frame, int fps)
    {
        fps = Math.max(1, fps);
        frame = Math.max(0, frame);

        int frames = frame % fps;
        int seconds = frame / fps;

        return String.format("%02d:%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60, frames);
    }
}
