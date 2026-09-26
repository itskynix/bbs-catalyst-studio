package mchorse.bbs_mod.cubic.render.vanilla;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Vanilla-accurate Cape Renderer with smooth physics interpolation (partialTicks lerp)
 * and seamless Wavey Capes dynamic cloth simulation bridge.
 */
public class CapeRenderer
{
    public static final Identifier DEFAULT_CAPE = new Identifier("bbs", "textures/default_cape.png");

    private final ModelPart cape;
    private final Map<Object, CapePhysicsState> states = new WeakHashMap<>();
    private final Map<Object, ActorCapeState> capeStates = new WeakHashMap<>();

    public CapeRenderer()
    {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        root.addChild(
            "cloak",
            ModelPartBuilder.create()
                .uv(0, 0)
                .cuboid(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, Dilation.NONE, 1.0F, 0.5F),
            ModelTransform.NONE
        );

        /* PlayerEntityModel cloak uses textureScaleY=0.5F on 64x64 base data,
         * resulting in effective UV divisor (64, 32) matching standard 64x32 cape textures. */
        this.cape = root.createPart(64, 64).getChild("cloak");
    }

    public static Identifier resolveCapeTexture(Link link)
    {
        if (link == null)
        {
            return DEFAULT_CAPE;
        }

        String path = link.path;
        String source = link.source;

        Identifier id;

        if (source == null || source.isEmpty() || source.equals(Link.ASSETS))
        {
            id = new Identifier(path);
        }
        else
        {
            id = new Identifier(source, path);
        }

        Texture texture = BBSModClient.getTextures().getTexture(link);

        if (texture != null && texture.isValid())
        {
            net.minecraft.client.texture.TextureManager tm = MinecraftClient.getInstance().getTextureManager();

            if (tm.getOrDefault(id, null) == null)
            {
                tm.registerTexture(id, new AbstractTexture()
                {
                    {
                        this.glId = texture.id;
                    }

                    @Override
                    public void load(ResourceManager manager)
                    {}
                });
            }
        }

        return id;
    }

    public void tick(IEntity entity)
    {
        if (entity != null)
        {
            ActorCapeState state = this.capeStates.computeIfAbsent(entity, (e) -> new ActorCapeState());
            state.updateTick(entity);

            if (isWaveyCapesLoaded())
            {
                stepSimulation(entity, state);
            }
        }
    }

    /* Wavey Capes soft-hook reflection bridge */
    private static boolean waveyCapesChecked = false;
    private static boolean waveyCapesLoaded = false;
    private static boolean waveyCapesInitialized = false;
    private static boolean loggedError = false;

    private static Object customCapeRenderer = null;
    private static Object vanillaCapeRenderer = null;
    private static java.lang.reflect.Constructor<?> playerWrapperConstructor = null;
    private static java.lang.reflect.Method waveyRenderMethod = null;

    private static Class<?> capeHolderClass = null;
    private static java.lang.reflect.Method getSimulationMethod = null;
    private static java.lang.reflect.Method setSimulationMethod = null;
    private static java.lang.reflect.Method createSimulationMethod = null;
    private static java.lang.reflect.Method updateSimulationMethod = null;
    private static java.lang.reflect.Method simulateMethod = null;

    private static Class<?> mcPlayerInterface = null;
    private static Class<?> basicSimClass = null;
    private static java.lang.reflect.Method initSimulationMethod = null;

    private static final Map<Object, Object> actorSimulations = new WeakHashMap<>();

    private static boolean isWaveyCapesLoaded()
    {
        if (!waveyCapesChecked)
        {
            waveyCapesChecked = true;
            try
            {
                waveyCapesLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("waveycapes");
            }
            catch (Throwable t)
            {
                waveyCapesLoaded = false;
            }
        }
        return waveyCapesLoaded;
    }

    private static double sanitize(double val, double fallback)
    {
        return Double.isFinite(val) ? val : fallback;
    }

    private static float sanitize(float val, float fallback)
    {
        return Float.isFinite(val) ? val : fallback;
    }

