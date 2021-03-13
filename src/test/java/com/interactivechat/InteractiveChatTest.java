package com.interactivechat;

import com.interactivechat.InteractiveChatPlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class InteractiveChatTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(InteractiveChatPlugin.class);
		RuneLite.main(args);
	}
}