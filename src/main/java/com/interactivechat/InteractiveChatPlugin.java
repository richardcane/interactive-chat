package com.interactivechat;

import com.google.inject.Provides;

import java.awt.Color;
import java.util.regex.Pattern;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@PluginDescriptor(
	name = "Interactive Chat"
)
public class InteractiveChatPlugin extends Plugin
{
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
	InteractiveChatConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InteractiveChatConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		final ChatMessageType type = chatMessage.getType();
		if (type != ChatMessageType.FRIENDSCHAT &&
				type != ChatMessageType.PRIVATECHAT &&
				type != ChatMessageType.PUBLICCHAT)
		{
			return;
		}

		String messageContent = chatMessage.getMessage();
		String[] parts = SEARCH_PATTERN.split(messageContent);

		ChatMessageBuilder builder = new ChatMessageBuilder();
		for (String part : parts)
		{
			if (!part.startsWith(LEFT_DELIMITER))
			{
				builder.append(ChatColorType.NORMAL);
				builder.append(part);
				continue;
			}

			builder.append(LINK_COLOR, part.trim());
		}

		final MessageNode messageNode = chatMessage.getMessageNode();
		messageNode.setRuneLiteFormatMessage(builder.build());
		chatMessageManager.update(messageNode);
		client.refreshChat();
	}
}
