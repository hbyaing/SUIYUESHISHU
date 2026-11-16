package dev.starcrown;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative combat controller for the GeckoLib Ming Gu Long. */
public final class MingGuLongEntity extends Monster implements GeoEntity {
    /** Translation wrapper identifies our boss bar without matching localized names. */
    public static final String BOSS_BAR_NAME = "boss.starcrowndoll.eternal_dragon";
    private final ServerBossEvent bossEvent = new ServerBossEvent(
        Component.translatable(BOSS_BAR_NAME, getDisplayName()),
        BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private static final EntityDataAccessor<Integer> ANIMATION =
        SynchedEntityData.defineId(MingGuLongEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIMATION_SERIAL =
        SynchedEntityData.defineId(MingGuLongEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIMATION_AGE =
        SynchedEntityData.defineId(MingGuLongEntity.class, EntityDataSerializers.INT);

    private static final String[] ANIMATIONS = {
        "World_Idle", "Common_Alert", "Common_Anger", "World_Walk", "World_Run",
        "World_Fly_Start", "World_Fly_Gliding", "World_Fly_Hover", "World_Fly_Landing",
        "Fight_Attack_1", "Fight_Attack_2", "Fly_Attack_1", "Fly_Attack_2",
        "Fight_Skill_1_HuangWu_LongCharge", "Fight_Die"
    };
    private static final int IDLE = 0;
    private static final int ALERT = 1;
    private static final int ANGER = 2;
    private static final int WALK = 3;
    private static final int RUN = 4;
    private static final int FLY_START = 5;
    private static final int FLY_GLIDE = 6;
    private static final int FLY_HOVER = 7;
    private static final int FLY_LAND = 8;
    private static final int GROUND_ATTACK_1 = 9;
    private static final int GROUND_ATTACK_2 = 10;
    private static final int AIR_ATTACK_1 = 11;
    private static final int AIR_ATTACK_2 = 12;
    private static final int HUANGWU = 13;
    private static final int DIE = 14;

    private static final int ATTACK_LENGTH_TICKS = 54;
    // Native BB clips: Common_Alert=4.6667s, Common_Anger=1.5s (20 ticks/s).
    private static final int ALERT_LENGTH_TICKS = 94;
    private static final int ANGER_LENGTH_TICKS = 30;
    private static final int TAKEOFF_LENGTH_TICKS = 51;
    private static final int LANDING_LENGTH_TICKS = 31;
    private static final int GROUND_ATTACK_1_CONTACT_TICK = 15;
    private static final int GROUND_ATTACK_2_CONTACT_TICK = 14;
    private static final int AIR_ATTACK_1_CONTACT_TICK = 21;
    private static final int AIR_ATTACK_2_CONTACT_TICK = 31;
    private static final double GROUND_ATTACK_1_REACH = 5.8D;
    private static final double GROUND_ATTACK_2_REACH = 4.6D;
    private static final double AIR_ATTACK_1_REACH = 6.1D;
    private static final double AIR_ATTACK_2_REACH = 5.2D;
    private static final int HUANGWU_RELEASE_TICK = 36;
    private static final int HUANGWU_LENGTH_TICKS = 106;
    private static final double TARGET_DETECTION_RANGE = 96.0D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int stateTicks;
    private int attackCooldown;
    private int huangwuCooldown;
    private int targetScanCooldown;
    private int flightTicks;
    private int flightModeTicks;
    private int flightMode = FLY_HOVER;
    private int pathRefreshTicks;
    private int failedPathAttempts;
    private Vec3 pathDestination = Vec3.ZERO;
    private boolean huangwuPending;
    private int noSightTicks;
    private boolean airborne;
    private boolean sawTarget;
    private boolean releasedHuangwu;
    private boolean attackHitApplied;
    private boolean nextGroundAttackTwo;
    private boolean nextAirAttackTwo;
    private final Map<UUID, Double> approachDistanceSamples = new HashMap<>();
    private Vec3 lastKnownTargetPosition = Vec3.ZERO;

    public MingGuLongEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 30;
        this.setMaxUpStep(1.2f);
        this.moveControl = new MoveControl(this);
    }

    @Override protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 666.0D)
            .add(Attributes.ARMOR, 12.0D)
            .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
            .add(Attributes.ATTACK_DAMAGE, 18.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.28D)
            .add(Attributes.FOLLOW_RANGE, TARGET_DETECTION_RANGE)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
            .add(Attributes.FLYING_SPEED, 0.65D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIMATION, IDLE);
        this.entityData.define(ANIMATION_SERIAL, 0);
        this.entityData.define(ANIMATION_AGE, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));

        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
    }

    @Override
    public void tick() {
        clearFire();
        super.tick();
        if (!level().isClientSide) updateBossBar();
        if (level().isClientSide || isNoAi()) return;
        if (isDeadOrDying()) {
            haltMovement();
            setNoGravity(false);
            playAnimation(DIE);
            return;
        }
        if (attackCooldown > 0) attackCooldown--;
        if (huangwuCooldown > 0) huangwuCooldown--;
        if (huangwuCooldown == 0 && !isHuangwuAnimation()) huangwuPending = true;
        if (targetScanCooldown > 0) targetScanCooldown--;
        if (pathRefreshTicks > 0) pathRefreshTicks--;
        stateTicks++;
        if (isHuangwuAnimation()) entityData.set(ANIMATION_AGE, stateTicks);
        if (targetScanCooldown == 0) {
            targetScanCooldown = 10;
            refreshTarget();
        }
        if (isHuangwuAnimation()) { tickHuangwu(); return; }
        if (isAttackAnimation()) { tickAttack(); return; }

        LivingEntity target = getTarget();
        // Transitions finish before any skill selection, even if a target moves
        // into melee range or the skill cooldown expires during takeoff.
        if (getAnimationKey() == FLY_START) {
            if (target != null) lookAtTarget(target);
            haltMovement();
            if (stateTicks < TAKEOFF_LENGTH_TICKS) {
                if (level().noCollision(this, getBoundingBox().move(0, .07D, 0)))
                    setDeltaMovement(0, .055D, 0);
                return;
            }
            selectFlightMode();
        }
        if (getAnimationKey() == FLY_LAND) {
            haltMovement();
            if (stateTicks < LANDING_LENGTH_TICKS) return;
            playAnimation(IDLE);
        }
        if (target == null) {
            sawTarget = false;
            if (airborne) {
                var ground = level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPosition());
                Vec3 landing = new Vec3(getX(), ground.getY(), getZ());
                if (getY() - landing.y > 1.3D) {
                    navigateToward(landing, .7D);
                    tickFlightMode();
                } else beginLanding();
            } else { haltMovement(); playAnimation(IDLE); }
            return;
        }
        if (!sawTarget) { sawTarget = true; playAnimation(ALERT); }
        if (getAnimationKey() == ALERT) {
            if (stateTicks < ALERT_LENGTH_TICKS) { haltMovement(); lookAtTarget(target); return; }
            playAnimation(ANGER);
        }
        if (getAnimationKey() == ANGER) {
            if (stateTicks < ANGER_LENGTH_TICKS) { haltMovement(); lookAtTarget(target); return; }
            playAnimation(IDLE);
        }
        double horizontal = target.position().subtract(position()).horizontalDistance();
        double vertical = Math.abs(target.getY() - getY());
        boolean sight = hasLineOfSight(target);
        if (!sight && noSightTicks > 200) { setTarget(null); haltMovement(); return; }
        lookAtTarget(target);

        // A ready skill has priority over starting another attack or changing
        // locomotion. The in-progress attack above is never interrupted.
        if (tryStartHuangwu(target)) return;
        boolean wantsFlight = vertical > (airborne ? 5.0D : 8.0D) || horizontal > 24.0D
            || !airborne && failedPathAttempts >= 4;
        if (!airborne && wantsFlight) {
            setFlightNavigation(true);
            flightTicks = 0;
            haltMovement();
            playAnimation(FLY_START);
            return;
        }
        if (airborne) {
            flightTicks++;
            if (!wantsFlight && flightTicks >= 120 && vertical < 3.0D && horizontal < 14.0D
                    && level().clip(new net.minecraft.world.level.ClipContext(position(), position().add(0,-2,0),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,this)).getType()
                        != net.minecraft.world.phys.HitResult.Type.MISS) {
                beginLanding(); return;
            }
        }
        double reach = (airborne ? (nextAirAttackTwo ? AIR_ATTACK_2_REACH : AIR_ATTACK_1_REACH)
            : (nextGroundAttackTwo ? GROUND_ATTACK_2_REACH : GROUND_ATTACK_1_REACH)) + target.getBbWidth() * .5D;
        if (attackCooldown <= 0 && sight && horizontal <= reach && vertical <= (airborne ? 6 : 4)) {
            startAttack(airborne); return;
        }
        if (airborne && sight && horizontal <= reach && vertical <= 6) {
            haltMovement(); playAnimation(FLY_HOVER);
        } else {
            Vec3 destination = sight ? target.position() : lastKnownTargetPosition;
            if (airborne) destination = destination.add(0, Math.min(2, target.getBbHeight() * .5D), 0);
            navigateToward(destination, airborne ? 1.0D : horizontal > 12 ? 1.15D : .85D);
            if (airborne) tickFlightMode();
            else playAnimation(getNavigation().isDone() ? IDLE : horizontal > 12 ? RUN : WALK);
        }
    }

    private boolean tryStartHuangwu(LivingEntity target) {
        if (!huangwuPending || target == null || !target.isAlive() || !hasLineOfSight(target)) return false;
        Vec3 offset = target.position().subtract(position());
        if (offset.horizontalDistance() > 36 || Math.abs(offset.y) > 14) return false;
        startHuangwu();
        return true;
    }

    private void setFlightNavigation(boolean flying) {
        getNavigation().stop();
        airborne = flying;
        setNoGravity(flying);
        if (flying) {
            var flight = new FlyingPathNavigation(this, level());
            flight.setCanPassDoors(true);
            flight.setCanFloat(true);
            navigation = flight;
            moveControl = new FlightControl(this);
        } else {
            navigation = createNavigation(level());
            moveControl = new MoveControl(this);
        }
        pathRefreshTicks = 0;
        failedPathAttempts = 0;
    }

    private void beginLanding() {
        setFlightNavigation(false);
        haltMovement();
        playAnimation(FLY_LAND);
    }

    private void selectFlightMode() {
        // Random full-loop runs, rather than restarting a clip every server tick.
        flightMode = random.nextBoolean() ? FLY_GLIDE : FLY_HOVER;
        flightModeTicks = flightMode == FLY_GLIDE ? 120 * (1 + random.nextInt(2)) : 30 * (2 + random.nextInt(3));
        playAnimation(flightMode);
    }

    private void tickFlightMode() {
        if (--flightModeTicks <= 0) {
            flightMode = flightMode == FLY_GLIDE ? FLY_HOVER : FLY_GLIDE;
            flightModeTicks = flightMode == FLY_GLIDE ? 120 * (1 + random.nextInt(2)) : 30 * (2 + random.nextInt(3));
        }
        playAnimation(flightMode);
    }

    private void navigateToward(Vec3 destination, double speed) {
        // Keep a valid path between refreshes. Both navigators use collision-aware
        // node evaluators; failed paths are retried without moving through walls.
        if (pathRefreshTicks == 0 || destination.distanceToSqr(pathDestination) > 4) {
            pathRefreshTicks = 10;
            pathDestination = destination;
            if (getNavigation().moveTo(destination.x, destination.y, destination.z, speed)) failedPathAttempts = 0;
            else failedPathAttempts++;
        }
    }

    private void haltMovement() {
        getNavigation().stop();
        moveControl.setWantedPosition(getX(), getY(), getZ(), 0);
        setSpeed(0);
        setZza(0);
        setYya(0);
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(0, airborne ? 0 : motion.y, 0);
        pathRefreshTicks = 0;
    }

    private static final class FlightControl extends MoveControl {
        FlightControl(MingGuLongEntity dragon) { super(dragon); }
        @Override public void tick() {
            if (operation != Operation.MOVE_TO) return;
            operation = Operation.WAIT;
            Vec3 offset = new Vec3(wantedX, wantedY, wantedZ).subtract(mob.position());
            double speed = Math.min(offset.length(), speedModifier * .32D);
            Vec3 desired = offset.lengthSqr() < .01D ? Vec3.ZERO : offset.normalize().scale(speed);
            mob.setDeltaMovement(mob.getDeltaMovement().lerp(desired, .25D));
            if (offset.horizontalDistanceSqr() > .04D) {
                float yaw = (float)(Mth.atan2(offset.z, offset.x) * Mth.RAD_TO_DEG - 90);
                mob.setYRot(Mth.approachDegrees(mob.getYRot(), yaw, 8));
            }
            mob.setZza(0);
            mob.setYya(0);
        }
    }

    private void refreshTarget() {
        LivingEntity current = getTarget();
        if (current != null && !isValidTarget(current)) {
            setTarget(null);
            current = null;
        }
        if (current != null && distanceToSqr(current) <= Mth.square(TARGET_DETECTION_RANGE)) {
            if (hasLineOfSight(current)) {
                noSightTicks = 0;
                lastKnownTargetPosition = current.position();
            } else {
                noSightTicks += 10;
            }
            if (noSightTicks <= 200) return;
            setTarget(null);
        }

        LivingEntity attacker = getLastHurtByMob();
        if (attacker != null && isValidTarget(attacker) && distanceToSqr(attacker) <= Mth.square(TARGET_DETECTION_RANGE) && hasLineOfSight(attacker)) {
            setTarget(attacker);
            lastKnownTargetPosition = attacker.position();
            noSightTicks = 0;
            return;
        }
        List<LivingEntity> candidates = ((ServerLevel) level()).getEntitiesOfClass(LivingEntity.class,
            getBoundingBox().inflate(TARGET_DETECTION_RANGE), candidate -> isValidTarget(candidate)
                && distanceToSqr(candidate) <= Mth.square(TARGET_DETECTION_RANGE));
        Set<UUID> sampledTargets = new HashSet<>();
        candidates.forEach(candidate -> sampledTargets.add(candidate.getUUID()));
        candidates.stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(candidate -> {
            setTarget(candidate);
            lastKnownTargetPosition = candidate.position();
            noSightTicks = 0;
        });
        approachDistanceSamples.keySet().retainAll(sampledTargets);
    }

    private boolean isApproaching(LivingEntity candidate) {
        Vec3 towardDragon = position().subtract(candidate.position());
        Vec3 horizontalTowardDragon = new Vec3(towardDragon.x, 0.0D, towardDragon.z);
        double horizontalDistanceSqr = horizontalTowardDragon.lengthSqr();
        Double previousDistanceSqr = approachDistanceSamples.put(candidate.getUUID(), horizontalDistanceSqr);
        double closingSpeed = horizontalDistanceSqr > 0.001D
            ? candidate.getDeltaMovement().dot(horizontalTowardDragon.normalize()) : 0.0D;
        return horizontalDistanceSqr <= Mth.square(12.0D) || closingSpeed > 0.01D
            || (previousDistanceSqr != null && horizontalDistanceSqr + 0.25D < previousDistanceSqr);
    }

    private boolean isValidTarget(LivingEntity candidate) {
        if (candidate instanceof MingGuLongEntity || !candidate.isAlive() || candidate.isAlliedTo(this) || candidate.isInvulnerable()) return false;
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return true;
    }

    private void tickAttack() {
        LivingEntity target = getTarget();
        haltMovement();
        if (target != null) lookAtTarget(target);
        int markerTick = switch (getAnimationKey()) {
            case GROUND_ATTACK_1 -> GROUND_ATTACK_1_CONTACT_TICK;
            case GROUND_ATTACK_2 -> GROUND_ATTACK_2_CONTACT_TICK;
            case AIR_ATTACK_1 -> AIR_ATTACK_1_CONTACT_TICK;
            case AIR_ATTACK_2 -> AIR_ATTACK_2_CONTACT_TICK;
            default -> Integer.MAX_VALUE;
        };
        double clawReach = switch (getAnimationKey()) {
            case GROUND_ATTACK_1 -> GROUND_ATTACK_1_REACH;
            case GROUND_ATTACK_2 -> GROUND_ATTACK_2_REACH;
            case AIR_ATTACK_1 -> AIR_ATTACK_1_REACH;
            case AIR_ATTACK_2 -> AIR_ATTACK_2_REACH;
            default -> 0.0D;
        };
        if (!attackHitApplied && stateTicks >= markerTick) {
            attackHitApplied = true;
            if (target != null && target.isAlive() && hasLineOfSight(target) && clawTouchesTarget(target, clawReach)) {
                MingGuLongDamage.hurt(this, target, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
                target.knockback(0.45D, getX() - target.getX(), getZ() - target.getZ());
            }
        }
        if (stateTicks >= ATTACK_LENGTH_TICKS) {
            attackCooldown = 10;
            if (tryStartHuangwu(target)) return;
            playAnimation(airborne ? FLY_HOVER : IDLE);
            stateTicks = 0;
        }
    }

    private boolean clawTouchesTarget(LivingEntity target, double reach) {
        double horizontal = Math.sqrt(Mth.square(target.getX() - getX()) + Mth.square(target.getZ() - getZ()));
        double vertical = Math.abs(target.getY() + target.getBbHeight() * 0.5D - (getY() + 1.25D));
        double targetPadding = target.getBbWidth() * 0.5D;
        return horizontal <= reach + targetPadding && vertical <= (airborne ? 3.5D : 4.0D);
    }

    private void tickHuangwu() {
        LivingEntity target = getTarget();
        haltMovement();
        if (target != null) lookAtTarget(target);
        getNavigation().stop();
        if (!releasedHuangwu && stateTicks >= HUANGWU_RELEASE_TICK) {
            releasedHuangwu = true;
            if (target != null && target.isAlive()) {
                Vec3 impact = target.position().add(0, target.getBbHeight() * 0.5D, 0);
                MingGuLongProjectile projectile = new MingGuLongProjectile(StarcrownDoll.MINGGULONG_PROJECTILE.get(), level(), this, impact);
                level().addFreshEntity(projectile);
            }
        }
        if (stateTicks >= HUANGWU_LENGTH_TICKS) {
            huangwuCooldown = 360;
            releasedHuangwu = false;
            playAnimation(airborne ? FLY_HOVER : IDLE);
            stateTicks = 0;
        }
    }

    private void startAttack(boolean air) {
        haltMovement();
        int animation;
        if (air) {
            animation = nextAirAttackTwo ? AIR_ATTACK_2 : AIR_ATTACK_1;
            nextAirAttackTwo = !nextAirAttackTwo;
        } else {
            animation = nextGroundAttackTwo ? GROUND_ATTACK_2 : GROUND_ATTACK_1;
            nextGroundAttackTwo = !nextGroundAttackTwo;
        }
        attackHitApplied = false;
        playAnimation(animation);
        stateTicks = 0;
    }

    private void startHuangwu() {
        getNavigation().stop();
        huangwuCooldown = 360;
        huangwuPending = false;
        haltMovement();
        releasedHuangwu = false;
        playAnimation(HUANGWU);
        stateTicks = 0;
    }

    private boolean isHuangwuAnimation() { return getAnimationKey() == HUANGWU; }
    private boolean isAttackAnimation() {
        return getAnimationKey() >= GROUND_ATTACK_1 && getAnimationKey() <= AIR_ATTACK_2;
    }

    private void lookAtTarget(LivingEntity target) {
        Vec3 offset = target.getEyePosition().subtract(getEyePosition());
        float yaw = (float)(Mth.atan2(offset.z, offset.x) * Mth.RAD_TO_DEG - 90);
        float pitch = (float)(-Mth.atan2(offset.y, offset.horizontalDistance()) * Mth.RAD_TO_DEG);
        getLookControl().setLookAt(target, 20, 35);
        setYHeadRot(Mth.approachDegrees(getYHeadRot(), yaw, 20));
        setXRot(Mth.approachDegrees(getXRot(), Mth.clamp(pitch, -35, 35), 8));
        if (getNavigation().isDone()) {
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12));
            yBodyRot = getYRot();
        }
    }

    public void onHuangwuImpact(Vec3 impact, int sequenceId) {
        onHuangwuImpact(impact, sequenceId, yBodyRot);
    }

    public void onHuangwuImpact(Vec3 impact, int sequenceId, float releaseYaw) {
        if (!(level() instanceof ServerLevel server)) return;
        MingGuLongDamage.impact(server, this, impact);
        MingGuLongVortex vortex = new MingGuLongVortex(StarcrownDoll.MINGGULONG_VORTEX.get(), server, this, impact, sequenceId);
        vortex.setYRot(releaseYaw);
        server.addFreshEntity(vortex);
    }

    public float skillBaseDamage() { return 24.0F; }
    public int getAnimationKey() { return entityData.get(ANIMATION); }
    public int getAnimationSerial() { return entityData.get(ANIMATION_SERIAL); }
    public float getHuangwuAge(float partialTick) { return entityData.get(ANIMATION_AGE) + partialTick; }

    public Vec3 getHuangwuChargePosition() {
        // User-approved BB anchor: (5.1113,6.8264,-19.9812), in model units.
        double yaw = Math.toRadians(180.0D - this.yBodyRot);
        double x = -5.11132431D / 16.0D, z = -19.981182098D / 16.0D;
        return position().add(x * Math.cos(yaw) + z * Math.sin(yaw),
            6.826404572D / 16.0D + 0.01D, -x * Math.sin(yaw) + z * Math.cos(yaw));
    }
    public String getAnimationName() { return ANIMATIONS[Mth.clamp(getAnimationKey(), 0, ANIMATIONS.length - 1)]; }

    private void playAnimation(int key) {
        if (getAnimationKey() == key && key != ALERT && key != ANGER) return;
        entityData.set(ANIMATION, key);
        entityData.set(ANIMATION_SERIAL, getAnimationSerial() + 1);
        entityData.set(ANIMATION_AGE, 0);
        stateTicks = 0;
    }

    private void updateBossBar() {
        float max = getMaxHealth();
        float progress = max > 0.0F ? getHealth() / max : 0.0F;
        bossEvent.setProgress(Float.isFinite(progress) ? Mth.clamp(progress, 0.0F, 1.0F) : 0.0F);
        bossEvent.setName(Component.translatable(BOSS_BAR_NAME, getDisplayName()));
        bossEvent.setVisible(isAlive() && !isRemoved());
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        updateBossBar();
        bossEvent.addPlayer(player);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override public void remove(RemovalReason reason) {
        super.remove(reason);
        if (bossEvent != null) bossEvent.removeAllPlayers();
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) { clearFire(); return false; }
        if (isDeadOrDying()) return false;
        return super.hurt(source, amount);
    }

    @Override public void setSecondsOnFire(int seconds) { clearFire(); }

    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return target instanceof LivingEntity living
            && MingGuLongDamage.hurt(this, living, (float)getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("MingGuLongAnimation", getAnimationKey());
        tag.putInt("MingGuLongSkillCooldown", huangwuCooldown);
        tag.putBoolean("MingGuLongAirborne", airborne);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ANIMATION, Mth.clamp(tag.getInt("MingGuLongAnimation"), 0, ANIMATIONS.length - 1));
        huangwuCooldown = Math.max(0, tag.getInt("MingGuLongSkillCooldown"));
        huangwuPending = huangwuCooldown == 0;
        int animation = getAnimationKey();
        setFlightNavigation(tag.getBoolean("MingGuLongAirborne") || animation >= FLY_START && animation <= FLY_HOVER || animation == AIR_ATTACK_1 || animation == AIR_ATTACK_2);
        if (airborne && animation != FLY_START) selectFlightMode();
        else if (animation == HUANGWU || isAttackAnimation()) playAnimation(IDLE);
    }

    public boolean canSpawn(MobSpawnType reason) { return reason != MobSpawnType.NATURAL || level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "minggulong_controller", 0, this::animationPredicate));
    }

    private <E extends MingGuLongEntity> PlayState animationPredicate(AnimationState<E> state) {
        state.getController().setAnimation(RawAnimation.begin().thenPlay(getAnimationName()));
        return PlayState.CONTINUE;
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
