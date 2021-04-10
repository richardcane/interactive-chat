package com.interactivechat;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class InteractiveChatTest {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(InteractiveChatPlugin.class);
    RuneLite.main(args);
  }
}