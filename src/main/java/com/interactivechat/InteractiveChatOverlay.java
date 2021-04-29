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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

import okhttp3.HttpUrl;
import org.apache.commons.lang3.ArrayUtils;

class InteractiveChatOverlay extends Overlay {
	private final InteractiveChatConfig config;
	private final Client client;

	static final HttpUrl WIKI_BASE = HttpUrl.parse("https://oldschool.runescape.wiki");
	static final Pattern BRACKETED_PATTERN = Pattern.compile("((?<=\\])|(?=\\[))", Pattern.DOTALL);
	static final String LEFT_DELIMITER = "[";
	static final String RIGHT_DELIMITER = "]";
	static final String HITBOX_WIDGET_NAME = "InteractiveChatHitbox";
	static final int CHATLINE_HEIGHT = 14;
	static final int VARPLAYER_ENABLE_SPLIT_CHAT = 287;
	static final int VARBIT_HIDE_SPLIT_CHAT = 4089;

	private Widget messageLinesWidget;
	private Widget splitChatWidget;
	private Widget hitboxWidget;
	private String search = "";

	@Inject
	InteractiveChatOverlay(InteractiveChatConfig config, Client client, EventBus eventBus) {
		this.config = config;
		this.client = client;

		eventBus.register(this);
		setPosition(OverlayPosition.DYNAMIC);
	}

	private static class Match extends Rectangle {
		final int index;
		final String term;

		Match(int index, String term, int x, int y, int width) {
			super(x, y + 4, width, CHATLINE_HEIGHT);
			this.index = index;
			this.term = term;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		final net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		final Point mousePoint = new Point(mouse.getX(), mouse.getY());
		if (client.isMenuOpen()) {
			return null;
		}

		if (hitboxWidget == null) {
			initHitboxWidget();
			if (hitboxWidget == null)
				return null;
		}
		setHitboxBounds(0, 0, 0);

		Widget messageWidget = getMessageWidgetAtPoint(mousePoint);
		if (messageWidget == null) {
			return null;
		}

		final String message = Text.removeFormattingTags(messageWidget.getText());
		final String[] messageParts = BRACKETED_PATTERN.split(message);
		if (messageParts.length == 1 && !message.startsWith(LEFT_DELIMITER)) {
			return null;
		}

		final FontTypeFace font = messageWidget.getFont();
		final Rectangle messageBounds = messageWidget.getBounds();
		final int messageWidgetWidth = messageWidget.getWidth();

		final int minY = (int) messageBounds.getMinY();
		final int minX = (int) messageBounds.getMinX();

		int searchIndex = 0;
		int incrementedWidth = 0;
		int incrementedY = minY;
		final List<Match> matches = new ArrayList<Match>();

		for (String part : messageParts) {
			final boolean bracketed = part.startsWith(LEFT_DELIMITER) && part.endsWith(RIGHT_DELIMITER);
			final int partWidth = font.getTextWidth(part);

			String term = "";
			if (bracketed) {
				term = part.replace(LEFT_DELIMITER, "").replace(RIGHT_DELIMITER, "");
				searchIndex++;
			}

			if (incrementedWidth + partWidth > messageWidgetWidth) {
				for (String word : part.split("(?=\\s+)")) {
					final int wordWidth = font.getTextWidth(word);
					if (incrementedWidth + wordWidth <= messageWidgetWidth) {
						if (bracketed) {
							matches.add(new Match(searchIndex, term, minX + incrementedWidth, incrementedY, wordWidth));
						}

						incrementedWidth += wordWidth;
						continue;
					} else if (wordWidth > messageWidgetWidth) {
						// keeps hitbox positioning correct
						// when people spam keys like
						// hi fffffffffffffffffffffffffffffffffff [hitbox]
						// where a single word exceeds the widget width

						// if it's not the first word in the message
						// then it'll get put on its own line
						if (incrementedWidth > 0) {
							incrementedY += CHATLINE_HEIGHT;
						}

						if (bracketed) {
							matches.add(new Match(searchIndex, term, minX + incrementedWidth, incrementedY, wordWidth));
						}

						incrementedY += CHATLINE_HEIGHT;
						incrementedWidth = 0;
						break;
					} else {
						// new line, trim and reset incremented width
						final int trimmedWidth = font.getTextWidth(word.trim());
						incrementedY += CHATLINE_HEIGHT;
						incrementedWidth = trimmedWidth;

						if (bracketed) {
							matches.add(new Match(searchIndex, term, minX, incrementedY, trimmedWidth));
						}
					}
				}
			} else if (bracketed) {
				matches.add(new Match(searchIndex, term, minX + incrementedWidth, incrementedY, partWidth));
				incrementedWidth += partWidth;
			} else {
				incrementedWidth += partWidth;
			}
		}

		List<Match> keywords = new ArrayList<Match>();
		for (Match bounds : matches) {
			if (!bounds.contains(mousePoint)) {
				continue;
			}
			
			setHitboxBounds(bounds.x, bounds.y - 4, bounds.width + 1);
			search = bounds.term;

			if (config.onHover() == HoverMode.OFF) {
				break;
			}

			keywords = matches.stream().filter(keywordBounds -> keywordBounds.index == bounds.index)
					.collect(Collectors.toList());
			break;
		}

		if (config.onHover() == HoverMode.OFF) {
			return null;
		}

		final int wordCount = keywords.size();
		for (int i = 0; i < wordCount; i++) {
			Match bounds = keywords.get(i);

			// width and x modifications make it look nicer.
			int x = i == 0 ? bounds.x + 2 : bounds.x;
			int width = wordCount > 1 && (i == 0 || i == wordCount - 1) ? bounds.width - 2 : bounds.width - 4;

			// -4 correction because of earlier repositioning
			final Rectangle hoverEffect = new Rectangle(x, bounds.y + CHATLINE_HEIGHT - 4, width, 1);

			if (config.onHover() == HoverMode.HIGHLIGHT) {
				hoverEffect.x = bounds.x;
				hoverEffect.y = bounds.y - 3;
				hoverEffect.height = CHATLINE_HEIGHT;
				hoverEffect.width = bounds.width + 1;
			}

			graphics.setPaint(config.itemColor());
			graphics.fill(hoverEffect);
		}

		return null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		switch (event.getGameState()) {
		case LOGGING_IN:
		case HOPPING:
			destroy();
			break;
		default:
			break;
		}
	}

