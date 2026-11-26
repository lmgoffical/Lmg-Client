package dev.lmg.client.screen;

import dev.lmg.client.LmgConfig;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.MenuBackground;
import dev.lmg.client.render.MenuSnow;
import dev.lmg.client.render.Theme;
import dev.lmg.client.social.Social;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Your friends: who's online, where they're playing, a button to join them. Requests at the top,
 * then online friends, then everyone else. Add someone by typing their Minecraft name.
 */
public class FriendsScreen extends Screen implements dev.lmg.client.render.WorldFilter.GreyBehind {
	private static final int HEADER = 24, TOP = 50, ROW = 26, SECTION = 15, PINK = 0xFF7EB3;

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private int W, H, x0, y0, scroll;
	private EditBox addBox;
	private String message = "";
	private boolean messageBad;
	private long messageAt;
	private UUID confirmRemove;
	private long confirmRemoveAt;

	/** One line in the list. */
	private record Line(Kind kind, String text, UUID uuid, Social.Friend friend) {}

	private enum Kind { SECTION, REQUEST, FRIEND, SENT }

	/** A clickable spot drawn this frame. */
	private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}

	private final List<Hit> hits = new ArrayList<>();

	public FriendsScreen(Screen parent) {
		super(Component.literal("Friends"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		W = Math.min(400, width - 16);
		H = Math.min(268, height - 16);
		x0 = width / 2 - W / 2;
		y0 = height / 2 - H / 2;
		Social.refreshSoon();
		addRenderableWidget(new SkinButton(x0 + 6, y0 + 6, 44, 13, Component.literal("< Back"), openedAt, this::onClose));
		if (!Social.enabled()) {
			addRenderableWidget(new PillButton(x0 + W / 2 - 60, y0 + H - 50, 120, 18, Component.literal("Turn on friends"), true, openedAt, () -> {
				Social.enable();
				rebuildWidgets();
			}));
			return;
		}
		// our own field look: the text box itself has no border, drawn on a rounded field
		addBox = new EditBox(font, x0 + 16, y0 + HEADER + 12, W - 98, 10, Component.literal("Add a friend"));
		addBox.setBordered(false);
		addBox.setHint(Component.literal("Add a friend by their Minecraft name..."));
		addBox.setMaxLength(16);
		addRenderableWidget(addBox);
		addRenderableWidget(new PillButton(x0 + W - 66, y0 + HEADER + 7, 56, 16, Component.literal("Add"), true, openedAt, this::sendRequest));
	}

	private void sendRequest() {
		String name = addBox.getValue().trim();
		if (name.isEmpty()) {
			say("Type their Minecraft name first.", true);
			return;
		}
		say("Sending...", false);
		Social.add(name, err -> {
			if (err != null) say(err, true);
			else {
				boolean nowFriends = Social.state().friends().stream().anyMatch(f -> f.name().equalsIgnoreCase(name));
				say(nowFriends ? "You and " + name + " are friends now!" : "Request sent to " + name + ".", false);
				addBox.setValue("");
			}
		});
	}

	private void say(String text, boolean bad) {
		message = text;
		messageBad = bad;
		messageAt = Util.getMillis();
	}

	private List<Line> lines() {
		Social.State s = Social.state();
		List<Line> out = new ArrayList<>();
		if (!s.incoming().isEmpty()) {
			out.add(new Line(Kind.SECTION, "Requests  " + s.incoming().size(), null, null));
			for (Social.Person p : s.incoming()) out.add(new Line(Kind.REQUEST, p.name(), p.uuid(), null));
		}
		int online = Social.onlineCount();
		if (online > 0) {
			out.add(new Line(Kind.SECTION, "Online  " + online, null, null));
			for (Social.Friend f : s.friends()) if (f.online()) out.add(new Line(Kind.FRIEND, f.name(), f.uuid(), f));
		}
		if (s.friends().size() > online) {
			out.add(new Line(Kind.SECTION, "Offline  " + (s.friends().size() - online), null, null));
			for (Social.Friend f : s.friends()) if (!f.online()) out.add(new Line(Kind.FRIEND, f.name(), f.uuid(), f));
		}
		if (!s.outgoing().isEmpty()) {
			out.add(new Line(Kind.SECTION, "Sent  " + s.outgoing().size(), null, null));
			for (Social.Person p : s.outgoing()) out.add(new Line(Kind.SENT, p.name(), p.uuid(), null));
		}
		return out;
	}

	private static int height(Line l) {
		return l.kind == Kind.SECTION ? SECTION : ROW;
	}

	// ---- drawing ---------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level != null) {
			super.extractBackground(g, mouseX, mouseY, partialTick);
			MenuSnow.greyBackdrop(g, width, height);
		} else {
			MenuBackground.draw(g, width, height, true);
		}
		MenuSnow.update(width, height);
		MenuSnow.drawBack(g, 1f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 300f));
		CosmeticsScreen.Pal p = CosmeticsScreen.pal();
		MenuSkin.simplePanel(g, font, x0, y0, x0 + W, y0 + H, HEADER, "Friends", a);
		MenuSnow.panelTop(x0, y0, x0 + W);
		hits.clear();

		if (!Social.enabled()) drawIntro(g, p, a);
		else drawList(g, p, mouseX, mouseY, a);

		super.extractRenderState(g, mouseX, mouseY, partialTick);
		MenuSnow.drawFront(g, 1f);
	}

	private void drawIntro(GuiGraphicsExtractor g, CosmeticsScreen.Pal p, float a) {
		int cx = x0 + W / 2;
		int y = y0 + HEADER + 22;
		// three faces in a row, as a little picture
		for (int i = 0; i < 3; i++) {
			int fx = cx - 34 + i * 24;
			Draw.roundRect(g, fx - 1, y - 1, fx + 21, y + 21, 3, Draw.withAlpha(p.accent(), 0.25f * a));
			PlayerFaceExtractor.extractRenderState(g, net.minecraft.client.resources.DefaultPlayerSkin.get(new UUID(0x853c80ef3c3749fdL, i * 3L)), fx, y, 20);
		}
		y += 34;
		g.centeredText(font, "Play with your friends", cx, y, Draw.withAlpha(p.text(), a));
		y += 16;
		String[] lines = {
				"See who's online and where they're playing,",
				"and join them with one click.",
				"",
				"Your friends see that you're online and your server",
				"(you can hide the server). Nobody else does.",
				"Your Minecraft login stays with Mojang."
		};
		for (String l : lines) {
			g.centeredText(font, l, cx, y, Draw.withAlpha(p.dim(), a));
			y += 11;
		}
	}

	private void drawList(GuiGraphicsExtractor g, CosmeticsScreen.Pal p, int mouseX, int mouseY, float a) {
		int lx0 = x0 + 8, lx1 = x0 + W - 12, ly0 = y0 + TOP + 4, ly1 = y0 + H - 34;
		// the add-a-friend field
		boolean focused = addBox != null && addBox.isFocused();
		int fx0 = x0 + 10, fy0 = y0 + HEADER + 7, fx1 = x0 + W - 72, fy1 = fy0 + 16;
		Draw.roundRect(g, fx0 - 1, fy0 - 1, fx1 + 1, fy1 + 1, 5, Draw.withAlpha(focused ? p.accent() : 0x22304D, a));
		Draw.roundRect(g, fx0, fy0, fx1, fy1, 4, Draw.withAlpha(0x0A101D, a));
		List<Line> lines = lines();

		// status line under the add box when there's nothing else to say
		Social.Status st = Social.status();
		if (st != Social.Status.ONLINE) {
			String text = switch (st) {
				case SIGNING_IN -> "Signing in with your Minecraft account...";
				case NEEDS_ACCOUNT, ERROR -> Social.problem();
				default -> "";
			};
			g.centeredText(font, text, x0 + W / 2, ly0 + 30, Draw.withAlpha(st == Social.Status.SIGNING_IN ? p.dim() : 0xFF8C7C, a));
		} else if (lines.isEmpty()) {
			g.centeredText(font, "No friends yet.", x0 + W / 2, ly0 + 26, Draw.withAlpha(p.text(), a));
			g.centeredText(font, "Type a name above. They need Lmg Client with friends on.", x0 + W / 2, ly0 + 40, Draw.withAlpha(p.dim(), a));
		}

		int total = 0;
		for (Line l : lines) total += height(l);
		int view = ly1 - ly0;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - view)));

		g.enableScissor(lx0, ly0, lx1 + 4, ly1);
		int y = ly0 - scroll;
		for (Line l : lines) {
			int h = height(l);
			if (y + h > ly0 && y < ly1) drawLine(g, p, l, lx0, y, lx1, mouseX, mouseY, ly0, ly1, a);
			y += h;
		}
		g.disableScissor();
		if (total > view) {
			int barH = Math.max(12, view * view / total);
			int by = ly0 + (view - barH) * scroll / Math.max(1, total - view);
			Draw.roundRect(g, lx1 + 2, by, lx1 + 4, by + barH, 1, Draw.withAlpha(p.accent(), 0.6f * a));
		}

		// footer: settings as small switches, or the last message
		int fy = y0 + H - 22;
		g.fill(x0 + 8, fy - 6, x0 + W - 8, fy - 5, Draw.withAlpha(0x1C2943, a));
		float age = (Util.getMillis() - messageAt) / 1000f;
		if (!message.isEmpty() && age < 5f) {
			g.text(font, message, x0 + 10, fy + 2, Draw.withAlpha(messageBad ? 0xFF8C7C : 0x8BE28B, a), false);
		} else {
			LmgConfig c = LmgConfig.get();
			int fx = x0 + 10;
			fx = footSwitch(g, p, fx, fy, "Show my server", c.friendsShowServer, () -> c.friendsShowServer = !c.friendsShowServer, mouseX, mouseY, a);
			fx = footSwitch(g, p, fx, fy, "Pop-ups", c.friendsToasts, () -> c.friendsToasts = !c.friendsToasts, mouseX, mouseY, a);
			footSwitch(g, p, fx, fy, "Appear offline", c.friendsAppearOffline, () -> c.friendsAppearOffline = !c.friendsAppearOffline, mouseX, mouseY, a);
		}
		// switching friends off sits quietly on the right (it deletes your friends data)
		String off = "Turn off";
		int ox1 = x0 + W - 10, ox0 = ox1 - font.width(off);
		boolean overOff = mouseX >= ox0 && mouseX < ox1 && mouseY >= fy && mouseY < fy + 12;
		g.text(font, off, ox0, fy + 2, Draw.withAlpha(overOff ? 0xFF8C7C : p.dim(), 0.8f * a), false);
		hits.add(new Hit(ox0, fy, ox1, fy + 12, () -> minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) Social.disable();
			minecraft.gui.setScreen(this);
		}, Component.literal("Turn friends off?"),
				Component.literal("This deletes your friends list, requests and partner from the Lmg server. You can turn it on again any time, but you'll need to add your friends again.")))));
	}

	private int footSwitch(GuiGraphicsExtractor g, CosmeticsScreen.Pal p, int x, int y, String label, boolean on,
						   Runnable flip, int mouseX, int mouseY, float a) {
		int w = 18 + 4 + font.width(label);
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 12;
		int track = on ? (0xFF000000 | dev.lmg.client.render.Theme.primary()) : 0xFF2A3550;
		Draw.roundRect(g, x, y + 1, x + 16, y + 10, 4, Draw.fade(track, a));
		int kx = on ? x + 8 : x + 1;
		Draw.roundRect(g, kx, y + 2, kx + 7, y + 9, 3, Draw.white(a));
		g.text(font, label, x + 20, y + 2, Draw.withAlpha(over ? p.text() : p.dim(), a), false);
		hits.add(new Hit(x, y, x + w, y + 12, () -> {
			flip.run();
			LmgConfig.save();
			Social.refreshSoon();
		}));
		return x + w + 12;
	}

	private void drawLine(GuiGraphicsExtractor g, CosmeticsScreen.Pal p, Line l, int lx0, int y, int lx1,
						  int mouseX, int mouseY, int clipY0, int clipY1, float a) {
		if (l.kind == Kind.SECTION) {
			g.text(font, l.text.toUpperCase(), lx0 + 2, y + 5, Draw.withAlpha(p.dim(), 0.9f * a), false);
			return;
		}
		boolean over = mouseX >= lx0 && mouseX < lx1 && mouseY >= y && mouseY < y + ROW - 2 && mouseY >= clipY0 && mouseY < clipY1;
		Social.Friend f = l.friend;
		boolean online = f == null || f.online();
		Draw.roundRect(g, lx0, y, lx1, y + ROW - 2, 4, Draw.fade(over ? p.cardHover() : p.card(), a));

		// face, with a green dot when online
		face(g, l.uuid, lx0 + 5, y + 4, 16, online ? -1 : 0xFF707070);
		if (f != null && f.online()) {
			Draw.roundRect(g, lx0 + 16, y + 15, lx0 + 23, y + 22, 3, Draw.withAlpha(0x0C1322, a));
			Draw.roundRect(g, lx0 + 17, y + 16, lx0 + 22, y + 21, 2, Draw.withAlpha(0x5BD45B, a));
		}

		int tx = lx0 + 28;
		boolean partner = l.uuid.equals(Social.state().partner());
		g.text(font, l.text, tx, y + 4, Draw.withAlpha(online ? p.text() : p.dim(), a), false);
		int after = tx + font.width(l.text) + 4;
		if (partner) {
			g.text(font, "\u2764", after, y + 4, Draw.withAlpha(PINK, a), false);
			after += font.width("\u2764") + 4;
		}
		Integer uid = dev.lmg.client.social.Uids.of(l.uuid);
		if (uid != null) g.text(font, "#" + uid, after, y + 4, Draw.withAlpha(dev.lmg.client.social.Uids.color(uid), online ? a : 0.6f * a), false);

		String sub = switch (l.kind) {
			case REQUEST -> "wants to be friends";
			case SENT -> "waiting for them to say yes";
			default -> f.status();
		};
		if (l.kind == Kind.FRIEND && l.uuid.equals(Social.state().partnerRequest())) sub = "asked you to be partners ❤";
		int subColor = f != null && f.joinable() ? Draw.lerpRgb(p.accent(), 0xFFFFFF, 0.25f) : p.dim();
		g.text(font, font.plainSubstrByWidth(sub, lx1 - tx - 120), tx, y + 14, Draw.withAlpha(subColor, a), false);

		// buttons on the right
		int bx = lx1 - 4;
		switch (l.kind) {
			case REQUEST -> {
				bx = pill(g, p, bx, y, "No", false, mouseX, mouseY, a, () -> Social.remove(l.uuid, err -> say(err != null ? err : "Request declined.", err != null)));
				pill(g, p, bx, y, "Accept", true, mouseX, mouseY, a, () -> Social.accept(l.uuid, err -> say(err != null ? err : "You and " + l.text + " are friends now!", err != null)));
			}
			case SENT -> pill(g, p, bx, y, "Cancel", false, mouseX, mouseY, a, () -> Social.remove(l.uuid, err -> say(err != null ? err : "Request cancelled.", err != null)));
			case FRIEND -> {
				boolean sure = l.uuid.equals(confirmRemove) && Util.getMillis() - confirmRemoveAt < 4000;
				if (over || sure) {
					bx = pill(g, p, bx, y, sure ? "Sure?" : "x", false, mouseX, mouseY, a, () -> {
						if (sure) {
							Social.remove(l.uuid, err -> say(err != null ? err : l.text + " removed.", err != null));
							confirmRemove = null;
						} else {
							confirmRemove = l.uuid;
							confirmRemoveAt = Util.getMillis();
						}
					});
					String heart = partner ? "Unpartner" : l.uuid.equals(Social.state().partnerRequest()) ? "Say yes ❤" : "Partner ❤";
					bx = pill(g, p, bx, y, heart, false, mouseX, mouseY, a, () -> Social.partner(partner ? null : l.uuid, err -> {
						if (err != null) say(err, true);
						else if (partner) say("You're not partners any more.", false);
						else say(l.uuid.equals(Social.state().partner()) ? "You and " + l.text + " are partners now ❤" : "Asked " + l.text + " to be partners.", false);
					}));
				}
				if (f.joinable()) pill(g, p, bx, y, "Join", true, mouseX, mouseY, a, () -> confirmJoin(f));
			}
			default -> {
			}
		}
	}

	/** A small rounded button, right-aligned to {@code right}. Returns where the next one goes. */
	private int pill(GuiGraphicsExtractor g, CosmeticsScreen.Pal p, int right, int y, String text, boolean primary,
					 int mouseX, int mouseY, float a, Runnable action) {
		int w = font.width(text) + 12, x = right - w, by = y + 6;
		boolean over = mouseX >= x && mouseX < right && mouseY >= by && mouseY < by + 13;
		PillButton.draw(g, font, x, by, w, 13, text, primary, over ? 1f : 0f, a);
		hits.add(new Hit(x, by, right, by + 13, action));
		return x - 4;
	}

	/** A player's face from their UUID (looked up and cached by the game). */
	static void face(GuiGraphicsExtractor g, UUID uuid, int x, int y, int size, int color) {
		var skin = net.minecraft.client.Minecraft.getInstance().playerSkinRenderCache()
				.getOrDefault(ResolvableProfile.createUnresolved(uuid)).playerSkin();
		PlayerFaceExtractor.extractRenderState(g, skin, x, y, size, color);
	}

	// ---- joining ---------------------------------------------------------------------------------

	private void confirmJoin(Social.Friend f) {
		String where = f.serverName() != null && !f.serverName().isBlank() ? f.serverName() : f.server();
		if (minecraft.level == null) {
			join(f);
			return;
		}
		String already = Social.normalizeServer(f.server());
		var cur = minecraft.getCurrentServer();
		if (cur != null && already != null && already.equals(Social.normalizeServer(cur.ip))) {
			say("You're already on " + where + " with " + f.name() + ".", false);
			return;
		}
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) join(f);
			else minecraft.gui.setScreen(this);
		}, Component.literal("Join " + f.name() + "?"),
				Component.literal("You'll leave this world and connect to " + where + " (" + f.server() + ").")));
	}

	private void join(Social.Friend f) {
		if (minecraft.level != null) {
			boolean local = minecraft.isLocalServer();
			minecraft.level.disconnect(Component.literal("Joining " + f.name()));
			if (local) minecraft.disconnectWithSavingScreen();
			else minecraft.disconnectWithProgressScreen();
		}
		String name = f.serverName() != null && !f.serverName().isBlank() ? f.serverName() : f.server();
		ServerData data = new ServerData(name, f.server(), ServerData.Type.OTHER);
		ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(f.server()), data, false, null);
	}

	// ---- input -----------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		for (Hit h : List.copyOf(hits)) {
			if (event.x() >= h.x0 && event.x() < h.x1 && event.y() >= h.y0 && event.y() < h.y1) {
				h.action.run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (addBox != null && addBox.isFocused() && (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)) {
			sendRequest();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= (int) (scrollY * 14);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
