package dev.starcrown;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;
import java.util.function.Supplier;

/** Authoritative audiovisual cues. Only server-supplied paths are sent to observers. */
final class ExcaliburEffects {
    enum Kind {
        VEIL(15), REVEAL(15), WIND(13), BEAM(ExcaliburChant.AFTER_HIT_TICKS), WIND_HIT(7), HIT(7), SANCTUARY(AvalonItem.DURATION), HEAL(20);

        final int ticks;
        Kind(int ticks) { this.ticks = ticks; }
    }

    private enum Cue {
        VEIL, REVEAL, WIND_CHARGE, CHARGE, WIND, BEAM, IMPACT, SLASH, SANCTUARY, HEAL;

        final RegistryObject<SoundEvent> sound;
        Cue() {
            String id = "excalibur_" + name().toLowerCase(java.util.Locale.ROOT);
            sound = StarcrownDoll.SOUNDS.register(id,
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(StarcrownDoll.ID, id)));
        }
    }

    record Effect(Kind kind, ResourceLocation dimension, int owner, Vec3 start, Vec3 end, Direction hitFace) {
        static void encode(Effect e, FriendlyByteBuf b) {
            b.writeEnum(e.kind);
            b.writeResourceLocation(e.dimension);
            b.writeVarInt(e.owner);
            writePoint(b, e.start);
            writePoint(b, e.end);
            if (e.kind == Kind.BEAM) b.writeByte(e.hitFace == null ? -1 : e.hitFace.get3DDataValue());
        }

        static Effect decode(FriendlyByteBuf b) {
            var kind = b.readEnum(Kind.class);
            var dimension = b.readResourceLocation();
            int owner = b.readVarInt();
            Vec3 start = readPoint(b), end = readPoint(b);
            int face = kind == Kind.BEAM ? b.readByte() : -1;
            return new Effect(kind, dimension, owner, start, end,
                    face >= 0 && face < 6 ? Direction.from3DDataValue(face) : null);
        }

        private static void writePoint(FriendlyByteBuf b, Vec3 v) {
            b.writeDouble(v.x);
            b.writeDouble(v.y);
            b.writeDouble(v.z);
        }

        private static Vec3 readPoint(FriendlyByteBuf b) {
            return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        }

        static void handle(Effect effect, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ExcaliburVisuals.receive(effect)));
            c.setPacketHandled(true);
        }
    }

    static void register() {
        Cue.values(); // Register sound events while the mod's DeferredRegister is still open.
        SevenNetwork.CHANNEL.registerMessage(11, Effect.class, Effect::encode, Effect::decode,
                Effect::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    static void toggle(ServerPlayer p, boolean concealed) {
        Vec3 center = p.getEyePosition().add(p.getLookAngle().scale(.72)).add(0, -.19, 0);
        emit(p, concealed ? Kind.VEIL : Kind.REVEAL, center, center);
        play(p, center, concealed ? Cue.VEIL : Cue.REVEAL, .68f);
    }

    static void startCharge(ServerPlayer p, boolean wind) {
        play(p, p.position(), wind ? Cue.WIND_CHARGE : Cue.CHARGE, wind ? .48f : .79f);
    }

    static void release(ServerPlayer p, boolean wind, Vec3 start, Vec3 end, Direction hitFace) {
        Vec3 muzzle = start;
        double length = start.distanceTo(end);
        if (length >= 1) {
            // The true-name beam follows the already clipped damage ray from its first section to its impact.
            muzzle = start.lerp(end, .68 / length);
            if (wind) {
                double yaw = Math.toRadians(p.getYRot());
                boolean right = (p.getMainArm() == HumanoidArm.RIGHT) == (p.getUsedItemHand() == InteractionHand.MAIN_HAND);
                Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(right ? .32 : -.32);
                muzzle = muzzle.add(side).add(0, -.23, 0);
                if (p.level().clip(new ClipContext(start, muzzle, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) muzzle = start;
            }
        }
        emit(p, wind ? Kind.WIND : Kind.BEAM, muzzle, end, wind ? null : hitFace);
        play(p, start, wind ? Cue.WIND : Cue.BEAM, wind ? 1.1f : 2.3f);
        if (!wind) play(p, end, Cue.IMPACT, 1.9f);
    }

    static void beamHit(ServerPlayer p, Vec3 hit) {
        emit(p, Kind.HIT, hit, hit);
    }

    static void melee(ServerPlayer p, Vec3 hit, boolean wind) {
        emit(p, wind ? Kind.WIND_HIT : Kind.HIT, hit, hit);
        play(p, hit, Cue.SLASH, .62f);
    }

    static void avalon(ServerPlayer p, boolean sanctuary) {
        Vec3 center = p.position().add(0, p.getBbHeight() * .5, 0);
        emit(p, sanctuary ? Kind.SANCTUARY : Kind.HEAL, center, center);
        play(p, center, sanctuary ? Cue.SANCTUARY : Cue.HEAL, sanctuary ? .86f : .19f);
    }

    private static void play(ServerPlayer p, Vec3 pos, Cue cue, float volume) {
        p.serverLevel().playSound(null, pos.x, pos.y, pos.z, cue.sound.get(), SoundSource.PLAYERS,
                volume, .98f + .04f * p.getRandom().nextFloat());
    }

    private static void emit(ServerPlayer p, Kind kind, Vec3 start, Vec3 end) {
        emit(p, kind, start, end, null);
    }

    private static void emit(ServerPlayer p, Kind kind, Vec3 start, Vec3 end, Direction hitFace) {
        var level = p.serverLevel();
        Vec3 middle = start.lerp(end, .5);
        double radius = 64 + start.distanceTo(end) * .5;
        var near = new PacketDistributor.TargetPoint(middle.x, middle.y, middle.z, radius, level.dimension());
        SevenNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> near),
                new Effect(kind, level.dimension().location(), p.getId(), start, end, hitFace));
    }
}
