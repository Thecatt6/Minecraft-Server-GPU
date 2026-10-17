# Server With GPU

Mod Fabric per **Minecraft 26.3** (server e client), scritta a partire dal template ufficiale
`fabric-example-mod` (branch `26.3`).

> Stato: **fondamenta del progetto**. Il backend CPU e la configurazione sono pronti;
> il backend GPU non è ancora implementato. Vedi "Cosa funziona oggi".

## Requisiti

| Componente   | Versione            |
|--------------|---------------------|
| Minecraft    | 26.3                |
| Fabric Loader| 0.19.5 o superiore  |
| Fabric API   | 0.163.0+26.3        |
| Java         | 25                  |
| Loom         | 1.18-SNAPSHOT (plugin `net.fabricmc.fabric-loom`, non-remapping) |

Minecraft 26.1 e successivi non sono offuscati: non servono mappings (Yarn), si usa `implementation`
invece di `modImplementation` e il jar finale è quello standard di `jar`.

## Build

```bash
cd serverwithgpu
./gradlew build      # su Windows: gradlew.bat build
```

Il jar si trova in `build/libs/`. Per il test in locale: `./gradlew runServer` (o `runClient`).

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

- `enabled`: interruttore generale. Con `false` il mod non modifica nessun calcolo del server.
- `preferGpu`: usa la GPU se disponibile, altrimenti la CPU in automatico.
- `benchmarkSize`, `terrainOctaves`: parametri del benchmark e dell'heightmap.

## Comandi

- `/serverwithgpu status`: mostra backend attivo e stato di abilitazione.
- `/serverwithgpu benchmark`: calcola un'heightmap di prova e mostra il tempo medio.

Richiedono permesso di livello gamemaster.

## Struttura

```
src/main/java/com/serverwithgpu/
├── ServerWithGpu.java            entrypoint, carica config e backend
├── config/ServerWithGpuConfig.java
├── compute/
│   ├── ComputeBackend.java       interfaccia: stesso input -> stesso output su CPU e GPU
│   ├── CpuBackend.java           backend di riferimento (sempre disponibile)
│   ├── Backends.java             sceglie il backend
│   ├── HeightmapRequest.java     parametri di una richiesta
│   └── PerlinNoise2D.java        Perlin 2D deterministico dal seme
├── terrain/HeightmapBenchmark.java
└── command/ServerWithGpuCommands.java
```

## Cosa funziona oggi

- Caricamento e salvataggio della configurazione.
- Calcolo heightmap multi-ottava deterministico su CPU.
- Comandi `status` e `benchmark`.

## Cosa NON è ancora implementato

- **Backend GPU** (OpenCL o Vulkan compute): `Backends.select` usa sempre la CPU e lo segnala nel log.
- **Hook nella generazione del terreno, illuminazione, minerali, pathfinding, esplosioni,
  fisica delle entità e random tick**: richiedono mixin o hook di Fabric API, e vanno aggiunti
  uno alla volta, ciascuno dietro il proprio interruttore in config.

## Nota sulla compatibilità con vanilla

Alcune funzioni che hai descritto (esplosioni e random tick che sostituiscono la logica vanilla,
generazione del terreno) cambiano il comportamento di gioco. Per questo ogni funzione deve:

1. essere disattivata di default;
2. produrre risultati identici alla versione CPU (altrimenti i mondi generati cambierebbero);
3. avere un fallback CPU automatico se la GPU non è presente o fallisce.
