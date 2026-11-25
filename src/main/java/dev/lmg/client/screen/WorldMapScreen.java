package dev.lmg.client.screen;

import dev.lmg.client.LmgClient;
import dev.lmg.client.feature.LmgKeys;
import dev.lmg.client.map.MapData;
import dev.lmg.client.map.MapRegion;
import dev.lmg.client.map.Waypoints;
import dev.lmg.client.render.Draw;
import dev.lmg.client.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Locale;

/**
 * The full map (M). Drag to move around, scroll to zoom, right-click to drop a waypoint
 * (right-click a waypoint to remove it). Everything you've explored in this world is here.
 */
public class WorldMapScreen extends Screen {
	private static final Identifier ARROW = LmgClient.id("textures/gui/map_arrow.png");
	private static final int TOP = 28, LIST_W = 110;
	private static float zoom = 1f;   // screen pixels per block

	private final Screen parent;
	private final long openedAt = Util.getMillis();
	private double centerX, centerZ;
	private boolean dragging, movedWhileDragging;

	public WorldMapScreen(Screen parent) {
		super(Component.literal("World map"));
		this.parent = parent;
		var p = net.minecraft.client.Minecraft.getInstance().player;
		if (p != null) {
			centerX = p.getX();
			centerZ = p.getZ();
		}
	}

	@Override
	protected void init() {
		addRenderableWidget(new SkinButton(8, 8, 44, 13, Component.literal("< Back"), openedAt, this::onClose));
		addRenderableWidget(new SkinButton(width - LIST_W - 8, 8, 70, 13, Component.literal("Find me"), openedAt, () -> {
			if (minecraft.player != null) {
				centerX = minecraft.player.getX();
				centerZ = minecraft.player.getZ();
			}
		}));
	}

	private int mapX1() {
		return width - LIST_W - 12;
	}

	private double screenToWorldX(double sx) {
		return centerX + (sx - mapX1() / 2.0) / zoom;
	}

	private double screenToWorldZ(double sy) {
		return centerZ + (sy - (TOP + height) / 2.0) / zoom;
	}

	private float worldToScreenX(double wx) {
		return (float) (mapX1() / 2.0 + (wx - centerX) * zoom);
	}

	private float worldToScreenY(double wz) {
		return (float) ((TOP + height) / 2.0 + (wz - centerZ) * zoom);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xFF070B14);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float a = Draw.easeOutCubic(Theme.progress(openedAt, 250f));
		int mx0 = 6, my0 = TOP, mx1 = mapX1(), my1 = height - 6;

		// map
		g.fill(mx0, my0, mx1, my1, 0xFF0C1322);
		g.enableScissor(mx0, my0, mx1, my1);
		int rx0 = (int) Math.floor(screenToWorldX(mx0)) >> 9, rx1 = (int) Math.floor(screenToWorldX(mx1)) >> 9;
		int rz0 = (int) Math.floor(screenToWorldZ(my0)) >> 9, rz1 = (int) Math.floor(screenToWorldZ(my1)) >> 9;
		if ((rx1 - rx0 + 1) * (rz1 - rz0 + 1) <= 64) MapData.loadArea(rx0, rz0, rx1, rz1);
		Matrix3x2fStack pose = g.pose();
		for (MapRegion r : MapData.regions()) {
			if (r.rx < rx0 || r.rx > rx1 || r.rz < rz0 || r.rz > rz1) continue;
			pose.pushMatrix();
			pose.translate(worldToScreenX(r.rx * 512.0), worldToScreenY(r.rz * 512.0));
			pose.scale(zoom, zoom);
			g.blit(RenderPipelines.GUI_TEXTURED, r.textureId, 0, 0, 0f, 0f, 512, 512, 512, 512, 0xFFFFFFFF);
			pose.popMatrix();
		}

		// waypoints
		Waypoints.Waypoint hovered = waypointAt(mouseX, mouseY);
		for (Waypoints.Waypoint w : Waypoints.all()) {
			int wx = Math.round(worldToScreenX(w.x() + 0.5)), wy = Math.round(worldToScreenY(w.z() + 0.5));
			g.fill(wx - 3, wy - 3, wx + 4, wy + 4, 0xFF070B14);
			g.fill(wx - 2, wy - 2, wx + 3, wy + 3, 0xFF000000 | w.color());
			if (w == hovered || zoom >= 1f) {
				int tw = font.width(w.name());
				g.fill(wx - tw / 2 - 3, wy - 16, wx + tw / 2 + 3, wy - 5, 0xCC070B14);
				g.centeredText(font, w.name(), wx, wy - 14, 0xFFE8EDF5);
			}
		}

		// you
		if (minecraft.player != null) {
			float px = worldToScreenX(minecraft.player.getX()), py = worldToScreenY(minecraft.player.getZ());
			Draw.texture(g, ARROW, 32, px, py, 12f, (float) Math.toRadians(minecraft.player.getYRot() + 180), 0xFFFFFFFF);
		}
		g.disableScissor();

