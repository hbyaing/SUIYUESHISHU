package dev.starcrownqa;

import dev.starcrown.MingGuLongEntity;
import dev.starcrown.StarcrownDoll;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starcrownbossqa")
@PrefixGameTestTemplate(false)
public final class MingGuLongDeathTests {
    public static final class DropProbe {
        MingGuLongEntity dragon;
        int drops;
        @SubscribeEvent public void drops(LivingDropsEvent event) {
            if (event.getEntity()==dragon) drops++;
        }
    }
    private static void animate(MingGuLongEntity dragon,int key) {
        try {var m=MingGuLongEntity.class.getDeclaredMethod("playAnimation",int.class);m.setAccessible(true);m.invoke(dragon,key);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }

    @GameTest(template="empty",templateNamespace="starcrownbossqa",timeoutTicks=60)
    public static void deathCompletesWithAndWithoutAi(GameTestHelper test) {
        var player=ChastityTests.playerAt(test,test.absolutePos(new BlockPos(1,4,1)));
        var probe=new DropProbe();MinecraftForge.EVENT_BUS.register(probe);
        try {
            for(boolean noAi:new boolean[]{false,true}) {
                var dragon=StarcrownDoll.MINGGULONG.get().create(test.getLevel());
                probe.dragon=dragon;probe.drops=0;
                ((Mob)dragon).setNoAi(noAi);
                ((Entity)dragon).moveTo(player.getX()+12,player.getY(),player.getZ());
                test.getLevel().addFreshEntity(dragon);
                animate(dragon,noAi?13:7); // interrupt HuangWu and airborne locomotion.
                ((LivingEntity)dragon).hurt(test.getLevel().damageSources().playerAttack(player),10000);
                test.assertTrue("Fight_Die".equals(dragon.getAnimationName()),"fatal hit immediately switches animation");
                int serial=dragon.getAnimationSerial();
                int xpBefore=test.getLevel().getEntitiesOfClass(ExperienceOrb.class,((Entity)dragon).getBoundingBox().inflate(3)).stream().mapToInt(ExperienceOrb::getValue).sum();
                test.assertTrue(probe.drops==1,"vanilla loot hook fires once at death");
                var target=EntityType.COW.create(test.getLevel());float hp=target.getHealth();
                test.assertTrue(!((Mob)dragon).doHurtTarget(target)&&target.getHealth()==hp,"corpse cannot deal melee damage");
                for(int tick=1;tick<MingGuLongEntity.DEATH_REMOVE_TICKS;tick++) {
                    ((Entity)dragon).tick();
                    test.assertTrue(!((Entity)dragon).isRemoved(),"body retained through tick "+tick+", NoAI="+noAi);
                    test.assertTrue(((LivingEntity)dragon).deathTime==tick,"death clock advances without reset");
                    test.assertTrue("Fight_Die".equals(dragon.getAnimationName())&&dragon.getAnimationSerial()==serial,"death clip is not restarted");
                    test.assertTrue(!MingGuLongBossBarTests.bar(dragon).isVisible(),"dead boss bar stays hidden");
                }
                ((Entity)dragon).tick();
                test.assertTrue(((Entity)dragon).getRemovalReason()==Entity.RemovalReason.KILLED,"remove after the entire death clip");
                test.assertTrue(probe.drops==1,"death ticks do not duplicate loot");
                int xpAfter=test.getLevel().getEntitiesOfClass(ExperienceOrb.class,((Entity)dragon).getBoundingBox().inflate(3)).stream().mapToInt(ExperienceOrb::getValue).sum();
                test.assertTrue(xpAfter==xpBefore,"death ticks do not duplicate experience");
            }
            test.succeed();
        } finally {MinecraftForge.EVENT_BUS.unregister(probe);if(probe.dragon!=null)((Entity)probe.dragon).discard();ChastityTests.close(player);}
    }

    @GameTest(template="empty",templateNamespace="starcrownbossqa",timeoutTicks=60)
    public static void reloadedCorpsePlaysCompleteClip(GameTestHelper test) {
        var dragon=StarcrownDoll.MINGGULONG.get().create(test.getLevel());
        ((Mob)dragon).setNoAi(true);((LivingEntity)dragon).setHealth(0);
        for(int i=0;i<50;i++)((Entity)dragon).tick();
        CompoundTag saved=((Entity)dragon).saveWithoutId(new CompoundTag());
        var restored=StarcrownDoll.MINGGULONG.get().create(test.getLevel());
        ((Entity)restored).load(saved);
        try {
            test.assertTrue(((LivingEntity)restored).deathTime==0&&"Fight_Die".equals(restored.getAnimationName()),"reload starts a full death clip");
            for(int i=1;i<MingGuLongEntity.DEATH_REMOVE_TICKS;i++) {
                ((Entity)restored).tick();test.assertTrue(!((Entity)restored).isRemoved(),"reloaded corpse retained until completion");
            }
            ((Entity)restored).tick();test.assertTrue(((Entity)restored).isRemoved(),"reloaded corpse removed after completion");
            test.succeed();
        } finally {((Entity)dragon).discard();((Entity)restored).discard();}
    }
}
