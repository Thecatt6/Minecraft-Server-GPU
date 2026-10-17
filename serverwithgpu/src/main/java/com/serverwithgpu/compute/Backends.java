package com.serverwithgpu.compute;

import com.serverwithgpu.ServerWithGpu;

public final class Backends {
	private Backends() {
	}

	/**
	 * Sceglie il backend migliore. Oggi l'unico backend disponibile è la CPU:
	 * il backend GPU (OpenCL/Vulkan) va aggiunto come {@link ComputeBackend} separato
	 * e restituito qui quando il driver è rilevato correttamente.
	 */
	public static ComputeBackend select(boolean preferGpu) {
		if (preferGpu) {
			ServerWithGpu.LOGGER.warn("GPU richiesta, ma nessun backend GPU è ancora implementato: uso la CPU.");
		}
		return new CpuBackend();
	}
}
