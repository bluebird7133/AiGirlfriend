package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

public final class CompanionSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger("skyblock-girlfriend");
	private static final float MIN_SCALE = 0.3F;
	private static final float MAX_SCALE = 2.0F;
	private static final int MAX_PREVIEW_SKIN_CACHE = 32;

	private final Path settingsFile = FabricLoader.getInstance().getConfigDir()
			.resolve("skyblock-girlfriend")
			.resolve("settings.properties");
	private final Path skinsDirectory = FabricLoader.getInstance().getGameDir().resolve("skins");
	private final List<SkinFile> skins = new ArrayList<>();
	private float scale = 0.5F;
	private boolean invisible;
	private float notificationPositionX = 0.02F;
	private float notificationPositionY = 0.08F;
	private int notificationWidth = 280;
	private int notificationHeight = 52;
	private float conversationPositionX;
	private float conversationPositionY = 0.5F;
	private int conversationWidth = 170;
	private int conversationHeight = 180;
	private String companionName = "Companion";
	private String selectedSkinFile = "";
	private String skinDirectoryMessage = "";
	private LoadedSkin loadedSkin;
	private final LinkedHashMap<String, LoadedSkin> previewSkins = new LinkedHashMap<>(16, 0.75F, true);

	public CompanionSettings() {
		try {
			load();
		} catch (IOException exception) {
			LOGGER.error("Could not load companion settings", exception);
		}
		refreshSkins();
	}

	public float scale() {
		return scale;
	}

	public void setScale(float scale) {
		this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
	}

	public float notificationPositionX() {
		return notificationPositionX;
	}

	public float notificationPositionY() {
		return notificationPositionY;
	}

	public void setNotificationPosition(float x, float y) {
		notificationPositionX = clampPosition(x);
		notificationPositionY = clampPosition(y);
	}

	public int notificationWidth() {
		return notificationWidth;
	}

	public int notificationHeight() {
		return notificationHeight;
	}

	public void setNotificationSize(int width, int height) {
		notificationWidth = Math.max(280, Math.min(4096, width));
		notificationHeight = Math.max(52, Math.min(4096, height));
	}

	public float conversationPositionX() {
		return conversationPositionX;
	}

	public float conversationPositionY() {
		return conversationPositionY;
	}

	public void setConversationPosition(float x, float y) {
		conversationPositionX = clampPosition(x);
		conversationPositionY = clampPosition(y);
	}

	public int conversationWidth() {
		return conversationWidth;
	}

	public int conversationHeight() {
		return conversationHeight;
	}

	public void setConversationSize(int width, int height) {
		conversationWidth = Math.max(170, Math.min(4096, width));
		conversationHeight = Math.max(180, Math.min(4096, height));
	}

	public String companionName() {
		return companionName;
	}

	public String companionDisplayName() {
		String trimmedName = companionName.trim();
		return trimmedName.isEmpty() ? "Companion" : trimmedName;
	}

	public void setCompanionName(String name) {
		if (name != null) {
			companionName = name.length() <= 32 ? name : name.substring(0, 32);
		}
	}

	public boolean isInvisible() {
		return invisible;
	}

	public void toggleInvisible() {
		invisible = !invisible;
		save();
	}

	public Path skinsDirectory() {
		return skinsDirectory;
	}

	public List<SkinFile> availableSkins() {
		return List.copyOf(skins);
	}

	public boolean hasSkinsDirectory() {
		return Files.isDirectory(skinsDirectory);
	}

	public String skinDirectoryMessage() {
		return skinDirectoryMessage;
	}

	public String selectedSkinFile() {
		return selectedSkinFile;
	}

	public String selectedSkinName() {
		SkinFile selected = findSkin(selectedSkinFile);
		return selected == null ? "Current Skin" : selected.name();
	}

	public void selectSkin(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			selectedSkinFile = "";
			save();
			return;
		}

		if (findSkin(fileName) == null) {
			LOGGER.warn("Ignoring selection of a skin outside the instance skins folder: {}", fileName);
			return;
		}

		selectedSkinFile = fileName;
		save();
	}

	public boolean refreshSkins() {
		skins.clear();
		skinDirectoryMessage = "";
		if (!Files.isDirectory(skinsDirectory)) {
			skinDirectoryMessage = "Cannot locate skins directory. Please create a skins directory in your Minecraft instance.";
			return false;
		}

		try (Stream<Path> files = Files.list(skinsDirectory)) {
			files.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
					.map(path -> {
						String fileName = path.getFileName().toString();
						return new SkinFile(fileName, fileName.substring(0, fileName.length() - 4));
					})
					.sorted(Comparator.comparing(SkinFile::name, String.CASE_INSENSITIVE_ORDER))
					.forEach(skins::add);
		} catch (IOException exception) {
			LOGGER.error("Could not read skins directory {}", skinsDirectory, exception);
			skinDirectoryMessage = "Could not read the skins directory. Check the game log.";
			return false;
		}

		if (!selectedSkinFile.isEmpty() && findSkin(selectedSkinFile) == null) {
			LOGGER.warn("Selected companion skin '{}' was not found in {}", selectedSkinFile, skinsDirectory);
			selectedSkinFile = "";
			save();
		}
		LOGGER.info("Found {} companion skin PNG(s) in {}", skins.size(), skinsDirectory);
		return true;
	}

	public void save() {
		Properties properties = new Properties();
		properties.setProperty("scale", Float.toString(scale));
		properties.setProperty("invisible", Boolean.toString(invisible));
		properties.setProperty("notification_x", Float.toString(notificationPositionX));
		properties.setProperty("notification_y", Float.toString(notificationPositionY));
		properties.setProperty("notification_width", Integer.toString(notificationWidth));
		properties.setProperty("notification_height", Integer.toString(notificationHeight));
		properties.setProperty("conversation_x", Float.toString(conversationPositionX));
		properties.setProperty("conversation_y", Float.toString(conversationPositionY));
		properties.setProperty("conversation_width", Integer.toString(conversationWidth));
		properties.setProperty("conversation_height", Integer.toString(conversationHeight));
		properties.setProperty("companion_name", companionName);
		properties.setProperty("skin", selectedSkinFile);
		try {
			Files.createDirectories(settingsFile.getParent());
			try (OutputStream output = Files.newOutputStream(settingsFile)) {
				properties.store(output, "SkyBlock Girlfriend companion settings");
			}
		} catch (IOException exception) {
			LOGGER.error("Could not save companion settings to {}", settingsFile, exception);
		}
	}

	public PlayerSkin skinForRender(Minecraft client, PlayerSkin currentSkin) {
		SkinFile selected = findSkin(selectedSkinFile);
		if (selected == null) {
			releaseLoadedSkin(client);
			return currentSkin;
		}

		try {
			loadedSkin = loadSkin(client, selected, loadedSkin, false);
			return loadedSkin.skin();
		} catch (IOException exception) {
			LOGGER.error("Could not load companion skin '{}'", selected.fileName(), exception);
			releaseLoadedSkin(client);
			selectedSkinFile = "";
			save();
			return currentSkin;
		}
	}

	public PlayerSkin skinForPreview(Minecraft client, PlayerSkin currentSkin) {
		return skinForPreview(client, selectedSkinFile, currentSkin);
	}

	public PlayerSkin skinForPreview(Minecraft client, String fileName, PlayerSkin currentSkin) {
		SkinFile selected = findSkin(fileName);
		if (selected == null) {
			return currentSkin;
		}

		LoadedSkin cached = previewSkins.get(selected.fileName());
		try {
			LoadedSkin preview = loadSkin(client, selected, cached, true);
			previewSkins.put(selected.fileName(), preview);
			evictPreviewSkins(client);
			return preview.skin();
		} catch (IOException exception) {
			LOGGER.error("Could not preview companion skin '{}'", selected.fileName(), exception);
			if (cached != null) {
				previewSkins.remove(selected.fileName());
				client.getTextureManager().release(cached.textureId());
			}
			return currentSkin;
		}
	}

	public void releasePreviewSkin(Minecraft client) {
		for (LoadedSkin preview : previewSkins.values()) {
			client.getTextureManager().release(preview.textureId());
		}
		previewSkins.clear();
	}

	private void evictPreviewSkins(Minecraft client) {
		Iterator<Map.Entry<String, LoadedSkin>> iterator = previewSkins.entrySet().iterator();
		while (previewSkins.size() > MAX_PREVIEW_SKIN_CACHE && iterator.hasNext()) {
			Map.Entry<String, LoadedSkin> oldest = iterator.next();
			client.getTextureManager().release(oldest.getValue().textureId());
			iterator.remove();
		}
	}

	private LoadedSkin loadSkin(Minecraft client, SkinFile selected, LoadedSkin cached, boolean preview)
			throws IOException {
		Path skinPath = skinsDirectory.resolve(selected.fileName());
		long size = Files.size(skinPath);
		FileTime modified = Files.getLastModifiedTime(skinPath);
		if (cached != null && cached.fileName().equals(selected.fileName())
				&& cached.size() == size && cached.modified().equals(modified)) {
			return cached;
		}

		NativeImage image;
		try (InputStream input = Files.newInputStream(skinPath)) {
			image = NativeImage.read(input);
		}
		if (image.getWidth() != 64 || image.getHeight() != 64) {
			image.close();
			throw new IOException("Skin images must be 64 by 64 pixels.");
		}

		PlayerModelType modelType = detectModelType(image);
		Identifier textureId = textureId(selected.fileName(), preview);
		DynamicTexture texture = new DynamicTexture(() -> "SkyBlock Girlfriend skin " + selected.name(), image);
		if (cached != null) {
			client.getTextureManager().release(cached.textureId());
		}
		client.getTextureManager().register(textureId, texture);
		PlayerSkin skin = PlayerSkin.insecure(new SkinTexture(textureId), null, null, modelType);
		return new LoadedSkin(selected.fileName(), size, modified, textureId, skin);
	}

	private static PlayerModelType detectModelType(NativeImage image) {
		// Slim-arm skins leave the fourth column of the right arm transparent.
		for (int y = 20; y < 32; y++) {
			if ((image.getPixel(54, y) >>> 24) != 0) {
				return PlayerModelType.WIDE;
			}
		}
		return PlayerModelType.SLIM;
	}

	private void releaseLoadedSkin(Minecraft client) {
		if (loadedSkin != null) {
			client.getTextureManager().release(loadedSkin.textureId());
			loadedSkin = null;
		}
	}

	private SkinFile findSkin(String fileName) {
		for (SkinFile skin : skins) {
			if (skin.fileName().equals(fileName)) {
				return skin;
			}
		}
		return null;
	}

	private static Identifier textureId(String fileName, boolean preview) {
		String textureKey = UUID.nameUUIDFromBytes(fileName.toLowerCase(Locale.ROOT)
				.getBytes(StandardCharsets.UTF_8)).toString();
		String directory = preview ? "previews/" : "skins/";
		return Identifier.fromNamespaceAndPath("skyblock-girlfriend", directory + textureKey);
	}

	private void load() throws IOException {
		if (!Files.exists(settingsFile)) {
			return;
		}

		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(settingsFile)) {
			properties.load(input);
		}
		String savedScale = properties.getProperty("scale");
		if (savedScale != null) {
			try {
				float parsedScale = Float.parseFloat(savedScale);
				if (Float.isFinite(parsedScale)) {
					setScale(parsedScale);
				} else {
					LOGGER.warn("Ignoring non-finite companion scale '{}'", savedScale);
				}
			} catch (NumberFormatException exception) {
				LOGGER.warn("Ignoring invalid companion scale '{}'", savedScale);
			}
		}
		String savedInvisible = properties.getProperty("invisible");
		if ("true".equalsIgnoreCase(savedInvisible) || "false".equalsIgnoreCase(savedInvisible)) {
			invisible = Boolean.parseBoolean(savedInvisible);
		} else if (savedInvisible != null) {
			LOGGER.warn("Ignoring invalid companion invisible setting '{}'", savedInvisible);
		}
		notificationPositionX = readPosition(properties, "notification_x", notificationPositionX);
		notificationPositionY = readPosition(properties, "notification_y", notificationPositionY);
		setNotificationSize(
				readSize(properties, "notification_width", notificationWidth),
				readSize(properties, "notification_height", notificationHeight)
		);
		conversationPositionX = readPosition(properties, "conversation_x", conversationPositionX);
		conversationPositionY = readPosition(properties, "conversation_y", conversationPositionY);
		setConversationSize(
				readSize(properties, "conversation_width", conversationWidth),
				readSize(properties, "conversation_height", conversationHeight)
		);
		setCompanionName(properties.getProperty("companion_name", companionName));
		selectedSkinFile = properties.getProperty("skin", "");
	}

	private static int readSize(Properties properties, String key, int defaultValue) {
		String savedValue = properties.getProperty(key);
		if (savedValue == null) {
			return defaultValue;
		}
		try {
			return Math.max(1, Integer.parseInt(savedValue));
		} catch (NumberFormatException exception) {
			LOGGER.warn("Ignoring invalid companion size '{}' for {}", savedValue, key);
			return defaultValue;
		}
	}

	private static float readPosition(Properties properties, String key, float defaultValue) {
		String savedValue = properties.getProperty(key);
		if (savedValue == null) {
			return defaultValue;
		}
		try {
			float parsed = Float.parseFloat(savedValue);
			if (Float.isFinite(parsed)) {
				return clampPosition(parsed);
			}
			LOGGER.warn("Ignoring non-finite notification position '{}'", savedValue);
		} catch (NumberFormatException exception) {
			LOGGER.warn("Ignoring invalid notification position '{}'", savedValue);
		}
		return defaultValue;
	}

	private static float clampPosition(float position) {
		return Math.max(0.0F, Math.min(1.0F, position));
	}

	public record SkinFile(String fileName, String name) {
	}

	private record LoadedSkin(String fileName, long size, FileTime modified, Identifier textureId, PlayerSkin skin) {
	}

	private record SkinTexture(Identifier id) implements ClientAsset.Texture {
		@Override
		public Identifier texturePath() {
			return id;
		}
	}
}
