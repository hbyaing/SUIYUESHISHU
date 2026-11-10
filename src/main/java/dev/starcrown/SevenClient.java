package dev.starcrown;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class SevenClient {
 private static java.util.Set<String> disabledEffects=java.util.Set.of();
 private static int potionLevel=PotionFilter.MAX_LEVEL;
 public static void receiveEffects(SevenNetwork.EffectsState next){disabledEffects=next.disabled();potionLevel=next.level();if(Minecraft.getInstance().screen instanceof Panel panel)panel.refresh();}
 private static SevenNetwork.State state=new SevenNetwork.State(false,true,false,true,1);
 public static void receive(SevenNetwork.State next){state=next;if(Minecraft.getInstance().screen instanceof Panel panel)panel.refresh();else if(Minecraft.getInstance().screen instanceof YulePanel panel)panel.refresh(next);}
 public static void openPanel(){var mc=Minecraft.getInstance();if(mc.player!=null&&(CalmYule.active(mc.player)||YuleHumility.active(mc.player)))mc.setScreen(new YulePanel(CalmYule.active(mc.player)));else mc.setScreen(new Panel());}
 private static final KeyMapping OPEN=new KeyMapping("key.starcrowndoll.seven",com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_H,"key.categories.starcrowndoll");
 @Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Setup {@SubscribeEvent public static void keys(net.minecraftforge.client.event.RegisterKeyMappingsEvent e){e.register(OPEN);}}
 private static boolean twilight(){var player=Minecraft.getInstance().player;return player!=null&&TwilightCrown.wearing(player);}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  var mc=Minecraft.getInstance();
  boolean yule=mc.player!=null&&(CalmYule.active(mc.player)||YuleHumility.active(mc.player));
  if(event.phase==TickEvent.Phase.START){
   if(yule&&mc.screen==null&&mc.getConnection()!=null&&(mc.options.keyShift.isDown()||mc.player.isShiftKeyDown())
     &&mc.options.keyDrop.consumeClick()){
    while(mc.options.keyDrop.consumeClick()){}
    SevenNetwork.CHANNEL.sendToServer(new SevenNetwork.Action(CalmYule.GIFT_ACTION,0));
   }
   return;
  }
  while(OPEN.consumeClick())if(mc.player!=null&&mc.screen==null&&(yule||DivineWill.wearing(mc.player)||twilight()))openPanel();
 }

 private static final class Panel extends Screen {
  int page,slot,effectPage,px,py,pw,ph,body,step,potionDraft=-1;String search="";net.minecraft.client.gui.components.EditBox searchBox;
  private final boolean twilight=SevenClient.twilight();
  Panel(){super(Component.translatable(SevenClient.twilight()?"block.starcrowndoll.twilight_doll":"block.starcrowndoll.doll"));if(Minecraft.getInstance().getConnection()!=null)SevenNetwork.CHANNEL.sendToServer(new SevenNetwork.Action(16,0));}
  void refresh(){boolean focused=searchBox!=null&&getFocused()==searchBox;rebuildWidgets();if(focused&&page==3)setFocused(searchBox);}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void onClose(){SevenAppearance.save();super.onClose();}
  void action(int id,int value){if(Minecraft.getInstance().getConnection()!=null)SevenNetwork.CHANNEL.sendToServer(new SevenNetwork.Action(id,value));}
  Button add(int x,int y,int w,int h,String label,Runnable run,java.util.function.BooleanSupplier selected){return addRenderableWidget(SevenLook.button(x,y,w,h,label,run,selected));}
  void button(int col,int row,String text,Runnable run){int w=(pw-40)/2;add(px+16+col*(w+8),body+row*step,w,Math.min(38,step-5),text,run,()->false);}
  void toggle(int col,int row,String label,boolean enabled,Runnable run){int w=(pw-40)/2;add(px+16+col*(w+8),body+row*step,w,Math.min(38,step-5),label+"  "+(enabled?"● 开启":"○ 关闭"),run,()->enabled);}
  void card(int col,int row,String title,String desc,String icon,boolean enabled,boolean toggle,Runnable run){int w=(pw-40)/2;var widget=SevenLook.card(px+16+col*(w+8),body+row*step,w,Math.min(41,step-3),title,desc,icon,()->enabled,toggle,run);widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(desc)));addRenderableWidget(widget);}
  @Override protected void init(){
   pw=Math.min(610,width-28);ph=Math.min(420,height-22);px=(width-pw)/2;py=(height-ph)/2;body=py+(ph<330?85:106);step=Math.max(22,Math.min(46,(ph-163)/5));
   String[] tabs=twilight?new String[]{"神性与领域","群星归途"}:new String[]{"神性与领域","群星归途","岁月与权柄","药水过滤"};
   for(int i=0;i<tabs.length;i++){final int tab=i;int w=(pw-32)/tabs.length;add(px+16+i*w,py+(ph<330?51:68),w-4,23,tabs[i],()->{page=tab;rebuildWidgets();},()->page==tab);}
   if(twilight&&page==0){
    card(0,0,"展开万象领域","半径 16 格 · 持续 10 秒 · 冷却 45 秒","✧",false,false,()->{action(0,0);onClose();});
    card(0,1,"夜视庇护","让黑夜也清晰可见","◐",state.night(),true,()->action(2,0));
    card(1,1,"范围采掘","按所选范围采集方块","◇",state.mine(),true,()->action(3,0));
    int start=px+Math.min(92,pw/5),w=(pw-(start-px)-22)/2;
    for(int i=0;i<2;i++){final int index=i;int size=3+i*2;add(start+i*w,body+3*step,w-5,22,size+"×"+size+"×"+size,()->action(18,index),()->state.mining()==index);}
   }else if(twilight&&page==1){
    button(0,0,"准星安全瞬移  ↗",()->{action(4,0);onClose();});
    button(1,0,"锚点槽位  0"+(slot+1),()->{slot=(slot+1)%3;rebuildWidgets();});
    button(0,1,"记录余晖锚点",()->action(5,slot));
    button(1,1,"返回锚点 · 1000 经验",()->{action(6,slot);onClose();});
   }else if(page==0){
    card(0,0,"万象领域","将周围的秩序交还神明","✧",state.domain(),true,()->action(0,0));card(1,0,"领域驱逐","驱离领域周围的敌人","⌁",false,false,()->action(15,0));card(0,1,"夜视庇护","让黑夜也清晰可见","◐",state.night(),true,()->action(2,0));card(1,1,"范围采掘","按所选范围采集方块","◇",state.mine(),true,()->action(3,0));
    int start=px+Math.min(92,pw/5),widthEach=(pw-(start-px)-22)/5;
    for(int i=0;i<5;i++){final int index=i;add(start+i*widthEach,body+2*step,widthEach-5,22,"×"+new String[]{"1","1.5","2","3","5"}[i],()->action(1,index),()->state.speed()==index);}
    int sizeWidth=(pw-(start-px)-22)/4;
    for(int i=0;i<4;i++){final int index=i;int size=3+i*2;add(start+i*sizeWidth,body+3*step,sizeWidth-5,22,size+"×"+size+"×"+size,()->action(18,index),()->state.mining()==index);}
   }else if(page==1){button(0,0,"准星安全瞬移  ↗",()->{action(4,0);onClose();});button(1,0,"返回传送前位置  ↶",()->{action(7,0);onClose();});button(0,1,"返回死亡地点",()->{action(8,0);onClose();});button(1,1,"锚点槽位  0"+(slot+1),()->{slot=(slot+1)%8;rebuildWidgets();});button(0,2,"记录神性锚点",()->action(5,slot));button(1,2,"返回神性锚点  ↗",()->{action(6,slot);onClose();});}
   else if(page==2){toggle(0,0,"玩家庇护",state.players(),()->action(11,0));button(1,0,"准星目标：加入 / 移出友方",()->{action(12,0);onClose();});button(0,1,"准星敌人：旧伤重现",()->{action(13,0);onClose();});button(1,1,"领取没收的玩偶",()->action(17,0));}
   else if(page==3){
    if(searchBox==null){searchBox=new net.minecraft.client.gui.components.EditBox(font,px+17,body,pw-34,19,Component.literal("搜索药水"));searchBox.setMaxLength(128);searchBox.setValue(search);searchBox.setHint(Component.literal("搜索名称或模组 ID"));searchBox.setTextColor(0xe8d9ff);searchBox.setResponder(text->{search=text;effectPage=0;refresh();setFocused(searchBox);});}
    int allWidth=Math.min(108,(pw-42)/2);
    int filterY=py+(ph<330?76:96),listY=filterY+49,pagerY=py+ph-67;
    searchBox.setX(px+17);searchBox.setY(filterY);searchBox.setWidth(pw-40-allWidth);addRenderableWidget(searchBox);
    var allOff=add(px+pw-16-allWidth,filterY,allWidth,19,"一键关闭全部",()->action(DivineSeven.DISABLE_POTIONS_ACTION,0),()->false);
    allOff.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("关闭玩偶发放的全部正面效果，可逐项开启。")));
    addRenderableWidget(SevenLook.slider(px+16,filterY+21,pw-40-allWidth,"正面药水获取等级",
      potionDraft<0?potionLevel:potionDraft,PotionFilter.MIN_LEVEL,PotionFilter.MAX_LEVEL,
      value->Math.round(value)+" 级",value->potionDraft=(int)Math.round(value)));
    add(px+pw-16-allWidth,filterY+21,allWidth,24,"应用等级",()->{
      if(potionDraft>0){action(DivineSeven.POTION_LEVEL_ACTION,potionDraft);potionDraft=-1;}
    },()->potionDraft>0&&potionDraft!=potionLevel);
    String query=search.toLowerCase(java.util.Locale.ROOT);
    var effects=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValues().stream()
      .filter(PotionFilter::filterable)
      .filter(e->e.getDisplayName().getString().toLowerCase(java.util.Locale.ROOT).contains(query)
         ||net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(e).toString().contains(query))
      .sorted(java.util.Comparator.comparing(e->net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(e).toString())).toList();
    int rows=Math.max(1,(pagerY-listY+4)/28),per=rows*2,pages=Math.max(1,(effects.size()+per-1)/per);effectPage=Math.max(0,Math.min(effectPage,pages-1));int w=(pw-40)/2;
    for(int i=effectPage*per;i<Math.min(effects.size(),(effectPage+1)*per);i++){var effect=effects.get(i);var id=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect);boolean enabled=!disabledEffects.contains(id.toString());int index=i-effectPage*per;var button=add(px+16+(index%2)*(w+8),listY+(index/2)*28,w,24,(enabled?"● ":"○ ")+effect.getDisplayName().getString(),()->{if(Minecraft.getInstance().getConnection()!=null)SevenNetwork.CHANNEL.sendToServer(new SevenNetwork.EffectAction(id,!enabled));},()->enabled);button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(id+"\n切换玩偶是否自动发放此效果。")));}
    add(px+16,pagerY,65,20,"上一页",()->{effectPage--;rebuildWidgets();},()->false).active=effectPage>0;
    add(px+pw/2-35,pagerY,70,20,(effectPage+1)+" / "+pages,()->{},()->false).active=false;
    add(px+pw-81,pagerY,65,20,"下一页",()->{effectPage++;rebuildWidgets();},()->false).active=effectPage<pages-1;
   }else if(page==4){
    double[] values={SevenAppearance.opacity,SevenAppearance.stars,SevenAppearance.character,SevenAppearance.size,SevenAppearance.speed,SevenAppearance.trailStrength,SevenAppearance.glow,SevenAppearance.textSpeed};
    String[] names={"面板不透明度","星空亮度","角色透明度","角色大小","摆动速度","残影强度","边框辉光","文字流速"};double[] min={.25,0,0,.65,.35,0,0,.5},max={.98,1.5,1,1.4,2.5,.55,36,3};
    for(int i=0;i<8;i++){final int k=i;int w=(pw-40)/2;addRenderableWidget(SevenLook.slider(px+16+(i%2)*(w+8),body+(i/2)*step,w,names[i],values[i],min[i],max[i],v->{switch(k){case 0->SevenAppearance.opacity=v;case 1->SevenAppearance.stars=v;case 2->SevenAppearance.character=v;case 3->SevenAppearance.size=v;case 4->SevenAppearance.speed=v;case 5->SevenAppearance.trailStrength=v;case 6->SevenAppearance.glow=v;case 7->SevenAppearance.textSpeed=v;}}));}
    int w=(pw-40)/3;add(px+16,body+4*step,w,21,"残影："+(SevenAppearance.trail?"开":"关"),()->{SevenAppearance.trail=!SevenAppearance.trail;refresh();},()->SevenAppearance.trail);add(px+20+w,body+4*step,w,21,"渐变："+(SevenAppearance.flow?"开":"关"),()->{SevenAppearance.flow=!SevenAppearance.flow;refresh();},()->SevenAppearance.flow);add(px+24+w*2,body+4*step,w,21,"角色："+(SevenAppearance.showCharacter?"开":"关"),()->{SevenAppearance.showCharacter=!SevenAppearance.showCharacter;refresh();},()->SevenAppearance.showCharacter);
   }
   add(px+pw-100,py+16,80,20,page==4?"返回功能":"外观调试",()->{page=page==4?0:4;SevenAppearance.save();refresh();},()->page==4);
   add(px+pw-76,py+ph-29,60,19,"关闭",this::onClose,()->false);
  }
  @Override public void render(GuiGraphics g,int mx,int my,float partial){
   SevenLook.backdrop(g,width,height,px,py,pw,ph);
   SevenLook.icon(g,px+16,py+9);SevenLook.text(g,"以神戴冠",px+53,py+12,false);SevenLook.text(g,getTitle().getString(),px+53,py+27,false);if(ph>=330)SevenLook.godText(g,Component.translatable(twilight?"tooltip.starcrowndoll.twilight.divinity":"tooltip.starcrowndoll.divinity").getString(),px+53,py+42);
   g.fill(px+16,py+(ph<330?43:60),px+pw-16,py+(ph<330?44:61),0x446c4a8a);g.fill(px+16,py+ph-42,px+pw-16,py+ph-41,0x446c4a8a);
   if(page==0){g.drawString(font,twilight?"移速 +35% · 挖掘 ×3":"移动速度",px+18,body+2*step+7,0xc4b4d5,false);g.drawString(font,"采掘范围",px+18,body+3*step+7,0xc4b4d5,false);}
   String lore="不论凡人或不朽，都将承认你的神性";if(page<3)SevenLook.text(g,lore,px+(pw-font.width(lore))/2,py+ph-59,true);
   String hint=page==3?"过滤与等级只作用于玩偶发放的正面效果":page==4?"外观设置仅影响本机 · 关闭面板自动保存":"H 打开面板 · 神性权柄仅佩戴时生效";
   g.drawString(font,font.plainSubstrByWidth(hint,pw-110),px+17,py+ph-23,0xa491b5,false);
   super.render(g,mx,my,partial);
  }
 }
}
