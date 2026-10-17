package com.serverwithgpu.compute;

import com.serverwithgpu.ServerWithGpu;

/**
 * Usa il backend principale (GPU). Se una chiamata fallisce, passa in modo permanente
 * al backend di fallback (CPU) e registra l'errore: il server non si ferma mai per la GPU.
 */
public final class ResilientBackend implements ComputeBackend {
	private final ComputeBackend primary;
	private final ComputeBackend fallback;
	private volatile boolean primaryFailed = false;

	public ResilientBackend(ComputeBackend primary, ComputeBackend fallback) {
		this.primary = primary;
		this.fallback = fallback;
	}

	private void failed(RuntimeException e) {
		if (!primaryFailed) {
			primaryFailed = true;
			ServerWithGpu.LOGGER.error("Errore nel backend {}, passo alla CPU per il resto della sessione",
					primary.name(), e);
		}
	}

	@Override
	public String name() {
		return primaryFailed ? fallback.name() + " (fallback)" : primary.name();
	}

	@Override
	public boolean isGpu() {
		return !primaryFailed && primary.isGpu();
	}

	@Override
	public float[] heightmap(HeightmapRequest r) {
		if (!primaryFailed) {
			try {
				return primary.heightmap(r);
			} catch (RuntimeException e) {
				failed(e);
			}
		}
		return fallback.heightmap(r);
	}

	@Override
	public boolean[] blast(BlastGrid g, double cx, double cy, double cz,
			float[] dirs, float[] energies, int maxSteps) {
		if (!primaryFailed) {
			try {
				return primary.blast(g, cx, cy, cz, dirs, energies, maxSteps);
			} catch (RuntimeException e) {
				failed(e);
			}
		}
		return fallback.blast(g, cx, cy, cz, dirs, energies, maxSteps);
	}

	@Override
	public void integrate(EntityBatch b, float dt, float gravity, float drag, float maxSpeed) {
		if (!primaryFailed) {
			try {
				primary.integrate(b, dt, gravity, drag, maxSpeed);
				return;
			} catch (RuntimeException e) {
				failed(e);
			}
		}
		fallback.integrate(b, dt, gravity, drag, maxSpeed);
	}
}
