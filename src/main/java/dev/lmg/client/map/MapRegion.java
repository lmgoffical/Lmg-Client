package dev.lmg.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lmg.client.LmgClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** A 512x512 block piece of the map: one pixel per block, transparent where you haven't been. */
public final class MapRegion {
	public static final int SIZE = 512;
	private static int counter;

	public final int rx, rz;
	final DynamicTexture texture;
	public final Identifier textureId;
	boolean dirty, needsSave;

	MapRegion(int rx, int rz, NativeImage loaded) {
		this.rx = rx;
		this.rz = rz;
		NativeImage img = loaded != null ? loaded : new NativeImage(SIZE, SIZE, true);
		texture = new DynamicTexture(() -> "Lmg map " + rx + "," + rz, img);
		textureId = LmgClient.id("map/" + (counter++));
		Minecraft.getInstance().getTextureManager().register(textureId, texture);
	}

	NativeImage image() {
		return texture.getPixels();
	}

	/** ARGB colour of one block (x, z inside this region), 0 if unexplored. */
	public int get(int x, int z) {
		return image().getPixel(x, z);
	}

	void set(int x, int z, int argb) {
		NativeImage img = image();
		if (img.getPixel(x, z) != argb) {
			img.setPixel(x, z, argb);
			dirty = true;
			needsSave = true;
		}
	}

	void upload() {
		if (dirty) {
			texture.upload();
			dirty = false;
		}
	}

	void close() {
		Minecraft.getInstance().getTextureManager().release(textureId);
	}
}
