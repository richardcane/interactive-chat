/*

Copyright (c) 2021, Richard Cane
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.interactivechat;

import com.google.inject.Provides;
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
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
    name = "Interactive Chat",
    description = "Lets users send interactive chat messages",
    tags = {"interactive", "chat", "wiki", "search"})
public class InteractiveChatPlugin extends Plugin {
  static final Pattern BRACKETED_PATTERN = InteractiveChat.BRACKETED_PATTERN;
  static final String LEFT_DELIMITER = InteractiveChat.LEFT_DELIMITER;
  static final String RIGHT_DELIMITER = InteractiveChat.RIGHT_DELIMITER;

  
  @Inject private Client client;
  
  @Inject private OverlayManager overlayManager;
  
  @Inject private ChatMessageManager chatMessageManager;
  
  @Inject private InteractiveChatConfig config;

  @Inject private InteractiveChatOverlay overlay;

  @Inject private InteractiveChatOverlayMouseListener interactiveChatOverlayMouseListener;
  
  @Inject private MouseManager mouseManager;

  @Provides
  InteractiveChatConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(InteractiveChatConfig.class);
  }

  @Override
  protected void startUp() throws Exception {
    overlayManager.add(overlay);
    mouseManager.registerMouseListener(interactiveChatOverlayMouseListener);
  }

  @Override
  protected void shutDown() throws Exception {
    overlay.unsetContainerWidgets();
    overlayManager.remove(overlay);
    mouseManager.unregisterMouseListener(interactiveChatOverlayMouseListener);
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
    }

    final String message = Text.removeFormattingTags(chatMessage.getMessage());
    final String[] parts = BRACKETED_PATTERN.split(message);
    if (parts.length == 1 && !message.startsWith(LEFT_DELIMITER)) {
      return;
    }

    ChatMessageBuilder builder = new ChatMessageBuilder();
    for (String part : parts) {
      if (!part.startsWith(LEFT_DELIMITER) || !part.endsWith(RIGHT_DELIMITER)) {
        builder.append(ChatColorType.NORMAL);
        builder.append(part);
        continue;
      }

      final String searchTerm = part.substring(1, part.length() - 1);
      builder.append(config.textColor(), String.format("[%s]", searchTerm.trim().replaceAll(" +", " ")));
    }

    final String finalMessage = builder.build().replaceAll("<lt>", "<").replaceAll("<gt>", ">");
    final MessageNode messageNode = chatMessage.getMessageNode();
    messageNode.setRuneLiteFormatMessage(finalMessage);
    chatMessageManager.update(messageNode);
    client.refreshChat();
  }
}
