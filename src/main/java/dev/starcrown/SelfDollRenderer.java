package dev.starcrown;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class SelfDollRenderer {
    record Skin(ResourceLocation texture, boolean slim) {}
    private record Part(List<BakedQuad> slim, List<BakedQuad> wide) {}

    private static final Cache<UUID, Skin> PLACED_SKINS = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterWrite(10, TimeUnit.MINUTES).build();
    private static final Skin DEFAULT_SKIN = new Skin(DefaultPlayerSkin.getDefaultSkin(), false);
    private static Map<String, Part> parts = Map.of();
    private static Map<ResourceLocation, Skin> localPackSkins;

    static void bakeModels(Map<ResourceLocation, BakedModel> models) {
        localPackSkins = null;
        var baked = new HashMap<String, Part>();
        for (String name : List.of("body", "head", "right_arm", "left_arm")) {
            var model = models.get(new ResourceLocation(StarcrownDoll.ID, "block/doll_part_" + name));
            if (model == null) continue;
            var quads = model.getQuads(null, null, RandomSource.create(0));
            var slim = quads.stream().map(quad -> skinQuad(quad, name, false)).toList();
            var wide = name.endsWith("_arm")
                    ? quads.stream().map(quad -> skinQuad(quad, name, true)).toList() : slim;
            baked.put(name, new Part(slim, wide));
        }
        parts = Map.copyOf(baked);
    }

    static void renderPlaced(StarcrownDoll.DollEntity doll, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        DollRenderer.renderBody(doll.getBlockState(), pose, buffers, light, overlay, 0, placedSkin(doll));
    }

    static void renderForPlayer(AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffers,
                                int light, int overlay, float raised) {
        DollRenderer.renderBody(StarcrownDoll.SELF_DOLL.get().defaultBlockState(), pose, buffers, light, overlay,
                raised, player == null ? DEFAULT_SKIN : playerSkin(player));
    }

    private static Skin playerSkin(AbstractClientPlayer player) {
        var mc = Minecraft.getInstance();
        boolean slim = "slim".equals(player.getModelName());
        ResourceLocation fallback = DefaultPlayerSkin.getDefaultSkin(player.getUUID());
        ResourceLocation nativeSkin = player.getSkinTextureLocation();
        if (!nativeSkin.equals(fallback)) return new Skin(nativeSkin, slim);

        var renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        ResourceLocation texture = renderer instanceof PlayerRenderer playerRenderer
                ? playerRenderer.getTextureLocation(player) : nativeSkin;
        Skin selected = new Skin(texture, slim);
        if (player != mc.player || !texture.equals(fallback)) return selected;

        if (localPackSkins == null) {
            // Offline launchers can put the selected skin on a different vanilla fallback slot.
            var overrides = new HashMap<ResourceLocation, Skin>();
            mc.getResourceManager().listResources("textures/entity/player", SelfDollRenderer::vanillaSkinSlot)
                    .forEach((path, resource) -> {
                        if (!resource.sourcePackId().equals("vanilla")) {
                            overrides.put(path, new Skin(path, path.getPath().startsWith("textures/entity/player/slim/")));
                        }
                    });
            localPackSkins = Map.copyOf(overrides);
        }
        Skin exact = localPackSkins.get(texture);
        if (exact != null) return exact;
        return localPackSkins.size() == 1 ? localPackSkins.values().iterator().next() : selected;
    }

    private static boolean vanillaSkinSlot(ResourceLocation texture) {
        return texture.getNamespace().equals("minecraft") && texture.getPath().matches(
                "textures/entity/player/(slim|wide)/(alex|ari|efe|kai|makena|noor|steve|sunny|zuri)\\.png");
    }

    private static Skin placedSkin(StarcrownDoll.DollEntity doll) {
        var owner = doll.skinOwner();
        if (owner == null || owner.getId() == null) return DEFAULT_SKIN;

        UUID id = owner.getId();
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getPlayerByUUID(id) instanceof AbstractClientPlayer player) {
            return playerSkin(player);
        }
        var connection = mc.getConnection();
        var playerInfo = connection == null ? null : connection.getPlayerInfo(id);
        if (playerInfo != null) {
            return new Skin(playerInfo.getSkinLocation(), "slim".equals(playerInfo.getModelName()));
        }

        Skin skin = PLACED_SKINS.getIfPresent(id);
        if (skin != null) return skin;

        Skin fallback = new Skin(DefaultPlayerSkin.getDefaultSkin(id),
                "slim".equals(DefaultPlayerSkin.getSkinModelName(id)));
        PLACED_SKINS.put(id, fallback);
        // SkinManager can replace profile properties while resolving an offline player's skin.
        var lookup = new GameProfile(id, owner.getName());
        lookup.getProperties().putAll(owner.getProperties());
        Minecraft.getInstance().getSkinManager().registerSkins(lookup, (type, texture, profileTexture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                PLACED_SKINS.put(id, new Skin(texture, "slim".equals(profileTexture.getMetadata("model"))));
            }
        }, true);
        return fallback;
    }

    static void renderPart(String name, Skin skin, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Part part = parts.get(name);
        if (part == null) return;
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture()));
        for (BakedQuad quad : skin.slim() ? part.slim() : part.wide()) {
            vertices.putBulkData(pose.last(), quad, 1, 1, 1, light, overlay);
        }
    }

    private static BakedQuad skinQuad(BakedQuad quad, String name, boolean wide) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        // The baked block quad stores UVs in the block atlas. Undo the atlas inset for the standalone skin.
        var sprite = quad.getSprite();
        float centerU = 0;
        float centerV = 0;
        for (int offset = 0; offset < vertices.length; offset += stride) {
            centerU += Float.intBitsToFloat(vertices[offset + 4]) / 4;
            centerV += Float.intBitsToFloat(vertices[offset + 5]) / 4;
        }
        float span = 1 - sprite.uvShrinkRatio();
        for (int offset = 0; offset < vertices.length; offset += stride) {
            float u = centerU + (Float.intBitsToFloat(vertices[offset + 4]) - centerU) / span;
            float v = centerV + (Float.intBitsToFloat(vertices[offset + 5]) - centerV) / span;
            float pixelU = sprite.getUOffset(u) * 4;
            if (wide) pixelU = wideArmU(quad, pixelU, name.equals("left_arm"));
            vertices[offset + 4] = Float.floatToRawIntBits(pixelU / 64);
            vertices[offset + 5] = Float.floatToRawIntBits(sprite.getVOffset(v) / 16);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), sprite, quad.isShade());
    }

    private static float wideArmU(BakedQuad quad, float u, boolean left) {
        // The two four-pixel depth faces stay the same width. The front, back and caps gain one column.
        float origin = left ? (u >= 48 - .001f ? 48 : 32) : 40;
        return switch (quad.getDirection()) {
            case WEST -> u;
            case EAST -> u + 1;
            case NORTH, UP -> u + (u - origin - 4) / 3;
            case DOWN -> u + 1 + (u - origin - 7) / 3;
            case SOUTH -> u + 1 + (u - origin - 11) / 3;
        };
    }
}
