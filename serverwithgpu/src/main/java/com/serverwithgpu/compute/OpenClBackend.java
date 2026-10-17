package com.serverwithgpu.compute;

import static org.jocl.CL.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

/**
 * Backend GPU tramite OpenCL (JOCL). Funziona con i driver NVIDIA che espongono OpenCL
 * (anche la GeForce MX130, Maxwell, compute capability 5.0). Richiede il supporto a double
 * (cl_khr_fp64) per restare coerente con la CPU.
 *
 * Nota: ogni chiamata alloca e libera i buffer. È semplice e corretto; si può ottimizzare
 * con buffer persistenti quando il calcolo sarà sul percorso critico del tick.
 */
public final class OpenClBackend implements ComputeBackend {
	private final String deviceName;
	private final cl_context context;
	private final cl_command_queue queue;
	private final cl_program program;
	private final cl_kernel kHeightmap;
	private final cl_kernel kBlast;
	private final cl_kernel kIntegrate;

	private OpenClBackend(String deviceName, cl_context context, cl_command_queue queue, cl_program program) {
		this.deviceName = deviceName;
		this.context = context;
		this.queue = queue;
		this.program = program;
		this.kHeightmap = kernel("heightmap");
		this.kBlast = kernel("blast");
		this.kIntegrate = kernel("integrate");
	}

	/** Crea il backend, o lancia un'eccezione se non c'è una GPU OpenCL utilizzabile. */
	public static OpenClBackend create() throws IOException {
		cl_platform_id platform = null;
		cl_device_id device = null;

		int[] numPlatforms = new int[1];
		check(clGetPlatformIDs(0, null, numPlatforms), "clGetPlatformIDs");
		cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
		check(clGetPlatformIDs(platforms.length, platforms, null), "clGetPlatformIDs");

		for (cl_platform_id p : platforms) {
			int[] numDevices = new int[1];
			if (clGetDeviceIDs(p, CL_DEVICE_TYPE_GPU, 0, null, numDevices) != CL_SUCCESS || numDevices[0] == 0) {
				continue;
			}
			cl_device_id[] devices = new cl_device_id[numDevices[0]];
			clGetDeviceIDs(p, CL_DEVICE_TYPE_GPU, devices.length, devices, null);
			platform = p;
			device = devices[0];
			break;
		}
		if (device == null) {
			throw new IllegalStateException("Nessuna GPU OpenCL trovata (driver NVIDIA/OpenCL installato?)");
		}

		String extensions = deviceString(device, CL_DEVICE_EXTENSIONS);
		if (!extensions.contains("cl_khr_fp64")) {
			throw new IllegalStateException("La GPU non supporta double (cl_khr_fp64)");
		}
		String name = deviceString(device, CL_DEVICE_NAME);

		cl_context_properties props = new cl_context_properties();
		props.addProperty(CL_CONTEXT_PLATFORM, platform);
		int[] err = new int[1];
		cl_context context = clCreateContext(props, 1, new cl_device_id[] {device}, null, null, err);
		check(err[0], "clCreateContext");

		cl_command_queue queue = clCreateCommandQueue(context, device, 0, err);
		check(err[0], "clCreateCommandQueue");

		String source = readKernelSource();
		cl_program program = clCreateProgramWithSource(context, 1, new String[] {source}, null, err);
		check(err[0], "clCreateProgramWithSource");

		int build = clBuildProgram(program, 0, null, "", null, null);
		if (build != CL_SUCCESS) {
			throw new IllegalStateException("Compilazione kernel fallita: " + buildLog(program, device));
		}
		return new OpenClBackend(name, context, queue, program);
	}

	@Override
	public String name() {
		return "GPU OpenCL: " + deviceName;
	}

	@Override
	public boolean isGpu() {
		return true;
	}

	@Override
	public synchronized float[] heightmap(HeightmapRequest r) {
		int n = r.size() * r.size();
		int[] perm = PerlinNoise2D.permutation(r.seed());
		List<cl_mem> mems = new ArrayList<>();
		try {
			cl_mem bPerm = alloc(mems, 512L * Sizeof.cl_int);
			cl_mem bOut = alloc(mems, (long) n * Sizeof.cl_float);
			upload(bPerm, Pointer.to(perm), 512L * Sizeof.cl_int);

			setArg(kHeightmap, 0, bPerm);
			setArg(kHeightmap, 1, bOut);
			setArg(kHeightmap, 2, r.originX());
			setArg(kHeightmap, 3, r.originZ());
			setArg(kHeightmap, 4, r.size());
			setArg(kHeightmap, 5, r.octaves());
			setArg(kHeightmap, 6, r.scale());
			setArg(kHeightmap, 7, PerlinNoise2D.maxAmplitude(r.octaves()));
			launch(kHeightmap, 2, new long[] {r.size(), r.size()});

			float[] out = new float[n];
			download(bOut, Pointer.to(out), (long) n * Sizeof.cl_float);
			return out;
		} finally {
			release(mems);
		}
	}

