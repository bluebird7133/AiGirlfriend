package com.skyblockgirlfriend.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class ClientFollowerMob extends Zombie {
	private final float companionScale;

	public ClientFollowerMob(ClientLevel level, float companionScale) {
		super(level);
		this.companionScale = companionScale;
		// This controller is never added to the world or sent to the server.
		setNoAi(true);
		setSilent(true);
		getNavigation().setCanFloat(true);
		refreshDimensions();
	}

	@Override
	public boolean isEffectiveAi() {
		return level().isClientSide();
	}

	@Override
	protected boolean isLocalClientAuthoritative() {
		return level().isClientSide();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(Entity entity) {
		return false;
	}

	public void tickLocalMovement() {
		super.tick();
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		float scale = companionScale > 0.0F ? companionScale : 1.0F;
		return super.getDefaultDimensions(pose).scale(scale);
	}
}
