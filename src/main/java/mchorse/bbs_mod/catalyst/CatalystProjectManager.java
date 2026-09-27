package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.BBSMod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages loading, saving, and deletion of Catalyst Editor project files.
 */
public class CatalystProjectManager
{
    public static File getProjectsFolder()
    {
        File folder = BBSMod.getAssetsPath("catalyst_projects");

        if (!folder.exists())
        {
            folder.mkdirs();
        }

        return folder;
    }

    public static File getProjectFile(String projectName)
    {
        String safeName = projectName.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (safeName.isEmpty())
        {
            safeName = "project";
        }

        return new File(getProjectsFolder(), safeName + ".json");
    }

    public static List<CatalystProject> loadAllProjects()
    {
        List<CatalystProject> list = new ArrayList<>();
        File folder = getProjectsFolder();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

        if (files != null)
        {
            for (File file : files)
            {
                CatalystProject project = CatalystProject.load(file);

                if (project != null)
                {
                    list.add(project);
                }
            }
        }

        list.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));

        return list;
    }

    public static void saveProject(CatalystProject project)
    {
        if (project == null)
        {
            return;
        }

        File file = getProjectFile(project.name);
        project.save(file);
    }

    public static boolean deleteProject(CatalystProject project)
    {
        if (project == null)
        {
            return false;
        }

        File file = getProjectFile(project.name);

        if (file.exists())
        {
            return file.delete();
        }

        return false;
    }
}
