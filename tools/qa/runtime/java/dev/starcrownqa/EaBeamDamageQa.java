package dev.starcrownqa;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="starcrownqa")
public final class EaBeamDamageQa {
    private static Vec3 center;
    private static Cow inside, outside, late, after;
    private static final List<Long> hitTicks=new ArrayList<>();
    private static final List<Float> amounts=new ArrayList<>();
    private static final List<Float> healthBefore=new ArrayList<>();
    private static ServerPlayer caster;
    private static boolean correctSource=true;

    static void setup(ServerPlayer owner){
        caster=owner;
        center=owner.getEyePosition().add(owner.getLookAngle().scale(19)).subtract(0,.6,0);
        inside=target(owner,center);
        outside=target(owner,center.add(6,0,0));
    }
    static void spawnLate(ServerPlayer owner){late=target(owner,center);}
    static void spawnAfter(ServerPlayer owner){after=target(owner,center);}

    private static Cow target(ServerPlayer owner,Vec3 position){
        var level=owner.serverLevel();
        Cow cow=EntityType.COW.create(level);
        cow.setNoGravity(true);
        cow.setNoAi(true);
        cow.setSilent(true);
        cow.setPersistenceRequired();
        cow.moveTo(position.x,position.y,position.z,0,0);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        cow.getAttribute(Attributes.ARMOR).setBaseValue(30);
        cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        cow.setHealth(1000);
        cow.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1600,4));
        level.addFreshEntity(cow);
        return cow;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event){
        if(inside!=null && event.getEntity()==inside){
            hitTicks.add(inside.level().getGameTime());
            amounts.add(event.getAmount());
            healthBefore.add(inside.getHealth());
            correctSource &= "starcrowndoll.ea_void".equals(event.getSource().getMsgId())
                    && event.getSource().getEntity()==caster;
        }
    }
    static boolean passed(){
        boolean successive=hitTicks.size()==4 && amounts.stream().allMatch(n->Math.abs(n-300f)<.01f);
        for(int i=1;i<hitTicks.size();i++)successive&=hitTicks.get(i)==hitTicks.get(i-1)+1;
        boolean ok=successive && correctSource && healthBefore.equals(List.of(1000f,700f,400f,100f))
                && inside.isDeadOrDying() && late!=null && late.isDeadOrDying()
                && outside.getHealth()==1000 && after!=null && after.getHealth()==1000;
        System.out.println("EA DAMAGE QA ticks="+hitTicks+" amounts="+amounts+" healthBefore="+healthBefore
                +" correctSource="+correctSource+" outside="+outside.getHealth()
                +" late="+(late==null?"missing":late.getHealth())+" after="+(after==null?"missing":after.getHealth()));
        return ok;
    }
}