		// top bar
		String dim = minecraft.level == null ? "" : pretty(minecraft.level.dimension().identifier().getPath());
		g.text(font, "World map", 60, 11, Draw.white(a), false);
		g.text(font, dim, 60 + font.width("World map") + 8, 11, Draw.withAlpha(0xF2C14E, a), false);
		String help = "drag to move  -  scroll to zoom  -  right-click to drop a waypoint";
		if (60 + font.width("World map") + font.width(dim) + 24 + font.width(help) < mapX1()) {
			g.text(font, help, mapX1() - font.width(help), 11, Draw.withAlpha(0x7C8BA3, a), false);
		}

		// cursor position
		if (mouseX >= mx0 && mouseX < mx1 && mouseY >= my0 && mouseY < my1) {
			String pos = (int) Math.floor(screenToWorldX(mouseX)) + ", " + (int) Math.floor(screenToWorldZ(mouseY));
			g.fill(mx0 + 4, my1 - 16, mx0 + 12 + font.width(pos), my1 - 4, 0xCC070B14);
			g.text(font, pos, mx0 + 8, my1 - 14, 0xFFA3B0C4, false);
		}
		String zoomText = zoom >= 1 ? Math.round(zoom) + "x" : "1/" + Math.round(1 / zoom) + "x";
		g.text(font, zoomText, mx1 - 6 - font.width(zoomText), my1 - 14, 0xFF7C8BA3, false);

		// waypoint list
		int lx = width - LIST_W - 6, ly = TOP;
		Draw.roundRect(g, lx, ly, width - 6, height - 6, 5, 0xFF0C1322);
		g.text(font, "Waypoints", lx + 8, ly + 8, Draw.white(a), false);
		List<Waypoints.Waypoint> all = Waypoints.all();
		if (all.isEmpty()) {
			int yy = ly + 24;
			for (String line : new String[]{"None yet.", "Right-click the map", "to drop one, or bind", "a key in Keys."}) {
				g.text(font, line, lx + 8, yy, 0xFF7C8BA3, false);
				yy += 10;
			}
		}
		for (int i = 0; i < all.size() && ly + 24 + i * 22 < height - 30; i++) {
			Waypoints.Waypoint w = all.get(i);
			int yy = ly + 22 + i * 22;
			boolean over = mouseX >= lx + 4 && mouseX < width - 10 && mouseY >= yy && mouseY < yy + 20;
			if (over) Draw.roundRect(g, lx + 4, yy, width - 10, yy + 20, 3, 0xFF111A2E);
			g.fill(lx + 8, yy + 4, lx + 12, yy + 8, 0xFF000000 | w.color());
			g.text(font, w.name(), lx + 16, yy + 2, 0xFFE8EDF5, false);
			g.text(font, w.x() + " " + w.y() + " " + w.z(), lx + 16, yy + 11, 0xFF7C8BA3, false);
		}

		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private Waypoints.Waypoint waypointAt(double mx, double my) {
		for (Waypoints.Waypoint w : Waypoints.all()) {
			float wx = worldToScreenX(w.x() + 0.5), wy = worldToScreenY(w.z() + 0.5);
			if (Math.abs(mx - wx) <= 4 && Math.abs(my - wy) <= 4) return w;
		}
		return null;
	}

	private static String pretty(String path) {
		String s = path.replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();
		// waypoint list: click to look at it
		int lx = width - LIST_W - 6;
		if (mx >= lx) {
			List<Waypoints.Waypoint> all = Waypoints.all();
			int i = (int) ((my - TOP - 22) / 22);
			if (my >= TOP + 22 && i >= 0 && i < all.size()) {
				centerX = all.get(i).x() + 0.5;
				centerZ = all.get(i).z() + 0.5;
			}
			return true;
		}
		if (my < TOP) return false;
		if (event.button() == 0) {
			dragging = true;
			movedWhileDragging = false;
			return true;
		}
		if (event.button() == 1) {
			Waypoints.Waypoint w = waypointAt(mx, my);
			if (w != null) Waypoints.remove(w);
			else if (minecraft.player != null) {
				Waypoints.add((int) Math.floor(screenToWorldX(mx)), minecraft.player.blockPosition().getY(), (int) Math.floor(screenToWorldZ(my)));
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			centerX -= dx / zoom;
			centerZ -= dy / zoom;
			movedWhileDragging = true;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= mapX1()) return false;
		// zoom around the cursor so the spot under it stays put
		double wx = screenToWorldX(mouseX), wz = screenToWorldZ(mouseY);
		zoom = (float) Math.max(0.125, Math.min(8, zoom * (scrollY > 0 ? 1.25 : 0.8)));
		centerX = wx - (mouseX - mapX1() / 2.0) / zoom;
		centerZ = wz - (mouseY - (TOP + height) / 2.0) / zoom;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (LmgKeys.WORLD_MAP.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
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
