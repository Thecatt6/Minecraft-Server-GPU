package com.serverwithgpu.compute;

import java.util.Random;

/**
 * Confronta il backend attivo con la CPU di riferimento su dati fissi, e misura i tempi.
 * Serve a verificare su una macchina reale (es. la MX130) che i kernel producano gli stessi risultati.
 */
public final class SelfTest {
	private SelfTest() {
	}

	public static String run(ComputeBackend backend) {
		StringBuilder sb = new StringBuilder();
		ComputeBackend cpu = new CpuBackend();
		sb.append("Backend testato: ").append(backend.name()).append('\n');

		// 1. Heightmap
		HeightmapRequest hr = new HeightmapRequest(12345L, -300, 200, 128, 5, 128.0);
		float[] h1 = cpu.heightmap(hr);
		float[] h2 = backend.heightmap(hr);
		float maxH = 0;
		for (int i = 0; i < h1.length; i++) {
			maxH = Math.max(maxH, Math.abs(h1[i] - h2[i]));
		}
		sb.append(String.format("Heightmap 128x128: differenza massima = %.2e %s%n", maxH, ok(maxH < 1e-5f)));
		sb.append(String.format("  tempo CPU %.2f ms, backend %.2f ms%n",
				time(() -> cpu.heightmap(hr)), time(() -> backend.heightmap(hr))));

		// 2. Esplosione
		int sx = 32, sy = 24, sz = 32;
		float[] res = new float[sx * sy * sz];
		for (int i = 0; i < res.length; i++) {
			res[i] = (i % 11 == 0) ? -1f : (i % 7) * 0.6f;
		}
		BlastGrid grid = new BlastGrid(sx, sy, sz, res);
		float[] dirs = Explosions.rayDirections(1352);
		float[] energies = Explosions.rayEnergies(1352, 6f, 42L);
		boolean[] b1 = cpu.blast(grid, 16.5, 12.5, 16.5, dirs, energies, 2000);
		boolean[] b2 = backend.blast(grid, 16.5, 12.5, 16.5, dirs, energies, 2000);
		int mismatch = 0;
		int destroyed = 0;
		for (int i = 0; i < b1.length; i++) {
			if (b1[i] != b2[i]) {
				mismatch++;
			}
			if (b1[i]) {
				destroyed++;
			}
		}
		sb.append(String.format("Esplosione (1352 raggi): blocchi distrutti CPU=%d, differenze=%d %s%n",
				destroyed, mismatch, ok(mismatch == 0)));
		sb.append(String.format("  tempo CPU %.2f ms, backend %.2f ms%n",
				time(() -> cpu.blast(grid, 16.5, 12.5, 16.5, dirs, energies, 2000)),
				time(() -> backend.blast(grid, 16.5, 12.5, 16.5, dirs, energies, 2000))));

		// 3. Entità
		int n = 4096;
		EntityBatch seed = new EntityBatch(n);
		Random rnd = new Random(7);
		for (int i = 0; i < n; i++) {
			seed.px[i] = rnd.nextFloat() * 100;
			seed.py[i] = rnd.nextFloat() * 100;
			seed.pz[i] = rnd.nextFloat() * 100;
			seed.vx[i] = rnd.nextFloat() * 4 - 2;
			seed.vy[i] = rnd.nextFloat() * 4 - 2;
			seed.vz[i] = rnd.nextFloat() * 4 - 2;
		}
		EntityBatch e1 = seed.copy();
		EntityBatch e2 = seed.copy();
		cpu.integrate(e1, 0.05f, 0.08f, 0.02f, 3.92f);
		backend.integrate(e2, 0.05f, 0.08f, 0.02f, 3.92f);
		float maxE = 0;
		for (int i = 0; i < n; i++) {
			maxE = Math.max(maxE, Math.abs(e1.px[i] - e2.px[i]));
			maxE = Math.max(maxE, Math.abs(e1.vy[i] - e2.vy[i]));
		}
		sb.append(String.format("Entità (%d): differenza massima = %.2e %s%n", n, maxE, ok(maxE < 1e-4f)));

		return sb.toString();
	}

	private static String ok(boolean good) {
		return good ? "[OK]" : "[ATTENZIONE]";
	}

	private static double time(Runnable r) {
		r.run(); // warm-up
		long start = System.nanoTime();
		r.run();
		return (System.nanoTime() - start) / 1_000_000.0;
	}
}
