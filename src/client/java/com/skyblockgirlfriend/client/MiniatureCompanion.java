package com.skyblockgirlfriend.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class MiniatureCompanion {
	private static final double FOLLOW_DISTANCE = 1.65;
	private static final double FOLLOW_START_DISTANCE = 3.0;
	private static final double TELEPORT_DISTANCE = 20.0;
	private static final double FOLLOW_SPEED = 1.5;
	private static final int REPATH_INTERVAL = 10;
	private static final float PLAYER_STANDING_HEIGHT = 1.8F;

	private double x;
	private double y;
	private double z;
	private double filteredPlayerMoveX;
	private double filteredPlayerMoveZ;
	private double previousX;
	private double previousY;
	private double previousZ;
	private double previousPlayerX;
	private double previousPlayerZ;
	private double lastPlayerGroundY;
	private double lastTargetX;
	private double lastTargetY;
	private double lastTargetZ;
	private float trailYaw;
	private float bodyYaw;
	private float headYaw;
	private float headPitch;
	private float previousWalkPhase;
	private float walkPhase;
	private float previousWalkSpeed;
	private float walkSpeed;
	private int repathTicks;
	private boolean initialized;
	private boolean following;
	private ClientLevel navigationLevel;
	private ClientFollowerMob navigationMob;
	private float navigationScale;
	private final CompanionSettings settings;

	public MiniatureCompanion(CompanionSettings settings) {
		this.settings = settings;
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			initialized = false;
			following = false;
			navigationMob = null;
			navigationLevel = null;
			return;
		}

		if (!initialized) {
			initialize(client.level, player);
			return;
		}

		ensureNavigationMob(client.level);
		previousX = x;
		previousY = y;
		previousZ = z;
		previousWalkPhase = walkPhase;
		previousWalkSpeed = walkSpeed;
		updateTrailDirection(player);
		if (player.onGround()) {
			lastPlayerGroundY = player.getY();
		}

		double playerDx = x - player.getX();
		double playerDy = y - player.getY();
		double playerDz = z - player.getZ();
		if (playerDx * playerDx + playerDy * playerDy + playerDz * playerDz
				> TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
			teleportNear(player);
			updateLookAtPlayer(player);
			return;
		}

		double ownerDistance = distanceTo(player);
		boolean separatedByElevation = Math.abs(lastPlayerGroundY - y) >= 0.6
				&& ownerDistance > FOLLOW_DISTANCE + 0.2;
		if (!following && (ownerDistance > FOLLOW_START_DISTANCE || separatedByElevation)) {
			following = true;
			repathTicks = 0;
		} else if (following && ownerDistance <= FOLLOW_DISTANCE + 0.2) {
			following = false;
			repathTicks = 0;
			navigationMob.getNavigation().stop();
		}

		updateNavigation(player);

		double movedX = x - previousX;
		double movedZ = z - previousZ;
		double movedDistance = Math.sqrt(movedX * movedX + movedZ * movedZ);
		if (movedDistance > 0.002) {
			walkPhase += (float) (movedDistance * 3.6);
		}
		float targetWalkSpeed = (float) Math.min(1.0, movedDistance * 8.0);
		walkSpeed += (targetWalkSpeed - walkSpeed) * 0.35F;
		updateLookAtPlayer(player);
	}

	public void addRenderState(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (!initialized || settings.isInvisible() || player == null || client.level == null) {
			return;
		}

		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (!(client.getEntityRenderDispatcher().extractEntity(player, partialTick) instanceof AvatarRenderState state)) {
			return;
		}

		state.x = interpolate(previousX, x, partialTick);
		state.y = interpolate(previousY, y, partialTick);
		state.z = interpolate(previousZ, z, partialTick);
		state.scale *= settings.scale();
		state.nameTag = Component.literal(settings.companionDisplayName());
		state.scoreText = null;
		state.nameTagAttachment = new Vec3(0.0,
				(PLAYER_STANDING_HEIGHT + 0.08F) * settings.scale(), 0.0);
		state.bodyRot = bodyYaw;
		state.yRot = wrapDegrees(headYaw - bodyYaw);
		state.xRot = headPitch;
		state.deathTime = 0.0F;
		state.hasRedOverlay = false;
		state.ticksSinceKineticHitFeedback = 0.0F;
		state.isFullyFrozen = false;
		state.walkAnimationPos = (float) interpolate(previousWalkPhase, walkPhase, partialTick);
		state.walkAnimationSpeed = (float) interpolate(previousWalkSpeed, walkSpeed, partialTick);
		state.skin = settings.skinForRender(client, state.skin);
		state.pose = Pose.STANDING;
		state.isCrouching = false;
		state.isFallFlying = false;
		state.isVisuallySwimming = false;
		state.isPassenger = false;
		state.isUsingItem = false;
		state.isInWater = false;
		state.isAutoSpinAttack = false;
		state.swimAmount = 0.0F;
		state.attackTime = 0.0F;
		state.showHat = true;
		state.showJacket = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
		state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		state.rightHandItemStack = ItemStack.EMPTY;
		state.leftHandItemStack = ItemStack.EMPTY;
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.heldOnHead.clear();
		state.wornHeadType = null;
		state.wornHeadProfile = null;
		state.showCape = false;
		state.arrowCount = 0;
		state.stingerCount = 0;
		state.parrotOnLeftShoulder = null;
		state.parrotOnRightShoulder = null;
		state.displayFireAnimation = false;

		CameraRenderState cameraState = context.levelState().cameraRenderState;
		state.distanceToCameraSq = cameraState.pos.distanceToSqr(state.x, state.y, state.z);
		client.getEntityRenderDispatcher().submit(
				state,
				cameraState,
				state.x - cameraState.pos.x,
				state.y - cameraState.pos.y,
				state.z - cameraState.pos.z,
				context.poseStack(),
				context.submitNodeCollector()
		);
		CompanionAppearanceRenderer.submit(
				context,
				state,
				settings,
				bodyYaw,
				(float) interpolate(previousWalkPhase, walkPhase, partialTick),
				(float) interpolate(previousWalkSpeed, walkSpeed, partialTick)
		);
	}

	private void initialize(ClientLevel level, LocalPlayer player) {
		trailYaw = player.getYRot();
		double yaw = Math.toRadians(trailYaw);
		x = player.getX() + Math.sin(yaw) * FOLLOW_DISTANCE;
		y = player.getY();
		z = player.getZ() - Math.cos(yaw) * FOLLOW_DISTANCE;
		previousX = x;
		previousY = y;
		previousZ = z;
		previousPlayerX = player.getX();
		previousPlayerZ = player.getZ();
		lastPlayerGroundY = player.getY();
		bodyYaw = player.getYRot();
		headYaw = bodyYaw;
		headPitch = 0.0F;
		previousWalkPhase = 0.0F;
		walkPhase = 0.0F;
		previousWalkSpeed = 0.0F;
		walkSpeed = 0.0F;
		filteredPlayerMoveX = 0.0;
		filteredPlayerMoveZ = 0.0;
		repathTicks = 0;
		following = false;
		initialized = true;
		navigationMob = null;
		ensureNavigationMob(level);
		navigationMob.setOnGround(player.onGround());
		updateLookAtPlayer(player);
	}

	private void ensureNavigationMob(ClientLevel level) {
		if (navigationMob != null && navigationLevel == level
				&& Float.compare(navigationScale, settings.scale()) == 0) {
			return;
		}

		boolean onGround = navigationMob == null || navigationMob.onGround();
		navigationMob = new ClientFollowerMob(level, settings.scale());
		navigationLevel = level;
		navigationScale = settings.scale();
		navigationMob.setPos(x, y, z);
		navigationMob.setOnGround(onGround);
		navigationMob.setYRot(trailYaw);
		navigationMob.setYHeadRot(trailYaw);
		repathTicks = 0;
	}

	private void updateTrailDirection(LocalPlayer player) {
		double playerMoveX = player.getX() - previousPlayerX;
		double playerMoveZ = player.getZ() - previousPlayerZ;
		filteredPlayerMoveX = filteredPlayerMoveX * 0.75 + playerMoveX * 0.25;
		filteredPlayerMoveZ = filteredPlayerMoveZ * 0.75 + playerMoveZ * 0.25;
		if (filteredPlayerMoveX * filteredPlayerMoveX + filteredPlayerMoveZ * filteredPlayerMoveZ > 0.000004) {
			float movementYaw = (float) Math.toDegrees(Math.atan2(-filteredPlayerMoveX, filteredPlayerMoveZ));
			trailYaw = approachAngle(trailYaw, movementYaw, 0.08F);
		}
		previousPlayerX = player.getX();
		previousPlayerZ = player.getZ();
	}

	private void updateNavigation(LocalPlayer player) {
		if (navigationMob.isSprinting() != following) {
			navigationMob.setSprinting(following);
		}
		if (following) {
			updateNavigationTarget(player);
			navigationMob.getNavigation().tick();
		} else {
			navigationMob.getNavigation().stop();
			navigationMob.getMoveControl().setWait();
		}

		navigationMob.getMoveControl().tick();
		navigationMob.getJumpControl().tick();
		navigationMob.tickLocalMovement();
		x = navigationMob.getX();
		y = navigationMob.getY();
		z = navigationMob.getZ();
	}

	private void updateNavigationTarget(LocalPlayer player) {
		double yaw = Math.toRadians(trailYaw);
		double targetX = player.getX() + Math.sin(yaw) * FOLLOW_DISTANCE;
		double targetZ = player.getZ() - Math.cos(yaw) * FOLLOW_DISTANCE;
		double targetY = lastPlayerGroundY;
		boolean targetMoved = Math.hypot(targetX - lastTargetX, targetZ - lastTargetZ) > 1.0
				|| Math.abs(targetY - lastTargetY) > 0.5;
		if (repathTicks <= 0 || targetMoved) {
			navigationMob.getNavigation().moveTo(targetX, targetY, targetZ, FOLLOW_SPEED);
			lastTargetX = targetX;
			lastTargetY = targetY;
			lastTargetZ = targetZ;
			repathTicks = REPATH_INTERVAL;
		} else {
			repathTicks--;
		}
	}

	private void teleportNear(LocalPlayer player) {
		double yaw = Math.toRadians(trailYaw);
		x = player.getX() + Math.sin(yaw) * FOLLOW_DISTANCE;
		z = player.getZ() - Math.cos(yaw) * FOLLOW_DISTANCE;
		y = lastPlayerGroundY;
		navigationMob.setPos(x, y, z);
		navigationMob.setOnGround(player.onGround());
		navigationMob.setDeltaMovement(Vec3.ZERO);
		navigationMob.getNavigation().stop();
		navigationMob.getMoveControl().setWait();
		navigationMob.setSprinting(false);
		previousX = x;
		previousY = y;
		previousZ = z;
		previousWalkSpeed = 0.0F;
		walkSpeed = 0.0F;
		following = false;
		repathTicks = 0;
	}

	private double distanceTo(LocalPlayer player) {
		double dx = x - player.getX();
		double dy = y - player.getY();
		double dz = z - player.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private void updateLookAtPlayer(LocalPlayer player) {
		double dx = player.getX() - x;
		double dy = player.getEyeY() - (y + 1.5 * settings.scale());
		double dz = player.getZ() - z;
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));

		bodyYaw = approachAngle(bodyYaw, targetYaw, 0.18F);
		float smoothedHeadYaw = approachAngle(headYaw, targetYaw, 0.32F);
		float headOffset = wrapDegrees(smoothedHeadYaw - bodyYaw);
		headYaw = bodyYaw + Math.max(-70.0F, Math.min(70.0F, headOffset));
		headPitch += (Math.max(-45.0F, Math.min(45.0F, targetPitch)) - headPitch) * 0.25F;
	}

	private static double interpolate(double from, double to, float partialTick) {
		return from + (to - from) * partialTick;
	}

	private static float approachAngle(float current, float target, float amount) {
		return current + wrapDegrees(target - current) * amount;
	}

	private static float wrapDegrees(float degrees) {
		degrees %= 360.0F;
		if (degrees >= 180.0F) {
			degrees -= 360.0F;
		}
		if (degrees < -180.0F) {
			degrees += 360.0F;
		}
		return degrees;
	}
}
