/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.client.BBSRendering
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gl.Framebuffer
 *  net.minecraft.client.gl.SimpleFramebuffer
 *  org.lwjgl.BufferUtils
 *  org.lwjgl.opengl.GL11
 *  org.lwjgl.opengl.GL13
 *  org.lwjgl.opengl.GL15
 *  org.lwjgl.opengl.GL20
 *  org.lwjgl.opengl.GL30
 */
package mchorse.bbs_mod.camera.pov.hand.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.IntBuffer;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class PovHandDepthManager {
    private static Framebuffer worldDepthFbo;
    private static Framebuffer replayDepthFbo;
    private static Framebuffer replayOnlyDepthFbo;
    private static Framebuffer savedSceneDepthFbo;
    private static Framebuffer tempHandDepthFbo;
    private static boolean hasCapturedWorldDepth;
    private static boolean hasCapturedReplayDepth;
    private static boolean hasSavedSceneDepth;
    private static int replayOnlyProgram;
    private static int uReplayWorldDepthLoc;
    private static int uReplayFullDepthLoc;
    private static int compressProgram;
    private static int uCompressSavedDepthLoc;
    private static int uCompressWorldDepthLoc;
    private static int uCompressModeLoc;
    private static int mergeProgram;
    private static int uMergeSavedSceneDepthLoc;
    private static int uMergeHandDepthLoc;
    private static int uMergeWorldDepthLoc;
    private static int uMergeModeLoc;
    private static int currentInteractionIrisMode;
    private static int quadVao;
    private static int quadVbo;
    private static final IntBuffer viewportBuf;

    private PovHandDepthManager() {
    }

    public static void onRenderWorldStart() {
        hasCapturedWorldDepth = false;
        hasCapturedReplayDepth = false;
        hasSavedSceneDepth = false;
    }

    public static void onBeforeReplayRender() {
        if (!hasCapturedWorldDepth) {
            PovHandDepthManager.captureWorldDepth();
            hasCapturedWorldDepth = true;
        }
    }

    public static void onRenderWorldEnd() {
        if (!hasCapturedWorldDepth) {
            PovHandDepthManager.captureWorldDepth();
            hasCapturedWorldDepth = true;
        }
    }

    private static int getRenderWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() != null && client.getFramebuffer().textureWidth > 0) {
            return client.getFramebuffer().textureWidth;
        }
        return client.getWindow().getFramebufferWidth();
    }

    private static int getRenderHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() != null && client.getFramebuffer().textureHeight > 0) {
            return client.getFramebuffer().textureHeight;
        }
        return client.getWindow().getFramebufferHeight();
    }

    private static Framebuffer ensureFbo(Framebuffer current, int width, int height) {
        if (current == null) {
            current = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
            current.setTexFilter(9728);
        } else if (current.textureWidth != width || current.textureHeight != height) {
            current.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
            current.setTexFilter(9728);
        }
        return current;
    }

    public static void captureWorldDepth() {
        int width = PovHandDepthManager.getRenderWidth();
        int height = PovHandDepthManager.getRenderHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        worldDepthFbo = PovHandDepthManager.ensureFbo(worldDepthFbo, width, height);
        PovHandDepthManager.blitDepthFromCurrent(worldDepthFbo);
        hasCapturedWorldDepth = true;
    }

    public static void captureReplayDepth() {
        int width = PovHandDepthManager.getRenderWidth();
        int height = PovHandDepthManager.getRenderHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        replayDepthFbo = PovHandDepthManager.ensureFbo(replayDepthFbo, width, height);
        PovHandDepthManager.blitDepthFromCurrent(replayDepthFbo);
        hasCapturedReplayDepth = true;
    }

    public static boolean hasSavedSceneDepth() {
        return hasSavedSceneDepth;
    }

    public static void prepareIrisHandDepth(boolean worldInteraction, boolean replayInteraction) {
        int width = PovHandDepthManager.getRenderWidth();
        int height = PovHandDepthManager.getRenderHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.depthFunc((int)515);
        savedSceneDepthFbo = PovHandDepthManager.ensureFbo(savedSceneDepthFbo, width, height);
        PovHandDepthManager.blitDepthFromCurrent(savedSceneDepthFbo);
        hasSavedSceneDepth = true;
        int mode = 0;
        mode = worldInteraction && replayInteraction ? 0 : (worldInteraction && !replayInteraction ? 1 : (!worldInteraction && replayInteraction ? 2 : 3));
        PovHandDepthManager.compressDepthToCurrent(width, height, mode);
    }

    public static void endIrisHandDepth(boolean worldInteraction, boolean replayInteraction) {
        if (!hasSavedSceneDepth || savedSceneDepthFbo == null) {
            return;
        }
        hasSavedSceneDepth = false;
        int width = PovHandDepthManager.getRenderWidth();
        int height = PovHandDepthManager.getRenderHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int mode = 0;
        mode = worldInteraction && replayInteraction ? 0 : (worldInteraction && !replayInteraction ? 1 : (!worldInteraction && replayInteraction ? 2 : 3));
        PovHandDepthManager.mergeDepthWithSaved(width, height, mode);
    }

    public static void prepareDepthForHand(boolean worldInteraction, boolean replayInteraction) {
        if (BBSRendering.isIrisShadersEnabled()) {
            return;
        }
        PovHandDepthManager.beginHandDepth(worldInteraction, replayInteraction);
    }

    public static void beginHandDepth(boolean worldInteraction, boolean replayInteraction) {
        if (BBSRendering.isIrisShadersEnabled()) {
            return;
        }
        if (!hasCapturedWorldDepth) {
            PovHandDepthManager.captureWorldDepth();
            hasCapturedWorldDepth = true;
        }
        int width = PovHandDepthManager.getRenderWidth();
        int height = PovHandDepthManager.getRenderHeight();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.depthFunc((int)515);
        if (!worldInteraction && !replayInteraction) {
            savedSceneDepthFbo = PovHandDepthManager.ensureFbo(savedSceneDepthFbo, width, height);
            PovHandDepthManager.blitDepthFromCurrent(savedSceneDepthFbo);
            hasSavedSceneDepth = true;
            RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
        } else if (worldInteraction && replayInteraction) {
            savedSceneDepthFbo = PovHandDepthManager.ensureFbo(savedSceneDepthFbo, width, height);
            PovHandDepthManager.blitDepthFromCurrent(savedSceneDepthFbo);
            hasSavedSceneDepth = true;
            if (hasCapturedReplayDepth && replayDepthFbo != null) {
                PovHandDepthManager.blitDepthToCurrent(replayDepthFbo);
            } else if (hasCapturedWorldDepth && worldDepthFbo != null) {
                PovHandDepthManager.blitDepthToCurrent(worldDepthFbo);
            }
        } else if (worldInteraction && !replayInteraction) {
            savedSceneDepthFbo = PovHandDepthManager.ensureFbo(savedSceneDepthFbo, width, height);
            PovHandDepthManager.blitDepthFromCurrent(savedSceneDepthFbo);
            hasSavedSceneDepth = true;
            if (hasCapturedWorldDepth && worldDepthFbo != null) {
                PovHandDepthManager.blitDepthToCurrent(worldDepthFbo);
            } else {
                RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
            }
        } else if (!worldInteraction && replayInteraction) {
            savedSceneDepthFbo = PovHandDepthManager.ensureFbo(savedSceneDepthFbo, width, height);
            PovHandDepthManager.blitDepthFromCurrent(savedSceneDepthFbo);
            hasSavedSceneDepth = true;
            if (hasCapturedWorldDepth && worldDepthFbo != null && hasCapturedReplayDepth && replayDepthFbo != null) {
                PovHandDepthManager.updateReplayOnlyDepth(width, height);
                PovHandDepthManager.blitDepthToCurrent(replayOnlyDepthFbo);
            } else if (hasCapturedReplayDepth && replayDepthFbo != null) {
                PovHandDepthManager.blitDepthToCurrent(replayDepthFbo);
            } else {
                RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
            }
        }
    }

    public static void endHandDepth(boolean worldInteraction, boolean replayInteraction) {
        if (BBSRendering.isIrisShadersEnabled()) {
            return;
        }
        if (!hasSavedSceneDepth || savedSceneDepthFbo == null) {
            return;
        }
        hasSavedSceneDepth = false;
        int width = PovHandDepthManager.getRenderWidth();
        int height = PovHandDepthManager.getRenderHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        PovHandDepthManager.mergeDepthWithSaved(width, height, -1);
    }

    private static void blitDepthFromCurrent(Framebuffer dst) {
        int prevRead = GL30.glGetInteger(36010);
        int prevDraw = GL30.glGetInteger(36006);
        int srcFbo = prevDraw != 0 ? prevDraw : (prevRead != 0 ? prevRead : 0);
        if (srcFbo == 0) {
            Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
            if (main != null) {
                srcFbo = main.fbo;
            }
        }
        if (srcFbo == 0 || srcFbo == dst.fbo) {
            return;
        }
        GL30.glBindFramebuffer(36008, srcFbo);
        GL30.glBindFramebuffer(36009, dst.fbo);
        GL30.glBlitFramebuffer(0, 0, dst.textureWidth, dst.textureHeight, 0, 0, dst.textureWidth, dst.textureHeight, 256, 9728);
        GL30.glBindFramebuffer(36008, prevRead);
        GL30.glBindFramebuffer(36009, prevDraw);
    }

    private static void blitDepthToCurrent(Framebuffer src) {
        if (src == null) {
            return;
        }
        int prevRead = GL30.glGetInteger(36010);
        int prevDraw = GL30.glGetInteger(36006);
        int dstFbo = prevDraw != 0 ? prevDraw : (prevRead != 0 ? prevRead : 0);
        if (dstFbo == 0) {
            Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
            if (main != null) {
                dstFbo = main.fbo;
            }
        }
        if (dstFbo == 0 || dstFbo == src.fbo) {
            return;
        }
        GL30.glBindFramebuffer(36008, src.fbo);
        GL30.glBindFramebuffer(36009, dstFbo);
        GL30.glBlitFramebuffer(0, 0, src.textureWidth, src.textureHeight, 0, 0, src.textureWidth, src.textureHeight, 256, 9728);
        GL30.glBindFramebuffer(36008, prevRead);
        GL30.glBindFramebuffer(36009, prevDraw);
    }

    private static void compressDepthToCurrent(int width, int height, int mode) {
        PovHandDepthManager.initCompressShader();
        if (compressProgram == 0) {
            return;
        }
        int prevRead = GL30.glGetInteger(36010);
        int prevDraw = GL30.glGetInteger(36006);
        int targetFbo = prevDraw != 0 ? prevDraw : (prevRead != 0 ? prevRead : 0);
        if (targetFbo == 0) {
            Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
            if (main != null) {
                targetFbo = main.fbo;
            }
        }
        if (targetFbo == 0) {
            return;
        }
        viewportBuf.clear();
        GL11.glGetIntegerv((int)2978, (IntBuffer)viewportBuf);
        GL30.glBindFramebuffer((int)36160, (int)targetFbo);
        GL11.glViewport((int)0, (int)0, (int)width, (int)height);
        GL20.glUseProgram((int)compressProgram);
        GL13.glActiveTexture((int)33984);
        int prevTex0 = GL11.glGetInteger((int)32873);
        GL11.glBindTexture((int)3553, (int)savedSceneDepthFbo.getDepthAttachment());
        GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
        GL20.glUniform1i((int)uCompressSavedDepthLoc, (int)0);
        GL13.glActiveTexture((int)33985);
        int prevTex1 = GL11.glGetInteger((int)32873);
        int worldDepthTex = worldDepthFbo != null ? worldDepthFbo.getDepthAttachment() : savedSceneDepthFbo.getDepthAttachment();
        GL11.glBindTexture((int)3553, (int)worldDepthTex);
        GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
        GL20.glUniform1i((int)uCompressWorldDepthLoc, (int)1);
        GL20.glUniform1i((int)uCompressModeLoc, (int)mode);
        RenderSystem.colorMask((boolean)false, (boolean)false, (boolean)false, (boolean)false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.depthFunc((int)519);
        PovHandDepthManager.renderFullscreenQuad();
        RenderSystem.colorMask((boolean)true, (boolean)true, (boolean)true, (boolean)true);
        RenderSystem.depthFunc((int)515);
        GL20.glUseProgram((int)0);
        GL13.glActiveTexture((int)33985);
        GL11.glBindTexture((int)3553, (int)prevTex1);
        GL13.glActiveTexture((int)33984);
        GL11.glBindTexture((int)3553, (int)prevTex0);
        RenderSystem.activeTexture((int)33984);
        GL11.glViewport((int)viewportBuf.get(0), (int)viewportBuf.get(1), (int)viewportBuf.get(2), (int)viewportBuf.get(3));
        GL30.glBindFramebuffer((int)36008, (int)prevRead);
        GL30.glBindFramebuffer((int)36009, (int)prevDraw);
    }

    private static void mergeDepthWithSaved(int width, int height, int mode) {
        tempHandDepthFbo = PovHandDepthManager.ensureFbo(tempHandDepthFbo, width, height);
        PovHandDepthManager.blitDepthFromCurrent(tempHandDepthFbo);
        PovHandDepthManager.initMergeShader();
        if (mergeProgram == 0) {
            return;
        }
        int prevRead = GL30.glGetInteger(36010);
        int prevDraw = GL30.glGetInteger(36006);
        int targetFbo = prevDraw != 0 ? prevDraw : (prevRead != 0 ? prevRead : 0);
        if (targetFbo == 0) {
            Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
            if (main != null) {
                targetFbo = main.fbo;
            }
        }
        if (targetFbo == 0) {
            return;
        }
        viewportBuf.clear();
        GL11.glGetIntegerv((int)2978, (IntBuffer)viewportBuf);
        GL30.glBindFramebuffer((int)36160, (int)targetFbo);
        GL11.glViewport((int)0, (int)0, (int)width, (int)height);
        GL20.glUseProgram((int)mergeProgram);
        GL13.glActiveTexture((int)33984);
        int prevTex0 = GL11.glGetInteger((int)32873);
        GL11.glBindTexture((int)3553, (int)savedSceneDepthFbo.getDepthAttachment());
        GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
        GL20.glUniform1i((int)uMergeSavedSceneDepthLoc, (int)0);
        GL13.glActiveTexture((int)33985);
        int prevTex1 = GL11.glGetInteger((int)32873);
        GL11.glBindTexture((int)3553, (int)tempHandDepthFbo.getDepthAttachment());
        GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
        GL20.glUniform1i((int)uMergeHandDepthLoc, (int)1);
        GL13.glActiveTexture((int)33986);
        int prevTex2 = GL11.glGetInteger((int)32873);
        int worldDepthTex = worldDepthFbo != null ? worldDepthFbo.getDepthAttachment() : savedSceneDepthFbo.getDepthAttachment();
        GL11.glBindTexture((int)3553, (int)worldDepthTex);
        GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
        GL20.glUniform1i((int)uMergeWorldDepthLoc, (int)2);
        GL20.glUniform1i((int)uMergeModeLoc, (int)mode);
        RenderSystem.colorMask((boolean)false, (boolean)false, (boolean)false, (boolean)false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.depthFunc((int)519);
        PovHandDepthManager.renderFullscreenQuad();
        RenderSystem.colorMask((boolean)true, (boolean)true, (boolean)true, (boolean)true);
        RenderSystem.depthFunc((int)515);
        GL20.glUseProgram((int)0);
        GL13.glActiveTexture((int)33986);
        GL11.glBindTexture((int)3553, (int)prevTex2);
        GL13.glActiveTexture((int)33985);
        GL11.glBindTexture((int)3553, (int)prevTex1);
        GL13.glActiveTexture((int)33984);
        GL11.glBindTexture((int)3553, (int)prevTex0);
        RenderSystem.activeTexture((int)33984);
        GL11.glViewport((int)viewportBuf.get(0), (int)viewportBuf.get(1), (int)viewportBuf.get(2), (int)viewportBuf.get(3));
        GL30.glBindFramebuffer((int)36008, (int)prevRead);
        GL30.glBindFramebuffer((int)36009, (int)prevDraw);
    }

    private static void initCompressShader() {
        if (compressProgram != 0) {
            return;
        }
        String vertSrc = "#version 150\nin vec2 Position;\nout vec2 texCoord;\nvoid main() {\n    texCoord = Position * 0.5 + 0.5;\n    gl_Position = vec4(Position, 0.0, 1.0);\n}\n";
        String fragSrc = "#version 150\nuniform sampler2D SavedSceneDepth;\nuniform sampler2D WorldDepth;\nuniform int Mode;\nin vec2 texCoord;\nvoid main() {\n    float dSaved = texture(SavedSceneDepth, texCoord).r;\n    float dPrepared = 1.0;\n    if (Mode == 0) {\n        dPrepared = (dSaved < 0.99999) ? (dSaved * 0.125 + 0.4375) : 1.0;\n    } else if (Mode == 1) {\n        float dWorld = texture(WorldDepth, texCoord).r;\n        dPrepared = (dWorld < 0.99999) ? (dWorld * 0.125 + 0.4375) : 1.0;\n    } else if (Mode == 2) {\n        float dWorld = texture(WorldDepth, texCoord).r;\n        float dReplay = (dSaved < dWorld - 0.00001) ? dSaved : 1.0;\n        dPrepared = (dReplay < 0.99999) ? (dReplay * 0.125 + 0.4375) : 1.0;\n    } else {\n        dPrepared = 1.0;\n    }\n    gl_FragDepth = dPrepared;\n}\n";
        int vs = GL20.glCreateShader((int)35633);
        GL20.glShaderSource((int)vs, (CharSequence)vertSrc);
        GL20.glCompileShader((int)vs);
        int fs = GL20.glCreateShader((int)35632);
        GL20.glShaderSource((int)fs, (CharSequence)fragSrc);
        GL20.glCompileShader((int)fs);
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader((int)prog, (int)vs);
        GL20.glAttachShader((int)prog, (int)fs);
        GL20.glBindAttribLocation((int)prog, (int)0, (CharSequence)"Position");
        GL20.glLinkProgram((int)prog);
        GL20.glDeleteShader((int)vs);
        GL20.glDeleteShader((int)fs);
        compressProgram = prog;
        uCompressSavedDepthLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"SavedSceneDepth");
        uCompressWorldDepthLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"WorldDepth");
        uCompressModeLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"Mode");
    }

    private static void initMergeShader() {
        if (mergeProgram != 0) {
            return;
        }
        String vertSrc = "#version 150\nin vec2 Position;\nout vec2 texCoord;\nvoid main() {\n    texCoord = Position * 0.5 + 0.5;\n    gl_Position = vec4(Position, 0.0, 1.0);\n}\n";
        String fragSrc = "#version 150\nuniform sampler2D SavedSceneDepth;\nuniform sampler2D HandDepth;\nuniform sampler2D WorldDepth;\nuniform int Mode;\nin vec2 texCoord;\nvoid main() {\n    float dSaved = texture(SavedSceneDepth, texCoord).r;\n    float dHand = texture(HandDepth, texCoord).r;\n\n    if (Mode < 0) {\n        gl_FragDepth = min(dSaved, dHand);\n        return;\n    }\n\n    float dPrepared = 1.0;\n    if (Mode == 0) {\n        dPrepared = (dSaved < 0.99999) ? (dSaved * 0.125 + 0.4375) : 1.0;\n    } else if (Mode == 1) {\n        float dWorld = texture(WorldDepth, texCoord).r;\n        dPrepared = (dWorld < 0.99999) ? (dWorld * 0.125 + 0.4375) : 1.0;\n    } else if (Mode == 2) {\n        float dWorld = texture(WorldDepth, texCoord).r;\n        float dReplay = (dSaved < dWorld - 0.00001) ? dSaved : 1.0;\n        dPrepared = (dReplay < 0.99999) ? (dReplay * 0.125 + 0.4375) : 1.0;\n    } else {\n        dPrepared = 1.0;\n    }\n\n    bool handDrawn = (dHand < dPrepared - 0.00001) && (dHand < 0.5625);\n    gl_FragDepth = handDrawn ? dHand : dSaved;\n}\n";
        int vs = GL20.glCreateShader((int)35633);
        GL20.glShaderSource((int)vs, (CharSequence)vertSrc);
        GL20.glCompileShader((int)vs);
        int fs = GL20.glCreateShader((int)35632);
        GL20.glShaderSource((int)fs, (CharSequence)fragSrc);
        GL20.glCompileShader((int)fs);
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader((int)prog, (int)vs);
        GL20.glAttachShader((int)prog, (int)fs);
        GL20.glBindAttribLocation((int)prog, (int)0, (CharSequence)"Position");
        GL20.glLinkProgram((int)prog);
        GL20.glDeleteShader((int)vs);
        GL20.glDeleteShader((int)fs);
        mergeProgram = prog;
        uMergeSavedSceneDepthLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"SavedSceneDepth");
        uMergeHandDepthLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"HandDepth");
        uMergeWorldDepthLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"WorldDepth");
        uMergeModeLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"Mode");
    }

    private static void updateReplayOnlyDepth(int width, int height) {
        replayOnlyDepthFbo = PovHandDepthManager.ensureFbo(replayOnlyDepthFbo, width, height);
        PovHandDepthManager.initReplayOnlyShader();
        if (replayOnlyProgram == 0) {
            return;
        }
        int prevRead = GL30.glGetInteger((int)36010);
        int prevDraw = GL30.glGetInteger((int)36006);
        viewportBuf.clear();
        GL11.glGetIntegerv((int)2978, (IntBuffer)viewportBuf);
        GL30.glBindFramebuffer((int)36160, (int)PovHandDepthManager.replayOnlyDepthFbo.fbo);
        GL11.glViewport((int)0, (int)0, (int)width, (int)height);
        GL20.glUseProgram((int)replayOnlyProgram);
        GL13.glActiveTexture((int)33984);
        int prevTex0 = GL11.glGetInteger((int)32873);
        GL11.glBindTexture((int)3553, (int)worldDepthFbo.getDepthAttachment());
        GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
        GL20.glUniform1i((int)uReplayWorldDepthLoc, (int)0);
        GL13.glActiveTexture((int)33985);
        int prevTex1 = GL11.glGetInteger((int)32873);
        GL11.glBindTexture((int)3553, (int)replayDepthFbo.getDepthAttachment());
        GL11.glTexParameteri((int)3553, (int)10241, (int)9728);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9728);
        GL20.glUniform1i((int)uReplayFullDepthLoc, (int)1);
        RenderSystem.colorMask((boolean)false, (boolean)false, (boolean)false, (boolean)false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.depthFunc((int)519);
        PovHandDepthManager.renderFullscreenQuad();
        RenderSystem.colorMask((boolean)true, (boolean)true, (boolean)true, (boolean)true);
        RenderSystem.depthFunc((int)515);
        GL20.glUseProgram((int)0);
        GL13.glActiveTexture((int)33985);
        GL11.glBindTexture((int)3553, (int)prevTex1);
        GL13.glActiveTexture((int)33984);
        GL11.glBindTexture((int)3553, (int)prevTex0);
        RenderSystem.activeTexture((int)33984);
        GL11.glViewport((int)viewportBuf.get(0), (int)viewportBuf.get(1), (int)viewportBuf.get(2), (int)viewportBuf.get(3));
        GL30.glBindFramebuffer((int)36008, (int)prevRead);
        GL30.glBindFramebuffer((int)36009, (int)prevDraw);
    }

    private static void initReplayOnlyShader() {
        if (replayOnlyProgram != 0) {
            return;
        }
        String vertSrc = "#version 150\nin vec2 Position;\nout vec2 texCoord;\nvoid main() {\n    texCoord = Position * 0.5 + 0.5;\n    gl_Position = vec4(Position, 0.0, 1.0);\n}\n";
        String fragSrc = "#version 150\nuniform sampler2D WorldDepth;\nuniform sampler2D FullDepth;\nin vec2 texCoord;\nvoid main() {\n    float dWorld = texture(WorldDepth, texCoord).r;\n    float dFull = texture(FullDepth, texCoord).r;\n    gl_FragDepth = (dFull < dWorld - 0.00001) ? dFull : 1.0;\n}\n";
        int vs = GL20.glCreateShader((int)35633);
        GL20.glShaderSource((int)vs, (CharSequence)vertSrc);
        GL20.glCompileShader((int)vs);
        int fs = GL20.glCreateShader((int)35632);
        GL20.glShaderSource((int)fs, (CharSequence)fragSrc);
        GL20.glCompileShader((int)fs);
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader((int)prog, (int)vs);
        GL20.glAttachShader((int)prog, (int)fs);
        GL20.glBindAttribLocation((int)prog, (int)0, (CharSequence)"Position");
        GL20.glLinkProgram((int)prog);
        GL20.glDeleteShader((int)vs);
        GL20.glDeleteShader((int)fs);
        replayOnlyProgram = prog;
        uReplayWorldDepthLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"WorldDepth");
        uReplayFullDepthLoc = GL20.glGetUniformLocation((int)prog, (CharSequence)"FullDepth");
    }

    private static void renderFullscreenQuad() {
        if (quadVao == 0) {
            quadVao = GL30.glGenVertexArrays();
            quadVbo = GL15.glGenBuffers();
            float[] vertices = new float[]{-1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f};
            GL30.glBindVertexArray((int)quadVao);
            GL15.glBindBuffer((int)34962, (int)quadVbo);
            GL15.glBufferData((int)34962, (float[])vertices, (int)35044);
            GL20.glEnableVertexAttribArray((int)0);
            GL20.glVertexAttribPointer((int)0, (int)2, (int)5126, (boolean)false, (int)0, (long)0L);
            GL30.glBindVertexArray((int)0);
        }
        GL30.glBindVertexArray((int)quadVao);
        GL11.glDrawArrays((int)4, (int)0, (int)6);
        GL30.glBindVertexArray((int)0);
    }

    static {
        hasCapturedWorldDepth = false;
        hasCapturedReplayDepth = false;
        hasSavedSceneDepth = false;
        replayOnlyProgram = 0;
        uReplayWorldDepthLoc = 0;
        uReplayFullDepthLoc = 0;
        compressProgram = 0;
        uCompressSavedDepthLoc = 0;
        uCompressWorldDepthLoc = 0;
        uCompressModeLoc = 0;
        mergeProgram = 0;
        uMergeSavedSceneDepthLoc = 0;
        uMergeHandDepthLoc = 0;
        uMergeWorldDepthLoc = 0;
        uMergeModeLoc = 0;
        currentInteractionIrisMode = 0;
        quadVao = 0;
        quadVbo = 0;
        viewportBuf = BufferUtils.createIntBuffer((int)16);
    }
}

