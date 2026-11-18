package dev.starcrown;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renders the player's own sleeve and arm through the light of the aperture. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class FateDrawHand extends RenderType {
    private static ShaderInstance shader;
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(1024));
    private static final Function<ResourceLocation,RenderType> SKIN=Util.memoize(skin->create(
            "starcrown_fate_draw_hand",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1024,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->shader))
                    .setTextureState(new TextureStateShard(skin,false,false)).setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false)));
    private FateDrawHand(){super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    @SubscribeEvent public static void register(RegisterShadersEvent event)throws IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),FateItemRenderer.id("fate_draw_hand"),
                DefaultVertexFormat.NEW_ENTITY),loaded->shader=loaded);
    }
    static void draw(PlayerRenderer renderer,AbstractClientPlayer player,boolean left,PoseStack pose,
                     MultiBufferSource original,int light,float age){
        float warm=FateClient.step(age,86,99)*(1-FateClient.step(age,111,115));
        float cold=FateClient.step(age,112,115)*(1-FateClient.step(age,141,147));
        boolean tinted=shader!=null&&(warm>.001f||cold>.001f);
        RenderType skin=tinted?SKIN.apply(player.getSkinTextureLocation()):null;
        MultiBufferSource source=original;
        if(tinted){
            float invert=FateClient.step(age,112,114)*(1-FateClient.step(age,116,119));
            shader.safeGetUniform("Tint").set(warm,cold,invert);
            source=ignored->BUFFER.getBuffer(skin);
            light=LightTexture.FULL_BRIGHT;
        }
        if(left)renderer.renderLeftHand(pose,source,light,player);
        else renderer.renderRightHand(pose,source,light,player);
        if(tinted)BUFFER.endBatch(skin);
    }
}
