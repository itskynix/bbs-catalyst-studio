package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.UIUtils;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import mchorse.bbs_mod.camera.export.HardwareEncoder;

public class VideoRecorder
{
    private static final Link RENDER_COMPLETE_SOUND = Link.assets("sounds/render_complete.ogg");

    private Process process;
    private WritableByteChannel channel;
    private boolean recording;

    private ByteBuffer buffer;
    private int textureId = -1;
    private int textureWidth;
    private int textureHeight;
    private int counter;

    public int serverTicks;
    public int lastServerTicks;

    public boolean isRecording()
    {
        return this.recording;
    }

    public int getTextureId()
    {
        return this.textureId;
    }

    public int getCounter()
    {
        return this.counter;
    }

    private int[] pbos;
    private int pboIndex;

    private int customExportFboId = -1;

    public void setExportFboId(int id)
    {
        this.customExportFboId = id;
    }

    public int getExportFboId()
    {
        if (this.customExportFboId > 0)
        {
            return this.customExportFboId;
        }
        return BBSRendering.getExportFboId();
    }

    private mchorse.bbs_mod.camera.export.VideoExportProfile currentProfile;
    private String lastErrorMessage = "";

    public static void sendChatMessage(String message)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null)
        {
            mc.execute(() ->
            {
                if (mc.player != null)
                {
                    mc.player.sendMessage(net.minecraft.text.Text.literal(message), false);
                }
            });
        }
        else
        {
            System.err.println(message);
        }
    }

    public String getLastErrorMessage()
    {
        return this.lastErrorMessage;
    }

    public mchorse.bbs_mod.camera.export.VideoExportProfile getCurrentProfile()
    {
        return this.currentProfile;
    }

    /**
     * Start recording the video using ffmpeg
     */
    public void startRecording(String movieName, File audioFile, int textureId, int width, int height)
    {
        this.startRecording(movieName, audioFile, textureId, width, height, null);
    }

    /**
     * Start recording the video using ffmpeg with a specific export profile
     */
    public void startRecording(String movieName, File audioFile, int textureId, int width, int height, mchorse.bbs_mod.camera.export.VideoExportProfile profile)
    {
        if (this.recording)
        {
            return;
        }

        this.currentProfile = profile;
        this.counter = 0;
        this.textureId = textureId;
        this.textureWidth = width;
        this.textureHeight = height;

        int size = width * height * 4;

        if (this.buffer == null || this.buffer.capacity() < size)
        {
            if (this.buffer != null)
            {
                MemoryUtil.memFree(this.buffer);
            }
            this.buffer = MemoryUtil.memAlloc(size);
        }

        try
        {
            File movies = BBSRendering.getVideoFolder();

            movies.mkdirs();

            Path path = Paths.get(movies.toString());

            if (movieName == null || movieName.isEmpty())
            {
                movieName = StringUtils.createTimestampFilename();
            }

            List<String> args;

            if (profile != null)
            {
                mchorse.bbs_mod.camera.export.FFmpegCommandBuilder cmdBuilder = new mchorse.bbs_mod.camera.export.FFmpegCommandBuilder(profile)
                    .movieName(movieName)
                    .audio(audioFile)
                    .inputResolution(width, height)
                    .inputFramerate(BBSRendering.getVideoFrameRate())
                    .motionBlur(BBSRendering.getMotionBlur());

                args = cmdBuilder.buildRecordingArgs();
            }
            else
            {
                String params = audioFile == null
                    ? BBSSettings.videoArguments.get()
                    : BBSSettings.videoArgumentsAudio.get();
                StringBuilder filters = new StringBuilder("vflip");
                float frameRate = (float) BBSRendering.getVideoFrameRate();

                int motionBlur = BBSRendering.getMotionBlur();

                for (int i = 0; i < motionBlur; i++)
                {
                    filters.append(",tblend=all_mode=average,framestep=2");
                }

                args = new ArrayList<>();
                String encoder = FFMpegUtils.getFFMPEG();

                args.add(encoder);

                for (String arg : params.split(" "))
                {
                    if (arg.isEmpty())
                    {
                        continue;
                    }

                    arg = arg.replace("%WIDTH%", String.valueOf(width));
                    arg = arg.replace("%HEIGHT%", String.valueOf(height));
                    arg = arg.replace("%FPS%", String.valueOf(frameRate));
                    arg = arg.replace("%NAME%", movieName);
                    arg = arg.replace("%FILTERS%", filters.toString());

                    if (audioFile != null)
                    {
                        arg = arg.replace("%AUDIO_TRACK%", audioFile.getAbsolutePath());
                    }

                    args.add(arg);
                }
            }

            System.out.println("[VideoRecorder] Launching FFmpeg with command: " + String.join(" ", args));

            /**
             * macOS reads the frame synchronously straight into {@link #buffer} (see
             * {@link #recordFrameDirect()}); the asynchronous PBO pipeline below misbehaves
             * there and produces pitch-black footage, so we only set it up off macOS.
             */
            if (OS.CURRENT == OS.MACOS)
            {
                this.pbos = null;
            }
            else
            {
                this.pbos = new int[2];
                this.pboIndex = 0;

                for (int i = 0; i < 2; i++)
                {
                    this.pbos[i] = GL30.glGenBuffers();

                    GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[i]);
                    GL30.glBufferData(GL30.GL_PIXEL_PACK_BUFFER, size, GL30.GL_STREAM_READ);
                }

                GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);
            }

            // Clean quotes from every argument to prevent Windows CreateProcess error 123
            List<String> cleanArgs = new ArrayList<>();
            for (String a : args)
            {
                if (a != null)
                {
                    String c = a.replace("\"", "").trim();
                    if (!c.isEmpty())
                    {
                        cleanArgs.add(c);
                    }
                }
            }

            // Determine safe absolute working directory to avoid 'in directory ""' failure
            File workDir = movies != null ? movies.getAbsoluteFile() : null;
            if (workDir == null || !workDir.exists() || !workDir.isDirectory())
            {
                workDir = BBSMod.getGameFolder();
            }
            if (workDir == null || !workDir.exists())
            {
                workDir = new File(".").getAbsoluteFile();
            }

            File log = new File(workDir, movieName.concat(".log"));

            if (!BBSSettings.videoEncoderLog.get())
            {
                log = BBSMod.getSettingsPath("video.log");
            }

            System.out.println("[VideoRecorder] Launching FFmpeg with command: " + String.join(" ", cleanArgs));

            ProcessBuilder builder = new ProcessBuilder(cleanArgs);
            builder.directory(workDir);
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);

            this.process = builder.start();

            // Give FFmpeg a brief moment to validate parameters and codec initialization
            try
            {
                Thread.sleep(120);
            }
            catch (InterruptedException ignored)
            {}

            if (!this.process.isAlive())
            {
                int exitCode = this.process.exitValue();
                String errDetail = "";
                System.err.println("[VideoRecorder] FFmpeg failed to initialize (exit code: " + exitCode + ")!");
                if (log.exists())
                {
                    try
                    {
                        List<String> logLines = Files.readAllLines(log.toPath());
                        System.err.println("[VideoRecorder] --- FFmpeg Log Dump (" + logLines.size() + " lines) ---");
                        for (String line : logLines)
                        {
                            System.err.println("  [FFmpeg] " + line);
                            String lower = line.toLowerCase();
                            if (lower.contains("error") || lower.contains("unknown") || lower.contains("failed") || lower.contains("invalid") || lower.contains("cannot"))
                            {
                                errDetail = line.trim();
                            }
                        }
                        System.err.println("[VideoRecorder] --- End FFmpeg Log Dump ---");
                        if (errDetail.isEmpty() && !logLines.isEmpty())
                        {
                            errDetail = logLines.get(logLines.size() - 1).trim();
                        }
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }

                String failReason = exitCode + (errDetail.isEmpty() ? "" : " / " + errDetail);
                this.lastErrorMessage = "FFmpeg Hatası: " + failReason;
                System.err.println("[VideoRecorder] " + this.lastErrorMessage);
                sendChatMessage("§c[BBS] FFmpeg Hatası: " + failReason);
                sendChatMessage("§c[BBS] Render başlatılamadı: FFmpeg süreci sonlandı (kod " + exitCode + ")");
                this.stopRecording(false);
                return;
            }

            /**
             * Java wraps the process output stream into a BufferedOutputStream,
             *
             * but its little buffer is just slowing everything down with the
             * huge amount of data we're dealing here, so unwrap it with this little
             * hack.
             */
            OutputStream os = this.process.getOutputStream();
            Unsafe unsafe = UnsafeUtils.getUnsafe();

            if (os instanceof FilterOutputStream)
            {
                try
                {
                    Field outField = FilterOutputStream.class.getDeclaredField("out");

                    os = (OutputStream) unsafe.getObject(os, unsafe.objectFieldOffset(outField));
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            os = new BufferedOutputStream(os, 256 * 1024);
            this.channel = Channels.newChannel(os);
            this.recording = true;

            UIUtils.playClick(2F);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.lastErrorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
            sendChatMessage("§c[BBS] Render başlatılamadı: " + this.lastErrorMessage);
        }

        this.serverTicks = this.lastServerTicks = 0;
    }

    /**
     * Stop recording
     */
    public void stopRecording()
    {
        this.stopRecording(true);
    }

    /**
     * Stop recording. With {@code finishEffects} false the completion sound and the
     * folder opening are skipped - the caller runs {@link #playFinishEffects()} itself
     * once the file is actually final (audio post pass).
     */
    public void stopRecording(boolean finishEffects)
    {
        if (!this.recording)
        {
            return;
        }

        if (this.pbos != null)
        {
            for (int pbo : this.pbos)
            {
                GL30.glDeleteBuffers(pbo);
            }
        }

        this.pbos = null;
        this.textureId = -1;

        if (this.buffer != null)
        {
            MemoryUtil.memFree(this.buffer);

            this.buffer = null;
        }

        try
        {
            if (this.channel != null && this.channel.isOpen())
            {
                this.channel.close();
            }

            this.channel = null;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        try
        {
            if (this.process != null)
            {
                this.process.waitFor(1, TimeUnit.MINUTES);
                this.process.destroy();
            }

            this.process = null;
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }

        this.recording = false;

        if (finishEffects)
        {
            this.playFinishEffects();
        }

        this.serverTicks = this.lastServerTicks = 0;
    }

    /**
     * The end-of-export feedback (completion sound, opening the movies folder).
     */
    public void playFinishEffects()
    {
        if (BBSSettings.videoPlaySoundAfterExport.get())
        {
            if (BBSModClient.getSounds().play(RENDER_COMPLETE_SOUND) == null)
            {
                UIUtils.playClick(0.5F);
            }
        }

        if (BBSSettings.videoOpenFolderAfterExport.get())
        {
            File folder = BBSRendering.getVideoFolder();
            MinecraftClient.getInstance().execute(() -> UIUtils.openFolder(folder));
        }
    }

    /**
     * Record a frame
     */
    public void recordFrame()
    {
        if (!this.recording)
        {
            return;
        }

        if (OS.CURRENT == OS.MACOS)
        {
            this.recordFrameDirect();
        }
        else
        {
            this.recordFramePBO();
        }

        this.counter += 1;
    }

    /**
     * Asynchronous read-back path (Windows/Linux): {@code glReadPixels} directly from
     * target export FBO into a ping-pong pair of pixel pack buffers, mapping the
     * previously filled buffer to overlap GPU read-back with CPU-side write to ffmpeg.
     */
    private void recordFramePBO()
    {
        try
        {
            int pbo = this.pboIndex;
            int nextPbo = (this.pboIndex + 1) % this.pbos.length;

            int targetFbo = BBSRendering.getExportFboId();

            if (targetFbo <= 0)
            {
                MinecraftClient mc = MinecraftClient.getInstance();

                targetFbo = (mc != null && mc.getFramebuffer() != null) ? mc.getFramebuffer().fbo : 0;
            }
            int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, targetFbo);
            GL11.glReadBuffer(targetFbo != 0 ? GL30.GL_COLOR_ATTACHMENT0 : GL11.GL_BACK);

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);

            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[pbo]);
            GL11.glReadPixels(0, 0, this.textureWidth, this.textureHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);

            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[nextPbo]);

            ByteBuffer mappedBuffer = GL30.glMapBuffer(GL30.GL_PIXEL_PACK_BUFFER, GL30.GL_READ_ONLY);

            if (mappedBuffer != null && this.counter != 0)
            {
                mappedBuffer.position(0);
                mappedBuffer.limit(this.textureWidth * this.textureHeight * 4);

                if (this.counter < 10)
                {
                    byte r = mappedBuffer.get(0);
                    byte g = mappedBuffer.get(1);
                    byte b = mappedBuffer.get(2);
                    byte a = mappedBuffer.get(3);
                    System.out.println("[VideoRecorder PBO Capture CHECK] Frame " + this.counter + " -> R:" + (r & 0xFF) + " G:" + (g & 0xFF) + " B:" + (b & 0xFF) + " A:" + (a & 0xFF));
                    mappedBuffer.rewind();
                }

                this.channel.write(mappedBuffer);
            }

            GL30.glUnmapBuffer(GL30.GL_PIXEL_PACK_BUFFER);
            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);

            this.pboIndex = nextPbo;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Synchronous read-back path (macOS): {@code glReadPixels} directly from target export FBO
     * straight into {@link #buffer} and write it to ffmpeg.
     */
    private void recordFrameDirect()
    {
        this.buffer.clear();

        int targetFbo = BBSRendering.getExportFboId();

        if (targetFbo <= 0)
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            targetFbo = (mc != null && mc.getFramebuffer() != null) ? mc.getFramebuffer().fbo : 0;
        }
        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, targetFbo);
        GL11.glReadBuffer(targetFbo != 0 ? GL30.GL_COLOR_ATTACHMENT0 : GL11.GL_BACK);

        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);

        GL11.glReadPixels(0, 0, this.textureWidth, this.textureHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.buffer);
        this.buffer.rewind();
        this.buffer.position(0);
        this.buffer.limit(this.textureWidth * this.textureHeight * 4);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);

        if (this.counter < 10)
        {
            byte r = this.buffer.get(0);
            byte g = this.buffer.get(1);
            byte b = this.buffer.get(2);
            byte a = this.buffer.get(3);
            System.out.println("[VideoRecorder Capture CHECK] Frame " + this.counter + " -> R:" + (r & 0xFF) + " G:" + (g & 0xFF) + " B:" + (b & 0xFF) + " A:" + (a & 0xFF));
            this.buffer.rewind();
        }

        try
        {
            this.buffer.position(0);
            this.buffer.limit(this.textureWidth * this.textureHeight * 4);
            this.channel.write(this.buffer);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Toggle recording of the video
     */
    public void toggleRecording(int textureId, int textureWidth, int textureHeight)
    {
        if (this.recording)
        {
            this.stopRecording();
        }
        else
        {
            this.startRecording(StringUtils.createTimestampFilename(), null, textureId, textureWidth, textureHeight);
        }

        UIUtils.playClick();
    }
}