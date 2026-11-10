package dev.starcrown;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
public final class SevenNetwork {
 private static final String PROTOCOL="5";
 public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(StarcrownDoll.ID,"seven"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
 public record Action(int id,int value){static void encode(Action a,FriendlyByteBuf b){b.writeVarInt(a.id);b.writeVarInt(a.value);}static Action decode(FriendlyByteBuf b){return new Action(b.readVarInt(),b.readVarInt());}static void handle(Action a,Supplier<NetworkEvent.Context> supplier){var context=supplier.get();context.enqueueWork(()->{var p=context.getSender();if(p==null||a.id<0||a.id>CalmYule.CLEAR_PACTS_ACTION||(a.id==DivineSeven.POTION_LEVEL_ACTION?(a.value<PotionFilter.MIN_LEVEL||a.value>PotionFilter.MAX_LEVEL):(a.value<0||a.value>7)))return;long now=p.serverLevel().getGameTime();var data=p.getPersistentData();if(data.contains("starcrown_last_action")&&data.getLong("starcrown_last_action")==now)return;data.putLong("starcrown_last_action",now);DivineSeven.action(p,a.id,a.value);sync(p);});context.setPacketHandled(true);}}
 public record State(boolean domain,boolean night,boolean mine,boolean players,int speed,int mining){
  public State(boolean domain,boolean night,boolean mine,boolean players,int speed){this(domain,night,mine,players,speed,0);}
  static void encode(State s,FriendlyByteBuf b){b.writeBoolean(s.domain);b.writeBoolean(s.night);b.writeBoolean(s.mine);b.writeBoolean(s.players);b.writeVarInt(s.speed);b.writeVarInt(s.mining);}
  static State decode(FriendlyByteBuf b){return new State(b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt());}
  static void handle(State s,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->SevenClient.receive(s)));c.setPacketHandled(true);}
 }
 public record EffectAction(ResourceLocation effect,boolean enabled){
  static void encode(EffectAction a,FriendlyByteBuf b){b.writeResourceLocation(a.effect);b.writeBoolean(a.enabled);}
  static EffectAction decode(FriendlyByteBuf b){return new EffectAction(b.readResourceLocation(),b.readBoolean());}
  static void handle(EffectAction a,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{var p=c.getSender();if(p==null||!DivineWill.wearing(p))return;PotionFilter.set(p,a.effect,a.enabled);sync(p);});c.setPacketHandled(true);}
 }
 public record EffectsState(java.util.Set<String> disabled,int level){
  static void encode(EffectsState s,FriendlyByteBuf b){b.writeCollection(s.disabled,(buf,id)->buf.writeUtf(id,256));b.writeVarInt(s.level);}
  static EffectsState decode(FriendlyByteBuf b){return new EffectsState(java.util.Set.copyOf(b.readList(buf->buf.readUtf(256))),b.readVarInt());}
  static void handle(EffectsState s,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->SevenClient.receiveEffects(s)));c.setPacketHandled(true);}
 }
 public static void sync(net.minecraft.server.level.ServerPlayer p){
  var client=PacketDistributor.PLAYER.with(()->p);
  if(CalmYule.active(p)){CHANNEL.send(client,new State(CalmYule.fieldActive(p),CalmYule.frostEnabled(p),false,false,0,0));return;}
  if(YuleHumility.active(p)){CHANNEL.send(client,new State(CalmYule.peaceEnabled(p),CalmYule.purifyEnabled(p),false,false,0,0));return;}
  if(TwilightCrown.wearing(p)){var d=TwilightCrown.settings(p);CHANNEL.send(client,new State(TwilightCrown.domain(p),!d.getBoolean("night_off"),d.getBoolean("area_mine"),true,0,d.getInt("mining_range")));return;}
  CHANNEL.send(client,new EffectsState(PotionFilter.disabled(p),PotionFilter.level(p)));var d=DivineSeven.settings(p);CHANNEL.send(client,new State(d.getBoolean("domain"),PotionFilter.enabled(p,net.minecraft.world.effect.MobEffects.NIGHT_VISION),d.getBoolean("area_mine"),!d.getBoolean("players_off"),d.contains("speed")?d.getInt("speed"):1,DivineSeven.miningIndex(p)));
 }
 public static void init(){CHANNEL.registerMessage(2,EffectAction.class,EffectAction::encode,EffectAction::decode,EffectAction::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));CHANNEL.registerMessage(3,EffectsState.class,EffectsState::encode,EffectsState::decode,EffectsState::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));CHANNEL.registerMessage(1,State.class,State::encode,State::decode,State::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));CHANNEL.registerMessage(0,Action.class,Action::encode,Action::decode,Action::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));}
}