	public void destroy() {
		splitChatWidget = getSplitChatWidget();
		if (splitChatWidget == null) {
			return;
		}

		Widget[] children = splitChatWidget.getChildren();
		int index = ArrayUtils.indexOf(children, hitboxWidget);
		if (index != ArrayUtils.INDEX_NOT_FOUND) {
			children[index] = null;
		}

		hitboxWidget = null;
		messageLinesWidget = null;
		splitChatWidget = null;
	}

	private void initHitboxWidget() {
		splitChatWidget = getSplitChatWidget();
		if (splitChatWidget == null) {
			return;
		}

		hitboxWidget = splitChatWidget.createChild(-1, WidgetType.RECTANGLE);
		hitboxWidget.setName(HITBOX_WIDGET_NAME);
		hitboxWidget.setOpacity(255);
		hitboxWidget.setOriginalX(0);
		hitboxWidget.setOriginalY(0);
		hitboxWidget.setOriginalWidth(0);
		hitboxWidget.setOriginalHeight(CHATLINE_HEIGHT + 2);
		hitboxWidget.setNoClickThrough(true);

		hitboxWidget.setHasListener(true);
		hitboxWidget.setOnClickListener((JavaScriptCallback) this::search);

		hitboxWidget.revalidate();
	}

	private void setHitboxBounds(int x, int y, int width) {
		if (hitboxWidget.getOriginalX() == x && hitboxWidget.getOriginalY() == y && hitboxWidget.getWidth() == width) {
			return;
		}

		hitboxWidget.setOriginalX(x);
		hitboxWidget.setOriginalY(y);
		hitboxWidget.setOriginalWidth(width);

		hitboxWidget.revalidate();
	}

	private Widget getMessageWidgetAtPoint(Point point) {
		messageLinesWidget = getMessageLinesWidget();

		// TODO: chatbox hidden or not
		if (messageLinesWidget != null && messageLinesWidget.getBounds().contains(point)) {
			Optional<Widget> maybeMessageWidget = Stream.of(messageLinesWidget.getChildren())
					// 486 = chatbox width; ignores various game messages and parent chat lines
					.filter(widget -> !widget.isHidden()).filter(widget -> widget.getWidth() != 486)
					.filter(widget -> widget.getId() < WidgetInfo.CHATBOX_FIRST_MESSAGE.getId())
					.filter(widget -> widget.getBounds().contains(point)).findFirst();

			if (!maybeMessageWidget.isPresent()) {
				return null;
			}
			return maybeMessageWidget.get();
		}
		
		boolean splitChatEnabled = client.getVarpValue(VARPLAYER_ENABLE_SPLIT_CHAT) > 0;
		boolean splitChatHidden = client.getVarbitValue(VARBIT_HIDE_SPLIT_CHAT) > 0;
		if (!splitChatEnabled || splitChatHidden) {
			return null;
		}
		
		splitChatWidget = getSplitChatWidget();
		Optional<Widget> maybeSplitMessageWidget = Stream.of(splitChatWidget.getChildren())
			// 519 = split chat widget width
			.filter(widget -> widget.getWidth() != 519)
			.filter(widget -> widget.getBounds().contains(point)).findFirst();

		if (!maybeSplitMessageWidget.isPresent()) {
			return null;
		}
		return maybeSplitMessageWidget.get();
	}

	private Widget getMessageLinesWidget() {
		if (messageLinesWidget != null) {
			return messageLinesWidget;
		}

		return client.getWidget(WidgetInfo.CHATBOX_MESSAGE_LINES);
	}

	private Widget getSplitChatWidget() {
		if (splitChatWidget != null) {
			return splitChatWidget;
		}

		return client.getWidget(WidgetInfo.PRIVATE_CHAT_MESSAGE);
	}

	private void search(ScriptEvent ev) {
		LinkBrowser.browse(WIKI_BASE.newBuilder().addQueryParameter("search", search).build().toString());
		setHitboxBounds(0, 0, 0);
	}
}


// varplayer 287 split chat
// varbit 4089 split chat hidden
