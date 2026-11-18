package dev.starcrown.treasury;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** World-owned chain bindings. Accessed on the server thread, without looking up entities or chunks. */
public final class TreasurySavedData extends SavedData {
    private static final String FILE = "starcrowndoll_treasury_bindings";
    public record Binding(TreasuryNetwork.Cast cast, @Nullable Vec3 anchor) {}

    private final Map<UUID, Binding> casts = new HashMap<>();
    private final Map<UUID, UUID> castByOwner = new HashMap<>();
    private final Map<UUID, Set<UUID>> anchoredCastsByTarget = new HashMap<>();

    private TreasurySavedData() {}

    public static TreasurySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TreasurySavedData::load, TreasurySavedData::new, FILE);
    }

    @Nullable
    public Binding byId(UUID castId) { return casts.get(castId); }

    @Nullable
    public Binding byOwner(UUID owner) { return casts.get(castByOwner.get(owner)); }

    public List<Binding> bindings() { return List.copyOf(casts.values()); }

    @Nullable
    public Vec3 activeAnchor(ResourceKey<Level> dimension, UUID target) {
        var ids = anchoredCastsByTarget.get(target);
        if (ids == null) return null;
        for (UUID id : ids) {
            var binding = casts.get(id);
            if (binding.cast().dimension().equals(dimension)) return binding.anchor();
        }
        return null;
    }

    public void put(TreasuryNetwork.Cast cast) {
        if (!cast.chains() || cast.target().isEmpty()) {
            throw new IllegalArgumentException("Only chains attached to an entity can be saved");
        }
        if (casts.containsKey(cast.id())) return;
        insert(new Binding(cast, null));
        setDirty();
    }

    private void insert(Binding binding) {
        var cast = binding.cast();
        var previousId = castByOwner.put(cast.caster(), cast.id());
        if (previousId != null) unindexAnchor(casts.remove(previousId));
        casts.put(cast.id(), binding);
        if (binding.anchor() != null) {
            anchoredCastsByTarget.computeIfAbsent(cast.target().orElseThrow(), key -> new HashSet<>()).add(cast.id());
        }
    }

    private void unindexAnchor(Binding binding) {
        if (binding.anchor() == null) return;
        UUID target = binding.cast().target().orElseThrow();
        var ids = anchoredCastsByTarget.get(target);
        ids.remove(binding.cast().id());
        if (ids.isEmpty()) anchoredCastsByTarget.remove(target);
    }

    public void setAnchor(UUID castId, Vec3 anchor) {
        var binding = casts.get(castId);
        if (binding == null || binding.anchor() != null) return;
        if (!Double.isFinite(anchor.lengthSqr())) throw new IllegalArgumentException("Nonfinite chain anchor");
        insert(new Binding(binding.cast(), anchor));
        setDirty();
    }

    @Nullable
    public Binding remove(UUID castId) {
        var removed = casts.remove(castId);
        if (removed != null) {
            unindexAnchor(removed);
            castByOwner.remove(removed.cast().caster(), castId);
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        var list = new ListTag();
        for (var binding : casts.values()) {
            var cast = binding.cast();
            var tag = new CompoundTag();
            tag.putUUID("Cast", cast.id());
            tag.putUUID("Owner", cast.caster());
            tag.putUUID("Target", cast.target().orElseThrow());
            tag.putString("Dimension", cast.dimension().location().toString());
            tag.putLong("Start", cast.start());
            tag.put("Origin", vector(cast.origin()));
            tag.put("Forward", vector(cast.forward()));
            tag.put("End", vector(cast.end()));
            tag.putFloat("Width", cast.width());
            tag.putFloat("Height", cast.height());
            if (binding.anchor() != null) tag.put("Anchor", vector(binding.anchor()));
            list.add(tag);
        }
        root.put("Bindings", list);
        return root;
    }

    public static TreasurySavedData load(CompoundTag root) {
        var data = new TreasurySavedData();
        var list = root.getList("Bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            if (!tag.hasUUID("Cast") || !tag.hasUUID("Owner") || !tag.hasUUID("Target")
                    || !tag.contains("Start", Tag.TAG_ANY_NUMERIC)) continue;
            var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            var origin = vector(tag, "Origin");
            var forward = vector(tag, "Forward");
            var end = vector(tag, "End");
            var anchor = vector(tag, "Anchor");
            float width = tag.getFloat("Width"), height = tag.getFloat("Height");
            if (!tag.contains("Dimension", Tag.TAG_STRING) || dimension == null || dimension.getPath().isEmpty()
                    || origin == null || forward == null || end == null || (tag.contains("Anchor") && anchor == null)
                    || !Float.isFinite(width) || width <= 0 || !Float.isFinite(height) || height <= 0) continue;
            // The numeric entity ID is assigned anew on load. Tracking resolves the saved target UUID.
            var cast = new TreasuryNetwork.Cast(tag.getUUID("Cast"), tag.getUUID("Owner"),
                    ResourceKey.create(Registries.DIMENSION, dimension), 2, tag.getLong("Start"),
                    origin, forward, -1, Optional.of(tag.getUUID("Target")), end, width, height);
            if (!data.casts.containsKey(cast.id())) data.insert(new Binding(cast, anchor));
        }
        return data;
    }

    private static CompoundTag vector(Vec3 value) {
        var tag = new CompoundTag();
        tag.putDouble("X", value.x);
        tag.putDouble("Y", value.y);
        tag.putDouble("Z", value.z);
        return tag;
    }

    @Nullable
    private static Vec3 vector(CompoundTag parent, String key) {
        var tag = parent.getCompound(key);
        if (!tag.contains("X", Tag.TAG_ANY_NUMERIC) || !tag.contains("Y", Tag.TAG_ANY_NUMERIC)
                || !tag.contains("Z", Tag.TAG_ANY_NUMERIC)) return null;
        var value = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        return Double.isFinite(value.lengthSqr()) ? value : null;
    }
}
