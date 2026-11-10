package dev.starcrown;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.shapes.*;
@Mod(StarcrownDoll.ID)
public class StarcrownDoll {
 public static final String ID="starcrowndoll";
 static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,ID);
 public static final RegistryObject<net.minecraft.sounds.SoundEvent> MUSIC=SOUNDS.register("doll_music",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(ID,"doll_music")));
 public static final RegistryObject<net.minecraft.sounds.SoundEvent> YULE_MUSIC=SOUNDS.register("yule_music",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(ID,"yule_music")));
 public static java.util.function.BiConsumer<DollEntity,Boolean> musicListener=(doll,playing)->{};
 static java.util.function.Supplier<Component> selfDollName=()->Component.translatable("block.starcrowndoll.self_doll");
 static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
 static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,ID);
 static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
 static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
 public static final RegistryObject<Block> DOLL=BLOCKS.register("doll",()->new DollBlock());
 public static final RegistryObject<Item> ITEM=ITEMS.register("doll",()->new WearableDoll());
 public static final RegistryObject<Block> TWILIGHT_DOLL=BLOCKS.register("twilight_doll",DollBlock::new);
 public static final RegistryObject<Item> TWILIGHT_ITEM=ITEMS.register("twilight_doll",()->new WearableDoll(TWILIGHT_DOLL.get()));
 public static final RegistryObject<Block> YULE_DOLL=BLOCKS.register("yule_doll",()->new DollBlock());
 public static final RegistryObject<Item> YULE_ITEM=ITEMS.register("yule_doll",()->new WearableDoll(YULE_DOLL.get()));
 public static final RegistryObject<Block> CALM_DOLL=BLOCKS.register("calm_doll",DollBlock::new);
 public static final RegistryObject<Item> CALM_ITEM=ITEMS.register("calm_doll",()->new WearableDoll(CALM_DOLL.get()));
 public static final RegistryObject<Item> WHITE_RICE=ITEMS.register("white_rice",WhiteRiceItem::new);
 public static final RegistryObject<Item> FISHING_GOD_FISH=ITEMS.register("fishing_god_fish",()->new Item(new Item.Properties()));
 public static final RegistryObject<Block> SELF_DOLL=BLOCKS.register("self_doll",DollBlock::new);
 public static final RegistryObject<Item> SELF_ITEM=ITEMS.register("self_doll",SelfWearableDoll::new);
 public static boolean isDoll(ItemStack stack){return DollAuthority.doll(stack);}
 public static boolean isChengran(ItemStack stack){return stack.is(ITEM.get())||stack.is(TWILIGHT_ITEM.get());}
 public static boolean isYule(ItemStack stack){return stack.is(YULE_ITEM.get())||stack.is(CALM_ITEM.get());}
 public static boolean isYule(BlockState state){return state.is(YULE_DOLL.get())||state.is(CALM_DOLL.get());}
 public static BlockState dollState(ItemStack stack){return (stack.is(CALM_ITEM.get())?CALM_DOLL:stack.is(YULE_ITEM.get())?YULE_DOLL:stack.is(TWILIGHT_ITEM.get())?TWILIGHT_DOLL:DOLL).get().defaultBlockState();}
 public static final RegistryObject<CreativeModeTab> TAB=TABS.register("yishen_daiguan",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.starcrowndoll")).icon(()->new ItemStack(ITEM.get())).displayItems((parameters,output)->{output.accept(ITEM.get());output.accept(TWILIGHT_ITEM.get());output.accept(YULE_ITEM.get());output.accept(CALM_ITEM.get());output.accept(SELF_ITEM.get());output.accept(WHITE_RICE.get());output.accept(FISHING_GOD_FISH.get());}).build());
 public static final RegistryObject<BlockEntityType<DollEntity>> TYPE=TYPES.register("doll",()->BlockEntityType.Builder.of(DollEntity::new,DOLL.get(),TWILIGHT_DOLL.get(),YULE_DOLL.get(),CALM_DOLL.get(),SELF_DOLL.get()).build(null));
 static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES=DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES,ID);
 public static final RegistryObject<net.minecraft.core.particles.SimpleParticleType> STAR=PARTICLES.register("star",()->new net.minecraft.core.particles.SimpleParticleType(false));
 public StarcrownDoll(){SevenNetwork.init();IEventBus bus=FMLJavaModLoadingContext.get().getModEventBus();TABS.register(bus);SOUNDS.register(bus);PARTICLES.register(bus);BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);DivineEffects.EFFECTS.register(bus);}
 public static class WearableDoll extends BlockItem {
  @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){super.appendHoverText(stack,level,lines,flag);if(stack.is(SELF_ITEM.get()))return;String theme=stack.is(CALM_ITEM.get())?"calm.":stack.is(YULE_ITEM.get())?"yule.":stack.is(TWILIGHT_ITEM.get())?"twilight.":"";lines.add(net.minecraft.network.chat.Component.translatable("tooltip.starcrowndoll."+theme+"divinity").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));if(!isYule(stack))lines.add(net.minecraft.network.chat.Component.translatable("tooltip.starcrowndoll.lore").withStyle(net.minecraft.ChatFormatting.WHITE));}
  @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> c){c.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions(){public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return DollItemRenderer.instance();}});}
  WearableDoll(){this(DOLL.get());}
  WearableDoll(Block block){super(block,new Item.Properties().stacksTo(1));}
  @Override public net.minecraft.world.entity.EquipmentSlot getEquipmentSlot(ItemStack s){return net.minecraft.world.entity.EquipmentSlot.HEAD;}
  @Override public boolean canEquip(ItemStack s,net.minecraft.world.entity.EquipmentSlot slot,net.minecraft.world.entity.Entity e){return slot==net.minecraft.world.entity.EquipmentSlot.HEAD;}
  @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level l,net.minecraft.world.entity.player.Player p,net.minecraft.world.InteractionHand hand){
   ItemStack held=p.getItemInHand(hand);var slot=net.minecraft.world.entity.EquipmentSlot.HEAD;
   if(!p.getItemBySlot(slot).isEmpty())return net.minecraft.world.InteractionResultHolder.fail(held);
   p.setItemSlot(slot,held.copy());held.shrink(1);return net.minecraft.world.InteractionResultHolder.sidedSuccess(held,l.isClientSide);
  }
 }
 private static final class SelfWearableDoll extends WearableDoll {
  SelfWearableDoll(){super(SELF_DOLL.get());}
  @Override public Component getName(ItemStack stack){return selfDollName.get();}
  @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<Component> lines,TooltipFlag flag){
   super.appendHoverText(stack,level,lines,flag);
   lines.add(Component.translatable("tooltip.starcrowndoll.self.divinity").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
   lines.add(Component.translatable("tooltip.starcrowndoll.self.lore"));
  }
 }
 public static class DollEntity extends BlockEntity {
  private boolean playing;
  private GameProfile skinOwner;
  public boolean isPlaying(){return playing;}
  public GameProfile skinOwner(){return skinOwner;}
  void setSkinOwner(GameProfile playerProfile){
   skinOwner=new GameProfile(playerProfile.getId(),playerProfile.getName());
   skinOwner.getProperties().putAll("textures",playerProfile.getProperties().get("textures"));
   setChanged();
   level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
  }
  public void toggleMusic(){
   if(level==null||level.isClientSide)return;
   playing=!playing;setChanged();DollDance.sourceChanged(this);
   if(!playing)DollDance.refresh(level);
   level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
  }
  @Override protected void saveAdditional(CompoundTag tag){
   super.saveAdditional(tag);
   tag.putBoolean("Playing",playing);
   if(skinOwner!=null)tag.put("SkinOwner",NbtUtils.writeGameProfile(new CompoundTag(),skinOwner));
  }
  @Override public void load(CompoundTag tag){
   super.load(tag);
   boolean self=getBlockState().is(SELF_DOLL.get());
   skinOwner=self&&tag.contains("SkinOwner",Tag.TAG_COMPOUND)?NbtUtils.readGameProfile(tag.getCompound("SkinOwner")):null;
   playing=!self&&tag.getBoolean("Playing");
   if(level!=null)DollDance.sourceChanged(this);
  }
  @Override public net.minecraft.nbt.CompoundTag getUpdateTag(){return saveWithoutMetadata();}
  @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
  @Override public void onLoad(){super.onLoad();DollDance.sourceChanged(this);}
  @Override public void setRemoved(){super.setRemoved();DollDance.sourceChanged(this);if(level!=null&&level.isClientSide)musicListener.accept(this,false);}
public DollEntity(BlockPos p,BlockState s){super(TYPE.get(),p,s);}
 @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(.1,.5,.1);}}
 public static void emit(Level l,double x,double y,double z,double radius){
  double a=l.random.nextDouble()*Math.PI*2;
  l.addParticle(STAR.get(),x+Math.cos(a)*radius,y+l.random.nextDouble()*.09,z+Math.sin(a)*radius,Math.cos(a)*.006,-.012,Math.sin(a)*.006);
 }
 public static class DollBlock extends HorizontalDirectionalBlock implements EntityBlock {
  DollBlock(){super(Properties.of().strength(.5f).sound(SoundType.WOOL).noOcclusion());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
  protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING);}
  public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
  @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
   if(!level.isClientSide&&state.is(SELF_DOLL.get())&&placer instanceof Player player&&level.getBlockEntity(pos) instanceof DollEntity doll){
    doll.setSkinOwner(player.getGameProfile());
   }
  }
  public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
  public BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
  public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(1,0,0,15,16,16);}
  @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
   if(type!=TYPE.get()||state.is(SELF_DOLL.get()))return null;
   return (l,p,s,be)->{if(!l.isClientSide)return;DollDance.sourceChanged((DollEntity)be);if(l.getGameTime()%20==0)musicListener.accept((DollEntity)be,((DollEntity)be).isPlaying());if(!isYule(s)&&l.getGameTime()%4==0)emit(l,p.getX()+.5-s.getValue(FACING).getStepX()*.125,p.getY()+1.27,p.getZ()+.5-s.getValue(FACING).getStepZ()*.125,.4);};
  }
  @Override public net.minecraft.world.InteractionResult use(BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
   if(state.is(SELF_DOLL.get()))return net.minecraft.world.InteractionResult.PASS;
   if(!level.isClientSide&&level.getBlockEntity(pos) instanceof DollEntity doll)doll.toggleMusic();
   return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
  }
  @Override public RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
  public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new DollEntity(p,s);}
 }
}
