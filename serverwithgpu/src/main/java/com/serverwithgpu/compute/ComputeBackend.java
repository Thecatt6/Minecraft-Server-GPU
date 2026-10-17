package com.serverwithgpu.compute;

/**
 * Astrazione del calcolo batch. Un backend GPU e uno CPU devono produrre risultati
 * equivalenti (differenze al più di arrotondamento float), altrimenti due server con
 * impostazioni diverse genererebbero mondi o comportamenti diversi.
 */
public interface ComputeBackend {
	/** Nome leggibile del backend, per log e comandi. */
	String name();

	/** true se il calcolo avviene sulla GPU. */
	boolean isGpu();

	/** Heightmap size*size in [0, 1], riga-major (indice = z * size + x). */
	float[] heightmap(HeightmapRequest request);

	/**
	 * Raycast di esplosione. dirs = 3 float per raggio, energies = 1 float per raggio.
	 * Restituisce un array di lunghezza grid.size() con true per i blocchi distrutti.
	 */
	boolean[] blast(BlastGrid grid, double cx, double cy, double cz,
			float[] dirs, float[] energies, int maxSteps);

	/** Integra in-place posizione e velocità di un batch di entità. */
	void integrate(EntityBatch batch, float dt, float gravity, float drag, float maxSpeed);
}
