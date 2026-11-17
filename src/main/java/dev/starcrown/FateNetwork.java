package dev.starcrown;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;

public final class FateNetwork {
    public record State(int entity,java.util.UUID owner,ResourceLocation dimension,long start,Vec3 origin,float yaw,boolean offhand,int mode){
        static State of(FateItems.Cast c,int mode){return new State(c.player().getId(),c.player().getUUID(),c.dimension(),c.start(),c.origin(),c.yaw(),c.hand()==net.minecraft.world.InteractionHand.OFF_HAND,mode);}
    }
    public static void register(){
        SevenNetwork.CHANNEL.registerMessage(10,State.class,(s,b)->{
            b.writeVarInt(s.entity());b.writeUUID(s.owner());b.writeResourceLocation(s.dimension());b.writeLong(s.start());
            b.writeDouble(s.origin().x);b.writeDouble(s.origin().y);b.writeDouble(s.origin().z);b.writeFloat(s.yaw());b.writeBoolean(s.offhand());b.writeByte(s.mode());
        },b->new State(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readLong(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readBoolean(),b.readUnsignedByte()),(s,ctx)->{
            var context=ctx.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->FateClient.receive(s)));context.setPacketHandled(true);
        },java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