	@Override
	public synchronized boolean[] blast(BlastGrid g, double cx, double cy, double cz,
			float[] dirs, float[] energies, int maxSteps) {
		int rays = energies.length;
		if (dirs.length != rays * 3) {
			throw new IllegalArgumentException("dirs deve avere 3 valori per raggio");
		}
		List<cl_mem> mems = new ArrayList<>();
		try {
			cl_mem bRes = alloc(mems, (long) g.size() * Sizeof.cl_float);
			cl_mem bDirs = alloc(mems, (long) dirs.length * Sizeof.cl_float);
			cl_mem bEn = alloc(mems, (long) rays * Sizeof.cl_float);
			cl_mem bDes = alloc(mems, (long) g.size() * Sizeof.cl_int);
			upload(bRes, Pointer.to(g.resistance()), (long) g.size() * Sizeof.cl_float);
			upload(bDirs, Pointer.to(dirs), (long) dirs.length * Sizeof.cl_float);
			upload(bEn, Pointer.to(energies), (long) rays * Sizeof.cl_float);
			upload(bDes, Pointer.to(new int[g.size()]), (long) g.size() * Sizeof.cl_int);

			setArg(kBlast, 0, bRes);
			setArg(kBlast, 1, g.sizeX());
			setArg(kBlast, 2, g.sizeY());
			setArg(kBlast, 3, g.sizeZ());
			setArg(kBlast, 4, cx);
			setArg(kBlast, 5, cy);
			setArg(kBlast, 6, cz);
			setArg(kBlast, 7, bDirs);
			setArg(kBlast, 8, bEn);
			setArg(kBlast, 9, maxSteps);
			setArg(kBlast, 10, bDes);
			launch(kBlast, 1, new long[] {rays});

			int[] des = new int[g.size()];
			download(bDes, Pointer.to(des), (long) g.size() * Sizeof.cl_int);
			boolean[] out = new boolean[g.size()];
			for (int i = 0; i < des.length; i++) {
				out[i] = des[i] != 0;
			}
			return out;
		} finally {
			release(mems);
		}
	}

	@Override
	public synchronized void integrate(EntityBatch b, float dt, float gravity, float drag, float maxSpeed) {
		int n = b.size();
		if (n == 0) {
			return;
		}
		long bytes = (long) n * Sizeof.cl_float;
		List<cl_mem> mems = new ArrayList<>();
		try {
			cl_mem[] m = new cl_mem[6];
			float[][] host = {b.px, b.py, b.pz, b.vx, b.vy, b.vz};
			for (int i = 0; i < 6; i++) {
				m[i] = alloc(mems, bytes);
				upload(m[i], Pointer.to(host[i]), bytes);
				setArg(kIntegrate, i, m[i]);
			}
			setArg(kIntegrate, 6, dt);
			setArg(kIntegrate, 7, gravity);
			setArg(kIntegrate, 8, drag);
			setArg(kIntegrate, 9, maxSpeed);
			launch(kIntegrate, 1, new long[] {n});
			for (int i = 0; i < 6; i++) {
				download(m[i], Pointer.to(host[i]), bytes);
			}
		} finally {
			release(mems);
		}
	}

	// ---- helper ----

	private cl_kernel kernel(String name) {
		int[] err = new int[1];
		cl_kernel k = clCreateKernel(program, name, err);
		check(err[0], "clCreateKernel " + name);
		return k;
	}

	private void launch(cl_kernel k, int dims, long[] global) {
		check(clEnqueueNDRangeKernel(queue, k, dims, null, global, null, 0, null, null), "clEnqueueNDRangeKernel");
	}

	private cl_mem alloc(List<cl_mem> mems, long bytes) {
		int[] err = new int[1];
		cl_mem m = clCreateBuffer(context, CL_MEM_READ_WRITE, bytes, null, err);
		check(err[0], "clCreateBuffer");
		mems.add(m);
		return m;
	}

	private void upload(cl_mem m, Pointer p, long bytes) {
		check(clEnqueueWriteBuffer(queue, m, true, 0, bytes, p, 0, null, null), "clEnqueueWriteBuffer");
	}

	private void download(cl_mem m, Pointer p, long bytes) {
		check(clEnqueueReadBuffer(queue, m, true, 0, bytes, p, 0, null, null), "clEnqueueReadBuffer");
	}

	private static void release(List<cl_mem> mems) {
		for (cl_mem m : mems) {
			clReleaseMemObject(m);
		}
	}

	private void setArg(cl_kernel k, int i, cl_mem m) {
		check(clSetKernelArg(k, i, Sizeof.cl_mem, Pointer.to(m)), "clSetKernelArg " + i);
	}

	private static void setArg(cl_kernel k, int i, int v) {
		check(clSetKernelArg(k, i, Sizeof.cl_int, Pointer.to(new int[] {v})), "clSetKernelArg " + i);
	}

	private static void setArg(cl_kernel k, int i, float v) {
		check(clSetKernelArg(k, i, Sizeof.cl_float, Pointer.to(new float[] {v})), "clSetKernelArg " + i);
	}

	private static void setArg(cl_kernel k, int i, double v) {
		check(clSetKernelArg(k, i, Sizeof.cl_double, Pointer.to(new double[] {v})), "clSetKernelArg " + i);
	}

	private static void check(int code, String what) {
		if (code != CL_SUCCESS) {
			throw new IllegalStateException(what + " fallito con codice OpenCL " + code);
		}
	}

	private static String deviceString(cl_device_id d, int param) {
		long[] size = new long[1];
		check(clGetDeviceInfo(d, param, 0, null, size), "clGetDeviceInfo");
		byte[] buf = new byte[(int) size[0]];
		check(clGetDeviceInfo(d, param, buf.length, Pointer.to(buf), null), "clGetDeviceInfo");
		int len = buf.length;
		while (len > 0 && buf[len - 1] == 0) {
			len--;
		}
		return new String(buf, 0, len, StandardCharsets.UTF_8);
	}

	private static String buildLog(cl_program program, cl_device_id device) {
		long[] size = new long[1];
		clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, size);
		byte[] buf = new byte[(int) size[0]];
		clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, buf.length, Pointer.to(buf), null);
		return new String(buf, StandardCharsets.UTF_8);
	}

	private static String readKernelSource() throws IOException {
		try (InputStream in = OpenClBackend.class.getResourceAsStream("/serverwithgpu/kernels.cl")) {
			if (in == null) {
				throw new IOException("kernels.cl non trovato nel jar");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
