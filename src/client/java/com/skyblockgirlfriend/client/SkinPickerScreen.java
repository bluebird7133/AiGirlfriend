package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class SkinPickerScreen extends Screen {
	private final CompanionSettings settings;
	private final Screen parent;
	private SkinList skinList;
	private EditBox searchBox;
	private int listCenterX;
	private int listTop;

	public SkinPickerScreen(CompanionSettings settings, Screen parent) {
		super(Component.literal("Select companion skin"));
		this.settings = settings;
		this.parent = parent;
	}

	@Override
	protected void init() {
		settings.refreshSkins();
		listTop = !settings.skinDirectoryMessage().isEmpty() ? 116 : 80;
		int contentWidth = Math.min(560, Math.max(240, width - 40));
		int contentLeft = (width - contentWidth) / 2;
		listCenterX = width / 2;
		int previewWidth = Math.min(64, Math.max(32, (contentWidth - 28) / 3 - 16));
		int previewHeight = previewWidth * 4 / 3;
		searchBox = new EditBox(font, contentLeft, 48, contentWidth, 20, Component.literal("Search skins"));
		searchBox.setHint(Component.literal("Search skins..."));
		searchBox.setMaxLength(128);
		addRenderableWidget(searchBox);

		skinList = new SkinList(minecraft, contentWidth, Math.max(36, height - listTop - 56),
				listTop, previewHeight + 38, previewWidth, previewHeight);
		skinList.setX(contentLeft);
		addRenderableWidget(skinList);
		searchBox.setResponder(skinList::filter);
		skinList.filter("");

		addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
				.bounds(width / 2 - 100, height - 42, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.literal("Select a skin"), width / 2, 25, 0xFFFFFF);
		graphics.centeredText(font, Component.literal(
				settings.availableSkins().size() + " skin file(s) found"), listCenterX, 34, 0xAAAAAA);
		if (skinList != null && skinList.filteredCount() == 0) {
			graphics.centeredText(font, Component.literal("No skins match this search."),
					listCenterX, listTop + 20, 0xAAAAAA);
		}
		if (!settings.skinDirectoryMessage().isEmpty()) {
			graphics.textWithWordWrap(font, Component.literal(settings.skinDirectoryMessage()),
					12, height - 76, width - 24, 0xFFFF55);
			graphics.textWithWordWrap(font, Component.literal(settings.skinsDirectory().toString()),
					12, height - 52, width - 24, 0xAAAAAA);
		}
	}

	@Override
	public void removed() {
		settings.releasePreviewSkin(minecraft);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private final class SkinList extends AbstractSelectionList<SkinList.SkinRow> {
		private static final int COLUMNS = 3;
		private static final int CARD_GAP = 8;
		private int filteredCount;
		private final int previewWidth;
		private final int previewHeight;

		private SkinList(
				Minecraft minecraft,
				int width,
				int height,
				int top,
				int entryHeight,
				int previewWidth,
				int previewHeight
		) {
			super(minecraft, width, height, top, entryHeight);
			this.previewWidth = previewWidth;
			this.previewHeight = previewHeight;
		}

		private void filter(String query) {
			String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
			List<SkinOption> matches = new ArrayList<>();
			if (normalizedQuery.isEmpty() || "current skin".contains(normalizedQuery)) {
				matches.add(new SkinOption("", "Current Skin"));
			}
			for (CompanionSettings.SkinFile skin : settings.availableSkins()) {
				if (skin.fileName().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
					matches.add(new SkinOption(skin.fileName(), skin.name()));
				}
			}

			clearEntries();
			setSelected(null);
			setScrollAmount(0.0);
			filteredCount = matches.size();
			for (int index = 0; index < matches.size(); index += COLUMNS) {
				int end = Math.min(index + COLUMNS, matches.size());
				addEntry(new SkinRow(List.copyOf(matches.subList(index, end))));
			}
		}

		private int filteredCount() {
			return filteredCount;
		}

		@Override
		protected void extractListBackground(GuiGraphicsExtractor graphics) {
		}

		@Override
		protected void extractSelection(GuiGraphicsExtractor graphics, SkinRow row, int y) {
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, Component.literal("Available companion skins"));
		}

		private final class SkinRow extends AbstractSelectionList.Entry<SkinRow> {
			private final List<SkinOption> options;
			private final List<CardPreview> previews;

			private SkinRow(List<SkinOption> options) {
				this.options = options;
				Minecraft client = SkinPickerScreen.this.minecraft;
				List<CardPreview> previews = new ArrayList<>(options.size());
				for (SkinOption option : options) {
					previews.add(new CardPreview(previewWidth, previewHeight, client,
							() -> client.player == null
									? DefaultPlayerSkin.getDefaultSkin()
									: settings.skinForPreview(client, option.fileName(), client.player.getSkin())));
				}
				this.previews = List.copyOf(previews);
			}

			@Override
			public void extractContent(
					GuiGraphicsExtractor graphics,
					int mouseX,
					int mouseY,
					boolean hovered,
					float partialTick
			) {
				int cardWidth = (getContentWidth() - CARD_GAP * (COLUMNS - 1)) / COLUMNS;
				int cardY = getContentY() + 4;
				int cardHeight = getContentHeight() - 8;
				for (int column = 0; column < options.size(); column++) {
					SkinOption option = options.get(column);
					int cardX = getContentX() + column * (cardWidth + CARD_GAP);
					boolean selected = option.fileName().equals(settings.selectedSkinFile());
					graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight,
							selected ? 0xCC15341D : 0xCC141414);
					graphics.outline(cardX, cardY, cardWidth, cardHeight,
							selected ? 0xFF55DD66 : hovered ? 0xFFAAAAAA : 0xFF555555);
					CardPreview preview = previews.get(column);
					preview.setX(cardX + (cardWidth - previewWidth) / 2);
					preview.setY(cardY + 4);
					preview.extract(graphics, mouseX, mouseY, partialTick);
					drawCardName(graphics, option.name(), cardX, cardWidth,
							cardY + cardHeight - font.lineHeight - 6, selected ? 0xFFFFFFFF : 0xFFD0D0D0);
				}
			}

			private void drawCardName(
					GuiGraphicsExtractor graphics,
					String name,
					int cardX,
					int cardWidth,
					int textY,
					int color
			) {
				int textX = cardX + 6;
				int visibleWidth = cardWidth - 12;
				int textWidth = font.width(name);
				if (textWidth <= visibleWidth) {
					graphics.centeredText(font, name, cardX + cardWidth / 2, textY, color);
					return;
				}

				double maxOffset = textWidth - visibleWidth;
				double phase = System.nanoTime() / 1_000_000_000.0 * 12.0 % (maxOffset * 2.0);
				int offset = (int) Math.round(phase <= maxOffset ? phase : maxOffset * 2.0 - phase);
				graphics.enableScissor(textX, textY, textX + visibleWidth, textY + font.lineHeight);
				graphics.text(font, name, textX - offset, textY, color);
				graphics.disableScissor();
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (event.button() != 0) {
					return false;
				}
				int cardWidth = (getContentWidth() - CARD_GAP * (COLUMNS - 1)) / COLUMNS;
				int relativeX = (int) event.x() - getContentX();
				int column = relativeX / (cardWidth + CARD_GAP);
				if (column >= COLUMNS || relativeX % (cardWidth + CARD_GAP) >= cardWidth) {
					return false;
				}
				if (column < 0 || column >= options.size()) {
					return false;
				}

				settings.selectSkin(options.get(column).fileName());
				SkinList.this.setSelected(this);
				return true;
			}
		}
	}

	private static final class CardPreview extends PlayerSkinWidget {
		private CardPreview(int width, int height, Minecraft minecraft, Supplier<PlayerSkin> skin) {
			super(width, height, minecraft.getEntityModels(), skin);
		}

		private void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
		}
	}

	private record SkinOption(String fileName, String name) {
	}
}
