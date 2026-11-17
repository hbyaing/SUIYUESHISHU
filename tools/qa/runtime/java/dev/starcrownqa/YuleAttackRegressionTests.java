package dev.starcrownqa;

import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.gametest.*;

@GameTestHolder("yuleattackqa") @PrefixGameTestTemplate(false)
public final class YuleAttackRegressionTests {
    @GameTest(template="empty",templateNamespace="yuleattackqa",timeoutTicks=80)
    public static void headDollAllowsActualPlayerAttack(GameTestHelper h) {
        var p=EternalReturnTests.player(h);var cow=ChastityTests.spawn(EntityType.COW,p,1,0,0);
        try {
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.YULE_ITEM.get()));
            h.assertTrue(YuleHumility.active(p),"test has real Yule divinity equipped");
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8);
            float before=cow.getHealth();p.attack(cow);
            h.assertTrue(cow.getHealth()<before,"vanilla Player.attack must reach and damage the target while wearing Yule");
            cow.invulnerableTime=0;YuleTolerance.pacify(cow);before=cow.getHealth();p.attack(cow);
            h.assertTrue(cow.getHealth()<before,"manual attack still works against a pacified creature");
            cow.invulnerableTime=0;p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            before=cow.getHealth();p.attack(cow);
            h.assertTrue(cow.getHealth()<before,"ordinary attack must still work after removing the doll");h.succeed();
        } finally {cow.discard();EternalReturnTests.close(p);}
    }
    @GameTest(template="empty",templateNamespace="yuleattackqa",timeoutTicks=80)
    public static void divinitySlotAllowsMeleeAndArrow(GameTestHelper h) {
        var p=EternalReturnTests.player(h);var cow=ChastityTests.spawn(EntityType.COW,p,1,0,0);
        try {
            DivinityInventory.force(p,new ItemStack(StarcrownDoll.YULE_ITEM.get()));
            h.assertTrue(YuleHumility.active(p),"test has Yule in the real divinity slot");
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8);
            float before=cow.getHealth();p.attack(cow);
            h.assertTrue(cow.getHealth()<before,"divinity-slot Yule permits vanilla melee");
            cow.invulnerableTime=0;before=cow.getHealth();var arrow=new Arrow(p.serverLevel(),p);
            cow.hurt(p.damageSources().arrow(arrow,p),3);
            h.assertTrue(cow.getHealth()<before,"Yule-owned projectile damage is not globally suppressed");h.succeed();
        } finally {cow.discard();DivinityInventory.force(p,ItemStack.EMPTY);EternalReturnTests.close(p);}
    }
    @GameTest(template="empty",templateNamespace="yuleattackqa",timeoutTicks=80)
    public static void toleranceProtectionStillWorks(GameTestHelper h) {
        var p=EternalReturnTests.player(h);var cow=ChastityTests.spawn(EntityType.COW,p,1,0,0);
        try {
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.YULE_ITEM.get()));
            float before=p.getHealth();p.hurt(p.damageSources().mobAttack(cow),8);
            h.assertTrue(p.getHealth()==before,"incoming living attacker damage remains canceled");
            h.assertTrue(p.hasEffect(MobEffects.REGENERATION)&&cow.hasEffect(MobEffects.REGENERATION),"both participants still receive regeneration");
            h.assertTrue(p.hasEffect(MobEffects.ABSORPTION)&&cow.hasEffect(MobEffects.ABSORPTION),"both participants still receive absorption");
            YuleTolerance.pacify(cow);
            h.assertTrue(YuleTolerance.blockDamage(p,p.damageSources().mobAttack(cow)),"pacified creatures stay harmless");h.succeed();
        } finally {cow.discard();EternalReturnTests.close(p);}
    }
}
