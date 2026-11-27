package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.eye.PovRecorder;
import mchorse.bbs_mod.film.pov.PovClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.ReplayPov;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.toast.Toast;
import net.minecraft.item.ItemStack;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The recorded point of view: what the player's own interface showed, kept by the film studio's own
 * point of view recorder ({@link PovRecorder}: the screens opened, the cursor, the status effects,
 * the toasts, the totem, the hands' swing and dip, the walking bob), so Recap Studio plays it back
 * through the same point of view as a film.
 *
 * <p><b>Segments.</b> The recorder keys into keyframe channels, which grow slower to insert into the
 * longer they get, so a recording is cut into segments of a minute, each with a recorder of its own
 * and times of its own. A playback reads the one segment the moment is in.</p>
 *
 * <p><b>Playing.</b> {@link #replay} makes, for the segment of a moment, a replay no film holds: its
 * point of view the recorded one, its hotbar, selected slot, off hand and armour from
 * {@link RecapGear}, which a film's replay keeps in its own channels.</p>
 */
public class RecapPov
{
    private static final int SEGMENT_TICKS = 1200;

    private static final class Segment
    {
        private int start;
        /* One of the two: still recording, or sealed */
        private PovRecorder recorder;
        private MapType data;
        private Replay replay;

        private Segment(int start)
        {
            this.start = start;
        }
    }

    private final List<Segment> segments = new ArrayList<>();

    public boolean isEmpty()
    {
        return this.segments.isEmpty();
    }

    public void clear()
    {
        this.segments.clear();
    }

    /* Recording */

    private Segment recording()
    {
        Segment last = this.segments.isEmpty() ? null : this.segments.get(this.segments.size() - 1);

        return last == null || last.recorder == null ? null : last;
    }

    /** Keeps the interface as it is at {@code tick} of the recording. */
    public void record(int tick, ClientPlayerEntity player)
    {
        Segment current = this.recording();

        if (current != null && (tick - current.start >= SEGMENT_TICKS || tick < current.start))
        {
            seal(current, tick - current.start);
            current = null;
        }

        try
        {
            if (current == null)
            {
                current = new Segment(tick);
                current.recorder = new PovRecorder();
                this.segments.add(current);
            }

            current.recorder.record(tick - current.start, player);
        }
        catch (Exception e)
        {
            /* An interface that cannot be read is missing from the point of view, nothing else stops */
            BBSMod.LOGGER.warn("Recap could not keep the point of view of a tick", e);
        }
    }

    public void toast(int tick, Toast toast)
    {
        Segment current = this.recording();

        if (current != null)
        {
            current.recorder.recordToast(toast, tick - current.start);
        }
    }

    public void totem(int tick, ItemStack stack)
    {
        Segment current = this.recording();

        if (current != null)
        {
            current.recorder.recordTotem(stack, tick - current.start);
        }
    }

    /** Ends a segment's recording: what is open closes, its clips go onto its own timeline. */
    private static void seal(Segment segment, int length)
    {
        PovRecorder recorder = segment.recorder;

        if (recorder == null)
        {
            return;
        }

        segment.recorder = null;

        try
        {
            recorder.finish(Math.max(1, length));

            for (PovClip clip : recorder.clips)
            {
                clip.layer.set(recorder.pov.actions.findFreeLayer(clip));
                recorder.pov.actions.addClip(clip);
            }

            segment.data = (MapType) recorder.pov.toData();
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.warn("Recap could not keep a stretch of the point of view", e);
            segment.data = new MapType();
        }
    }

    /** Lets go of the segments that ended before {@code tick}, for the instant buffer. */
    public void dropBefore(int tick)
    {
        while (this.segments.size() > 1 && this.segments.get(1).start <= tick)
        {
            this.segments.remove(0);
        }
    }

    /**
     * A copy of what lies between {@code from} and {@code to} with {@code from} as its start; the
     * segment being recorded is ended first, and the next tick begins another.
     */
    public RecapPov extract(int from, int to)
    {
        Segment current = this.recording();

        if (current != null)
        {
            seal(current, to - current.start);
        }

        RecapPov copy = new RecapPov();

        for (int i = 0; i < this.segments.size(); i++)
        {
            Segment segment = this.segments.get(i);
            boolean ended = i + 1 < this.segments.size() && this.segments.get(i + 1).start <= from;

            if (!ended && segment.start < to && segment.data != null)
            {
                Segment kept = new Segment(segment.start - from);

                kept.data = segment.data;
                copy.segments.add(kept);
            }
        }

        return copy;
    }

    /* Files */

    public boolean write(File file)
    {
        ListType list = new ListType();

        for (Segment segment : this.segments)
        {
            if (segment.data == null)
            {
                continue;
            }

            MapType map = new MapType();

            map.putInt("start", segment.start);
            map.put("pov", segment.data);
            list.add(map);
        }

        MapType root = new MapType();

        root.put("segments", list);

        try
        {
            Files.write(file.toPath(), DataStorageUtils.writeToBytes(root));

            return true;
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.warn("Recap could not write " + file, e);

            return false;
        }
    }

    public static RecapPov read(File file)
    {
        if (file == null || !file.isFile())
        {
            return null;
        }

        try
        {
            BaseType root = DataStorageUtils.readFromBytes(Files.readAllBytes(file.toPath()));

            if (!(root instanceof MapType map))
            {
                return null;
            }

            RecapPov pov = new RecapPov();

            for (BaseType entry : map.getList("segments"))
            {
                if (entry instanceof MapType data)
                {
                    Segment segment = new Segment(data.getInt("start"));

                    segment.data = data.getMap("pov");
                    pov.segments.add(segment);
                }
            }

            return pov;
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.warn("Recap could not read " + file, e);

            return null;
        }
    }

    /* Playing */

    /** The playback of one recap's point of view: the replays made of its segments, once each. */
    public static final class Playback
    {
        private final RecapPov recorded;
        private final RecapGear gear;
        private final RecapMotion motion;
        /* For a recap kept before the point of view was: segments made from what it did keep */
        private final List<Segment> made = new ArrayList<>();

        private Replay replay;
        private int start;

        public Playback(RecapPov recorded, RecapGear gear, RecapMotion motion)
        {
            this.recorded = recorded;
            this.gear = gear;
            this.motion = motion;
        }

        /** The replay of the moment {@code source} of the recording, whose ticks count from {@link #start}. */
        public Replay replay(float source)
        {
            int tick = Math.max(0, (int) Math.floor(source));
            Segment segment = this.segmentAt(tick);

            if (segment.replay == null)
            {
                segment.replay = this.make(segment);
            }

            this.replay = segment.replay;
            this.start = segment.start;

            return this.replay;
        }

        /** Where in the recording the last replay given starts. */
        public int start()
        {
            return this.start;
        }

        private Segment segmentAt(int tick)
        {
            Segment found = null;

            if (this.recorded != null)
            {
                for (Segment segment : this.recorded.segments)
                {
                    if (segment.start > tick)
                    {
                        break;
                    }

                    found = segment;
                }
            }

            if (found != null)
            {
                return found;
            }

            int start = tick / SEGMENT_TICKS * SEGMENT_TICKS;

            for (Segment segment : this.made)
            {
                if (segment.start == start)
                {
                    return segment;
                }
            }

            Segment segment = new Segment(start);

            this.made.add(segment);

            return segment;
        }

        private Replay make(Segment segment)
        {
            Replay replay = new Replay("recap_pov");

            if (segment.data != null)
            {
                try
                {
                    replay.pov.fromData(segment.data);
                }
                catch (Exception e)
                {
                    BBSMod.LOGGER.warn("Recap could not read a stretch of the point of view", e);
                }
            }

            int from = Math.max(0, segment.start);
            int to = segment.start + SEGMENT_TICKS;

            this.fillGear(replay, segment.start, from, to);
            this.fillBob(replay.pov, segment.start, from, to);

            return replay;
        }

        /** The items in the replay's own channels, and the bars where the point of view did not keep them. */
        private void fillGear(Replay replay, int start, int from, int to)
        {
            if (this.gear == null || this.gear.isEmpty())
            {
                return;
            }

            ReplayKeyframes keyframes = replay.keyframes;
            ReplayPov pov = replay.pov;
            RecapGear.Frame frame = new RecapGear.Frame();
            RecapGear.Frame previous = null;
            boolean mode = pov.gameMode.isEmpty();
            boolean bars = pov.health.isEmpty();

            for (int tick = from; tick < Math.min(to, this.gear.size()); tick++)
            {
                if (!this.gear.sample(tick, frame))
                {
                    break;
                }

                int at = tick - start;

                if (previous == null || previous.selected != frame.selected)
                {
                    hold(keyframes.selectedSlot, at, frame.selected);
                }

                for (int slot = 0; slot < ReplayKeyframes.HOTBAR_SIZE && slot < keyframes.hotbar.size(); slot++)
                {
                    holdItem(keyframes.hotbar.get(slot), at, previous, frame, slot);
                }

                holdItem(keyframes.offHand, at, previous, frame, RecapGear.OFF_HAND);
                holdItem(keyframes.armorFeet, at, previous, frame, RecapGear.ARMOR);
                holdItem(keyframes.armorLegs, at, previous, frame, RecapGear.ARMOR + 1);
                holdItem(keyframes.armorChest, at, previous, frame, RecapGear.ARMOR + 2);
                holdItem(keyframes.armorHead, at, previous, frame, RecapGear.ARMOR + 3);

                if (mode && frame.mode >= 0 && (previous == null || previous.mode != frame.mode))
                {
                    hold(pov.gameMode, at, frame.mode);
                }

                if (bars)
                {
                    if (previous == null || previous.health != frame.health) hold(pov.health, at, (double) frame.health);
                    if (previous == null || previous.maxHealth != frame.maxHealth) hold(pov.maxHealth, at, (double) frame.maxHealth);
                    if (previous == null || previous.absorption != frame.absorption) hold(pov.absorption, at, (double) frame.absorption);
                    if (previous == null || previous.food != frame.food) hold(pov.hunger, at, (double) frame.food);
                    if (previous == null || previous.air != frame.air) hold(pov.air, at, (double) frame.air);
                    if (previous == null || previous.armor != frame.armor) hold(pov.armor, at, (double) frame.armor);
                    if (previous == null || previous.progress != frame.progress) hold(pov.experience, at, (double) frame.progress);
                    if (previous == null || previous.level != frame.level) hold(pov.experienceLevel, at, frame.level);
                }

                if (previous == null)
                {
                    previous = new RecapGear.Frame();
                }

                copy(frame, previous);
            }
        }

        /** The walking bob from the recorded stride, for a recap whose point of view did not keep it. */
        private void fillBob(ReplayPov pov, int start, int from, int to)
        {
            if (this.motion == null || this.motion.isEmpty() || !pov.bobPhase.isEmpty())
            {
                return;
            }

            RecapMotion.Frame frame = new RecapMotion.Frame();

            for (int tick = from; tick < Math.min(to, this.motion.size()); tick++)
            {
                if (!this.motion.sample(tick, frame))
                {
                    break;
                }

                boolean walking = frame.is(RecapMotion.ON_GROUND);

                pov.bobPhase.insert(tick - start, (double) -frame.limbPos * 0.15D);
                pov.bobStrength.insert(tick - start, walking ? Math.min(0.1D, frame.limbSpeed * 0.25D) : 0D);
            }
        }

        private static void holdItem(KeyframeChannel<ItemStack> channel, int at, RecapGear.Frame previous, RecapGear.Frame frame, int slot)
        {
            ItemStack stack = frame.item(slot);

            if (previous == null || !ItemStack.areEqual(previous.item(slot), stack))
            {
                hold(channel, at, stack.copy());
            }
        }

        /** A value held until the next change, as the game shows it. */
        private static <T> void hold(KeyframeChannel<T> channel, int at, T value)
        {
            Keyframe<T> key = channel.get(channel.insert(at, value));

            if (key != null)
            {
                key.getInterpolation().setInterp(Interpolations.CONST);
            }
        }

        private static void copy(RecapGear.Frame from, RecapGear.Frame to)
        {
            to.selected = from.selected;
            to.health = from.health;
            to.maxHealth = from.maxHealth;
            to.absorption = from.absorption;
            to.food = from.food;
            to.air = from.air;
            to.level = from.level;
            to.progress = from.progress;
            to.armor = from.armor;
            to.mode = from.mode;
            System.arraycopy(from.items, 0, to.items, 0, from.items.length);
        }
    }
}