    private static void initWaveyCapes()
    {
        if (waveyCapesInitialized || !isWaveyCapesLoaded())
        {
            return;
        }
        waveyCapesInitialized = true;
        try
        {
            Class<?> customCapeRendererClass = Class.forName("dev.tr7zw.waveycapes.render.CustomCapeRenderer");
            Class<?> vanillaCapeRendererClass = Class.forName("dev.tr7zw.waveycapes.render.VanillaCapeRenderer");
            Class<?> playerWrapperClass = Class.forName("dev.tr7zw.transition.mc.entitywrapper.PlayerWrapper");
            capeHolderClass = Class.forName("dev.tr7zw.waveycapes.versionless.CapeHolder");
            mcPlayerInterface = Class.forName("dev.tr7zw.waveycapes.versionless.nms.MinecraftPlayer");
            basicSimClass = Class.forName("dev.tr7zw.waveycapes.versionless.sim.BasicSimulation");

            customCapeRenderer = customCapeRendererClass.getConstructor().newInstance();
            vanillaCapeRenderer = vanillaCapeRendererClass.getConstructor().newInstance();

            for (java.lang.reflect.Constructor<?> c : playerWrapperClass.getConstructors())
            {
                if (c.getParameterCount() == 1)
                {
                    playerWrapperConstructor = c;
                    break;
                }
            }

            for (java.lang.reflect.Method m : customCapeRendererClass.getMethods())
            {
                if (m.getName().equals("render") && m.getParameterCount() == 6)
                {
                    m.setAccessible(true);
                    waveyRenderMethod = m;
                    break;
                }
            }

            getSimulationMethod = capeHolderClass.getMethod("getSimulation");
            createSimulationMethod = capeHolderClass.getMethod("createSimulation");

            for (java.lang.reflect.Method m : capeHolderClass.getMethods())
            {
                if (m.getName().equals("setSimulation") && m.getParameterCount() == 1)
                {
                    setSimulationMethod = m;
                }
                else if (m.getName().equals("simulate") && m.getParameterCount() == 1)
                {
                    simulateMethod = m;
                }
                else if (m.getName().equals("updateSimulation") && m.getParameterCount() == 1)
                {
                    updateSimulationMethod = m;
                }
            }

            try
            {
                initSimulationMethod = basicSimClass.getMethod("init", int.class);
            }
            catch (Throwable ignored)
            {}
        }
        catch (Throwable t)
        {
            if (!loggedError)
            {
                loggedError = true;
                System.err.println("[BBS] Wavey Capes initialization failed:");
                t.printStackTrace();
            }
        }
    }

    private static Object getOrCreateSimulation(IEntity entity, Object player)
    {
        return actorSimulations.computeIfAbsent(entity, (e) ->
        {
            try
            {
                Object s = createSimulationMethod != null ? createSimulationMethod.invoke(player) : null;
                if (s != null && initSimulationMethod != null)
                {
                    try
                    {
                        initSimulationMethod.invoke(s, 16);
                    }
                    catch (Throwable ignored)
                    {}
                }
                return s;
            }
            catch (Throwable t)
            {
                return null;
            }
        });
    }

