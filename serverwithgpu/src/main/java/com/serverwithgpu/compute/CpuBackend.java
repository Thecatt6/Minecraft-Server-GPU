package com.serverwithgpu.compute;

/** Backend di riferimento su CPU. È sempre disponibile ed è la fonte di verità per i risultati. */
public final class CpuBackend implements ComputeBackend {
	private static final double STEP = 0.3;

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
		double maxAmp = PerlinNoise2D.maxAmplitude(r.octaves());

		for (int z = 0; z < r.size(); z++) {
			for (int x = 0; x < r.size(); x++) {
				double wx = (double) (r.originX() + x) / r.scale();
				double wz = (double) (r.originZ() + z) / r.scale();
				double sum = 0;
				double a = 1;
				double f = 1;
				for (int o = 0; o < r.octaves(); o++) {
					sum += a * noise.noise(wx * f, wz * f);
					a *= 0.5;
					f *= 2;
				}
				double n = (sum / maxAmp + 1) * 0.5;
				out[z * r.size() + x] = (float) Math.min(Math.max(n, 0.0), 1.0);
			}
		}
		return out;
	}

	@Override
	public boolean[] blast(BlastGrid g, double cx, double cy, double cz,
			float[] dirs, float[] energies, int maxSteps) {
		boolean[] destroyed = new boolean[g.size()];
		for (int r = 0; r < energies.length; r++) {
			float e = energies[r];
			double dx = dirs[3 * r];
			double dy = dirs[3 * r + 1];
			double dz = dirs[3 * r + 2];

			for (int s = 0; s < maxSteps && e > 0.0f; s++) {
				double t = s * STEP;
				double fx = Math.floor(cx + dx * t);
				double fy = Math.floor(cy + dy * t);
				double fz = Math.floor(cz + dz * t);
				if (fx < 0.0 || fy < 0.0 || fz < 0.0
						|| fx >= g.sizeX() || fy >= g.sizeY() || fz >= g.sizeZ()) {
					break;
				}
				int idx = g.index((int) fx, (int) fy, (int) fz);

				e -= 0.225f;
				float res = g.resistance()[idx];
				if (res >= 0.0f) {
					e -= (res + 0.3f) * 0.3f;
					if (e > 0.0f) {
						destroyed[idx] = true;
					}
				}
			}
		}
		return destroyed;
	}

	@Override
	public void integrate(EntityBatch b, float dt, float gravity, float drag, float maxSpeed) {
		for (int i = 0; i < b.size(); i++) {
			float x = b.vx[i];
			float y = b.vy[i];
			float z = b.vz[i];

			y -= gravity * dt;
			float damp = 1.0f - drag * dt;
			x *= damp;
			y *= damp;
			z *= damp;

			float sp = (float) Math.sqrt(x * x + y * y + z * z);
			if (sp > maxSpeed) {
				float k = maxSpeed / sp;
				x *= k;
				y *= k;
				z *= k;
			}

			b.vx[i] = x;
			b.vy[i] = y;
			b.vz[i] = z;
			b.px[i] += x * dt;
			b.py[i] += y * dt;
			b.pz[i] += z * dt;
		}
	}
}
