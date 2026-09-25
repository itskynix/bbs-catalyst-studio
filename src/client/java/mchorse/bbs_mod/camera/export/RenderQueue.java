package mchorse.bbs_mod.camera.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global Deliver Render Queue manager.
 */
public class RenderQueue
{
    private static final List<RenderJob> jobs = new ArrayList<>();
    private static RenderJob activeJob = null;
    private static boolean isProcessingQueue = false;
    private static final List<Runnable> listeners = new ArrayList<>();

    public static synchronized List<RenderJob> getJobs()
    {
        return Collections.unmodifiableList(jobs);
    }

    public static synchronized void addJob(RenderJob job)
    {
        if (job != null)
        {
            jobs.add(job);
            notifyListeners();
        }
    }

    public static synchronized void removeJob(RenderJob job)
    {
        if (job != null)
        {
            jobs.remove(job);
            if (activeJob == job)
            {
                activeJob = null;
            }
            notifyListeners();
        }
    }

    public static synchronized void clear()
    {
        jobs.clear();
        activeJob = null;
        isProcessingQueue = false;
        notifyListeners();
    }

    public static synchronized void clearCompleted()
    {
        jobs.removeIf((j) -> j.getStatus() == RenderJob.Status.COMPLETED || j.getStatus() == RenderJob.Status.CANCELLED);
        notifyListeners();
    }

    public static synchronized RenderJob getActiveJob()
    {
        return activeJob;
    }

    public static synchronized void setActiveJob(RenderJob job)
    {
        activeJob = job;
        if (job != null)
        {
            job.setStatus(RenderJob.Status.RENDERING);
        }
        notifyListeners();
    }

    public static synchronized RenderJob getNextPendingJob()
    {
        for (RenderJob job : jobs)
        {
            if (job.getStatus() == RenderJob.Status.PENDING)
            {
                return job;
            }
        }
        return null;
    }

    public static synchronized boolean isProcessingQueue()
    {
        return isProcessingQueue;
    }

    public static synchronized void setProcessingQueue(boolean processing)
    {
        isProcessingQueue = processing;
        notifyListeners();
    }

    public static void addListener(Runnable listener)
    {
        if (listener != null && !listeners.contains(listener))
        {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener)
    {
        listeners.remove(listener);
    }

    public static void notifyListeners()
    {
        for (Runnable listener : listeners)
        {
            try
            {
                listener.run();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
