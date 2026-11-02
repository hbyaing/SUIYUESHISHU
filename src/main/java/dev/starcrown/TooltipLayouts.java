package dev.starcrown;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small bounded caches; resource/font reloads invalidate both wrapping and glyph advances. */
public final class TooltipLayouts {
 public record Layout<T>(List<T> lines,int header){}
 private record Key(String id,int width,boolean expanded,Component name,String language){}
 public record Glyph(int index,int code,Style style,int x){}
 private static final Map<Key,Layout<?>> LAYOUTS=new LinkedHashMap<>();
 private static final Map<FormattedCharSequence,List<Glyph>> GLYPHS=new WeakHashMap<>();
 @SuppressWarnings("unchecked") public static <T> Layout<T> get(String id,int width,boolean expanded,Component name,Supplier<Layout<T>> build){
  Key key=new Key(id,width,expanded,name,Minecraft.getInstance().getLanguageManager().getSelected());
  var cached=LAYOUTS.get(key);if(cached!=null)return (Layout<T>)cached;
  if(LAYOUTS.size()>=16){LAYOUTS.clear();GLYPHS.clear();ModernUiCompat.clear();}
  var layout=build.get();LAYOUTS.put(new Key(id,width,expanded,name.copy(),key.language),layout);return layout;
 }
 public static List<Glyph> glyphs(FormattedCharSequence line){return GLYPHS.computeIfAbsent(line,row->{
  var result=new ArrayList<Glyph>();int[] offset={0};
  row.accept((index,style,code)->{result.add(new Glyph(index,code,style,offset[0]));offset[0]+=Minecraft.getInstance().font.width(Component.literal(new String(Character.toChars(code))).withStyle(style));return true;});
  return List.copyOf(result);
 });}
 public static void clear(){LAYOUTS.clear();GLYPHS.clear();ModernUiCompat.clear();}
 @Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Reload {
  @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->clear());}
 }
}
