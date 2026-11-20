package com.skyblockgirlfriend.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.PlayerSkin;

public final class SkinHeadRenderer {
	private SkinHeadRenderer() {
	}

	public static void draw(GuiGraphicsExtractor graphics, PlayerSkin skin, int x, int y, int size) {
		var texture = skin.body().texturePath();
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
				8, 8, size, size, 8, 8, 64, 64);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
				40, 8, size, size, 8, 8, 64, 64);
	}
}
