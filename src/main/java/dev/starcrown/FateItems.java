package dev.starcrown;

import dev.starcrown.treasury.TreasuryItems;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Server-authoritative eight-second retrieval. There is no attack or terrain effect. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class FateItems {
    public static final int DURATION=160, COOLDOWN=200;
    private static final Map<UUID,Cast> ACTIVE=new HashMap<>();
    public record Cast(ServerPlayer player,ItemStack key,InteractionHand hand,ResourceLocation dimension,
                       long start,Vec3 origin,float yaw) {}
    private static boolean hasEa(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(StarcrownDoll.EA.get())) return true;
        }
        return false;
    }
    public static final class Key extends Item {
        public Key(){super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));}
        @Override public int getUseDuration(ItemStack stack){return DURATION;}
        @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            ItemStack stack=player.getItemInHand(hand);
            if(hasEa(player)||player.getCooldowns().isOnCooldown(this)||!player.isAlive())return InteractionResultHolder.fail(stack);
            player.startUsingItem(hand);
            if(player instanceof ServerPlayer server){
                cancel(server);
                Cast cast=new Cast(server,stack,hand,level.dimension().location(),level.getGameTime(),player.position(),player.getYRot());
                ACTIVE.put(server.getUUID(),cast);send(cast,0);
            }
            return InteractionResultHolder.consume(stack);
        }
        @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
            if(entity instanceof ServerPlayer player)cancel(player);
        }
        @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
            if(!(entity instanceof ServerPlayer player))return stack;
            Cast cast=ACTIVE.get(player.getUUID());
            if(cast==null||cast.key()!=stack||cast.hand()!=player.getUsedItemHand()
                    ||!cast.dimension().equals(level.dimension().location())||!player.isAlive()
                    ||level.getGameTime()-cast.start()<DURATION-1||hasEa(player)){cancel(player);return stack;}
            ACTIVE.remove(player.getUUID());send(cast,2);
            // Vanilla replaces the active stack with the return value, including in creative mode.
            // Do not copy or drop the key: successful retrieval consumes that specific key.
            player.getCooldowns().addCooldown(this,COOLDOWN);
            EnkiduCompanion.summon(player);
            return new ItemStack(StarcrownDoll.EA.get());
        }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> c){client(c);}
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag f){
            lines.add(Component.translatable("tooltip.starcrowndoll.bab_ilu").withStyle(ChatFormatting.GOLD));
        }
    }
    public static final class Ea extends SwordItem {
        public Ea(){super(Tiers.DIAMOND,6,-2.8f,new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));}
        @Override public int getUseDuration(ItemStack stack){return EaSkill.selected(stack) == EaSkill.ENUMA ? 72000 : Integer.MAX_VALUE;}
        @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            if(!player.isShiftKeyDown()){
                ItemStack stack=player.getItemInHand(hand);
                switch (EaSkill.selected(stack)) {
                    case GATE_NORMAL: return TreasuryItems.useGate(level, player, hand, false);
                    case GATE_FULL: return TreasuryItems.useGate(level, player, hand, true);
                    case CHAINS: return TreasuryItems.useChains(level, player, hand);
                }
                if(player instanceof ServerPlayer server&&!dev.eavfx.EaServer.begin(server,hand))return InteractionResultHolder.fail(stack);
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            if(!(player instanceof ServerPlayer server))return InteractionResultHolder.success(player.getItemInHand(hand));
            dev.eavfx.EaServer.interrupt(server);
            EnkiduCompanion.dismiss(server);
            return InteractionResultHolder.consume(new ItemStack(StarcrownDoll.BAB_ILU.get()));
        }
        @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null || player.isShiftKeyDown() || EaSkill.selected(stack) == EaSkill.ENUMA) return InteractionResult.PASS;
            return use(context.getLevel(), player, context.getHand()).getResult();
        }
        @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
            if(entity instanceof ServerPlayer server && EaSkill.selected(stack) == EaSkill.ENUMA)dev.eavfx.EaServer.interrupt(server);
        }
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag f){
            EaSkill skill = EaSkill.selected(s);
            lines.add(Component.translatable("message.starcrowndoll.ea.skill", skill.display()).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("tooltip.starcrowndoll.ea.switch", Component.keybind("key.starcrowndoll.ea_cycle_skill")));
            switch (skill) {
                case ENUMA -> lines.add(Component.translatable("tooltip.starcrowndoll.ea.enuma").withStyle(ChatFormatting.RED));
                case GATE_NORMAL, GATE_FULL -> lines.add(Component.translatable("tooltip.starcrowndoll.ea.gates",
                        skill == EaSkill.GATE_FULL ? 100 : 20));
                case CHAINS -> lines.add(Component.translatable("tooltip.starcrowndoll.chains_of_heaven"));
            }
            lines.add(Component.translatable("tooltip.starcrowndoll.ea.retract").withStyle(ChatFormatting.GOLD));
        }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> c){client(c);}
    }
    private static void client(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> c){
        c.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return FateItemRenderer.instance();}
            @Override public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity entity,InteractionHand hand,ItemStack stack){
                if(!entity.isUsingItem()||entity.getUsedItemHand()!=hand)return null;
                if(stack.is(StarcrownDoll.EA.get()))return EaSkill.selected(stack) == EaSkill.ENUMA ? FateClient.EA_CHARGE_POSE : null;
                return stack.is(StarcrownDoll.BAB_ILU.get())?FateClient.RETRIEVAL_POSE:null;
            }
            @Override public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.player.LocalPlayer player,
                    net.minecraft.world.entity.HumanoidArm arm,ItemStack stack,float partial,float equip,float swing){
                return FateClient.handPose(pose,player,arm,stack,partial);
            }
        });
    }
    private static void send(Cast cast,int mode){
        SevenNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(cast::player),FateNetwork.State.of(cast,mode));
    }
    private static void cancel(ServerPlayer player){Cast cast=ACTIVE.remove(player.getUUID());if(cast!=null)send(cast,1);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        Cast cast=ACTIVE.get(p.getUUID());if(cast==null)return;
        if(!p.isAlive()||!p.isUsingItem()||p.getUseItem()!=cast.key()||p.getUsedItemHand()!=cast.hand()
                ||!p.level().dimension().location().equals(cast.dimension())||p.level().getGameTime()-cast.start()>DURATION+2)cancel(p);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){
        if(e.getEntity() instanceof ServerPlayer viewer&&e.getTarget() instanceof ServerPlayer target){
            Cast cast=ACTIVE.get(target.getUUID());if(cast!=null)SevenNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),FateNetwork.State.of(cast,0));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
}
