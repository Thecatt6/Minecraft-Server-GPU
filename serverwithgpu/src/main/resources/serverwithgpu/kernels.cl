// Kernel OpenCL di Server With GPU.
// Ogni kernel DEVE restare identico, operazione per operazione, alla versione CPU di riferimento
// (CpuBackend.java): è così che il comando /serverwithgpu selftest può verificare la correttezza.

#pragma OPENCL EXTENSION cl_khr_fp64 : enable

inline double fade(double t) {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

inline double grad2(int h, double x, double y) {
    switch (h & 7) {
        case 0: return x + y;
        case 1: return -x + y;
        case 2: return x - y;
        case 3: return -x - y;
        case 4: return x;
        case 5: return -x;
        case 6: return y;
        default: return -y;
    }
}

inline double perlin2(__global const int* perm, double x, double y) {
    double fx = floor(x);
    double fy = floor(y);
    int xi = ((int)fx) & 255;
    int yi = ((int)fy) & 255;
    double xf = x - fx;
    double yf = y - fy;
    double u = fade(xf);
    double v = fade(yf);

    int aa = perm[perm[xi] + yi];
    int ab = perm[perm[xi] + yi + 1];
    int ba = perm[perm[xi + 1] + yi];
    int bb = perm[perm[xi + 1] + yi + 1];

    double g00 = grad2(aa, xf, yf);
    double g10 = grad2(ba, xf - 1.0, yf);
    double g01 = grad2(ab, xf, yf - 1.0);
    double g11 = grad2(bb, xf - 1.0, yf - 1.0);

    double x1 = g00 + u * (g10 - g00);
    double x2 = g01 + u * (g11 - g01);
    return x1 + v * (x2 - x1);
}

// Heightmap fBm: out[z * size + x] in [0, 1]
__kernel void heightmap(__global const int* perm, __global float* out,
                        const int originX, const int originZ, const int size,
                        const int octaves, const double scale, const double maxAmp) {
    int x = get_global_id(0);
    int z = get_global_id(1);
    if (x >= size || z >= size) return;

    double wx = (double)(originX + x) / scale;
    double wz = (double)(originZ + z) / scale;
    double sum = 0.0;
    double a = 1.0;
    double f = 1.0;
    for (int o = 0; o < octaves; o++) {
        sum += a * perlin2(perm, wx * f, wz * f);
        a *= 0.5;
        f *= 2.0;
    }
    double n = (sum / maxAmp + 1.0) * 0.5;
    n = fmin(fmax(n, 0.0), 1.0);
    out[z * size + x] = (float)n;
}

// Raycast di esplosione: un work-item per raggio.
// Indice blocco: (y * sz + z) * sx + x. resist < 0 significa aria (non distruttibile).
// destroyed[] viene scritto con 1: più raggi possono scrivere lo stesso valore, il che è sicuro.
__kernel void blast(__global const float* resist, const int sx, const int sy, const int sz,
                    const double cx, const double cy, const double cz,
                    __global const float* dirs, __global const float* energies,
                    const int maxSteps, __global int* destroyed) {
    int r = get_global_id(0);
    float e = energies[r];
    double dx = dirs[3 * r];
    double dy = dirs[3 * r + 1];
    double dz = dirs[3 * r + 2];

    for (int s = 0; s < maxSteps && e > 0.0f; s++) {
        double t = (double)s * 0.3;
        double fx = floor(cx + dx * t);
        double fy = floor(cy + dy * t);
        double fz = floor(cz + dz * t);
        if (fx < 0.0 || fy < 0.0 || fz < 0.0 || fx >= (double)sx || fy >= (double)sy || fz >= (double)sz) {
            break;
        }
        int x = (int)fx;
        int y = (int)fy;
        int z = (int)fz;
        int idx = (y * sz + z) * sx + x;

        e -= 0.225f;
        float res = resist[idx];
        if (res >= 0.0f) {
            e -= (res + 0.3f) * 0.3f;
            if (e > 0.0f) {
                destroyed[idx] = 1;
            }
        }
    }
}

// Integrazione batch di entità (SoA): gravità, attrito, limite di velocità, poi posizione.
__kernel void integrate(__global float* px, __global float* py, __global float* pz,
                        __global float* vx, __global float* vy, __global float* vz,
                        const float dt, const float gravity, const float drag, const float maxSpeed) {
    int i = get_global_id(0);
    float x = vx[i];
    float y = vy[i];
    float z = vz[i];

    y -= gravity * dt;
    float damp = 1.0f - drag * dt;
    x *= damp;
    y *= damp;
    z *= damp;

    float sp = sqrt(x * x + y * y + z * z);
    if (sp > maxSpeed) {
        float k = maxSpeed / sp;
        x *= k;
        y *= k;
        z *= k;
    }

    vx[i] = x;
    vy[i] = y;
    vz[i] = z;
    px[i] += x * dt;
    py[i] += y * dt;
    pz[i] += z * dt;
}
