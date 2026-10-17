package com.serverwithgpu.compute;

import com.serverwithgpu.ServerWithGpu;

public final class Backends {
	private Backends() {
	}

	/**
	 * Sceglie il backend: GPU OpenCL se disponibile (con fallback CPU automatico),
	 * altrimenti CPU. Qualsiasi errore di inizializzazione della GPU (anche UnsatisfiedLinkError)
	 * viene registrato e non blocca l'avvio.
	 */
	public static ComputeBackend select(boolean preferGpu) {
		if (!preferGpu) {
			return new CpuBackend();
		}
		try {
			ComputeBackend gpu = OpenClBackend.create();
			ServerWithGpu.LOGGER.info("GPU inizializzata: {}", gpu.name());
			return new ResilientBackend(gpu, new CpuBackend());
		} catch (Throwable t) {
			ServerWithGpu.LOGGER.warn("GPU non disponibile, uso la CPU: {}", t.toString());
			return new CpuBackend();
		}
	}
}
