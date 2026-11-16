package dev.starcrownqa;

import dev.starcrown.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class EternalReturnVisualQa {
    private static boolean started,failed,finished,previewCaptured,requestedRewind;
    private static volatile boolean ready,rewound;
    private static int ticks,animationTick=-1,captures;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean enabled(){return Files.exists(root().resolve("enable-eternal-qa"));}
    private static synchronized void log(String s){try{Files.writeString(root().resolve("eternal-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);log("PASS "+s);}
    private static void fail(Throwable e){failed=true;e.printStackTrace();log("FAIL "+e);Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||finished)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        try {
            mc.options.pauseOnLostFocus=false;
            if(!started) {
                if(mc.screen!=null)return;
                started=true;log("GPU "+GL11.glGetString(GL11.GL_RENDERER));
                mc.getSingleplayerServer().execute(()->{try{
                    var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayers().get(0);
                    DivineAnchor.unlock(p);DivinityInventory.force(p,ItemStack.EMPTY);p.getInventory().clearContent();
                    p.getPersistentData().remove("starcrown_eternal_return");p.removeAllEffects();
                    p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;
                    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
                        level.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        for(int y=101;y<=108;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                    p.teleportTo(level,0,101,0,0,0);
                    var halo=new ItemStack(StarcrownDoll.ETERNAL_RETURN.get());
                    CuriosApi.getCuriosInventory(p).resolve().orElseThrow().getStacksHandler("head").orElseThrow().getStacks().setStackInSlot(0,halo);
                    EternalReturn.updateAttributes(p);p.setHealth(p.getMaxHealth());
                    check(EternalReturn.equipped(p)==halo,"production Curios head slot equips original model");
                    ready=true;
                }catch(Throwable e){fail(e);}});return;
            }
            if(!ready)return;ticks++;
            if(ticks==30) {
                check(!EternalReturn.equipped(mc.player).isEmpty(),"Curios equipment synchronizes to client");
                var icon=new ItemStack(StarcrownDoll.ETERNAL_RETURN.get());
                check(!mc.getItemRenderer().getModel(icon,mc.level,mc.player,0).isCustomRenderer(),"inventory retains 64 pixel icon");
                check(mc.getItemRenderer().getModel(EternalReturnClient.rewindVisual(),mc.level,mc.player,0).isCustomRenderer(),"rewind selects HD 3D inner-ring renderer");
                mc.setScreen(new Preview());
            }
            if(ticks>60&&previewCaptured&&!requestedRewind) {
                requestedRewind=true;mc.setScreen(null);
                mc.getSingleplayerServer().execute(()->{try {
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.invulnerableTime=0;
                    p.hurt(p.damageSources().generic(),10000);
                    check(p.isAlive()&&EternalReturn.data(p).getLong("rewind_ready")>EternalReturn.now(p),"production fatal damage triggers rewind mixin");
                    rewound=true;
                }catch(Throwable e){fail(e);}});
            }
            if(requestedRewind) {
                var visual=activationItem(mc);
                if(animationTick<0 && !visual.isEmpty()) {
                    check(visual.is(StarcrownDoll.ETERNAL_RETURN.get())&&visual.getOrCreateTag().getBoolean("EternalRewindVisual"),"vanilla totem animation receives inner-ring stack");
                    animationTick=0;
                } else if(animationTick>=0)animationTick++;
                if(ticks>180&&animationTick<0)throw new AssertionError("No rewind activation packet received");
                if(animationTick>=48) {
                    check(rewound&&captures==15,"captured original totem animation begin/middle/end and disappearance");
                    check(activationItem(mc).isEmpty(),"vanilla activation clears its temporary icon");
                    check(!EternalReturn.equipped(mc.player).hasTag()||!EternalReturn.equipped(mc.player).getTag().getBoolean("EternalRewindVisual"),"animation never changes equipped/inventory stack");
                    finished=true;log("COMPLETE HD worn halo and inner-only vanilla rewind animation");mc.stop();
                }
            }
        } catch(Throwable e){fail(e);}
    }
    private static ItemStack activationItem(Minecraft mc) throws Exception {
        for(String name:new String[]{"itemActivationItem","f_109080_"})try {
            var field=net.minecraft.client.renderer.GameRenderer.class.getDeclaredField(name);field.setAccessible(true);
            var value=field.get(mc.gameRenderer);return value instanceof ItemStack s?s:ItemStack.EMPTY;
        }catch(NoSuchFieldException ignored){}
        // Locate the unique ItemStack activation field in production mappings without mutating it.
        for(var field:net.minecraft.client.renderer.GameRenderer.class.getDeclaredFields())if(field.getType()==ItemStack.class) {
            field.setAccessible(true);var value=field.get(mc.gameRenderer);return value instanceof ItemStack s?s:ItemStack.EMPTY;
        }
        throw new NoSuchFieldException("activation ItemStack");
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||finished||!ready)return;
        var mc=Minecraft.getInstance();
        if(ticks>=45&&!previewCaptured&&mc.screen instanceof Preview) {capture("eternal-worn-three-views.png");previewCaptured=true;}
        int stage=animationTick>=42?3:animationTick>=29?2:animationTick>=17?1:animationTick>=5?0:-1;
        if(stage>=0&&(captures&(1<<stage))==0) {
            capture("eternal-rewind-"+new String[]{"begin","middle","end","cleared"}[stage]+".png");captures|=1<<stage;
        }
    }
    private static void capture(String filename) {var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,filename,mc.getMainRenderTarget(),m->{});log("GPU_CAPTURE "+filename);}
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("永劫回归 · 高清 3D 神环"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            g.fill(0,0,width,height,0xFF20232B);g.drawCenteredString(font,title,width/2,16,0xFFE8A7);
            var p=Minecraft.getInstance().player;
            float body=p.yBodyRot,head=p.yHeadRot,oldHead=p.yHeadRotO,yaw=p.getYRot(),pitch=p.getXRot();
            try {
                p.yBodyRot=0;p.yHeadRot=0;p.yHeadRotO=0;p.setYRot(0);p.setXRot(0);
                int size=(int)(height*.32);float[] angles={180,125,0};String[] labels={"正面","斜侧","背面"};
                for(int i=0;i<3;i++) {
                    int x=(i*2+1)*width/6;
                    InventoryScreen.renderEntityInInventory(g,x,height-40,size,new Quaternionf().rotationZ((float)Math.PI)
                            .mul(new Quaternionf().rotationY((float)Math.toRadians(angles[i]))),null,p);
                    g.drawCenteredString(font,Component.literal(labels[i]),x,height-24,0xC8BDAC);
                }
            } finally {p.yBodyRot=body;p.yHeadRot=head;p.yHeadRotO=oldHead;p.setYRot(yaw);p.setXRot(pitch);}
        }
    }
}
