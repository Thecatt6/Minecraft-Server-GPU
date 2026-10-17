package com.serverwithgpu.terrain;

import com.serverwithgpu.compute.ComputeBackend;
import com.serverwithgpu.compute.HeightmapRequest;

public final class HeightmapBenchmark {
	private HeightmapBenchmark() {
	}

	/** Esegue il calcolo per {@code runs} volte e restituisce il tempo medio in millisecondi. */
	public static double averageMillis(ComputeBackend backend, HeightmapRequest request, int runs) {
		backend.heightmap(request); // warm-up (JIT)
		long start = System.nanoTime();
		for (int i = 0; i < runs; i++) {
			backend.heightmap(request);
		}
		return (System.nanoTime() - start) / 1_000_000.0 / runs;
	}
}
