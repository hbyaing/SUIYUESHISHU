package dev.starcrown;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class EnkiduCompanion extends PathfinderMob implements GeoEntity {
    private static final String SUMMONED="starcrowndoll:enkidu_summoned";
    private static final Map<ServerPlayer,EnkiduCompanion> ACTIVE=new HashMap<>();
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("animation.enkidu.idle");
    private static final RawAnimation RUN=RawAnimation.begin().thenLoop("animation.enkidu.run");
    private final AnimatableInstanceCache animationCache=GeckoLibUtil.createInstanceCache(this);
    private UUID ownerId;
    private record FollowSpot(Vec3 position,boolean floating){}

    public EnkiduCompanion(EntityType<? extends PathfinderMob> type,Level level){
        super(type,level);
        setInvulnerable(true);
        setSilent(true);
    }

    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeSeenAsEnemy(){return false;}
    @Override public boolean removeWhenFarAway(double distance){return false;}

    @Override protected void customServerAiStep(){
        ServerLevel world=(ServerLevel)level();
        ServerPlayer owner=ownerId!=null&&world.getPlayerByUUID(ownerId) instanceof ServerPlayer player?player:null;
        if(owner==null||owner.level()!=world||!owner.isAlive()||!isSummoned(owner)){
            discard();
            return;
        }
        double distance=distanceToSqr(owner);
        if((distance>144||Math.abs(getY()-owner.getY())>4||isNoGravity())&&tickCount%5==0){
            FollowSpot target=safeSpot(owner,this);
            if(target!=null){
                if(!isNoGravity()||!target.floating()||distance>9)place(target,owner);
                return;
            }
        }
        if(distance<4)getNavigation().stop();
        else if(tickCount%8==0)getNavigation().moveTo(owner,1.25);
    }

    private void place(FollowSpot spot,ServerPlayer owner){
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(spot.floating());
        Vec3 point=spot.position();
        moveTo(point.x,point.y,point.z,owner.getYRot(),0);
    }

    private static FollowSpot safeSpot(ServerPlayer owner,EnkiduCompanion pet){
        ServerLevel world=owner.serverLevel();
        FollowSpot fallback=null;
        for(int vertical:new int[]{0,1,-1,2,-2}){
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=2)continue;
                BlockPos feet=owner.blockPosition().offset(dx,vertical,dz);
                if(!world.hasChunkAt(feet)||!world.getWorldBorder().isWithinBounds(feet))continue;
                Vec3 position=Vec3.atBottomCenterOf(feet);
                var box=pet.getType().getDimensions().makeBoundingBox(position);
                if(!world.noCollision(pet,box)||world.containsAnyLiquid(box))continue;
                if(world.getBlockState(feet.below()).isFaceSturdy(world,feet.below(),net.minecraft.core.Direction.UP))
                    return new FollowSpot(position,false);
                if(fallback==null)fallback=new FollowSpot(position,true);
            }
        }
        return fallback;
    }

    private static boolean isSummoned(ServerPlayer player){
        return player.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG).getBoolean(SUMMONED);
    }
    private static void setSummoned(ServerPlayer player,boolean summoned){
        var persisted=player.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG);
        if(summoned)persisted.putBoolean(SUMMONED,true);
        else persisted.remove(SUMMONED);
        player.getPersistentData().put(ServerPlayer.PERSISTED_NBT_TAG,persisted);
    }
    static void summon(ServerPlayer owner){
        setSummoned(owner,true);
        ensurePresent(owner);
    }
    static void dismiss(ServerPlayer owner){
        setSummoned(owner,false);
        removePet(owner);
    }
    private static void removePet(ServerPlayer owner){
        EnkiduCompanion pet=ACTIVE.remove(owner);
        if(pet!=null)pet.discard();
    }
    private static void ensurePresent(ServerPlayer owner){
        EnkiduCompanion current=ACTIVE.get(owner);
        if(current!=null&&current.level()==owner.level()&&!current.isRemoved())return;
        removePet(owner);
        if(!owner.isAlive())return;
        EnkiduCompanion pet=StarcrownDoll.ENKIDU.get().create(owner.serverLevel());
        if(pet==null)return;
        FollowSpot point=safeSpot(owner,pet);
        if(point==null)return;
        pet.ownerId=owner.getUUID();
        pet.place(point,owner);
        if(owner.serverLevel().addFreshEntity(pet))ACTIVE.put(owner,pet);
    }
    @SubscribeEvent public static void tickOwner(TickEvent.PlayerTickEvent event){
        if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer owner
                &&owner.tickCount%10==0&&owner.isAlive()&&isSummoned(owner))ensurePresent(owner);
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer owner)removePet(owner);
    }
    @SubscribeEvent public static void onClone(PlayerEvent.Clone event){
        if(event.getOriginal() instanceof ServerPlayer owner)removePet(owner);
    }
    @SubscribeEvent public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event){
        if(event.getEntity() instanceof ServerPlayer owner)removePet(owner);
    }
    static void attributes(EntityAttributeCreationEvent event){
        event.put(StarcrownDoll.ENKIDU.get(),Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED,.30).build());
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new AnimationController<>(this,"movement",4,state->state.setAndContinue(state.isMoving()?RUN:IDLE)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return animationCache;}
}
