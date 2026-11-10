package dev.starcrown;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.WeakHashMap;

/** Client-only positional, streaming playback for placed dolls. */
final class DollMusic extends AbstractTickableSoundInstance {
 private static final WeakHashMap<StarcrownDoll.DollEntity,java.lang.ref.WeakReference<DollMusic>> ACTIVE=new WeakHashMap<>();
 private final StarcrownDoll.DollEntity doll;

 private DollMusic(StarcrownDoll.DollEntity doll){
  super(StarcrownDoll.isYule(doll.getBlockState())?StarcrownDoll.YULE_MUSIC.get():StarcrownDoll.MUSIC.get(),SoundSource.RECORDS,RandomSource.create());
  this.doll=doll;var pos=doll.getBlockPos();x=pos.getX()+.5;y=pos.getY()+.5;z=pos.getZ()+.5;
  volume=2;pitch=1;looping=true;delay=0;relative=false;
 }
 static boolean suppressing(){
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.getSoundSourceVolume(SoundSource.RECORDS)<=0||mc.options.getSoundSourceVolume(SoundSource.MASTER)<=0)return false;
  for(var entry:ACTIVE.entrySet()){
   var doll=entry.getKey();var sound=entry.getValue().get();
   if(sound!=null&&doll!=null&&doll.isPlaying()&&!doll.isRemoved()&&doll.getLevel()==mc.level&&mc.player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(doll.getBlockPos()))<=1024)return true;
  }
  return false;
 }
 static void toggle(StarcrownDoll.DollEntity doll,boolean start){
  var manager=Minecraft.getInstance().getSoundManager();
  var reference=ACTIVE.get(doll);DollMusic previous=reference==null?null:reference.get();
  if(start&&previous!=null&&manager.isActive(previous))return;
  ACTIVE.remove(doll);if(previous!=null)manager.stop(previous);
  if(start){DollMusic sound=new DollMusic(doll);ACTIVE.put(doll,new java.lang.ref.WeakReference<>(sound));if(suppressing())DollMusicPriority.stopOthers();manager.play(sound);}
 }
 @Override public void tick(){
  var level=Minecraft.getInstance().level;
  if(!doll.isPlaying()||doll.isRemoved()||level==null||doll.getLevel()!=level||level.getBlockEntity(doll.getBlockPos())!=doll){
   stop();ACTIVE.remove(doll);
  }
 }
}
