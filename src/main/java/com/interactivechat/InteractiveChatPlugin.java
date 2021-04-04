package com.interactivechat;

import com.google.inject.Provides;

import java.awt.Color;
import java.util.regex.Pattern;

import javax.inject.Inject;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "Interactive Chat")
public class InteractiveChatPlugin extends Plugin {
	private static final Pattern SEARCH_PATTERN = Pattern.compile("((?<=\\])|(?=\\[))", Pattern.DOTALL);
	private static final String LEFT_DELIMITER = "[";
	private static final String RIGHT_DELIMITER = "]";
	private final Color LINK_COLOR = Color.GREEN;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Client client;

	@Inject
	private InteractiveChatConfig config;

	@Inject
	private InteractiveChatOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Provides
	InteractiveChatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(InteractiveChatConfig.class);
	}

	@Override
	protected void startUp() throws Exception {
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception {
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage) {
		final ChatMessageType type = chatMessage.getType();
		switch (type) {
		case MODCHAT:
		case PUBLICCHAT:
		case PRIVATECHAT:
		case PRIVATECHATOUT:
		case MODPRIVATECHAT:
		case FRIENDSCHAT:
			break;
		default:
			return;
		};

		ChatMessageBuilder builder = new ChatMessageBuilder();
		for (String part : SEARCH_PATTERN.split(chatMessage.getMessage())) {
			if (!part.startsWith(LEFT_DELIMITER)) {
				builder.append(ChatColorType.NORMAL);
				builder.append(part);
				continue;
			}

			final String searchTerm = part.substring(1, part.length() - 1);
			builder.append(LINK_COLOR, String.format("[%s]", searchTerm.trim().replaceAll(" +", " ")));
		}

		final MessageNode messageNode = chatMessage.getMessageNode();
		messageNode.setRuneLiteFormatMessage(builder.build());
		chatMessageManager.update(messageNode);
		client.refreshChat();
	}
}
