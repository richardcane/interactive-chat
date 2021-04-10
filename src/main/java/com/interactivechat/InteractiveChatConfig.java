package com.interactivechat;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("interactivechat")
public interface InteractiveChatConfig extends Config {
  @ConfigItem(
      keyName = "itemColor",
      name = "Item color",
      description = "The color of interactive chat items"
  )
  default Color itemColor() {
    return new Color(85, 175, 251);
  }

  @ConfigItem(
    keyName = "onHover",
    name = "On hover",
    description = "What the plugin should do when you hover over a keyword"
  )
  default HoverMode onHover() {
    return HoverMode.UNDERLINE;
  }
}
