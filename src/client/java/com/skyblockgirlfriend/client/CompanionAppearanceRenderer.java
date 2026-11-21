package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

public final class CompanionAppearanceRenderer {
	private static final int HORIZONTAL_SEGMENTS = 12;
	private static final int VERTICAL_SEGMENTS = 8;
	private static final float PI = (float) Math.PI;
	private static final float HALF_PI = PI / 2.0F;
	private static final float TORSO_FRONT_LEFT = 20.0F;
	private static final float TORSO_FRONT_RIGHT = 28.0F;
	private static final float TORSO_FRONT_TOP = 20.0F;
	private static final float TORSO_FRONT_BOTTOM = 32.0F;
	private static final float TORSO_OVERLAY_TOP = 36.0F;
	private static final float TORSO_CENTER_X = 24.0F;
	private static final float TORSO_TOP_Y = 1.5F;

	private CompanionAppearanceRenderer() {
	}

	public static void submit(
			LevelRenderContext context,
			AvatarRenderState state,
			CompanionSettings settings,
			float bodyYaw,
			float walkPhase,
			float walkSpeed
	) {
		if (!settings.isAppearanceEnabled()) {
			return;
		}

		float scale = settings.scale();
		float activity = settings.isAppearancePhysicsEnabled()
				? Math.max(0.0F, Math.min(1.0F, walkSpeed))
				: 0.0F;
		float phase = walkPhase * 0.6F;
		float bounce = settings.isAppearancePhysicsEnabled()
				? (float) Math.sin(phase) * settings.bustBounce() * activity * 0.1F
				: 0.0F;
		float flop = settings.isAppearancePhysicsEnabled()
				? (float) Math.sin(phase + 0.65F) * settings.bustFloppiness() * activity * 8.0F
				: 0.0F;
		float skinCenterY = 1.08F + settings.bustHeight();
		float centerY = skinCenterY + bounce;
		float skinPixelOffsetY = -bounce * 16.0F;
		float centerZ = -0.145F - settings.bustDepth();
		float radiusX = 0.115F * settings.bustSize();
		float radiusY = 0.11F * settings.bustSize();
		float radiusZ = 0.085F * settings.bustSize();
		float centerOffset = 0.105F * settings.bustSpacing();
		Vec3 camera = context.levelState().cameraRenderState.pos;

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(state.x - camera.x, state.y - camera.y, state.z - camera.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
		poseStack.scale(scale, scale, scale);

		var collector = context.submitNodeCollector();
		var skinTexture = state.skin.body().texturePath();
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(skinTexture),
				(pose, vertices) -> drawPair(vertices, pose, state.lightCoords, settings,
						centerY, skinCenterY, skinPixelOffsetY, centerZ, radiusX, radiusY, radiusZ,
						centerOffset, flop, false));
		if (state.showJacket) {
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(skinTexture),
					(pose, vertices) -> drawPair(vertices, pose, state.lightCoords, settings,
							centerY, skinCenterY, skinPixelOffsetY, centerZ - 0.003F,
							radiusX, radiusY, radiusZ, centerOffset, flop, true));
		}
		poseStack.popPose();
	}

	private static void drawPair(
			VertexConsumer vertices,
			PoseStack.Pose pose,
			int light,
			CompanionSettings settings,
			float centerY,
			float skinCenterY,
			float skinPixelOffsetY,
			float centerZ,
			float radiusX,
			float radiusY,
			float radiusZ,
			float centerOffset,
			float flop,
			boolean outerLayer
	) {
		drawDome(vertices, pose, light, -centerOffset, centerY, centerZ,
				radiusX, radiusY, radiusZ,
				skinCenterY, skinPixelOffsetY, -settings.bustAngle() - flop, outerLayer);
		drawDome(vertices, pose, light, centerOffset, centerY, centerZ,
				radiusX, radiusY, radiusZ,
				skinCenterY, skinPixelOffsetY, settings.bustAngle() + flop, outerLayer);
	}

	private static void drawDome(
			VertexConsumer vertices,
			PoseStack.Pose pose,
			int light,
			float centerX,
			float centerY,
			float centerZ,
			float radiusX,
			float radiusY,
			float radiusZ,
			float skinCenterY,
			float skinPixelOffsetY,
			float rotationDegrees,
			boolean outerLayer
	) {
		float rotation = rotationDegrees * ((float) Math.PI / 180.0F);
		float sinRotation = (float) Math.sin(rotation);
		float cosRotation = (float) Math.cos(rotation);
		for (int vertical = 0; vertical < VERTICAL_SEGMENTS; vertical++) {
			float phi0 = -HALF_PI + PI * vertical / VERTICAL_SEGMENTS;
			float phi1 = -HALF_PI + PI * (vertical + 1) / VERTICAL_SEGMENTS;
			for (int horizontal = 0; horizontal < HORIZONTAL_SEGMENTS; horizontal++) {
				float theta0 = -HALF_PI + PI * horizontal / HORIZONTAL_SEGMENTS;
				float theta1 = -HALF_PI + PI * (horizontal + 1) / HORIZONTAL_SEGMENTS;
				vertex(vertices, pose, light, centerX, centerY, centerZ, radiusX, radiusY, radiusZ,
						theta0, phi0, sinRotation, cosRotation,
						skinCenterY, skinPixelOffsetY,
						outerLayer);
				vertex(vertices, pose, light, centerX, centerY, centerZ, radiusX, radiusY, radiusZ,
						theta0, phi1, sinRotation, cosRotation,
						skinCenterY, skinPixelOffsetY,
						outerLayer);
				vertex(vertices, pose, light, centerX, centerY, centerZ, radiusX, radiusY, radiusZ,
						theta1, phi1, sinRotation, cosRotation,
						skinCenterY, skinPixelOffsetY,
						outerLayer);
				vertex(vertices, pose, light, centerX, centerY, centerZ, radiusX, radiusY, radiusZ,
						theta1, phi0, sinRotation, cosRotation,
						skinCenterY, skinPixelOffsetY,
						outerLayer);
			}
		}
	}

	private static void vertex(
			VertexConsumer vertices,
			PoseStack.Pose pose,
			int light,
			float centerX,
			float centerY,
			float centerZ,
			float radiusX,
			float radiusY,
			float radiusZ,
			float theta,
			float phi,
			float sinRotation,
			float cosRotation,
			float skinCenterY,
			float skinPixelOffsetY,
			boolean outerLayer
	) {
		float sinTheta = (float) Math.sin(theta);
		float cosTheta = (float) Math.cos(theta);
		float sinPhi = (float) Math.sin(phi);
		float cosPhi = (float) Math.cos(phi);
		float localX = radiusX * sinTheta * cosPhi;
		float localY = radiusY * sinPhi;
		float localZ = -radiusZ * cosTheta * cosPhi;
		float rotatedX = localX * cosRotation + localZ * sinRotation;
		float rotatedZ = -localX * sinRotation + localZ * cosRotation;
		float normalX = sinTheta * cosPhi / radiusX;
		float normalY = sinPhi / radiusY;
		float normalZ = -cosTheta * cosPhi / radiusZ;
		float rotatedNormalX = normalX * cosRotation + normalZ * sinRotation;
		float rotatedNormalZ = -normalX * sinRotation + normalZ * cosRotation;
		float normalLength = (float) Math.sqrt(
				rotatedNormalX * rotatedNormalX + normalY * normalY + rotatedNormalZ * rotatedNormalZ);
		float skinX = clamp(TORSO_CENTER_X + (centerX + rotatedX) * 16.0F,
				TORSO_FRONT_LEFT, TORSO_FRONT_RIGHT);
		float skinY = clamp(TORSO_FRONT_TOP + (TORSO_TOP_Y - skinCenterY - localY) * 16.0F
						+ skinPixelOffsetY,
				TORSO_FRONT_TOP, TORSO_FRONT_BOTTOM);
		float overlayOffset = outerLayer ? TORSO_OVERLAY_TOP - TORSO_FRONT_TOP : 0.0F;
		float u = skinX / 64.0F;
		float v = (skinY + overlayOffset) / 64.0F;

		vertices.addVertex(pose, centerX + rotatedX, centerY + localY, centerZ + rotatedZ)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, rotatedNormalX / normalLength, normalY / normalLength, rotatedNormalZ / normalLength);
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
