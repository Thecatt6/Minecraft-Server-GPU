package com.serverwithgpu.compute;

/**
 * Richiesta di heightmap per un'area quadrata di colonne.
 *
 * @param seed    seme del mondo: stesso seme e stessi parametri danno sempre lo stesso risultato
 * @param originX coordinata X del primo blocco dell'area
 * @param originZ coordinata Z del primo blocco dell'area
 * @param size    lato dell'area in colonne
 * @param octaves numero di ottave del rumore
 * @param scale   frequenza base del rumore (blocchi per ciclo, es. 128)
 */
public record HeightmapRequest(long seed, int originX, int originZ, int size, int octaves, double scale) {
	public HeightmapRequest {
		if (size <= 0) {
			throw new IllegalArgumentException("size deve essere > 0");
		}
		if (octaves <= 0) {
			throw new IllegalArgumentException("octaves deve essere > 0");
		}
		if (!(scale > 0)) {
			throw new IllegalArgumentException("scale deve essere > 0");
		}
	}
}
