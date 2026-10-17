package com.serverwithgpu.compute;

/** Backend di riferimento su CPU. È sempre disponibile ed è la fonte di verità per i risultati. */
public final class CpuBackend implements ComputeBackend {
	@Override
	public String name() {
		return "CPU (riferimento)";
	}

	@Override
	public boolean isGpu() {
		return false;
	}

	@Override
	public float[] heightmap(HeightmapRequest r) {
		PerlinNoise2D noise = new PerlinNoise2D(r.seed());
		float[] out = new float[r.size() * r.size()];

		double maxAmp = 0;
		double amp = 1;
		for (int o = 0; o < r.octaves(); o++) {
			maxAmp += amp;
			amp *= 0.5;
		}

		for (int z = 0; z < r.size(); z++) {
			for (int x = 0; x < r.size(); x++) {
				double wx = (r.originX() + x) / r.scale();
				double wz = (r.originZ() + z) / r.scale();
				double sum = 0;
				double a = 1;
				double f = 1;
				for (int o = 0; o < r.octaves(); o++) {
					sum += a * noise.noise(wx * f, wz * f);
					a *= 0.5;
					f *= 2;
				}
				// da [-maxAmp, maxAmp] a [0, 1]
				double normalized = (sum / maxAmp + 1) * 0.5;
				out[z * r.size() + x] = (float) Math.clamp(normalized, 0.0, 1.0);
			}
		}
		return out;
	}
}
