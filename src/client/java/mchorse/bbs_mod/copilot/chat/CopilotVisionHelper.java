package mchorse.bbs_mod.copilot.chat;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CopilotVisionHelper {
    private static final Map<String, Texture> TEXTURE_CACHE = new HashMap<>();

    public static class CapturedImage {
        public final String base64;
        public final int width;
        public final int height;

        public CapturedImage(String base64, int width, int height) {
            this.base64 = base64;
            this.width = width;
            this.height = height;
        }
    }

    public static CompletableFuture<CapturedImage> captureScreenshotAsync() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return CompletableFuture.completedFuture(null);
        }
        Framebuffer fb = client.getFramebuffer();
        if (fb == null) {
            return CompletableFuture.completedFuture(null);
        }

        NativeImage rawScreenshot;
        try {
            rawScreenshot = ScreenshotRecorder.takeScreenshot(fb);
        } catch (Throwable t) {
            System.err.println("[BBS Copilot] Screenshot capture failed: " + t.getMessage());
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (NativeImage nativeImage = rawScreenshot) {
                int origW = nativeImage.getWidth();
                int origH = nativeImage.getHeight();

                int maxDim = 640;
                int targetW = origW;
                int targetH = origH;

                if (origW > maxDim || origH > maxDim) {
                    if (origW >= origH) {
                        targetW = maxDim;
                        targetH = Math.max(1, (origH * maxDim) / origW);
                    } else {
                        targetH = maxDim;
                        targetW = Math.max(1, (origW * maxDim) / origH);
                    }
                }

                byte[] bytes;
                if (targetW == origW && targetH == origH) {
                    bytes = nativeImage.getBytes();
                } else {
                    try (NativeImage resized = new NativeImage(targetW, targetH, false)) {
                        nativeImage.resizeSubRectTo(0, 0, origW, origH, resized);
                        bytes = resized.getBytes();
                    }
                }

                String base64 = Base64.getEncoder().encodeToString(bytes);
                return new CapturedImage(base64, targetW, targetH);
            } catch (Throwable t) {
                System.err.println("[BBS Copilot] Screenshot downscale failed: " + t.getMessage());
                return null;
            }
        });
    }

    public static CapturedImage captureScreenshot() {
        try {
            return captureScreenshotAsync().get();
        } catch (Throwable t) {
            return null;
        }
    }

    public static CompletableFuture<CapturedImage> loadImageAsync(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (InputStream stream = new FileInputStream(file);
                 NativeImage nativeImage = NativeImage.read(stream)) {
                if (nativeImage == null) {
                    return null;
                }
                int origW = nativeImage.getWidth();
                int origH = nativeImage.getHeight();

                int maxDim = 640;
                int targetW = origW;
                int targetH = origH;

                if (origW > maxDim || origH > maxDim) {
                    if (origW >= origH) {
                        targetW = maxDim;
                        targetH = Math.max(1, (origH * maxDim) / origW);
                    } else {
                        targetH = maxDim;
                        targetW = Math.max(1, (origW * maxDim) / origH);
                    }
                }

                byte[] bytes;
                if (targetW == origW && targetH == origH) {
                    bytes = nativeImage.getBytes();
                } else {
                    try (NativeImage resized = new NativeImage(targetW, targetH, false)) {
                        nativeImage.resizeSubRectTo(0, 0, origW, origH, resized);
                        bytes = resized.getBytes();
                    }
                }

                String base64 = Base64.getEncoder().encodeToString(bytes);
                return new CapturedImage(base64, targetW, targetH);
            } catch (Throwable t) {
                System.err.println("[Blockbuster AI] Image load & downscale failed: " + t.getMessage());
                return null;
            }
        });
    }

    public static CapturedImage loadImage(File file) {
        try {
            return loadImageAsync(file).get();
        } catch (Throwable t) {
            return null;
        }
    }

    public static Texture getTexture(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        Texture cached = TEXTURE_CACHE.get(base64);
        if (cached != null) {
            return cached;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            Link link = new Link("copilot_img", "thumb_" + Math.abs(base64.hashCode()) + ".png");
            Texture tex = BBSModClient.getTextures().createTexture(link);
            tex.bind();
            tex.uploadTexture(Pixels.fromPNGStream(new ByteArrayInputStream(bytes)));
            if (TEXTURE_CACHE.size() > 50) {
                TEXTURE_CACHE.clear();
            }
            TEXTURE_CACHE.put(base64, tex);
            return tex;
        } catch (Throwable t) {
            System.err.println("[BBS Copilot] Texture upload failed: " + t.getMessage());
            return null;
        }
    }
}
