package dev.starcrown.treasury;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starcrown.StarcrownDoll;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID, value=Dist.CLIENT)
public final class TreasuryClient {
    private static final long HELD = Long.MAX_VALUE;
    record Visual(TreasuryNetwork.Cast cast, int chainGateCount, long releaseAt) {
        List<TreasuryLayout.Gate> gates() { return cast.gates(); }

        boolean expired(long now) {
            return cast.hasTimedOut(now) || (releaseAt != HELD && now - releaseAt >= TreasuryNetwork.RELEASE_TICKS);
        }

        float chainStage(long now, float partial) {
            if (releaseAt != HELD) return 65f + Math.max(0f, now - releaseAt + partial);
            long elapsed = now - cast.start();
            return cast.isBoundChain() ? Math.min(60L, elapsed) + partial : elapsed + partial;
        }
    }
    private static final Map<UUID, Visual> ACTIVE = new LinkedHashMap<>();
    private static ClientLevel world;
    private TreasuryClient() {}

    static void add(TreasuryNetwork.Cast cast) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(cast.dimension())) return;
        useLevel(level);
        if (cast.hasTimedOut(level.getGameTime())) return;
        ACTIVE.compute(cast.id(), (id, previous) -> {
            if (previous != null) {
                return new Visual(cast, previous.chainGateCount(), previous.releaseAt());
            }
            return new Visual(cast, cast.chains() ? TreasuryChainLayout.anchors(cast.seed(), cast.width(), cast.height()).size() : 0, HELD);
        });
    }
    static void remove(UUID id) { ACTIVE.remove(id); }
    static void release(UUID id, long releaseStart) {
        ACTIVE.computeIfPresent(id, (key, v) ->
                new Visual(v.cast(), v.chainGateCount(), Math.min(v.releaseAt(), releaseStart)));
    }

    private static void useLevel(ClientLevel level) {
        if (world != level) {
            ACTIVE.clear();
            world = level;
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        useLevel(Minecraft.getInstance().level);
        if (world != null) ACTIVE.values().removeIf(v -> v.expired(world.getGameTime()));
    }

    @SubscribeEvent public static void leave(ClientPlayerNetworkEvent.LoggingOut event) { useLevel(null); }

    public static int activeGateCount() {
        if (world == null) return 0;
        int count = 0;
        for (var visual : ACTIVE.values()) {
            var cast = visual.cast();
            long age = world.getGameTime() - cast.start();
            if (cast.chains() || age < 0) continue;
            for (int i = 0; i < visual.gates().size(); i++) {
                if (age < cast.launchAge(i) + TreasuryVolley.CLOSE_END_AFTER_LAUNCH) count++;
            }
        }
        return count;
    }
    public static int activeChainGateCount() { return ACTIVE.values().stream().mapToInt(Visual::chainGateCount).sum(); }
    public static int activeChainCount() { return (int)ACTIVE.values().stream().filter(v -> v.cast().chains()).count(); }

    record BindingSkin(float spread, float opacity, float pulse, float contactTurn, float contactHeight) {
        static final BindingSkin NONE = new BindingSkin(0, 0, 0, 0, 0);
        static final BindingSkin HELD = new BindingSkin(1, 1, 0, 0, 0);

        float strength() { return Math.min(spread * 1.35F, opacity); }
    }

    public static float bindingStrength(LivingEntity entity, float partialTick) {
        return bindingSkin(entity, partialTick).strength();
    }

    static BindingSkin bindingSkin(LivingEntity entity, float partialTick) {
        if (world == null || entity.level() != world || ACTIVE.isEmpty() || !entity.isAlive()) return BindingSkin.NONE;
        BindingSkin skin = BindingSkin.NONE;
        long now = world.getGameTime();
        for (var visual : ACTIVE.values()) {
            var cast = visual.cast();
            if (!cast.chains() || cast.targetId() != entity.getId() || !cast.target().filter(entity.getUUID()::equals).isPresent()) continue;
            boolean releasing = visual.releaseAt() <= now;
            float age = Math.min(35L, Math.min(now, visual.releaseAt()) - cast.start()) + (releasing ? 0 : partialTick);
            if (age <= 24) continue;
            if (age >= 33 && !releasing) return BindingSkin.HELD;
            float spread = Mth.clamp((age - 24) / 8F, 0, 1);
            float opacity = releasing ? 1F - ease((now - visual.releaseAt() + partialTick) / 12F) : 1F;
            float pulse = ease((age - 26) / 2F) * (1F - ease((age - 28) / 5F));
            if (Math.min(spread * 1.35F, opacity) <= skin.strength()) continue;
            float turn = 0, height = 0;
            if (spread < 1) {
                var anchors = TreasuryChainLayout.anchors(cast.seed(), entity.getBbWidth(), entity.getBbHeight());
                Vec3 first = anchors.get(0);
                // The first spear finishes its wrap .18 + 2.05 radians past the first gate.
                turn = (float) ((Math.atan2(first.z, first.x) + 2.23) / (2 * Math.PI));
                height = (float) (.5 + (.5 / anchors.size() - .5) * .53 - .22);
            }
            skin = new BindingSkin(spread, opacity, pulse, turn, height);
        }
        return skin;
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (ACTIVE.isEmpty() || world == null || world != Minecraft.getInstance().level) return;
        var stage = event.getStage();
        GoldenGateRenderer.Pass pass;
        PoseStack pose = event.getPoseStack();
        if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            pass = GoldenGateRenderer.Pass.BODY;
        } else if (Minecraft.useShaderTransparency() && stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            pass = GoldenGateRenderer.Pass.SORTED_HALO;
        } else if (!Minecraft.useShaderTransparency() && stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            pass = GoldenGateRenderer.Pass.HALO;
            // At the weather stage LevelRenderer has already put camera rotation in RenderSystem's model-view.
            pose = new PoseStack();
        } else return;
        var eye = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        long now = world.getGameTime();
        GoldenGateRenderer.drawWeapons(pose, eye, ACTIVE.values(), now, partial, pass);
        for (var visual : ACTIVE.values()) {
            var cast = visual.cast();
            long ticks = now - cast.start();
            if (!cast.chains() || ticks < 0 || visual.expired(now)) continue;
            // Bound casts can live for days. Retain sub-tick precision for their moving ripples.
            float age = (ticks < 524288L ? ticks : 262144L + ticks % 262144L) + partial;
            Vec3 relative = cast.origin().subtract(eye);
            pose.pushPose();
            try {
                pose.translate(relative.x, relative.y, relative.z);
                Vec3 end = cast.end();
                float width = cast.width(), height = cast.height();
                var entity = cast.targetId() < 0 ? null : world.getEntity(cast.targetId());
                if (cast.isBoundChain()) {
                    if (!(entity instanceof LivingEntity target) || !target.isAlive()
                            || !cast.target().filter(target.getUUID()::equals).isPresent()) continue;
                    end = target.getBoundingBox().getCenter().subtract(target.position()).add(target.getPosition(partial));
                    width = target.getBbWidth();
                    height = target.getBbHeight();
                }
                Vec3 offset = end.subtract(cast.origin());
                float chainStage = visual.chainStage(now, partial);
                GoldenGateRenderer.drawChainGates(pose, offset, width, height, cast.seed(), chainStage, age, pass);
                if (pass == GoldenGateRenderer.Pass.BODY) HeavenChainRenderer.draw(pose, offset, width, height, cast.seed(), chainStage, age);
            } finally { pose.popPose(); }
        }
    }
}
