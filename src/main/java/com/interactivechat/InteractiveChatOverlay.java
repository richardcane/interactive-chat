package com.interactivechat;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
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
	private final InteractiveChatConfig config;
	private final EventBus eventBus;

	static final HttpUrl WIKI_BASE = HttpUrl.parse("https://oldschool.runescape.wiki");
	static final Pattern SEARCH_PATTERN = Pattern.compile("((?<=\\])|(?=\\[))", Pattern.DOTALL);
	static final String LEFT_DELIMITER = "[";
	static final String RIGHT_DELIMITER = "]";
	static final String HITBOX_WIDGET_NAME = "InteractiveChatHitbox";

	private Widget chatboxWidget;
	private Widget hitboxWidget;
	private String search = "";

	@Inject
	InteractiveChatOverlay(Client client, InteractiveChatConfig config, EventBus eventBus) {
		this.client = client;
		this.config = config;
		this.eventBus = eventBus;

		eventBus.register(this);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (hitboxWidget == null) {
			createHitboxWidget();
			if (hitboxWidget == null)
				return null;
		}
		setHitboxPosition(0, 0, 0);

		final net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		final Point mousePoint = new Point(mouse.getX(), mouse.getY());

		Widget message = getChatMessageAtPoint(mousePoint);
		if (client.isMenuOpen() || message == null) {
			return null;
		}

		final FontTypeFace font = message.getFont();
		final String text = Text.removeFormattingTags(message.getText());
		final Rectangle messageBounds = message.getBounds();

		int xForBounds = (int) messageBounds.getMinX();
		int xForHitbox = message.getOriginalX();

		for (String part : SEARCH_PATTERN.split(text)) {
			final int partWidth = font.getTextWidth(part);
			if (!part.startsWith(LEFT_DELIMITER) || !part.endsWith(RIGHT_DELIMITER)) {
				xForBounds += partWidth;
				xForHitbox += partWidth;
				continue;
			}

			Rectangle partBounds = new Rectangle(xForBounds, (int) messageBounds.getMinY() + 1, partWidth, messageBounds.height);
			if (partBounds.contains(mousePoint)) {
				setHitboxPosition(xForHitbox, message.getOriginalY() + 1, partWidth);
				search = part.replace(LEFT_DELIMITER, "").replace(RIGHT_DELIMITER, "");

				final Rectangle underline = new Rectangle(partBounds.x + 2, partBounds.y + partBounds.height - 1,
						partBounds.width - 4, 1);
				graphics.setPaint(new Color(85, 175, 251));
				graphics.fill(underline);
			}

			xForBounds += partWidth;
			xForHitbox += partWidth;
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

	private void setHitboxPosition(int x, int y, int width) {
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
		if (chatboxWidget == null)
			return null;

		Optional<Widget> maybeChatMessage = Stream.of(chatboxWidget.getChildren()).filter(widget -> !widget.isHidden())
				.filter(widget -> widget.getWidth() != 486) // ignore various game messages and parent chat lines
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
