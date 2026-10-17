package com.serverwithgpu.compute;

/**
 * Griglia di resistenza all'esplosione. resistance[index] &lt; 0 indica aria.
 * Indice: (y * sizeZ + z) * sizeX + x.
 */
public record BlastGrid(int sizeX, int sizeY, int sizeZ, float[] resistance) {
	public BlastGrid {
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			throw new IllegalArgumentException("dimensioni non valide");
		}
		if (resistance.length != sizeX * sizeY * sizeZ) {
			throw new IllegalArgumentException("resistance deve avere sizeX*sizeY*sizeZ elementi");
		}
	}

	public int size() {
		return resistance.length;
	}

	public int index(int x, int y, int z) {
		return (y * sizeZ + z) * sizeX + x;
	}
}
