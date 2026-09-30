package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.BBSMod;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manages loading, saving, and deletion of Catalyst Editor project files.
 */
public class CatalystProjectManager
{
    private static final Set<String> DAMAGED_PROJECTS = new HashSet<>();

    private static final Set<String> RESERVED_NAMES = Set.of(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    public static File getProjectsFolder()
    {
        File folder = BBSMod.getAssetsPath("catalyst_projects");

        if (!folder.exists())
        {
            folder.mkdirs();
        }

        return folder;
    }

    private static String hashString(String input)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();

            for (int i = 0; i < 4; i++)
            {
                hex.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }

            return hex.toString();
        }
        catch (Exception e)
        {
            return String.format(Locale.ROOT, "%08x", (long) input.hashCode() & 0xFFFFFFFFL);
        }
    }

    public static File getProjectFile(String projectName)
    {
        if (projectName == null || projectName.trim().isEmpty())
        {
            projectName = "project";
        }

        String safeName = projectName.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (safeName.isEmpty() || safeName.replaceAll("_", "").isEmpty())
        {
            safeName = "project";
        }

        if (safeName.length() > 40)
        {
            safeName = safeName.substring(0, 40);
        }

        if (RESERVED_NAMES.contains(safeName.toUpperCase(Locale.ROOT)))
        {
            safeName = "_" + safeName;
        }

        String hash = hashString(projectName);
        String fileName = safeName + "_" + hash + ".json";

        return new File(getProjectsFolder(), fileName);
    }

    public static List<CatalystProject> loadAllProjects()
    {
        List<CatalystProject> list = new ArrayList<>();
        File folder = getProjectsFolder();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));

        DAMAGED_PROJECTS.clear();

        if (files != null)
        {
            for (File file : files)
            {
                CatalystProject project = CatalystProject.load(file);

                if (project != null)
                {
                    list.add(project);
                }
                else
                {
                    DAMAGED_PROJECTS.add(file.getName());
                }
            }
        }

        list.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));

        return list;
    }

    public static boolean isProjectDamaged(String fileName)
    {
        return DAMAGED_PROJECTS.contains(fileName);
    }

    public static Set<String> getDamagedProjects()
    {
        return Collections.unmodifiableSet(DAMAGED_PROJECTS);
    }

    public static void saveProject(CatalystProject project)
    {
        if (project == null)
        {
            return;
        }

        File file = getProjectFile(project.name);

        /* If the target file previously failed to load as damaged, ensure it was backed up before overwrite */
        if (file.exists() && isProjectDamaged(file.getName()))
        {
            File backup = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());

            try
            {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                BBSMod.LOGGER.info("Backed up damaged project file before overwriting: " + backup.getAbsolutePath());
            }
            catch (Exception e)
            {
                BBSMod.LOGGER.error("Failed to backup damaged project file before overwrite: " + file.getAbsolutePath(), e);
            }

            DAMAGED_PROJECTS.remove(file.getName());
        }

        /* If the project was renamed or migrated to the hashed filename, clean up the previous file */
        if (project.file != null && !project.file.equals(file) && project.file.exists())
        {
            DAMAGED_PROJECTS.remove(project.file.getName());
            project.file.delete();
        }

        project.file = file;
        project.save(file);
    }

    public static boolean deleteProject(CatalystProject project)
    {
        if (project == null)
        {
            return false;
        }

        boolean deleted = false;
        File file = getProjectFile(project.name);

        if (project.file != null && project.file.exists())
        {
            DAMAGED_PROJECTS.remove(project.file.getName());
            deleted = project.file.delete();
        }

        if (file.exists())
        {
            DAMAGED_PROJECTS.remove(file.getName());
            deleted = file.delete() || deleted;
        }

        return deleted;
    }
}