    private static Object createPlayerProxy(ActorCapeState state)
    {
        return java.lang.reflect.Proxy.newProxyInstance(
            CapeRenderer.class.getClassLoader(),
            new Class<?>[] { mcPlayerInterface },
            (proxy, method, mArgs) ->
            {
                String name = method.getName();
                double x = state.x;
                double y = state.y;
                double z = state.z;
                double prevX = state.prevX;
                double prevY = state.prevY;
                double prevZ = state.prevZ;

                float bodyYaw = state.bodyYaw;
                float prevBodyYaw = state.prevBodyYaw;
                float pitch = state.pitch;
                float yaw = state.yaw;

                double rad = Math.toRadians(bodyYaw);
                double forwardX = -Math.sin(rad);
                double forwardZ = Math.cos(rad);

                double deltaX = x - prevX;
                double deltaZ = z - prevZ;
                double physSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                double animSpeed = state.limbSpeed * 0.4;
                double extraLag = Math.max(0.0, animSpeed - physSpeed) * 1.5;

                double cloakX = sanitize(state.capeX - forwardX * extraLag, x);
                double cloakZ = sanitize(state.capeZ - forwardZ * extraLag, z);

                double effectivePrevX = prevX;
                double effectivePrevZ = prevZ;
                if (physSpeed < animSpeed)
                {
                    effectivePrevX = x - forwardX * animSpeed;
                    effectivePrevZ = z - forwardZ * animSpeed;
                }

                switch (name)
                {
                    case "getX": return x;
                    case "getY": return y;
                    case "getZ": return z;
                    case "getXo": return effectivePrevX;
                    case "getYo": return prevY;
                    case "getZo": return effectivePrevZ;
                    case "getYBodyRot": return bodyYaw;
                    case "getYBodyRotO": return prevBodyYaw;
                    case "getXRot": return pitch;
                    case "getYRot": return yaw;
                    case "isCrouching": return state.sneaking;
                    case "isVisuallySwimming": return false;
                    case "isUnderWater": return false;
                    case "getXCloak": return cloakX;
                    case "getZCloak": return cloakZ;
                    case "equals": return proxy == (mArgs != null && mArgs.length > 0 ? mArgs[0] : null);
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString": return "BBSActorMinecraftPlayerProxy";
                    default:
                        Class<?> ret = method.getReturnType();
                        if (ret == boolean.class) return false;
                        if (ret == int.class) return 0;
                        if (ret == float.class) return 0F;
                        if (ret == double.class) return 0D;
                        return null;
                }
            }
        );
    }

    private static void stepSimulation(IEntity entity, ActorCapeState state)
    {
        if (entity == null || !isWaveyCapesLoaded())
        {
            return;
        }

        initWaveyCapes();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || capeHolderClass == null || !capeHolderClass.isInstance(mc.player))
        {
            return;
        }

