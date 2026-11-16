package dev.starcrown;

import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;

public final class EternalReturnNetwork {
    public record Action() {}
    public record Rewind(int entityId) {}
    public static void rewind(ServerPlayer p) {
        SevenNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new Rewind(p.getId()));
    }
    public record State(boolean worn,int kindling,int seeds,int memory,int scars,int activeTicks,int cooldownTicks,
            int rewindTicks,int fatigueTicks,float shield,float stored,boolean enhanced,boolean awakened) {
        public static final State EMPTY=new State(false,0,0,0,0,0,0,0,0,0,0,false,false);
        static void encode(State s,FriendlyByteBuf b) {
            b.writeBoolean(s.worn);b.writeVarInt(s.kindling);b.writeVarInt(s.seeds);b.writeVarInt(s.memory);b.writeVarInt(s.scars);
            b.writeVarInt(s.activeTicks);b.writeVarInt(s.cooldownTicks);b.writeVarInt(s.rewindTicks);b.writeVarInt(s.fatigueTicks);
            b.writeFloat(s.shield);b.writeFloat(s.stored);b.writeBoolean(s.enhanced);b.writeBoolean(s.awakened);
        }
        static State decode(FriendlyByteBuf b) {return new State(b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),
                b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean());}
    }
    private static int left(long until,long now) {return (int)Math.min(Integer.MAX_VALUE,Math.max(0,until-now));}
    public static void sync(ServerPlayer p) {
        var d=EternalReturn.data(p);var halo=EternalReturn.equipped(p);long time=EternalReturn.now(p);
        boolean worn=!halo.isEmpty();d.putBoolean("client_worn",worn);
        SevenNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(worn,EternalReturn.kindling(halo),d.getInt("seeds"),
                d.getInt("memory"),d.getInt("scars"),left(d.getLong("active_until"),time),left(d.getLong("active_ready"),time),
                left(d.getLong("rewind_ready"),time),left(d.getLong("fatigue_until"),time),d.getFloat("shield"),d.getFloat("stored"),
                d.getBoolean("enhanced"),EternalReturn.awakened(halo)));
    }
    public static void register() {
        SevenNetwork.CHANNEL.registerMessage(7,Action.class,(a,b)->{},b->new Action(),(a,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{var p=context.getSender();if(p==null)return;
                var d=EternalReturn.data(p);long time=EternalReturn.now(p);
                if(d.contains("last_action")&&time-d.getLong("last_action")<4)return;
                d.putLong("last_action",time);EternalReturn.action(p);
            });context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        SevenNetwork.CHANNEL.registerMessage(8,State.class,State::encode,State::decode,(s,supplier)->{
            var context=supplier.get();context.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->EternalReturnClient.receive(s)));
            context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        SevenNetwork.CHANNEL.registerMessage(9,Rewind.class,(s,b)->b.writeVarInt(s.entityId()),b->new Rewind(b.readVarInt()),(s,supplier)->{
            var context=supplier.get();context.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->EternalReturnClient.rewind(s.entityId())));
            context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
