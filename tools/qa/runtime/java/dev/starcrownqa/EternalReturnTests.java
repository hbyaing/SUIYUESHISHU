package dev.starcrownqa;

import dev.starcrown.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import top.theillusivec4.curios.api.CuriosApi;

@GameTestHolder("eternalreturnqa") @PrefixGameTestTemplate(false)
public final class EternalReturnTests {
    static ServerPlayer player(GameTestHelper h) {
        var p=ChastityTests.playerAt(h,h.absolutePos(new BlockPos(3,4,3)));
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class,p,0,"f_8921_");
        p.serverLevel().setBlockAndUpdate(p.blockPosition().below(),Blocks.STONE.defaultBlockState());
        return p;
    }
    static void equip(ServerPlayer p,ItemStack stack) {
        var handler=CuriosApi.getCuriosInventory(p).resolve().orElseThrow();
        handler.getStacksHandler("head").orElseThrow().getStacks().setStackInSlot(0,stack);
        EternalReturn.updateAttributes(p);
    }
    static ItemStack equip(ServerPlayer p) {var stack=new ItemStack(StarcrownDoll.ETERNAL_RETURN.get());equip(p,stack);return stack;}
    static void close(ServerPlayer p) {equip(p,ItemStack.EMPTY);ChastityTests.close(p);}
    static void check(GameTestHelper h,boolean ok,String message) {h.assertTrue(ok,message);}
    static void near(GameTestHelper h,double actual,double expected,String message) {check(h,Math.abs(actual-expected)<.001,message+": "+actual+" != "+expected);}
    static net.minecraft.world.entity.monster.Zombie enemy(ServerPlayer p,int x) {
        var mob=EntityType.ZOMBIE.create(p.serverLevel());mob.setNoAi(true);mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);mob.getAttribute(Attributes.ARMOR).setBaseValue(0);mob.setHealth(1000);
        mob.moveTo(p.getX()+x,p.getY(),p.getZ());p.serverLevel().addFreshEntity(mob);return mob;
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void realInventoryBurningAndConservation(GameTestHelper h) {
        var p=player(h);try {
            var halo=new ItemStack(StarcrownDoll.ETERNAL_RETURN.get());
            p.getInventory().setItem(9,halo);p.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND,3));
            p.inventoryMenu.clicked(9,1,ClickType.PICKUP,p);
            check(h,p.inventoryMenu.getCarried().isEmpty()&&EternalReturn.kindling(halo)==3000,"real right-click burns cursor stack exactly once");
            p.inventoryMenu.clicked(9,1,ClickType.PICKUP,p);
            check(h,EternalReturn.kindling(p.inventoryMenu.getCarried())==3000,"empty cursor retrieves halo normally");
            p.getInventory().setItem(10,new ItemStack(Items.IRON_INGOT,2));p.inventoryMenu.clicked(10,1,ClickType.PICKUP,p);
            check(h,p.getInventory().getItem(10).isEmpty()&&EternalReturn.kindling(p.inventoryMenu.getCarried())==3200,"cursor halo burns target stack");
            h.succeed();
        }finally{close(p);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void capAwakeningAndDurableFuel(GameTestHelper h) {
        var p=player(h);try {
            var halo=equip(p);check(h,EternalReturn.kindling(halo)==0,"new ornament starts at zero");
            check(h,KindlingValues.value(new ItemStack(StarcrownDoll.ITEM.get()))==10000,"standard accessory worth 10000");
            EternalReturn.setKindling(halo,EternalReturn.CAP-7);var diamonds=new ItemStack(Items.DIAMOND,64);
            check(h,EternalReturn.burn(p,halo,diamonds)==7&&diamonds.getCount()==63,"cap consumes only required unit");
            check(h,EternalReturn.awakened(halo),"first full fuel permanently awakens");
            check(h,EternalReturn.burn(p,halo,diamonds)==0&&diamonds.getCount()==63,"full halo does not burn extra items");
            var restored=ItemStack.of(halo.save(new net.minecraft.nbt.CompoundTag()));EternalReturn.setKindling(restored,0);
            check(h,EternalReturn.awakened(restored)&&EternalReturn.kindling(halo)==EternalReturn.CAP,"fuel/awakening survive NBT; copies independent");
            h.succeed();
        }finally{close(p);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void headOnlyAttributesAndNonlethalTiers(GameTestHelper h) {
        var p=player(h);try {
            var halo=equip(p);check(h,EternalReturn.equipped(p)==halo,"native Curios head slot is authoritative");
            var item=(EternalReturnItem)halo.getItem();
            check(h,!item.canEquip(new top.theillusivec4.curios.api.SlotContext("starcrowndoll_as_written",p,0,false,true),halo),"book slot rejected");
            near(h,p.getMaxHealth(),28,"base health bonus");p.setHealth(14);
            EternalReturn.setKindling(halo,EternalReturn.CAP);EternalReturn.updateAttributes(p);
            near(h,p.getMaxHealth(),21,"highest penalty applied once, not accumulated");near(h,p.getHealth(),10.5,"health ratio preserved");
            p.setHealth(.01f);EternalReturn.setKindling(halo,5_000_000);EternalReturn.updateAttributes(p);
            check(h,p.isAlive()&&p.getHealth()>0,"tier changes cannot kill");
            equip(p,ItemStack.EMPTY);near(h,p.getMaxHealth(),20,"unequip removes own modifier");h.succeed();
        }finally{close(p);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void combatSeedsActiveStorageAndFinisher(GameTestHelper h) {
        var p=player(h);var enemy=enemy(p,2);try {
            var halo=equip(p);EternalReturn.setKindling(halo,20000);
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
            var d=EternalReturn.data(p);
            enemy.hurt(p.damageSources().playerAttack(p),10);
            near(h,enemy.getHealth(),988,"base final damage bonus");check(h,d.getInt("seeds")==1,"direct hit generates seed");
            enemy.invulnerableTime=0;enemy.hurt(p.damageSources().playerAttack(p),10);
            check(h,d.getInt("seeds")==1,"seed generation throttled");
            EternalReturn.action(p);check(h,!EternalReturn.active(p),"requires all twelve seeds");
            d.putInt("seeds",12);EternalReturn.action(p);
            check(h,EternalReturn.active(p)&&d.getBoolean("enhanced")&&EternalReturn.kindling(halo)==10000,"enhancement burns exactly 10000");
            check(h,d.getLong("active_until")-EternalReturn.now(p)==400,"enhanced active lasts twenty seconds");
            near(h,d.getFloat("shield"),p.getMaxHealth()*.4,"active shield");
            float before=enemy.getHealth();enemy.invulnerableTime=0;enemy.hurt(p.damageSources().playerAttack(p),10);
            near(h,before-enemy.getHealth(),20,"additive base and active damage");near(h,d.getFloat("stored"),7,"enhanced stores thirty five percent");
            before=enemy.getHealth();EternalReturn.finish(p);
            near(h,before-enemy.getHealth(),(80+7)*1.5,"solitary target receives entire enhanced finisher without double bonus");
            check(h,EternalReturn.fatigue(p)&&d.getFloat("shield")==0,"finisher clears shield and applies fatigue");
            var fuel=new ItemStack(Items.DIAMOND);EternalReturn.burn(p,halo,fuel);
            check(h,EternalReturn.kindling(halo)==10500,"fatigue halves fuel gain");
            d.putInt("seeds",12);EternalReturn.action(p);check(h,!EternalReturn.active(p),"ninety second cooldown cannot be bypassed");h.succeed();
        }finally{enemy.discard();close(p);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void splitFinisherAndStorageCap(GameTestHelper h) {
        var p=player(h);var main=enemy(p,2);var extra=enemy(p,4);try {
            equip(p);p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);var d=EternalReturn.data(p);
            d.putInt("seeds",12);EternalReturn.action(p);check(h,!d.getBoolean("enhanced"),"zero fuel gives ordinary sixteen second active");
            check(h,d.getLong("active_until")-EternalReturn.now(p)==320,"base duration");
            d.putUUID("target",main.getUUID());d.putFloat("stored",400);float a=main.getHealth(),b=extra.getHealth();EternalReturn.finish(p);
            near(h,a-main.getHealth(),480*.7,"main takes seventy percent");near(h,b-extra.getHealth(),480*.3,"nearby target takes thirty percent");h.succeed();
        }finally{main.discard();extra.discard();close(p);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void lethalRewindPrecedesTotemAndDebuffsPersist(GameTestHelper h) {
        var p=player(h);try {
            var halo=equip(p);EternalReturn.setKindling(halo,EternalReturn.CAP);EternalReturn.updateAttributes(p);
            var d=EternalReturn.data(p);d.putLong("combat_until",EternalReturn.now(p)+200);d.putInt("memory",29);
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,800));
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,800,1));p.setAbsorptionAmount(4);
            p.hurt(p.damageSources().generic(),10000);
            check(h,p.isAlive()&&p.getHealth()>=p.getMaxHealth()*.5,"actual fatal hit rewinds");
            check(h,p.getOffhandItem().is(Items.TOTEM_OF_UNDYING),"rewind runs before vanilla and preserves totem");
            check(h,d.getInt("memory")==30&&d.getBoolean("memory_finisher")&&d.getBoolean("free_enhanced"),"memory cap and awakened next enhancement: "+d+" equipped="+EternalReturn.equipped(p).save(new net.minecraft.nbt.CompoundTag()));
            check(h,p.getAbsorptionAmount()==0&&p.getEffect(MobEffects.MOVEMENT_SPEED).getDuration()==400,"rewind clears temporary shield and halves positive effect duration");
            check(h,EternalReturn.noNaturalHealing(p)&&EternalReturn.causalLocked(p),"rewind healing and causal penalties");
            p.addEffect(new MobEffectInstance(MobEffects.POISON,100));check(h,p.getEffect(MobEffects.POISON).getDuration()==104,"scar extends negative effects once");
            p.forceAddEffect(new MobEffectInstance(MobEffects.WITHER,100),null);check(h,p.getEffect(MobEffects.WITHER).getDuration()==104,"forced negative effect also extended once");
            float hp=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().generic(),10);near(h,p.getHealth(),hp,"two second rewind immunity");
            long ready=d.getLong("rewind_ready");equip(p,ItemStack.EMPTY);equip(p,halo);
            check(h,d.getLong("rewind_ready")==ready&&d.getInt("scars")==1,"unequip does not reset cooldown/scars");
            check(h,!EternalReturn.rewind(p,p.damageSources().generic()),"cooldown rejects a second rewind");
            var unconsumedTotem=p.getOffhandItem();
            d.putLong("immune_until",0);p.invulnerableTime=0;p.hurt(p.damageSources().generic(),10000);
            check(h,!p.isAlive()&&unconsumedTotem.getCount()==1,"causal lock blocks totem consumption and rescue chaining; death drops the preserved totem");h.succeed();
        }finally{close(p);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void incomingReductionShieldAndScars(GameTestHelper h) {
        var p=player(h);try {
            var halo=equip(p);p.setHealth(p.getMaxHealth());p.hurt(p.damageSources().generic(),5);
            near(h,p.getHealth(),p.getMaxHealth()-4,"base incoming reduction is twenty percent");
            EternalReturn.setKindling(halo,EternalReturn.CAP);EternalReturn.updateAttributes(p);p.setHealth(p.getMaxHealth());
            var d=EternalReturn.data(p);d.putInt("scars",2);d.putLong("scars_until",EternalReturn.now(p)+12000);
            p.invulnerableTime=0;p.hurt(p.damageSources().generic(),5);
            near(h,p.getHealth(),p.getMaxHealth()-5*.8*1.21,"highest fuel and memory-scar incoming penalties add");
            d.putInt("seeds",12);EternalReturn.action(p);float hp=p.getHealth(),shield=d.getFloat("shield");
            p.invulnerableTime=0;p.hurt(p.damageSources().generic(),2);
            near(h,p.getHealth(),hp,"active shield absorbs real final damage");
            near(h,d.getFloat("shield"),shield-2*.8*1.16,"consuming fuel drops from cap tier immediately");h.succeed();
        }finally{close(p);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void healingReductionAndNaturalHealingOnly(GameTestHelper h) {
        var p=player(h);try {
            var halo=equip(p);EternalReturn.setKindling(halo,5_000_000);EternalReturn.updateAttributes(p);p.setHealth(5);
            p.heal(10);near(h,p.getHealth(),14,"ten percent healing penalty");
            var d=EternalReturn.data(p);d.putLong("fatigue_until",EternalReturn.now(p)+400);
            var food=p.getFoodData();food.setFoodLevel(20);food.setSaturation(20);p.setHealth(5);
            for(int i=0;i<20;i++)food.tick(p);near(h,p.getHealth(),5,"fatigue blocks actual food regeneration");
            p.heal(2);near(h,p.getHealth(),6.8,"potion/direct healing remains allowed during fatigue");h.succeed();
        }finally{close(p);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=60)
    public static void playerCloneRetainsCostsNotActiveShield(GameTestHelper h) {
        var p=player(h);var next=player(h);try {
            equip(p);var d=EternalReturn.data(p);d.putLong("rewind_ready",123456);d.putLong("active_ready",123000);
            d.putInt("memory",20);d.putInt("scars",3);d.putLong("scars_until",200000);d.putLong("active_until",999999);d.putFloat("shield",100);
            EternalReturn.clone(new PlayerEvent.Clone(next,p,true));var restored=EternalReturn.data(next);
            check(h,restored.getLong("rewind_ready")==123456&&restored.getLong("active_ready")==123000&&restored.getInt("memory")==20
                    &&restored.getInt("scars")==3,"death clone preserves permanent progression and cooldowns");
            check(h,restored.getLong("active_until")==0&&restored.getFloat("shield")==0,"death clone cannot duplicate active or shield");
            h.succeed();
        }finally{close(p);close(next);}
    }
    @GameTest(template="empty",templateNamespace="eternalreturnqa",timeoutTicks=190)
    public static void sixSecondSafeHistoryAndRewindFinishesFirst(GameTestHelper h) {
        var p=player(h);var enemy=enemy(p,2);var halo=equip(p);var origin=p.position();
        p.setHealth(20);EternalReturn.record(p);p.setNoGravity(true);
        h.runAfterDelay(125,()->{try {
            p.teleportTo(p.getX()+5,p.getY(),p.getZ());p.serverLevel().setBlockAndUpdate(p.blockPosition().below(),Blocks.STONE.defaultBlockState());
            var d=EternalReturn.data(p);d.putInt("seeds",12);EternalReturn.action(p);d.putUUID("target",enemy.getUUID());
            float before=enemy.getHealth();p.hurt(p.damageSources().generic(),10000);
            check(h,p.isAlive()&&p.position().distanceTo(origin)<.01,"rewind returns to six-second safe history");
            near(h,p.getHealth(),20,"recorded health restored above minimum");
            check(h,enemy.getHealth()<before&&!EternalReturn.active(p)&&EternalReturn.fatigue(p),"lethal rewind resolves active finisher before teleport");h.succeed();
        }finally{enemy.discard();close(p);}});
    }
}
