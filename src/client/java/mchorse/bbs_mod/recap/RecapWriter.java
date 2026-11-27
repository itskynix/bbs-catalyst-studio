package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Writes Recap's files away from the game thread, so saving a recording with millions of block
 * changes never stops a frame.
 *
 * <p><b>How.</b> What is to be written is taken on the game thread as a copy of its own (the
 * caller hands in a snapshot), and one writer thread packs and writes it into a temporary file
 * next to the target, then moves that over the target in one step. A file on disk is therefore
 * always a whole one, the old one or the new one, never half of either; a crash or a full disk in
 * the middle leaves the old one as it was.</p>
 *
 * <p><b>Order.</b> The recaps' own small files have a thread of their own, the large data files
 * another; each writes one job after another, in the order asked, a file always on the same one. Several
 * writes asked of one file while it waits: only the last is written, the older ones being already
 * out of date. Reading a file waits for its write first ({@link #await}), so what was just saved
 * is what is read back. Leaving the game waits for everything still waiting ({@link #shutdown}).</p>
 */
public final class RecapWriter
{
    /** Writes a file's content into the file given: the temporary one. */
    @FunctionalInterface
    public interface Job
    {
        boolean write(File into) throws IOException;
    }

    /** Runs a sequential storage task on the background data writer thread. */
    @FunctionalInterface
    public interface DataTask
    {
        boolean run() throws Exception;
    }

    /* Two writers: the recaps' own small files, so the list never waits behind a recording's
     * millions of blocks, and the large data files. A file always goes to the same one, so the
     * writes of one file keep their order */
    private static final ExecutorService TEXT = writer("BBS Recap writer");
    private static final ExecutorService DATA = writer("BBS Recap data writer");

    private static ExecutorService writer(String name)
    {
        return Executors.newSingleThreadExecutor((runnable) ->
        {
            Thread thread = new Thread(runnable, name);

            /* Not holding the game open by itself; shutdown() waits for it on the way out */
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);

            return thread;
        });
    }

    private static ExecutorService executorOf(File file)
    {
        return file.getName().endsWith(".json") ? TEXT : DATA;
    }

    /** The newest write of each file, its generation, and what is waited on. */
    private static final Map<File, Integer> generations = new HashMap<>();
    private static final Map<File, CompletableFuture<Boolean>> pending = new HashMap<>();
    private static boolean closed;

    private RecapWriter()
    {}

    private static File key(File file)
    {
        return file.getAbsoluteFile();
    }

    /**
     * Writes a file in the background. {@code done}, when given, hears on the game thread whether
     * it was written; a write overtaken by a newer one of the same file counts as written.
     */
    public static synchronized CompletableFuture<Boolean> write(File target, Job job, Consumer<Boolean> done)
    {
        File file = key(target);

        if (closed)
        {
            /* On the way out: written here and now, the thread no longer takes work */
            boolean written = run(file, job);

            if (done != null)
            {
                done.accept(written);
            }

            return CompletableFuture.completedFuture(written);
        }

        int generation = generations.getOrDefault(file, 0) + 1;

        generations.put(file, generation);

        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
        {
            synchronized (RecapWriter.class)
            {
                if (generations.getOrDefault(file, 0) != generation)
                {
                    /* A newer write of this file waits behind this one: it alone is written */
                    return true;
                }
            }

            return run(file, job);
        }, executorOf(file));

        pending.put(file, future);
        future.whenComplete((written, error) ->
        {
            synchronized (RecapWriter.class)
            {
                if (pending.get(file) == future)
                {
                    pending.remove(file);
                }
            }

            if (done != null)
            {
                boolean ok = error == null && Boolean.TRUE.equals(written);

                net.minecraft.client.MinecraftClient.getInstance().execute(() -> done.accept(ok));
            }
        });

        return future;
    }

    /**
     * Runs a storage task on the background {@code DATA} thread in strict FIFO order and tracks
     * its completion under {@code keyFile} for {@link #await} and {@link #shutdown}.
     */
    public static synchronized CompletableFuture<Boolean> writeData(File keyFile, DataTask task)
    {
        File file = key(keyFile);

        if (closed)
        {
            return CompletableFuture.completedFuture(runDataTask(file, task));
        }

        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> runDataTask(file, task), DATA);

        pending.put(file, future);
        future.whenComplete((written, error) ->
        {
            synchronized (RecapWriter.class)
            {
                if (pending.get(file) == future)
                {
                    pending.remove(file);
                }
            }
        });

        return future;
    }

    private static boolean runDataTask(File file, DataTask task)
    {
        try
        {
            return task.run();
        }
        catch (Throwable e)
        {
            BBSMod.LOGGER.error("Recap data task failed for " + file.getName(), e);

            return false;
        }
    }

    /** Writes into a temporary file next to the target and moves it over the target in one step. */
    private static boolean run(File file, Job job)
    {
        File folder = file.getParentFile();
        File temporary = new File(folder, file.getName() + ".tmp");

        try
        {
            if (folder != null)
            {
                folder.mkdirs();
            }

            if (!job.write(temporary))
            {
                temporary.delete();

                return false;
            }

            if (file.getName().endsWith(".json") && file.isFile())
            {
                File backup = new File(folder, file.getName() + ".bak");

                try
                {
                    Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                catch (IOException ignored)
                {}
            }

            try
            {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
        }
        catch (Throwable e)
        {
            /* Even running out of memory on a huge recap leaves the old file and the game standing */
            BBSMod.LOGGER.error("Recap could not write " + file.getName(), e);
            temporary.delete();

            return false;
        }
    }

    /** Whether a write of the file still waits or runs. */
    public static synchronized boolean isPending(File file)
    {
        return pending.containsKey(key(file));
    }

    /** Waits for the file's write, if one waits or runs, so it reads back as it was saved. */
    public static void await(File file)
    {
        CompletableFuture<Boolean> future;

        synchronized (RecapWriter.class)
        {
            future = pending.get(key(file));
        }

        join(future);
    }

    /** Waits for the writes of every file that passes the test. */
    public static void awaitAll(Predicate<File> which)
    {
        CompletableFuture<?>[] futures;

        synchronized (RecapWriter.class)
        {
            futures = pending.entrySet().stream().filter((entry) -> which.test(entry.getKey())).map(Map.Entry::getValue).toArray(CompletableFuture[]::new);
        }

        for (CompletableFuture<?> future : futures)
        {
            join(future);
        }
    }

    private static void join(CompletableFuture<?> future)
    {
        if (future == null)
        {
            return;
        }

        try
        {
            future.get(60, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.warn("A recap write took too long to wait for", e);
        }
    }

    /** On the way out of the game: everything still waiting is written before it closes. */
    public static void shutdown()
    {
        synchronized (RecapWriter.class)
        {
            if (closed)
            {
                return;
            }

            closed = true;
        }

        TEXT.shutdown();
        DATA.shutdown();

        try
        {
            if (!TEXT.awaitTermination(30, TimeUnit.SECONDS) || !DATA.awaitTermination(120, TimeUnit.SECONDS))
            {
                BBSMod.LOGGER.error("Recap files were still being written when the game closed");
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