        try
        {
            Object actorSim = getOrCreateSimulation(entity, mc.player);
            if (actorSim != null && getSimulationMethod != null && setSimulationMethod != null && simulateMethod != null)
            {
                Object prevSim = getSimulationMethod.invoke(mc.player);
                try
                {
                    setSimulationMethod.invoke(mc.player, actorSim);
                    if (updateSimulationMethod != null)
                    {
                        updateSimulationMethod.invoke(mc.player, 16);
                        actorSim = getSimulationMethod.invoke(mc.player);
                        actorSimulations.put(entity, actorSim);
                    }

                    if (state.proxy == null)
                    {
                        state.proxy = createPlayerProxy(state);
                    }
                    simulateMethod.invoke(mc.player, state.proxy);
                }
                finally
                {
                    setSimulationMethod.invoke(mc.player, prevSim);
                }
            }
        }
        catch (Throwable t)
        {
            if (!loggedError)
            {
                loggedError = true;
                System.err.println("[BBS] Wavey Capes simulation step failed:");
                t.printStackTrace();
            }
        }
    }

    private static boolean renderWaveyCape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, IEntity entity, float transition, Identifier capeTexture, int light)
    {
        if (!isWaveyCapesLoaded())
        {
            return false;
        }

        initWaveyCapes();

        if (customCapeRenderer == null || waveyRenderMethod == null || playerWrapperConstructor == null)
        {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null)
        {
            return false;
        }

        try
        {
            Object playerWrapper = playerWrapperConstructor.newInstance(mc.player);
            Object actorSim = getOrCreateSimulation(entity, mc.player);

            Object prevSim = null;
            if (actorSim != null && getSimulationMethod != null && setSimulationMethod != null)
            {
                prevSim = getSimulationMethod.invoke(mc.player);
                setSimulationMethod.invoke(mc.player, actorSim);
            }

            try
            {
                matrices.push();
                VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(capeTexture));
                waveyRenderMethod.invoke(customCapeRenderer, playerWrapper, vanillaCapeRenderer, buffer, matrices, light, transition);
                matrices.pop();
                return true;
            }
            finally
            {
                if (actorSim != null && prevSim != null && setSimulationMethod != null)
                {
                    setSimulationMethod.invoke(mc.player, prevSim);
                }
            }
        }
        catch (Throwable t)
        {
            if (!loggedError)
            {
                loggedError = true;
                System.err.println("[BBS] Wavey Capes render invocation failed:");
                t.printStackTrace();
            }
            return false;
        }
    }

    public void renderCape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, IEntity entity, float transition, Identifier capeTexture, int light)
    {
        if (entity == null || capeTexture == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.getPerspective().isFirstPerson())
        {
            if (entity instanceof MCEntity mcEntity && mcEntity.getMcEntity() == mc.player)
            {
                return;
            }
        }

        if (isWaveyCapesLoaded())
        {
            if (renderWaveyCape(matrices, vertexConsumers, entity, transition, capeTexture, light))
            {
                return;
            }
        }

        CapePhysicsState state = this.states.computeIfAbsent(entity, (e) -> new CapePhysicsState());
        state.updateFrame(entity, transition);

        float totalPitch = state.currentPitch;
        float sideSwing = state.currentYaw;
        float sneak = state.currentSneak;

        matrices.push();
        /* Torso upper back offset (Vanilla 0.125F / 2 pixels behind torso, positive Z) */
        float sneakY = sneak * 0.08F;
        float sneakZ = sneak * -0.06F;
        matrices.translate(0.0F, sneakY, 0.125F + sneakZ);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(totalPitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sideSwing / 2.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - sideSwing / 2.0F));

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(capeTexture));
        this.cape.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);

        matrices.pop();
    }

    public static class ActorCapeState
    {
        public double capeX;
        public double capeY;
        public double capeZ;
        public double prevCapeX;
        public double prevCapeY;
        public double prevCapeZ;

        public double x;
        public double y;
        public double z;
        public double prevX;
        public double prevY;
        public double prevZ;

        public float bodyYaw;
        public float prevBodyYaw;
        public float pitch;
        public float yaw;
        public float limbSpeed;
        public boolean sneaking;
        public boolean initialized = false;

        public Object proxy = null;

        public void updateTick(IEntity entity)
        {
            double curX = sanitize(entity.getX(), 0.0);
            double curY = sanitize(entity.getY(), 0.0);
            double curZ = sanitize(entity.getZ(), 0.0);
            double curPrevX = sanitize(entity.getPrevX(), curX);
            double curPrevY = sanitize(entity.getPrevY(), curY);
            double curPrevZ = sanitize(entity.getPrevZ(), curZ);

            if (!this.initialized || Math.abs(curX - this.x) > 16.0 || Math.abs(curZ - this.z) > 16.0)
            {
                this.x = curX;
                this.y = curY;
                this.z = curZ;
                this.prevX = curPrevX;
                this.prevY = curPrevY;
                this.prevZ = curPrevZ;
                this.capeX = this.prevCapeX = curX;
                this.capeY = this.prevCapeY = curY;
                this.capeZ = this.prevCapeZ = curZ;
                this.initialized = true;
            }
            else
            {
                this.prevX = curPrevX;
                this.prevY = curPrevY;
                this.prevZ = curPrevZ;
                this.x = curX;
                this.y = curY;
                this.z = curZ;

                this.prevCapeX = this.capeX;
                this.prevCapeY = this.capeY;
                this.prevCapeZ = this.capeZ;

                double dx = curX - this.capeX;
                double dy = curY - this.capeY;
                double dz = curZ - this.capeZ;

                this.capeX += dx * 0.25;
                this.capeY += dy * 0.25;
                this.capeZ += dz * 0.25;
            }

            this.bodyYaw = sanitize(entity.getBodyYaw(), 0.0F);
            this.prevBodyYaw = sanitize(entity.getPrevBodyYaw(), this.bodyYaw);
            this.pitch = sanitize(entity.getPitch(), 0.0F);
            this.yaw = sanitize(entity.getYaw(), this.bodyYaw);
            this.limbSpeed = sanitize(entity.getLimbSpeed(1.0F), 0.0F);
            this.sneaking = entity.isSneaking();
        }
    }

    public static class CapePhysicsState
    {
        public float currentPitch = 6.0F;
        public float currentYaw = 0.0F;
        public float currentSneak = 0.0F;

        public double lastX;
        public double lastY;
        public double lastZ;
        public long lastRenderTime = 0;
        public boolean initialized = false;

        public void updateFrame(IEntity entity, float transition)
        {
            long now = System.currentTimeMillis();
            float dt = this.lastRenderTime == 0 ? 0.016F : (now - this.lastRenderTime) / 1000.0F;
            this.lastRenderTime = now;
            dt = MathHelper.clamp(dt, 0.001F, 0.1F);

            /* Current continuous world position of entity at partialTicks */
            double curX = MathHelper.lerp((double) transition, entity.getPrevX(), entity.getX());
            double curY = MathHelper.lerp((double) transition, entity.getPrevY(), entity.getY());
            double curZ = MathHelper.lerp((double) transition, entity.getPrevZ(), entity.getZ());
            float bodyYaw = MathHelper.lerpAngleDegrees(transition, entity.getPrevBodyYaw(), entity.getBodyYaw());

            if (!this.initialized)
            {
                this.lastX = curX;
                this.lastY = curY;
                this.lastZ = curZ;
                this.currentPitch = 6.0F;
                this.currentYaw = 0.0F;
                this.currentSneak = entity.isSneaking() ? 1.0F : 0.0F;
                this.initialized = true;

                return;
            }

            /* Position delta per frame (blocks per second) */
            double dx = (curX - this.lastX) / Math.max(dt, 0.001F);
            double dy = (curY - this.lastY) / Math.max(dt, 0.001F);
            double dz = (curZ - this.lastZ) / Math.max(dt, 0.001F);

            this.lastX = curX;
            this.lastY = curY;
            this.lastZ = curZ;

            /* Check for teleport or scrubbing jump */
            double speedSq = dx * dx + dy * dy + dz * dz;

            if (speedSq > 900.0)
            {
                dx = 0;
                dy = 0;
                dz = 0;
            }

            /* Convert velocity vector into entity local coordinates (forward / side) */
            double sinYaw = (double) MathHelper.sin(bodyYaw * 0.017453292F);
            double cosYaw = (double) (-MathHelper.cos(bodyYaw * 0.017453292F));

            double forwardSpeed = -(dx * sinYaw + dz * cosYaw);
            double sideSpeed = dx * cosYaw - dz * sinYaw;

            /* Procedural limb movement fallback / boost */
            float strideDistance = entity.getLimbSpeed(transition);
            float horizontalSpeed = entity.getLimbPos(transition);
            float strideBounce = MathHelper.sin(horizontalSpeed * 6.0F) * 32.0F * strideDistance;

            float speedFromPos = (float) Math.max(0.0, forwardSpeed) * 15.0F;
            float speedFromLimb = strideDistance * 75.0F;
            float forwardSwing = MathHelper.clamp(Math.max(speedFromPos, speedFromLimb), 0.0F, 120.0F);

            float sideSwing = (float) MathHelper.clamp(sideSpeed * 10.0, -20.0, 20.0);

            /* Sneaking smoothly lerped */
            float targetSneak = entity.isSneaking() ? 1.0F : 0.0F;
            this.currentSneak = MathHelper.lerp(dt * 10.0F, this.currentSneak, targetSneak);

            float targetPitch = 6.0F + forwardSwing / 2.0F + strideBounce * 0.5F + this.currentSneak * 25.0F;
            float targetYaw = sideSwing;

            /* Spring damping / smooth lerp */
            this.currentPitch = MathHelper.lerp(MathHelper.clamp(dt * 10.0F, 0.0F, 1.0F), this.currentPitch, targetPitch);
            this.currentYaw = MathHelper.lerp(MathHelper.clamp(dt * 10.0F, 0.0F, 1.0F), this.currentYaw, targetYaw);
        }
    }
}
