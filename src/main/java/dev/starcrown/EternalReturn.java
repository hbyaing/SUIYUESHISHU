package dev.starcrown;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/** All combat decisions and fuel mutations are server authoritative. Clocks use overworld ticks. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class EternalReturn {
    public static final int CAP=33_550_336;
    private static final String ROOT="starcrown_eternal_return", FUEL="EternalKindling", AWAKENED="EternalAwakened";
    private static final UUID HEALTH=UUID.fromString("40de8c8a-7e16-46d0-a931-3d193ed19d60"),
            SPEED=UUID.fromString("40de8c8a-7e16-46d0-a931-3d193ed19d61"),
            ATTACK_SPEED=UUID.fromString("40de8c8a-7e16-46d0-a931-3d193ed19d62"),
            KNOCKBACK=UUID.fromString("40de8c8a-7e16-46d0-a931-3d193ed19d63");
    private static final Map<ServerPlayer,Deque<Snapshot>> HISTORY=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> FINISHING=ThreadLocal.withInitial(()->false);
    public record Snapshot(ServerLevel level, Vec3 position, float yaw, float pitch, float health, long time) {}

    public static CompoundTag data(Player player) {
        var root=player.getPersistentData();
        if(!root.contains(ROOT))root.put(ROOT,new CompoundTag());
        return root.getCompound(ROOT);
    }
    public static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    public static ItemStack equipped(Player player) {
        if(player.isSpectator()) return ItemStack.EMPTY;
        return CuriosApi.getCuriosInventory(player).resolve().flatMap(h->h.getStacksHandler("head"))
                .map(h->{for(int i=0;i<h.getStacks().getSlots();i++) {
                    var stack=h.getStacks().getStackInSlot(i);
                    if(stack.is(StarcrownDoll.ETERNAL_RETURN.get()))return stack;
                }return ItemStack.EMPTY;}).orElse(ItemStack.EMPTY);
    }
    public static int kindling(ItemStack stack) { return stack.hasTag()?Math.max(0,Math.min(CAP,stack.getTag().getInt(FUEL))):0; }
    public static boolean awakened(ItemStack stack) { return stack.hasTag()&&stack.getTag().getBoolean(AWAKENED); }
    public static void setKindling(ItemStack stack, int amount) {
        var tag=stack.getOrCreateTag();int clamped=Math.max(0,Math.min(CAP,amount));tag.putInt(FUEL,clamped);
        if(clamped==CAP)tag.putBoolean(AWAKENED,true);
    }
    public static int burn(Player player,ItemStack halo,ItemStack offering) {
        if(player.level().isClientSide || halo.isEmpty() || !halo.is(StarcrownDoll.ETERNAL_RETURN.get())
                || offering.isEmpty() || offering.is(StarcrownDoll.ETERNAL_RETURN.get()))return 0;
        int room=CAP-kindling(halo);
        if(room<=0){player.displayClientMessage(Component.literal("柴薪已满"),true);return 0;}
        long unit=KindlingValues.value(offering);
        if(player instanceof ServerPlayer p && fatigue(p))unit=Math.max(1,unit/2);
        int count=(int)Math.min(offering.getCount(),(room+unit-1)/unit);
        int gain=(int)Math.min(room,count*unit);
        offering.shrink(count);setKindling(halo,kindling(halo)+gain);
        player.displayClientMessage(Component.literal("焚入柴薪 +"+gain),true);
        if(player instanceof ServerPlayer p) { updateAttributes(p);EternalReturnNetwork.sync(p); }
        return gain;
    }
    public static int tier(int fuel) {
        return fuel>=CAP?5:fuel>=20_000_000?4:fuel>=10_000_000?3:fuel>=5_000_000?2:fuel>=1_000_000?1:0;
    }
    public static double healthPenalty(int tier) { return new double[]{0,.05,.10,.15,.25,.35}[tier]; }
    public static double incomingPenalty(int tier) { return tier>=5?.15:tier==4?.10:tier==3?.05:0; }
    public static boolean active(ServerPlayer p) { return data(p).getLong("active_until")>now(p); }
    public static boolean fatigue(ServerPlayer p) { return data(p).getLong("fatigue_until")>now(p); }
    public static boolean causalLocked(LivingEntity entity) {
        return entity instanceof ServerPlayer p && data(p).getLong("causal_until")>now(p);
    }
    public static boolean noNaturalHealing(Player p) {
        return p instanceof ServerPlayer sp && (fatigue(sp)||data(sp).getLong("no_heal_until")>now(sp));
    }
    private static void modifier(ServerPlayer p,Attribute type,UUID id,double amount,AttributeModifier.Operation op) {
        var attribute=p.getAttribute(type);if(attribute==null)return;
        var previous=attribute.getModifier(id);
        if(previous!=null && previous.getAmount()==amount)return;
        if(previous!=null)attribute.removeModifier(id);
        if(amount!=0)attribute.addTransientModifier(new AttributeModifier(id,"Eternal return",amount,op));
    }
    public static void updateAttributes(ServerPlayer p) {
        var halo=equipped(p);boolean worn=!halo.isEmpty();
        float oldMax=p.getMaxHealth(), oldHealth=p.getHealth();
        modifier(p,Attributes.MAX_HEALTH,HEALTH,worn?.4-healthPenalty(tier(kindling(halo))):0,AttributeModifier.Operation.MULTIPLY_BASE);
        if(oldMax!=p.getMaxHealth() && oldHealth>0)
            p.setHealth(Math.max(Math.min(.01f,p.getMaxHealth()),Math.min(p.getMaxHealth(),oldHealth/oldMax*p.getMaxHealth())));
        modifier(p,Attributes.MOVEMENT_SPEED,SPEED,worn&&active(p)?.25:fatigue(p)?-.15:0,AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(p,Attributes.ATTACK_SPEED,ATTACK_SPEED,fatigue(p)?-.10:0,AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(p,Attributes.KNOCKBACK_RESISTANCE,KNOCKBACK,worn&&active(p)?1:0,AttributeModifier.Operation.ADDITION);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer p))return;
        long time=now(p);var d=data(p);boolean worn=!equipped(p).isEmpty();
        if(d.getLong("active_until")>0 && (!worn || d.getLong("active_until")<=time)) {
            if(worn && p.isAlive())finish(p);else stop(p);
        }
        if(d.getInt("scars")>0 && d.getLong("scars_until")<=time)d.putInt("scars",0);
        if(d.getLong("combat_until")<=time) {d.putInt("seeds",0);d.remove("negative_records");}
        updateAttributes(p);
        if(worn && p.isAlive() && time%10==0)record(p);
        else if(!worn)HISTORY.remove(p);
        if(time%10==0 && (worn||d.getBoolean("client_worn")))EternalReturnNetwork.sync(p);
    }
    private static boolean validTarget(ServerPlayer p,LivingEntity target) {
        if(!target.isAlive()||target==p||target.isInvulnerable()||TwilightCrown.ally(p,target))return false;
        if(target instanceof Player other)return !other.isCreative()&&!other.isSpectator()&&p.canHarmPlayer(other);
        return TwilightCrown.hostile(p,target);
    }
    private static void combat(ServerPlayer p,LivingEntity target) {
        var d=data(p);long time=now(p);
        if(d.getLong("combat_until")<=time)d.remove("negative_records");
        d.putLong("combat_until",time+200);
        if(target!=null)d.putUUID("target",target.getUUID());
        int tier=tier(kindling(equipped(p)));
        if(tier>0)negative(p,"kindling_health");
        if(tier>=2)negative(p,"kindling_healing");
        if(tier>=3)negative(p,"kindling_vulnerability");
        if(d.getInt("scars")>0&&d.getLong("scars_until")>time)negative(p,"memory_scar");
        if(fatigue(p))negative(p,"fatigue");
    }
    /** Once per second of successful direct combat damage; passive animals cannot supply seeds. */
    private static void seed(ServerPlayer p,LivingEntity target) {
        var d=data(p);combat(p,target);long time=now(p);
        if(!active(p) && (!d.contains("last_seed")||time-d.getLong("last_seed")>=20)) {
            d.putInt("seeds",Math.min(12,d.getInt("seeds")+1));d.putLong("last_seed",time);
        }
    }
    public static double damageBonus(ServerPlayer p) {
        var d=data(p);
        return Math.min(2.5,.20+kindling(equipped(p))/1_000_000*.01+Math.min(30,d.getInt("memory"))*.01+(active(p)?.80:0));
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void outgoing(LivingDamageEvent event) {
        if(FINISHING.get() || event.getAmount()<=0 || !(event.getSource().getEntity() instanceof ServerPlayer p)
                || equipped(p).isEmpty() || TwilightCrown.ally(p,event.getEntity()))return;
        float amount=(float)(event.getAmount()*(1+damageBonus(p)));event.setAmount(amount);
        var direct=event.getSource().getDirectEntity();
        if(!validTarget(p,event.getEntity()) || direct!=p && !(direct instanceof Projectile projectile && projectile.getOwner()==p))return;
        seed(p,event.getEntity());
        if(active(p)) {
            var d=data(p);float fraction=d.getBoolean("enhanced")?.35f:.25f;
            float actual=Math.min(amount,event.getEntity().getHealth());
            d.putFloat("stored",Math.min(d.getFloat("attack")*40,d.getFloat("stored")+actual*fraction));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void incoming(LivingDamageEvent event) {
        if(event.getAmount()<=0 || !(event.getEntity() instanceof ServerPlayer p))return;
        var d=data(p);boolean worn=!equipped(p).isEmpty();
        int scars=d.getLong("scars_until")>now(p)?Math.min(5,d.getInt("scars")):0;
        if(!worn && scars==0)return;
        if(event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        double penalty=(worn?incomingPenalty(tier(kindling(equipped(p)))):0)+scars*.03;
        float amount=(float)(event.getAmount()*(worn?.8:1)*(1+penalty));
        if(worn && active(p)) {
            float absorbed=Math.min(Math.max(0,d.getFloat("shield")),amount);
            d.putFloat("shield",Math.max(0,d.getFloat("shield")-absorbed));amount-=absorbed;
        }
        event.setAmount(amount);
        if(worn && event.getSource().getEntity() instanceof LivingEntity attacker && validTarget(p,attacker))combat(p,attacker);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void immunity(LivingAttackEvent event) {
        if(event.getEntity() instanceof ServerPlayer p && data(p).getLong("immune_until")>now(p)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void healing(LivingHealEvent event) {
        if(event.getEntity() instanceof ServerPlayer p && !equipped(p).isEmpty()
                && tier(kindling(equipped(p)))>=2)event.setAmount(event.getAmount()*.9f);
    }
    public static void action(ServerPlayer p) {
        if(equipped(p).isEmpty() || !p.isAlive() || p.isSpectator())return;
        if(active(p)) {finish(p);return;}
        var d=data(p);long time=now(p);
        if(d.getLong("active_ready")>time) {message(p,"永劫回归冷却："+(d.getLong("active_ready")-time+19)/20+" 秒");return;}
        if(d.getInt("seeds")<12) {message(p,"火种不足："+d.getInt("seeds")+" / 12");return;}
        var halo=equipped(p);boolean free=d.getBoolean("free_enhanced")&&awakened(halo);
        boolean enhanced=free||kindling(halo)>=10_000;
        if(free)d.putBoolean("free_enhanced",false);else if(enhanced)setKindling(halo,kindling(halo)-10_000);
        d.putInt("seeds",0);d.putBoolean("enhanced",enhanced);d.putFloat("stored",0);
        d.putFloat("attack",(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE));
        d.putLong("active_ready",time+1800);d.putLong("active_until",time+(enhanced?400:320));
        updateAttributes(p);d.putFloat("shield",p.getMaxHealth()*.4f);
        message(p,enhanced?"永劫回归 · 柴薪燃尽，命途重燃":"永劫回归 · 轮转开启");EternalReturnNetwork.sync(p);
    }
    private static void message(ServerPlayer p,String text) {p.displayClientMessage(Component.literal(text),true);}
    private static LivingEntity mainTarget(ServerPlayer p) {
        var d=data(p);
        if(d.hasUUID("target")) {
            var target=p.serverLevel().getEntity(d.getUUID("target"));
            if(target instanceof LivingEntity living && validTarget(p,living)&&p.distanceToSqr(living)<=32*32)return living;
        }
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(12),
                e->validTarget(p,e)&&p.distanceToSqr(e)<=144).stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
    }
    public static void finish(ServerPlayer p) {
        var d=data(p);if(d.getLong("active_until")==0)return;
        LivingEntity main=mainTarget(p);
        float total=(d.getFloat("attack")*8+d.getFloat("stored"))
                *(1+(d.getBoolean("enhanced")?.5f:0)+(d.getBoolean("memory_finisher")?.5f:0));
        stop(p);
        if(main!=null) {
            var extras=p.serverLevel().getEntitiesOfClass(LivingEntity.class,main.getBoundingBox().inflate(12),
                    e->e!=main&&validTarget(p,e)&&e.distanceToSqr(main)<=144);
            d.putBoolean("memory_finisher",false);
            FINISHING.set(true);
            try {
                strike(p,main,total*(extras.isEmpty()?1:.7f));
                if(!extras.isEmpty())for(var extra:extras)strike(p,extra,total*.3f/extras.size());
            } finally {FINISHING.remove();}
        }
        updateAttributes(p);EternalReturnNetwork.sync(p);
    }
    private static void strike(ServerPlayer p,LivingEntity target,float amount) {
        target.invulnerableTime=0;target.hurt(p.damageSources().playerAttack(p),amount);
    }
    private static void stop(ServerPlayer p) {
        var d=data(p);d.putLong("active_until",0);d.putFloat("shield",0);d.putFloat("stored",0);
        d.putLong("fatigue_until",now(p)+400);negative(p,"fatigue");
    }
    public static boolean safe(ServerPlayer p,Vec3 position) {
        var level=p.serverLevel();var pos=BlockPos.containing(position);
        var box=p.getBoundingBox().move(position.subtract(p.position()));
        return position.y>level.getMinBuildHeight()+1 && position.y<level.getMaxBuildHeight()-2
                && level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)
                && level.getFluidState(pos).isEmpty()&&level.getFluidState(pos.above()).isEmpty()
                && level.noCollision(p,box)&&level.getBlockState(pos.below()).isCollisionShapeFullBlock(level,pos.below())
                && !level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && !level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                && !level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE);
    }
    public static void record(ServerPlayer p) {
        var history=HISTORY.computeIfAbsent(p,k->new ArrayDeque<>());long time=now(p);
        history.removeIf(s->s.level()!=p.serverLevel()||s.time()<time-240);
        if(safe(p,p.position()))history.addLast(new Snapshot(p.serverLevel(),p.position(),p.getYRot(),p.getXRot(),p.getHealth(),time));
    }
    /** Called before vanilla's totem test; never handles /kill or bypass-invulnerability damage. */
    public static boolean rewind(LivingEntity entity,DamageSource source) {
        if(!(entity instanceof ServerPlayer p)||equipped(p).isEmpty()
                ||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        var d=data(p);long time=now(p);
        if(d.getLong("rewind_ready")>time || causalLocked(p))return false;
        if(d.getLong("active_until")>0)finish(p);
        p.stopRiding();p.stopFallFlying();
        Snapshot selected=null;var history=HISTORY.get(p);
        if(history!=null)for(var s:history)if(s.level()==p.serverLevel()&&s.time()<=time-120&&safe(p,s.position()))selected=s;
        // If six seconds of safe history do not exist, retain the present safe location; no arbitrary cross-world teleport.
        if(selected!=null)p.connection.teleport(selected.position().x,selected.position().y,selected.position().z,selected.yaw(),selected.pitch());
        else if(!safe(p,p.position())) {
            if(history!=null)for(var s:history)if(s.level()==p.serverLevel()&&safe(p,s.position()))selected=s;
            if(selected!=null)p.connection.teleport(selected.position().x,selected.position().y,selected.position().z,selected.yaw(),selected.pitch());
        }
        p.setHealth(Math.min(p.getMaxHealth(),Math.max(p.getMaxHealth()*.5f,selected==null?0:selected.health())));
        p.deathTime=0;p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;p.clearFire();
        d.putLong("rewind_ready",time+3600);d.putLong("immune_until",time+40);d.putLong("causal_until",time+160);
        d.putInt("memory",Math.min(30,d.getInt("memory")+1));
        if(d.getInt("memory")==30)d.putBoolean("memory_finisher",true);
        d.putInt("scars",Math.min(5,d.getInt("scars")+1));d.putLong("scars_until",time+12000);
        if(awakened(equipped(p)))d.putBoolean("free_enhanced",true);
        p.setAbsorptionAmount(0);d.putFloat("shield",0);
        for(var effect:new ArrayList<>(p.getActiveEffects()))if(effect.getEffect().isBeneficial()) {
            var shorter=effect.save(new CompoundTag());halveDuration(shorter);
            p.removeEffect(effect.getEffect());var replacement=MobEffectInstance.load(shorter);
            if(replacement!=null)p.addEffect(replacement);
        }
        // Reapplying a shortened Absorption effect can grant absorption again; clear after all reapplications.
        p.setAbsorptionAmount(0);
        d.putLong("no_heal_until",time+200);negative(p,"rewind_cost");negative(p,"memory_scar");negative(p,"causal_lock");
        HISTORY.remove(p);EternalReturnNetwork.rewind(p);
        message(p,"永劫回归 · 记忆 "+d.getInt("memory")+" / 30");EternalReturnNetwork.sync(p);return true;
    }
    private static void halveDuration(CompoundTag tag) {
        int duration=tag.getInt("Duration");if(duration>0)tag.putInt("Duration",Math.max(1,duration/2));
        if(tag.contains("HiddenEffect"))halveDuration(tag.getCompound("HiddenEffect"));
    }
    /** Typed, bounded negative records reserved for the future Demiurge book. */
    public static void negative(ServerPlayer p,String type) {
        var d=data(p);if(d.getLong("combat_until")<=now(p))return;
        var records=d.getCompound("negative_records");
        if(records.size()<12||records.contains(type))records.putBoolean(type,true);
        d.put("negative_records",records);
    }
    public static MobEffectInstance extendNegative(LivingEntity entity,MobEffectInstance effect) {
        if(!(entity instanceof ServerPlayer p)||effect.getEffect().getCategory()!=net.minecraft.world.effect.MobEffectCategory.HARMFUL
                ||effect.isInfiniteDuration())return effect;
        var d=data(p);int scars=d.getLong("scars_until")>now(p)?Math.min(5,d.getInt("scars")):0;
        if(scars<=0)return effect;
        var tag=effect.save(new CompoundTag());extendDuration(tag,1+scars*.04);
        negative(p,"harmful_effect");var extended=MobEffectInstance.load(tag);return extended==null?effect:extended;
    }
    private static void extendDuration(CompoundTag tag,double factor) {
        int duration=tag.getInt("Duration");if(duration>0)tag.putInt("Duration",(int)Math.min(Integer.MAX_VALUE,Math.ceil(duration*factor)));
        if(tag.contains("HiddenEffect"))extendDuration(tag.getCompound("HiddenEffect"),factor);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        var old=event.getOriginal().getPersistentData();
        if(old.contains(ROOT)) {
            var d=old.getCompound(ROOT).copy();d.putLong("active_until",0);d.putFloat("shield",0);d.putFloat("stored",0);
            d.putInt("seeds",0);d.remove("target");event.getEntity().getPersistentData().put(ROOT,d);
        }
        HISTORY.remove(event.getOriginal());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) {if(data(p).getLong("active_until")>0)stop(p);HISTORY.remove(p);}
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer p){if(data(p).getLong("active_until")>0)stop(p);HISTORY.remove(p);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void totem(LivingUseTotemEvent event) {
        if(causalLocked(event.getEntity()))event.setCanceled(true);
    }
}
