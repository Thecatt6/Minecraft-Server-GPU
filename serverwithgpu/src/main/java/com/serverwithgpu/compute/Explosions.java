package com.serverwithgpu.compute;

import java.util.Random;

/** Generazione dei raggi di un'esplosione, deterministica dal seme. */
public final class Explosions {
	private Explosions() {
	}

	/** Direzioni distribuite uniformemente su una sfera (spirale di Fibonacci). 3 float per raggio. */
	public static float[] rayDirections(int rays) {
		float[] dirs = new float[rays * 3];
		double golden = Math.PI * (3 - Math.sqrt(5));
		for (int i = 0; i < rays; i++) {
			double y = rays < 2 ? 1 : 1 - (i / (double) (rays - 1)) * 2;
			double r = Math.sqrt(Math.max(0, 1 - y * y));
			double theta = golden * i;
			dirs[3 * i] = (float) (Math.cos(theta) * r);
			dirs[3 * i + 1] = (float) y;
			dirs[3 * i + 2] = (float) (Math.sin(theta) * r);
		}
		return dirs;
	}

	/** Energia iniziale per raggio, come in vanilla: radius * (0.7 + rnd * 0.6). */
	public static float[] rayEnergies(int rays, float radius, long seed) {
		Random random = new Random(seed);
		float[] energies = new float[rays];
		for (int i = 0; i < rays; i++) {
			energies[i] = radius * (0.7f + random.nextFloat() * 0.6f);
		}
		return energies;
	}
}
