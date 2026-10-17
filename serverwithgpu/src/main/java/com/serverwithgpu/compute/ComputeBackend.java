package com.serverwithgpu.compute;

/**
 * Astrazione del calcolo batch. Un backend GPU e uno CPU devono produrre
 * risultati identici (o entro una tolleranza documentata), altrimenti due server
 * con impostazioni diverse genererebbero mondi diversi.
 */
public interface ComputeBackend {
	/** Nome leggibile del backend, per log e comandi. */
	String name();

	/** true se il calcolo avviene sulla GPU. */
	boolean isGpu();

	/**
	 * Calcola un'heightmap di size*size valori normalizzati in [0, 1], in ordine riga-major
	 * (indice = z * size + x).
	 */
	float[] heightmap(HeightmapRequest request);
}
