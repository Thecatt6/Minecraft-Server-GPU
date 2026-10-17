package com.serverwithgpu.compute;

/** Stato SoA (Structure of Arrays) di un batch di entità: ogni array ha lunghezza n. */
public final class EntityBatch {
	public final float[] px, py, pz;
	public final float[] vx, vy, vz;

	public EntityBatch(int n) {
		px = new float[n];
		py = new float[n];
		pz = new float[n];
		vx = new float[n];
		vy = new float[n];
		vz = new float[n];
	}

	public int size() {
		return px.length;
	}

	public EntityBatch copy() {
		EntityBatch c = new EntityBatch(size());
		System.arraycopy(px, 0, c.px, 0, size());
		System.arraycopy(py, 0, c.py, 0, size());
		System.arraycopy(pz, 0, c.pz, 0, size());
		System.arraycopy(vx, 0, c.vx, 0, size());
		System.arraycopy(vy, 0, c.vy, 0, size());
		System.arraycopy(vz, 0, c.vz, 0, size());
		return c;
	}
}
