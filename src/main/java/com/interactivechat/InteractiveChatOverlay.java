package com.interactivechat;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
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

class InteractiveChatOverlay extends Overlay {
	private final Client client;

	static final HttpUrl WIKI_BASE = HttpUrl.parse("https://oldschool.runescape.wiki");
	static final Pattern BRACKETED_PATTERN = Pattern.compile("((?<=\\])|(?=\\[))", Pattern.DOTALL);
	static final String WITH_DELIMITER_REGEX = "((?<=%1$s)|(?=%1$s))";
	static final String LEFT_DELIMITER = "[";
	static final String RIGHT_DELIMITER = "]";
	static final String HITBOX_WIDGET_NAME = "InteractiveChatHitbox";
	static final int CHATLINE_MAX_WIDTH = 486;
	static final int CHATLINE_HEIGHT = 14;

	private Widget chatboxWidget;
	private Widget hitboxWidget;
	private String search = "";

	@Inject
	InteractiveChatOverlay(Client client, EventBus eventBus) {
		this.client = client;

		eventBus.register(this);
		setPosition(OverlayPosition.DYNAMIC);
	}
	
	@SuppressWarnings("serial")
	class KeywordBoundary extends Rectangle {
		final String term;

		KeywordBoundary(String term, int x, int y, int width) {
			super(x, y, width, CHATLINE_HEIGHT);
			this.term = term;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (client.isMenuOpen()) {
			return null;
		}

		if (hitboxWidget == null) {
			createHitboxWidget();
			if (hitboxWidget == null)
				return null;
		}
		setHitboxBounds(0, 0, 0);

		final net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		final Point mousePoint = new Point(mouse.getX(), mouse.getY());
		Widget message = getChatMessageAtPoint(mousePoint);
		if (message == null) {
			return null;
		}

		final FontTypeFace font = message.getFont();
		final String text = Text.removeFormattingTags(message.getText());
		final Rectangle messageBounds = message.getBounds();
		final int messageWidth = message.getWidth();

		int currentWidth = 0;
		int currentY = (int) messageBounds.getMinY();

		final int minX = (int) messageBounds.getMinX();
		final int xd = minX - message.getOriginalX();
		final int yd = currentY - message.getOriginalY();

		final List<KeywordBoundary> boundaries = new ArrayList<KeywordBoundary>();

		for (String part : BRACKETED_PATTERN.split(text)) {
			final boolean isBracketedPart = part.startsWith(LEFT_DELIMITER) && part.endsWith(RIGHT_DELIMITER);
			final int partWidth = font.getTextWidth(part);

			if (currentWidth + partWidth > messageWidth) {
				for (String word : part.split("(?=\\s+)")) {
					final int wordWidth = font.getTextWidth(word);
					if (currentWidth + wordWidth < messageWidth) {
						currentWidth += wordWidth;
						continue;
					} else {
						final int trimmedWidth = font.getTextWidth(word.trim());
						currentWidth = trimmedWidth;
						currentY += CHATLINE_HEIGHT;
					}
				}
			} else if (isBracketedPart) {
				String term = part.replace(LEFT_DELIMITER, "").replace(RIGHT_DELIMITER, "");
				boundaries.add(new KeywordBoundary(term, minX + currentWidth, currentY, partWidth));
				currentWidth += partWidth;
			} else {
				currentWidth += partWidth;
			}
		}
		
		for (KeywordBoundary bounds : boundaries) {
			if (bounds.contains(mousePoint)) {
				setHitboxBounds(bounds.x - xd, bounds.y - yd, bounds.width);
				search = bounds.term;

				final Rectangle underline = new Rectangle(bounds.x + 2, bounds.y + CHATLINE_HEIGHT,
						bounds.width - 4, 1);
				graphics.setPaint(InteractiveChatPlugin.LINK_COLOR);
				graphics.fill(underline);
			}
		}

		return null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		switch (event.getGameState()) {
		case LOGGING_IN:
			chatboxWidget = null;
			hitboxWidget = null;
			break;
		default:
			break;
		}
	}

	private void createHitboxWidget() {
		chatboxWidget = getChatboxWidget();
		if (chatboxWidget == null)
			return;

		hitboxWidget = chatboxWidget.createChild(-1, WidgetType.RECTANGLE);
		hitboxWidget.setName(HITBOX_WIDGET_NAME);
		hitboxWidget.setOpacity(255);
		hitboxWidget.setOriginalX(0);
		hitboxWidget.setOriginalY(0);
		hitboxWidget.setOriginalWidth(0);
		hitboxWidget.setOriginalHeight(14);
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

	private Widget getChatMessageAtPoint(Point point) {
		chatboxWidget = getChatboxWidget();
		if (chatboxWidget == null || !chatboxWidget.getBounds().contains(point)) {
			return null;
		}

		Optional<Widget> maybeChatMessage = Stream.of(chatboxWidget.getChildren()).filter(widget -> !widget.isHidden())
				.filter(widget -> widget.getWidth() != CHATLINE_MAX_WIDTH) // ignore various game messages and parent chat lines
				.filter(widget -> widget.getName() != HITBOX_WIDGET_NAME)
				.filter(widget -> widget.getId() < WidgetInfo.CHATBOX_FIRST_MESSAGE.getId())
				.filter(widget -> widget.getBounds().contains(point))
				.findFirst();

		if (!maybeChatMessage.isPresent()) {
			return null;
		}
		return maybeChatMessage.get();
	}

	private Widget getChatboxWidget() {
		if (chatboxWidget != null) {
			return chatboxWidget;
		}

		return client.getWidget(WidgetInfo.CHATBOX_MESSAGE_LINES);
	}

	private void search(ScriptEvent ev) {
		LinkBrowser.browse(WIKI_BASE.newBuilder().addQueryParameter("search", search).build().toString());
	}

}
