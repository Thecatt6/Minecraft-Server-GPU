package com.serverwithgpu.compute;

import java.util.Random;

/** Perlin noise 2D deterministico, con permutazione derivata dal seme. */
public final class PerlinNoise2D {
	private final int[] perm;

	public PerlinNoise2D(long seed) {
		this.perm = permutation(seed);
	}

	/** Tabella di permutazione di 512 valori, usata anche dal kernel GPU. */
	public static int[] permutation(long seed) {
		int[] p = new int[256];
		for (int i = 0; i < 256; i++) {
			p[i] = i;
		}
		Random random = new Random(seed);
		for (int i = 255; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int tmp = p[i];
			p[i] = p[j];
			p[j] = tmp;
		}
		int[] perm = new int[512];
		for (int i = 0; i < 512; i++) {
			perm[i] = p[i & 255];
		}
		return perm;
	}

	/** Ampiezza massima della somma di {@code octaves} ottave (per normalizzare in [0, 1]). */
	public static double maxAmplitude(int octaves) {
		double maxAmp = 0;
		double amp = 1;
		for (int o = 0; o < octaves; o++) {
			maxAmp += amp;
			amp *= 0.5;
		}
		return maxAmp;
	}

	/** Valore in circa [-1, 1]. */
	public double noise(double x, double y) {
		int xi = (int) Math.floor(x) & 255;
		int yi = (int) Math.floor(y) & 255;
		double xf = x - Math.floor(x);
		double yf = y - Math.floor(y);
		double u = fade(xf);
		double v = fade(yf);

		int aa = perm[perm[xi] + yi];
		int ab = perm[perm[xi] + yi + 1];
		int ba = perm[perm[xi + 1] + yi];
		int bb = perm[perm[xi + 1] + yi + 1];

		double g00 = grad(aa, xf, yf);
		double g10 = grad(ba, xf - 1, yf);
		double g01 = grad(ab, xf, yf - 1);
		double g11 = grad(bb, xf - 1, yf - 1);

		double x1 = g00 + u * (g10 - g00);
		double x2 = g01 + u * (g11 - g01);
		return x1 + v * (x2 - x1);
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	private static double grad(int hash, double x, double y) {
		switch (hash & 7) {
			case 0: return x + y;
			case 1: return -x + y;
			case 2: return x - y;
			case 3: return -x - y;
			case 4: return x;
			case 5: return -x;
			case 6: return y;
			default: return -y;
		}
	}
}
