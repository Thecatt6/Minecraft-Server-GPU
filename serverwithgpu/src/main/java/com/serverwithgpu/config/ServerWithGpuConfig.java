package com.serverwithgpu.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.serverwithgpu.ServerWithGpu;

/**
 * Configurazione del mod. Tutto è disattivato di default, così il comportamento vanilla
 * resta invariato finché l'utente non abilita esplicitamente le funzioni.
 */
public class ServerWithGpuConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Interruttore generale: se false il mod non modifica alcun calcolo del server. */
	public boolean enabled = false;

	/** Se true prova a usare la GPU; se non disponibile usa automaticamente la CPU. */
	public boolean preferGpu = true;

	/** Lato (in blocchi) dell'area usata dal comando di benchmark. */
	public int benchmarkSize = 256;

	/** Numero di ottave del rumore usato per l'heightmap. */
	public int terrainOctaves = 5;

	public static ServerWithGpuConfig load(Path file) {
		ServerWithGpuConfig cfg = new ServerWithGpuConfig();
		try {
			if (Files.exists(file)) {
				try (Reader reader = Files.newBufferedReader(file)) {
					ServerWithGpuConfig read = GSON.fromJson(reader, ServerWithGpuConfig.class);
					if (read != null) {
						cfg = read;
					}
				}
			} else {
				Files.createDirectories(file.getParent());
				try (Writer writer = Files.newBufferedWriter(file)) {
					GSON.toJson(cfg, writer);
				}
			}
		} catch (IOException e) {
			ServerWithGpu.LOGGER.error("Impossibile leggere {}, uso i valori di default", file, e);
		}
		cfg.sanitize();
		return cfg;
	}

	private void sanitize() {
		benchmarkSize = Math.clamp(benchmarkSize, 16, 2048);
		terrainOctaves = Math.clamp(terrainOctaves, 1, 12);
	}
}
