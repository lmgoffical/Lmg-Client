package dev.lmg.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Tiny star particles: slow drifting "ambient" ones and fast "burst" ones. */
public final class Sparkles {
	private static final class Particle {
		float x, y, vx, vy, rot, spin, size, age, life, twinkle;
		float drag;
	}

	private final List<Particle> particles = new ArrayList<>();
	private final Random random = new Random();
	private long lastUpdate = -1;

	/** Keeps roughly {@code target} slow sparkles alive inside the given area. */
	public void keepAmbient(int target, float minX, float maxX, float minY, float maxY) {
		if (target <= 0) {
			particles.removeIf(p -> p.drag == 0);
			return;
		}
		int ambient = 0;
		for (Particle p : particles) if (p.drag == 0) ambient++;
		// Spawn gradually so they don't all appear on the same frame.
		if (ambient < target && random.nextFloat() < 0.15f) {
			Particle p = new Particle();
			p.x = minX + random.nextFloat() * (maxX - minX);
			p.y = minY + random.nextFloat() * (maxY - minY);
			p.vx = (random.nextFloat() - 0.5f) * 3f;
			p.vy = -2f - random.nextFloat() * 6f;
			p.rot = random.nextFloat() * 6.28f;
			p.spin = (random.nextFloat() - 0.5f) * 0.8f;
			p.size = 3f + random.nextFloat() * 5f;
			p.life = 4f + random.nextFloat() * 5f;
			p.twinkle = random.nextFloat() * 6.28f;
			particles.add(p);
		}
	}

	/** Shoots {@code count} sparkles outward from (cx, cy). */
	public void burst(float cx, float cy, int count, float speed) {
		for (int i = 0; i < count; i++) {
			Particle p = new Particle();
			double angle = random.nextDouble() * Math.PI * 2;
			float v = speed * (0.35f + random.nextFloat() * 0.9f);
			p.x = cx;
			p.y = cy;
			p.vx = (float) Math.cos(angle) * v;
			p.vy = (float) Math.sin(angle) * v;
			p.rot = random.nextFloat() * 6.28f;
			p.spin = (random.nextFloat() - 0.5f) * 8f;
			p.size = 3f + random.nextFloat() * 6f;
			p.life = 0.6f + random.nextFloat() * 0.8f;
			p.twinkle = random.nextFloat() * 6.28f;
			p.drag = 3.2f;
			particles.add(p);
		}
	}

	public void update() {
		long now = Util.getMillis();
		float dt = lastUpdate < 0 ? 0 : Math.min(0.1f, (now - lastUpdate) / 1000f);
		lastUpdate = now;
		Iterator<Particle> it = particles.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			p.age += dt;
			if (p.age >= p.life) {
				it.remove();
				continue;
			}
			if (p.drag > 0) {
				float k = (float) Math.exp(-p.drag * dt);
				p.vx *= k;
				p.vy *= k;
			}
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.rot += p.spin * dt;
		}
	}

	public void draw(GuiGraphicsExtractor g, float alphaMul) {
		float time = Util.getMillis() / 1000f;
		int rgb = Theme.accentSoft();
		for (Particle p : particles) {
			float t = p.age / p.life;
			// fade in over the first 15%, fade out over the last 40%
			float fade = Math.min(t / 0.15f, (1 - t) / 0.4f);
			float twinkle = p.drag > 0 ? 1f : 0.55f + 0.45f * (float) Math.sin(time * 3f + p.twinkle);
			float a = Draw.clamp01(fade) * twinkle * alphaMul;
			Draw.texture(g, Draw.SPARKLE, Draw.SPARKLE_TEX, p.x, p.y, p.size, p.rot, Draw.withAlpha(rgb, a * 0.9f));
		}
	}

	public void clear() {
		particles.clear();
	}
}
