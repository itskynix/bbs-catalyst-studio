package mchorse.bbs_mod.utils.manager;

import mchorse.bbs_mod.settings.values.core.ValueGroup;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Folder based manager
 */
public abstract class FolderManager <T extends ValueGroup> implements IManager<T>
{
    protected Supplier<File> folder;

    public FolderManager(Supplier<File> folder)
    {
        this.folder = folder;
    }

    public File getFolder()
    {
        if (this.folder == null)
        {
            return null;
        }

        File file = this.folder.get();

        if (file != null && !file.exists())
        {
            file.mkdirs();
        }

        return file;
    }

    @Override
    public boolean exists(String name)
    {
        File file = this.getFile(name);

        return file != null && file.exists();
    }

    @Override
    public boolean rename(String from, String to)
    {
        File file = this.getFile(from);
        File toFile = this.getFile(to);

        if (file != null && file.exists() && toFile != null)
        {
            if (file.renameTo(toFile))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean delete(String name)
    {
        File file = this.getFile(name);

        return file != null && file.delete();
    }

    /**
     * Add a folder.
     */
    public boolean addFolder(String path)
    {
        if (path == null)
        {
            return false;
        }

        File folder = this.getFolder(this.normalizePath(path));

        if (folder == null || folder.exists())
        {
            return false;
        }

        return folder.mkdirs();
    }

    /**
     * Rename given folder to another name. From and to arguments expect trailing slashes!
     */
    public boolean renameFolder(String from, String to)
    {
        if (from == null || to == null)
        {
            return false;
        }

        from = this.normalizePath(from);
        to = this.normalizePath(to);

        File folder = this.getFolder(from);
        File toFolder = this.getFolder(to);

        if (folder != null && folder.isDirectory() && toFolder != null)
        {
            if (folder.renameTo(toFolder))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Delete given folder. It only works if the folder is empty.
     */
    public boolean deleteFolder(String path)
    {
        if (path == null)
        {
            return false;
        }

        File folder = this.getFolder(this.normalizePath(path));

        if (folder != null && folder.isDirectory())
        {
            if (folder.delete())
            {
                return true;
            }
        }

        return false;
    }

    private String normalizePath(String path)
    {
        if (path == null)
        {
            return "";
        }

        return path.endsWith("/") ? path : path + "/";
    }

    @Override
    public Collection<String> getKeys()
    {
        Set<String> set = new HashSet<>();

        if (this.folder == null)
        {
            return set;
        }

        this.recursiveFind(set, this.getFolder(), "");

        return set;
    }

    private void recursiveFind(Set<String> set, File folder, String prefix)
    {
        if (folder == null)
        {
            return;
        }

        File[] files = folder.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            String name = file.getName();

            if (file.isFile() && this.isData(file))
            {
                set.add(prefix + name.substring(0, name.lastIndexOf(".")));
            }
            else if (file.isDirectory() && !file.getName().startsWith("_"))
            {
                File[] children = file.listFiles();

                if (children == null || children.length == 0)
                {
                    set.add(prefix + name + "/");
                }
                else
                {
                    this.recursiveFind(set, file, prefix + name + "/");
                }
            }
        }
    }

    protected boolean isData(File file)
    {
        return file != null && file.getName().endsWith(this.getExtension());
    }

    public File getFile(String name)
    {
        if (this.folder == null || name == null)
        {
            return null;
        }

        File base = this.getFolder();

        if (base == null)
        {
            return null;
        }

        File file = new File(base, name + this.getExtension());

        try
        {
            Path basePath = base.toPath().toAbsolutePath().normalize();
            Path filePath = file.toPath().toAbsolutePath().normalize();

            /* Prevent directory traversal outside the base folder */
            if (!filePath.startsWith(basePath))
            {
                return null;
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return file;
    }

    public File getFolder(String path)
    {
        if (this.folder == null || path == null)
        {
            return null;
        }

        File base = this.getFolder();

        if (base == null)
        {
            return null;
        }

        File file = new File(base, path);

        try
        {
            Path basePath = base.toPath().toAbsolutePath().normalize();
            Path filePath = file.toPath().toAbsolutePath().normalize();

            /* Prevent directory traversal outside the base folder */
            if (!filePath.startsWith(basePath))
            {
                return null;
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return file;
    }

    protected String getExtension()
    {
        return ".json";
    }
}