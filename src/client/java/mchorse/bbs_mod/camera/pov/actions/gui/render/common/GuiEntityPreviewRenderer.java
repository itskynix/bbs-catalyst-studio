package mchorse.bbs_mod.camera.pov.actions.gui.render.common;

import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.LiveGuiPreviewRenderer;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.MorphPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.render.PovViewportMetrics;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class GuiEntityPreviewRenderer {
   private GuiEntityPreviewRenderer() {
   }

   public static void draw(DrawContext context, float centerX, float centerY, int size, float yOffset, float lookX, float lookY, LivingEntity entity) {
      float entityHeight = entity != null ? entity.getHeight() : 1.8F;
      if (entityHeight <= 0.1F) {
         entityHeight = 1.8F;
      }

      float headY = centerY - entityHeight * (float)size * 0.7F;
      float f = (float)Math.atan((double)((centerX - lookX) / 40.0F));
      float g = (float)Math.atan((double)((headY - lookY) / 40.0F));
      Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
      Quaternionf quaternionf2 = new Quaternionf().rotateX(g * 20.0F * (float) (Math.PI / 180.0));
      quaternionf.mul(quaternionf2);
      float bodyYaw = entity.bodyYaw;
      float yaw = entity.getYaw();
      float pitch = entity.getPitch();
      float prevHeadYaw = entity.prevHeadYaw;
      float headYaw = entity.headYaw;
      entity.bodyYaw = 180.0F + f * 20.0F;
      entity.setYaw(180.0F + f * 40.0F);
      entity.setPitch(-g * 20.0F);
      entity.headYaw = entity.getYaw();
      entity.prevHeadYaw = entity.getYaw();
      Vector3f vector3f = new Vector3f(0.0F, entity.getHeight() / 2.0F + yOffset, 0.0F);

      try {
         InventoryScreen.drawEntity(context, (float)((int)centerX), (float)((int)centerY), size, vector3f, quaternionf, quaternionf2, entity);
         context.draw();
      } finally {
         entity.bodyYaw = bodyYaw;
         entity.setYaw(yaw);
         entity.setPitch(pitch);
         entity.prevHeadYaw = prevHeadYaw;
         entity.headYaw = headYaw;
      }
   }

   public static void drawInventoryPreview(GuiRenderContext ctx, boolean creativeSurvival) {
      if (ctx != null && !ctx.skipEntityPreview) {
         Batcher2D batcher = ctx.batcher;
         RecordedHandData handData = ctx.handData;
         float globalTick = ctx.globalTick;
         int screenWidth = ctx.screenWidth;
         int screenHeight = ctx.screenHeight;
         float scaleX = ctx.scaleX;
         float scaleY = ctx.scaleY;
         float originX = ctx.originX;
         float originY = ctx.originY;
         float curScreenX = ctx.curScreenX;
         float curScreenY = ctx.curScreenY;
         LivingEntity targetEntity = null;
         UIFilmPanel filmPanel = PovViewportMetrics.resolveFilmPanel();
         ModelForm replayModelForm = null;
         if (filmPanel != null && filmPanel.getController() != null && filmPanel.getData() != null) {
            Replay sourceReplay;
            if (filmPanel.getController().getPovMode() == 6) {
               sourceReplay = filmPanel.replayEditor.getReplay();
            } else {
               sourceReplay = PovCameraClips.resolveReplay((Film)filmPanel.getData(), (float)filmPanel.getCursor());
            }

            int selector = PovCameraClips.indexOfReplay((Film)filmPanel.getData(), sourceReplay);
            IEntity target = selector < 0 ? null : (IEntity)filmPanel.getController().getEntities().get(selector);
            if (target instanceof MCEntity mc && mc.getMcEntity() instanceof LivingEntity living) {
               targetEntity = living;
            }

            Form sourceForm = target == null ? (sourceReplay == null ? null : (Form)sourceReplay.form.get()) : target.getForm();
            if ((sourceForm == null ? null : FormUtils.getRoot(sourceForm)) instanceof ModelForm mf) {
               replayModelForm = mf;
            }
         }

         PovPlaybackContext.Frame frame = PovPlaybackContext.getActive();
         if (frame != null && frame.replay() != null) {
            int selectorx = (Integer)frame.clip().selector.get();
            IEntity playbackTarget = selectorx < 0 ? null : (IEntity)frame.controller().getEntities().get(selectorx);
            if (playbackTarget instanceof MCEntity mc && mc.getMcEntity() instanceof LivingEntity living) {
               targetEntity = living;
            }

            if (replayModelForm == null) {
               Form form = playbackTarget == null ? (Form)frame.replay().form.get() : playbackTarget.getForm();
               if ((form == null ? null : FormUtils.getRoot(form)) instanceof ModelForm mf) {
                  replayModelForm = mf;
               }
            }
         }

         if (targetEntity == null) {
            targetEntity = MinecraftClient.getInstance().player;
         }

         float entityHeight = targetEntity != null ? targetEntity.getHeight() : 1.8F;
         if (entityHeight <= 0.1F) {
            entityHeight = 1.8F;
         }

         int charSize = creativeSurvival ? (int)Math.min(20.0F, 36.0F / entityHeight) : (int)Math.min(30.0F, 54.0F / entityHeight);
         float centerX = creativeSurvival ? 88.0F : 51.0F;
         float centerY = creativeSurvival ? 26.0F : 43.0F;
         float lookX = ctx.cursorVisible ? ctx.cursorGuiX : centerX;
         float lookY = ctx.cursorVisible ? ctx.cursorGuiY : centerY;
         if (LiveGuiPreviewRenderer.isRenderingLive()) {
            if (targetEntity != null) {
               boolean queueWasActive = FormTranslucentQueue.suspend();

               try {
                  batcher.flush();
                  RenderSystem.enableDepthTest();
                  RenderSystem.depthFunc(515);
                  RenderSystem.depthMask(true);
                  RenderSystem.colorMask(true, true, true, true);
                  RenderSystem.enableCull();
                  RenderSystem.enableBlend();
                  RenderSystem.defaultBlendFunc();
                  RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
                  draw(batcher.getContext(), centerX, centerY, charSize, 0.0625F, lookX, lookY, targetEntity);
                  batcher.flush();
               } catch (Exception var41) {
               } finally {
                  FormTranslucentQueue.restore(queueWasActive);
                  RenderSystem.disableDepthTest();
                  RenderSystem.depthMask(false);
               }
            }
         } else if (targetEntity != null) {
            boolean queueWasActive = FormTranslucentQueue.suspend();
            Morph morph = Morph.getMorph(targetEntity);
            if (morph == null) {
               morph = new Morph(targetEntity);
            }

            Form originalForm = morph == null ? null : morph.getForm();
            String targetModel = handData != null && !handData.model.isEmpty() ? (String)handData.model.interpolate(globalTick, null) : null;
            Link targetTexture = handData != null && !handData.texture.isEmpty() ? (Link)handData.texture.interpolate(globalTick, null) : null;
            if (targetModel == null && replayModelForm != null) {
               targetModel = (String)replayModelForm.model.get();
               if (targetTexture == null) {
                  targetTexture = (Link)replayModelForm.texture.get();
               }
            }

            if (targetModel == null && originalForm instanceof ModelForm mf) {
               targetModel = (String)mf.model.get();
               if (targetTexture == null) {
                  targetTexture = (Link)mf.texture.get();
               }
            }

            if (targetModel == null || targetModel.isBlank()) {
               targetModel = "player/steve";
            }

            if (targetTexture == null && replayModelForm != null && targetModel.equals(replayModelForm.model.get())) {
               targetTexture = (Link)replayModelForm.texture.get();
            }

            ModelForm previewForm = new ModelForm();
            if (replayModelForm != null) {
               previewForm.copy(replayModelForm);
            } else if (originalForm instanceof ModelForm mfx) {
               previewForm.copy(mfx);
            }

            previewForm.visible.set(true);
            if (targetModel != null && !targetModel.isBlank()) {
               previewForm.model.set(targetModel);
            }

            if (targetTexture != null) {
               previewForm.texture.set(targetTexture);
            }

            if (morph != null) {
               previewForm.update(morph.entity);
               if (FormUtilsClient.getRenderer(previewForm) instanceof ModelFormRenderer modelRenderer) {
                  modelRenderer.ensureAnimator(0.0F);
               }

               ((MorphPovAccess)morph).bbsPov$setFormRaw(previewForm);
            }

            try {
               batcher.flush();
               RenderSystem.enableDepthTest();
               RenderSystem.depthFunc(515);
               RenderSystem.depthMask(true);
               RenderSystem.colorMask(true, true, true, true);
               RenderSystem.enableCull();
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
               draw(batcher.getContext(), centerX, centerY, charSize, 0.0625F, lookX, lookY, targetEntity);
               batcher.flush();
            } catch (Exception var40) {
            } finally {
               FormTranslucentQueue.restore(queueWasActive);
               RenderSystem.disableDepthTest();
               RenderSystem.depthMask(false);
               if (morph != null) {
                  ((MorphPovAccess)morph).bbsPov$setFormRaw(originalForm);
               }
            }
         }
      }
   }
}
