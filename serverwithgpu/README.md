# Server With GPU

Mod Fabric per **Minecraft 26.3** (server), che sposta alcuni calcoli batch del server sulla GPU
via **OpenCL**, con fallback automatico su CPU.

> Stato: **motore di calcolo GPU/CPU + primo set di kernel verificabile**. Gli hook nel gioco
> vanilla (terreno, luce, minerali, pathfinding, random tick) NON sono ancora collegati: vedi
> "Cosa manca".

## Requisiti

| Componente    | Versione                                                       |
|---------------|----------------------------------------------------------------|
| Minecraft     | 26.3                                                           |
| Fabric Loader | 0.19.5+                                                        |
| Fabric API    | 0.163.0+26.3                                                   |
| Java          | 25                                                             |
| Loom          | 1.18-SNAPSHOT (`net.fabricmc.fabric-loom`, non-remapping)      |
| JOCL          | 2.0.6 (binding OpenCL, incluso nel jar con `include`)          |

### GPU NVIDIA (es. MX130)

- La MX130 è Maxwell (compute capability 5.0). **CUDA 13 non la supporta più**, quindi il mod usa
  **OpenCL**, che i driver NVIDIA continuano a esporre. Installa un driver NVIDIA recente con OpenCL
  (su Linux: il pacchetto `nvidia-driver-*` + `ocl-icd-libopencl1`).
- Verifica con `clinfo` (Linux) o con il tool OpenCL del driver: deve comparire "GeForce MX130"
  e l'estensione `cl_khr_fp64` (necessaria per risultati coerenti con la CPU).
- La MX130 ha 2 GB di VRAM e 384 core: per batch piccoli la CPU può essere più veloce.
  Il comando `/serverwithgpu benchmark` e `selftest` ti dicono la verità sulla tua macchina.

## Build

```bash
cd serverwithgpu
./gradlew build        # Windows: gradlew.bat build
./gradlew runServer    # avvia un server di test
```

Il jar è in `build/libs/`.

## Configurazione

Al primo avvio viene creato `config/serverwithgpu.json`:

```json
{
  "enabled": false,
  "preferGpu": true,
  "benchmarkSize": 256,
  "terrainOctaves": 5
}
```

- `enabled`: interruttore generale per gli hook vanilla (oggi non ancora collegati).
- `preferGpu`: usa la GPU se disponibile, altrimenti la CPU.

## Comandi (livello gamemaster)

- `/serverwithgpu status`: backend attivo.
- `/serverwithgpu benchmark`: heightmap di prova, tempo medio.
- `/serverwithgpu selftest`: confronta GPU e CPU su heightmap, esplosione (1352 raggi) ed entità,
  con differenze massime e tempi. **Esegui questo comando per primo sulla tua macchina.**

## Kernel e algoritmi

| Funzione            | CPU (riferimento)            | GPU (OpenCL)               | Stato      |
|---------------------|------------------------------|----------------------------|------------|
| Heightmap Perlin    | `CpuBackend.heightmap`       | kernel `heightmap`         | pronto     |
| Raycast esplosione  | `CpuBackend.blast`           | kernel `blast`             | pronto     |
| Integrazione entità | `CpuBackend.integrate`       | kernel `integrate`         | pronto     |
| Illuminazione       | -                            | -                          | da fare    |
| Pathfinding A*      | -                            | -                          | da fare    |
| Minerali            | -                            | -                          | da fare    |
| Random tick         | -                            | -                          | da fare    |

I kernel sono in `src/main/resources/serverwithgpu/kernels.cl` e replicano la CPU operazione per
operazione. Il selftest accetta differenze di arrotondamento float (heightmap < 1e-5, entità < 1e-4).

## Struttura

```
src/main/java/com/serverwithgpu/
├── ServerWithGpu.java                 entrypoint
├── config/ServerWithGpuConfig.java
├── compute/
│   ├── ComputeBackend.java            interfaccia comune CPU/GPU
│   ├── CpuBackend.java                riferimento CPU
│   ├── OpenClBackend.java             backend GPU (JOCL)
│   ├── ResilientBackend.java          GPU con fallback CPU su errore
│   ├── Backends.java                  selezione backend
│   ├── SelfTest.java                  confronto GPU/CPU
│   ├── BlastGrid, EntityBatch, Explosions, HeightmapRequest, PerlinNoise2D
├── terrain/HeightmapBenchmark.java
└── command/ServerWithGpuCommands.java
```

## Cosa manca (e perché)

- **Hook vanilla** (illuminazione, minerali, pathfinding, esplosioni in gioco, random tick, fisica
  entità): richiedono mixin sulle classi interne di 26.3 (es. `ServerLevel`, `LevelChunk`,
  `Explosion`, `Mob`). Per scriverli correttamente servono i nomi reali delle classi/metodi, che si
  ottengono dal sorgente decompilato generato da Loom.
- **Illuminazione e pathfinding su GPU**: kernel da scrivere e validare con lo stesso schema
  CPU/GPU; il pathfinding richiede un batching delle richieste per tick.
- **Coerenza del mondo**: il terreno generato su GPU deve dare esattamente la stessa altezza
  della CPU, altrimenti i chunk generati cambiano a seconda dell'hardware.
