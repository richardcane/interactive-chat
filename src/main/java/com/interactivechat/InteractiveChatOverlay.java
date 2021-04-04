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
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;

class InteractiveChatOverlay extends Overlay {
	private final Client client;
	private final InteractiveChatConfig config;

	static final HttpUrl WIKI_BASE = HttpUrl.parse("https://oldschool.runescape.wiki");
	static final Pattern SEARCH_PATTERN = Pattern.compile("((?<=\\])|(?=\\[))", Pattern.DOTALL);
	static final String LEFT_DELIMITER = "[";
	static final String RIGHT_DELIMITER = "]";

	static Widget chatbox;
	static Widget hitbox;
	static String search = "";

	@Inject
	InteractiveChatOverlay(Client client, InteractiveChatConfig config) {
		this.client = client;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
	}

	private void createHitboxWidget() {
		chatbox = this.getChatbox();
		if (chatbox == null)
			return;

		hitbox = chatbox.createChild(-1, WidgetType.RECTANGLE);
		hitbox.setName("Wiki");
		hitbox.setOpacity(255);
		hitbox.setOriginalX(0);
		hitbox.setOriginalY(0);
		hitbox.setOriginalWidth(0);
		hitbox.setOriginalHeight(14);
		hitbox.setNoClickThrough(true);

		hitbox.setHasListener(true);
		hitbox.setOnClickListener((JavaScriptCallback) this::search);

		hitbox.revalidate();
	}

	private void positionHitboxWidget(int x, int y, int width) {
		if (hitbox.getOriginalX() == x && hitbox.getOriginalY() == y && hitbox.getWidth() == width) {
			return;
		}

		hitbox.setOriginalX(x);
		hitbox.setOriginalY(y);
		hitbox.setOriginalWidth(width);

		hitbox.revalidate();
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (hitbox == null) {
			this.createHitboxWidget();
			if (hitbox == null)
				return null;
		}
		this.positionHitboxWidget(0, 0, 0);

		Widget chatLine = this.getHoveredChatline();
		if (client.isMenuOpen() || chatLine == null || chatLine.getWidth() == 486) {
			return null;
		}

		final FontTypeFace font = chatLine.getFont();
		final String text = Text.removeFormattingTags(chatLine.getText());
		final int textWidth = font.getTextWidth(text);
		final Rectangle outerBounds = chatLine.getBounds();

		final net.runelite.api.Point mouse = this.client.getMouseCanvasPosition();
		final Point mousePoint = new Point(mouse.getX(), mouse.getY());

		if (textWidth <= outerBounds.width) {
			String[] parts = SEARCH_PATTERN.split(text);

			int xForBounds = (int) outerBounds.getMinX();
			int xForHitbox = chatLine.getOriginalX();
			for (String part : parts) {
				final int partWidth = font.getTextWidth(part);

				if (!part.startsWith(LEFT_DELIMITER)) {
					xForBounds += partWidth;
					xForHitbox += partWidth;
					continue;
				}

				Rectangle partBounds = new Rectangle(xForBounds, (int) outerBounds.getMinY() + 1, partWidth,
						outerBounds.height);

				if (partBounds.contains(mousePoint)) {
					search = part.replace(LEFT_DELIMITER, "").replace(RIGHT_DELIMITER, "");

					this.positionHitboxWidget(xForHitbox, chatLine.getOriginalY() + 1, partWidth);

					graphics.setPaint(Color.GREEN);
					graphics.fill(new Rectangle(partBounds.x + 2, partBounds.y + partBounds.height - 1, partBounds.width - 4, 1));
				}

				xForBounds += partWidth;
				xForHitbox += partWidth;
			}
		}

		return null;
	}

	private Widget getHoveredChatline() {
		chatbox = this.getChatbox();
		if (chatbox == null)
			return null;

		Optional<Widget> maybeChatline = Stream.of(chatbox.getChildren()).filter(widget -> !widget.isHidden())
				.filter(widget -> widget.getName() != "Wiki")
				.filter(widget -> widget.getId() < WidgetInfo.CHATBOX_FIRST_MESSAGE.getId()).filter(widget -> {
					int mouseY = this.client.getMouseCanvasPosition().getY();
					return (mouseY >= widget.getBounds().getMinY() && mouseY <= widget.getBounds().getMaxY());
				}).skip(1).findFirst();

		if (!maybeChatline.isPresent()) {
			return null;
		}
		return maybeChatline.get();
	}

	private Widget getChatbox() {
		if (chatbox != null) {
			return chatbox;
		}

		return this.client.getWidget(WidgetInfo.CHATBOX_MESSAGE_LINES);
	}

	protected void search(ScriptEvent ev) {
		LinkBrowser.browse(WIKI_BASE.newBuilder().addQueryParameter("search", search).build().toString());
	}

}
