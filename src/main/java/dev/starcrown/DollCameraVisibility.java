package dev.starcrown;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Optional Player Animator compatibility; Iron's uses this pass for casting arms. */
public final class DollCameraVisibility {
    private static final MethodHandle FIRST_PERSON_PASS = findFirstPersonPass();

    private DollCameraVisibility() {}

    private static MethodHandle findFirstPersonPass() {
        try {
            var type = Class.forName("dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode",
                    false, DollCameraVisibility.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(type, "isFirstPersonPass",
                    MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    public static boolean isFirstPersonBodyPass(Player player) {
        var mc = Minecraft.getInstance();
        // The pass flag, unlike Camera.isDetached(), remains valid while Player Animator
        // temporarily pretends that the first-person camera is a third-person camera.
        // Check the subject as well: other players and inventory portraits stay visible.
        if (player == null || player != mc.getCameraEntity()
                || !mc.options.getCameraType().isFirstPerson() || FIRST_PERSON_PASS == null) return false;
        try {
            return (boolean) FIRST_PERSON_PASS.invokeExact();
        } catch (Throwable incompatibleApi) {
            return false;
        }
    }
}
