package dev.starcrown;

import net.minecraft.world.entity.Entity;
/** Only player health entries are bound; ordinary synchronized fields have no ownership overhead. */
public interface OwnedVitalData {
 void starcrown$bindVitalOwner(Entity owner);
}
