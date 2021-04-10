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

class InteractiveChatOverlay extends Overlay {
	private final InteractiveChatConfig config;
	private final Client client;

	static final HttpUrl WIKI_BASE = HttpUrl.parse("https://oldschool.runescape.wiki");
	static final Pattern BRACKETED_PATTERN = Pattern.compile("((?<=\\])|(?=\\[))", Pattern.DOTALL);
	static final String LEFT_DELIMITER = "[";
	static final String RIGHT_DELIMITER = "]";
	static final String HITBOX_WIDGET_NAME = "InteractiveChatHitbox";
	static final int CHATLINE_HEIGHT = 14;

	private Widget chatboxWidget;
	private Widget hitboxWidget;
	private String search = "";

	@Inject
	InteractiveChatOverlay(InteractiveChatConfig config, Client client, EventBus eventBus) {
		this.config = config;
		this.client = client;

		eventBus.register(this);
		setPosition(OverlayPosition.DYNAMIC);
	}
	
	@SuppressWarnings("serial")
	class KeywordBoundary extends Rectangle {
		final int index;
		final String term;

		KeywordBoundary(int index, String term, int x, int y, int width) {
			super(x, y, width, CHATLINE_HEIGHT);
			this.index = index;
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
		Widget messageWidget = getChatMessageAtPoint(mousePoint);
		if (messageWidget == null) {
			return null;
		}

		final FontTypeFace font = messageWidget.getFont();
		final String message = Text.removeFormattingTags(messageWidget.getText());
		final Rectangle messageBounds = messageWidget.getBounds();
		final int messageWidgetWidth = messageWidget.getWidth();

		// +4 results in better final position
		final int minY = (int) messageBounds.getMinY() + 4;
		final int minX = (int) messageBounds.getMinX();
		
		int keywordIndex = 0;
		int calculatedWidth = 0;
		int currentY = minY;
		final List<KeywordBoundary> keywordBoundaries = new ArrayList<KeywordBoundary>();
		
		for (String part : BRACKETED_PATTERN.split(message)) {
			final boolean isBracketedPart = part.startsWith(LEFT_DELIMITER) && part.endsWith(RIGHT_DELIMITER);
			final int partWidth = font.getTextWidth(part);

			String term = "";
			if (isBracketedPart) {
				term = part.replace(LEFT_DELIMITER, "").replace(RIGHT_DELIMITER, "");
				keywordIndex++;
			}

			if (calculatedWidth + partWidth > messageWidgetWidth) {
				for (String word : part.split("(?=\\s+)")) {
					final int wordWidth = font.getTextWidth(word);
					if (calculatedWidth + wordWidth <= messageWidgetWidth) {
						if (isBracketedPart) {
							keywordBoundaries.add(new KeywordBoundary(keywordIndex, term, minX + calculatedWidth, currentY, wordWidth));
						}
						
						calculatedWidth += wordWidth;
						continue;
					} else if (wordWidth > messageWidgetWidth) {
						// keeps hitbox positioning correct
						// when people spam keys like
						// hi fffffffffffffffffffffffffffffffffff [hitbox]
						// where a single word exceeds the widget width
						if (calculatedWidth > 0) {
							// if it's not the first word in the message
							// then it'll get put on its own line
							currentY += CHATLINE_HEIGHT;
						}

						if (isBracketedPart) {
							keywordBoundaries.add(new KeywordBoundary(keywordIndex, term, minX + calculatedWidth, currentY, wordWidth));
						}

						currentY += CHATLINE_HEIGHT;
						calculatedWidth = 0;
						break;
					} else {
						final int trimmedWidth = font.getTextWidth(word.trim());
						currentY += CHATLINE_HEIGHT;
						calculatedWidth = trimmedWidth;

						if (isBracketedPart) {
							keywordBoundaries.add(new KeywordBoundary(keywordIndex, term, minX, currentY, trimmedWidth));
						}
					}
				}
			} else if (isBracketedPart) {
				keywordBoundaries.add(new KeywordBoundary(keywordIndex, term, minX + calculatedWidth, currentY, partWidth));
				calculatedWidth += partWidth;
			} else {
				calculatedWidth += partWidth;
			}
		}

		// positioning deltas
		final int xd = minX - messageWidget.getOriginalX();
		final int yd = minY - messageWidget.getOriginalY();

		List<KeywordBoundary> wordBoundaries = new ArrayList<KeywordBoundary>();
		for (KeywordBoundary boundary : keywordBoundaries) {
			if (!boundary.contains(mousePoint)) {
				continue;
			}
			// +1 because hitbox needs to be very slightly lower than bounds
			setHitboxBounds(boundary.x - xd, boundary.y - yd + 1, boundary.width);
			search = boundary.term;

			if (config.onHover() == HoverMode.OFF) {
				break;
			}

			wordBoundaries = keywordBoundaries.stream()
				.filter(keywordBoundary -> keywordBoundary.index == boundary.index)
					.collect(Collectors.toList());
			break;
		}

		if (config.onHover() == HoverMode.OFF) {
			return null;
		}

		final int wordCount = wordBoundaries.size();
		for (int i = 0; i < wordCount; i++) {
			KeywordBoundary bounds = wordBoundaries.get(i);
			
			// width and x modifications make it look nicer.
			int x = i == 0 ? bounds.x + 2 : bounds.x;
			int width = wordCount > 1 && (i == 0 || i == wordCount - 1) 
				? bounds.width - 2 : bounds.width - 4;

			// -4 correction because of earlier repositioning
			final Rectangle hoverEffect = new Rectangle(x, bounds.y + CHATLINE_HEIGHT - 4, width, 1);

			if (config.onHover() == HoverMode.HIGHLIGHT) {
				hoverEffect.x = bounds.x;
				hoverEffect.y = bounds.y - 3;
				hoverEffect.height = CHATLINE_HEIGHT;
				hoverEffect.width = bounds.width;
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
		hitboxWidget.setOriginalHeight(18);
		hitboxWidget.setNoClickThrough(true);

		hitboxWidget.setHasListener(true);
		hitboxWidget.setOnClickListener((JavaScriptCallback) this::search);

		hitboxWidget.revalidate();
	}

	private void setHitboxBounds(int x, int y, int width) {
		if (hitboxWidget.getOriginalX() == x 
				&& hitboxWidget.getOriginalY() == y 
				&& hitboxWidget.getWidth() == width) {
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

		Optional<Widget> maybeMessageWidget = Stream.of(chatboxWidget.getChildren()).filter(widget -> !widget.isHidden())
				// 486 = chatbox width; ignores various game messages and parent chat lines
				.filter(widget -> widget.getWidth() != 486) 
				.filter(widget -> widget.getName() != HITBOX_WIDGET_NAME)
				.filter(widget -> widget.getId() < WidgetInfo.CHATBOX_FIRST_MESSAGE.getId())
				.filter(widget -> widget.getBounds().contains(point))
				.findFirst();

		if (!maybeMessageWidget.isPresent()) {
			return null;
		}
		return maybeMessageWidget.get();
	}

	private Widget getChatboxWidget() {
		if (chatboxWidget != null) {
			return chatboxWidget;
		}

		return client.getWidget(WidgetInfo.CHATBOX_MESSAGE_LINES);
	}

	private void search(ScriptEvent ev) {
		LinkBrowser.browse(
				WIKI_BASE.newBuilder()
						.addQueryParameter("search", search)
						.build()
						.toString()
		);
	}

}
