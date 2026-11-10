package dev.starcrownqa;

import dev.starcrown.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class DivinitySlotsQa {
    static int ticks;static boolean finished;static CompletableFuture<Void> pending;static final StringBuilder proof=new StringBuilder();
    static boolean reload(){return Files.exists(Path.of("verify-divinity-reload"));}
    static ItemStack doll(){var stack=new ItemStack(StarcrownDoll.CALM_ITEM.get());stack.getOrCreateTag().putString("proof","divinity-persistence");return stack;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(finished||event.phase!=TickEvent.Phase.END||!Files.exists(Path.of("enable-divinity-qa")))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getSingleplayerServer()==null)return;
        try{
            mc.options.pauseOnLostFocus=false;
            switch(++ticks){
                case 30 -> server(mc,p->{
                    require(p.inventoryMenu.slots.size()==(CuriosEquipment.loaded()?46:49),"only active backend adds slots");
                    if(reload()){
                        boolean empty=Files.exists(Path.of("expect-empty-divinity-save"));
                        require(empty?DivinityInventory.stack(p).isEmpty():ItemStack.isSameItemSameTags(DivinityInventory.stack(p),doll()),"saved/migrated divinity item NBT or deliberately empty slot");
                        require(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),"ordinary helmet saved independently");
                        require(p.getInventory().countItem(StarcrownDoll.CALM_ITEM.get())==0,"backend migration never duplicates equipped doll into inventory");
                        var books=CuriosEquipment.loaded()?CuriosEquipment.slots(p,CuriosEquipment.BOOK):AsWrittenInventory.get(p);
                        for(int i=0;i<2;i++)require(empty?books.getStackInSlot(i).isEmpty():books.getStackInSlot(i).getCount()==1,"book slot migration preserves exact quantity");
                        pass(empty?"reinstall keeps deliberately cleared slots empty; stale native Curios snapshot does not duplicate items":"reloaded/migrated divinity NBT, book quantities and independent ordinary helmet; no duplicated doll");
                    }else{
                        DivineAnchor.unlock(p);DivinityInventory.force(p,ItemStack.EMPTY);p.getInventory().clearContent();
                        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.serverLevel().setDayTime(6000);
                        var pos=new net.minecraft.core.BlockPos(0,150,0);
                        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)p.serverLevel().setBlockAndUpdate(pos.offset(x,0,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        p.teleportTo(p.serverLevel(),.5,151,.5,180,0);
                    }
                    pass(CuriosEquipment.loaded()?"native Curios owns slots; standalone inventory extension disabled":"vanilla inventory has original 46 + three side slots");
                });
                case 60 -> {done();open(mc);}
                case 95 -> {
                    screen(mc);
                    require(DivinityInventory.ICON.equals(mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(DivinityInventory.ICON).contents().name()),"white mold icon atlas");
                    var slots=((AbstractContainerScreen<?>)mc.screen).getMenu().slots;
                    long gods=slots.stream().filter(s->s.getNoItemIcon()!=null&&DivinityInventory.ICON.equals(s.getNoItemIcon().getSecond())).count();
                    long books=slots.stream().filter(s->s.getNoItemIcon()!=null&&AsWrittenInventory.ICON.equals(s.getNoItemIcon().getSecond())).count();
                    require(gods==1&&books==2,"exactly one divinity and two book slots in real UI");
                    require(CuriosEquipment.loaded()==mc.screen.children().stream().noneMatch(AsWrittenClient.ToggleButton.class::isInstance),"standalone toggle appears only without Curios");
                    pass("real inventory UI: white-mold divinity slot and two book slots, no extra screen");
                    if(reload()&&Files.exists(Path.of("clear-divinity-save")))server(mc,p->{
                        DivinityInventory.force(p,ItemStack.EMPTY);
                        var clearedBooks=CuriosEquipment.loaded()?CuriosEquipment.slots(p,CuriosEquipment.BOOK):AsWrittenInventory.get(p);
                        for(int i=0;i<2;i++)clearedBooks.setStackInSlot(i,ItemStack.EMPTY);
                        pass("deliberately cleared standalone fixture for stale Curios snapshot reinstall test");
                    });
                }
                case 105 -> shot(mc,reload()?"divinity-reloaded.png":"divinity-empty.png");
                case 115 -> {if(reload()){done();finish(mc);return;}if(!CuriosEquipment.loaded()){toggle(mc);require(!AsWrittenClient.expanded,"collapse flag");}}
                case 125 -> {if(!CuriosEquipment.loaded())shot(mc,"divinity-hidden.png");}
                case 135 -> {
                    if(!CuriosEquipment.loaded()){var previous=mc.screen;toggle(mc);require(AsWrittenClient.expanded&&previous==mc.screen,"toggle preserves same inventory screen");pass("small collapse/expand button, same backpack screen");}
                    server(mc,p->{p.getInventory().setItem(9,doll());p.getInventory().setItem(10,new ItemStack(StarcrownDoll.SELF_ITEM.get()));p.getInventory().setItem(11,new ItemStack(Items.PAPER,3));});
                }
                case 165 -> {done();click(mc,inventorySlot(mc,9),CuriosEquipment.loaded()?ClickType.PICKUP:ClickType.QUICK_MOVE);}
                case 180 -> {if(CuriosEquipment.loaded())click(mc,godSlot(mc),ClickType.PICKUP);}
                case 195 -> {
                    require(ItemStack.isSameItemSameTags(DivinityInventory.stack(mc.player),doll()),"real client divinity sync");require(CalmYule.active(mc.player),"client ability predicate");
                    server(mc,p->{require(CalmYule.active(p)&&p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"server ability active from divinity");});
                    click(mc,inventorySlot(mc,10),ClickType.PICKUP);
                }
                case 215 -> {done();click(mc,inventorySlot(mc,39),ClickType.PICKUP);}
                case 235 -> {
                    require(mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()&&mc.player.containerMenu.getCarried().is(StarcrownDoll.SELF_ITEM.get()),"real helmet click rejects second divinity; cursor item preserved");
                    click(mc,inventorySlot(mc,10),ClickType.PICKUP);pass("network clicks equip divinity and reject a second doll in helmet slot without consuming it");
                    click(mc,inventorySlot(mc,11),CuriosEquipment.loaded()?ClickType.PICKUP:ClickType.QUICK_MOVE);
                }
                case 245 -> {
                    if(CuriosEquipment.loaded())for(var slot:((AbstractContainerScreen<?>)mc.screen).getMenu().slots)
                        if(slot.getNoItemIcon()!=null&&AsWrittenInventory.ICON.equals(slot.getNoItemIcon().getSecond()))mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot.index,1,ClickType.PICKUP,mc.player);
                }
                case 250 -> {if(CuriosEquipment.loaded())click(mc,inventorySlot(mc,11),ClickType.PICKUP);}
                case 260 -> {
                    server(mc,p->{
                        var books=CuriosEquipment.loaded()?CuriosEquipment.slots(p,CuriosEquipment.BOOK):AsWrittenInventory.get(p);
                        require(books.getStackInSlot(0).getCount()==1&&books.getStackInSlot(1).getCount()==1&&p.getInventory().countItem(Items.PAPER)==1,"book slot transfer still works: "+books.getStackInSlot(0)+" / "+books.getStackInSlot(1)+" / inventory="+p.getInventory().countItem(Items.PAPER));
                        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));require(CalmYule.active(p),"ordinary helmet coexists with divinity");
                        validateAllDolls(p);
                    });
                }
                case 285 -> {done();shot(mc,"divinity-equipped.png");pass("ordinary diamond helmet coexists; same head-top doll renderer");}
                case 300 -> {
                    click(mc,godSlot(mc),ClickType.QUICK_MOVE);
                }
                case 325 -> {
                    require(DivinityInventory.stack(mc.player).isEmpty()&&!CalmYule.active(mc.player),"real unequip removes client ability");
                    server(mc,p->{require(DivinityInventory.stack(p).isEmpty()&&!CalmYule.active(p)&&p.getInventory().countItem(StarcrownDoll.CALM_ITEM.get())==1,"server unequip returns exactly one doll and removes ability");});
                    pass("native shift unequip removes ability and returns original doll");
                }
                case 345 -> {
                    done();server(mc,p->{
                        for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(StarcrownDoll.CALM_ITEM.get())){var value=p.getInventory().removeItem(i,1);DivinityInventory.force(p,value);break;}
                        require(ItemStack.isSameItemSameTags(DivinityInventory.stack(p),doll()),"save fixture re-equipped");
                    });
                }
                case 370 -> {done();mc.player.closeContainer();mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
                case 395 -> shot(mc,"divinity-head-render.png");
                case 410 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    if(CuriosEquipment.loaded()){finish(mc);return;}
                    server(mc,p->p.setGameMode(net.minecraft.world.level.GameType.CREATIVE));
                }
                case 440 -> {
                    done();mc.setScreen(new CreativeModeInventoryScreen(mc.player,mc.level.enabledFeatures(),true));
                    var tab=CreativeModeTabs.allTabs().stream().filter(t->t.getType()==CreativeModeTab.Type.INVENTORY).findFirst().orElseThrow();
                    net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(CreativeModeInventoryScreen.class,"m_98560_",CreativeModeTab.class).invoke(mc.screen,tab);
                }
                case 465 -> {
                    require(mc.screen instanceof CreativeModeInventoryScreen c&&c.isInventoryOpen(),"creative player inventory tab");
                    require(((AbstractContainerScreen<?>)mc.screen).getMenu().slots.stream().filter(s->AsWrittenClient.unwrap(s) instanceof EquipmentSlots.SideSlot).count()==3,"creative wrappers retain three side slots");
                    shot(mc,"divinity-creative.png");creativeClick(mc,0);
                }
                case 490 -> {
                    require(DivinityInventory.stack(mc.player).isEmpty()&&mc.player.inventoryMenu.getCarried().is(StarcrownDoll.CALM_ITEM.get()),"creative click takes original divinity, cursor synced");creativeClick(mc,0);
                }
                case 515 -> {
                    require(ItemStack.isSameItemSameTags(DivinityInventory.stack(mc.player),doll())&&mc.player.inventoryMenu.getCarried().isEmpty(),"creative click re-equips same item without duplication");
                    pass("creative inventory native tab and side-slot network pickup/re-equip");
                    mc.player.closeContainer();server(mc,p->p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
                }
                case 540 -> {done();finish(mc);}
                default -> {}
            }
        }catch(Throwable error){error.printStackTrace();proof.append("FAIL ").append(error).append('\n');finish(mc);}
    }
    static void open(Minecraft mc)throws Exception{
        if(CuriosEquipment.loaded()){
            var key=(KeyMapping)Class.forName("top.theillusivec4.curios.client.KeyRegistry").getField("openCurios").get(null);KeyMapping.click(key.getKey());
        }else KeyMapping.click(AsWrittenClient.OPEN.getKey());
    }
    static void screen(Minecraft mc){require(mc.screen instanceof AbstractContainerScreen<?> && (CuriosEquipment.loaded()?mc.screen.getClass().getName().contains("CuriosScreen"):mc.screen instanceof InventoryScreen),"real native backpack/Curios screen");}
    static int inventorySlot(Minecraft mc,int inventoryIndex){return ((AbstractContainerScreen<?>)mc.screen).getMenu().slots.stream().filter(s->s.container==mc.player.getInventory()&&s.getSlotIndex()==inventoryIndex).findFirst().orElseThrow().index;}
    static int godSlot(Minecraft mc){return ((AbstractContainerScreen<?>)mc.screen).getMenu().slots.stream().filter(s->s.getNoItemIcon()!=null&&DivinityInventory.ICON.equals(s.getNoItemIcon().getSecond())).findFirst().orElseThrow().index;}
    static void click(Minecraft mc,int slot,ClickType type){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,type,mc.player);}
    static void toggle(Minecraft mc){((Button)mc.screen.children().stream().filter(AsWrittenClient.ToggleButton.class::isInstance).findFirst().orElseThrow()).onPress();}
    static void creativeClick(Minecraft mc,int kind)throws Exception {
        var screen=(AbstractContainerScreen<?>)mc.screen;
        var slot=screen.getMenu().slots.stream().filter(s->AsWrittenClient.unwrap(s) instanceof EquipmentSlots.SideSlot side&&side.kind==kind).findFirst().orElseThrow();
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(CreativeModeInventoryScreen.class,"m_6597_",Slot.class,int.class,int.class,ClickType.class).invoke(screen,slot,slot.index,0,ClickType.PICKUP);
    }
    static void validateAllDolls(ServerPlayer player) {
        var original=DivinityInventory.stack(player).copy();var helmet=player.getItemBySlot(EquipmentSlot.HEAD).copy();
        var god=player.containerMenu.slots.stream().filter(s->s.getNoItemIcon()!=null&&DivinityInventory.ICON.equals(s.getNoItemIcon().getSecond())).findFirst().orElseThrow();
        try {
            for(var first:DivinityTests.dolls()) {
                player.getInventory().armor.set(3,ItemStack.EMPTY);DivinityInventory.force(player,new ItemStack(first));
                require(DivinityInventory.equipped(player).is(first),"all five dolls share current backend");
                require(DivineWill.wearing(player)==(first==StarcrownDoll.ITEM.get())&&TwilightCrown.wearing(player)==(first==StarcrownDoll.TWILIGHT_ITEM.get())&&YuleHumility.active(player)==(first==StarcrownDoll.YULE_ITEM.get())&&CalmYule.active(player)==(first==StarcrownDoll.CALM_ITEM.get()),"all ability predicates use current backend");
                for(var second:DivinityTests.dolls())require(!player.inventoryMenu.getSlot(5).mayPlace(new ItemStack(second)),"current backend blocks every second doll in helmet");
                DivineAnchor.unlock(player);DivinityInventory.force(player,ItemStack.EMPTY);player.getInventory().armor.set(3,new ItemStack(first));
                for(var second:DivinityTests.dolls())require(!god.mayPlace(new ItemStack(second)),"helmet blocks every second doll in active divinity slot");
            }
            pass("all five dolls recognized; all 25 pairs rejected in both equip directions on active backend");
            DivineAnchor.unlock(player);player.getInventory().armor.set(3,helmet);
            DivinityInventory.force(player,new ItemStack(StarcrownDoll.ITEM.get()));DivineAnchor.observe(player);DivineAnchor.lock(player);
            player.containerMenu.setCarried(ItemStack.EMPTY);player.containerMenu.clicked(god.index,0,ClickType.PICKUP,player);DivineAnchor.repair(player);
            require(DivinityInventory.stack(player).isEmpty()&&!DivineWill.wearing(player)&&!DivineAnchor.locked(player)&&player.containerMenu.getCarried().is(StarcrownDoll.ITEM.get()),"native menu removes locked supreme divinity without auto-restoring");
            require(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),"supreme unequip preserves ordinary helmet");
            player.containerMenu.setCarried(ItemStack.EMPTY);pass("native menu normally removes supreme divinity and releases anchor; ordinary helmet preserved");
        } finally { DivineAnchor.unlock(player);player.getInventory().armor.set(3,helmet);DivinityInventory.force(player,original); }
    }
    static void server(Minecraft mc,Consumer<ServerPlayer> action){var server=mc.getSingleplayerServer();var id=mc.player.getUUID();pending=CompletableFuture.runAsync(()->action.accept(server.getPlayerList().getPlayer(id)),server);}
    static void done(){require(pending!=null&&pending.isDone(),"server action completed");pending.join();}
    static void pass(String text){proof.append("PASS ").append(text).append('\n');}
    static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{});}
    static void finish(Minecraft mc){finished=true;if(proof.indexOf("FAIL")<0)proof.append("ALL_PASS\n");try{Files.writeString(Path.of("divinity-results.txt"),proof.toString());}catch(Exception error){throw new RuntimeException(error);}mc.stop();}
}
