package com.serverwithgpu;

import com.serverwithgpu.command.ServerWithGpuCommands;
import com.serverwithgpu.compute.Backends;
import com.serverwithgpu.compute.ComputeBackend;
import com.serverwithgpu.config.ServerWithGpuConfig;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerWithGpu implements ModInitializer {
	public static final String MOD_ID = "serverwithgpu";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ServerWithGpuConfig config;
	private static ComputeBackend backend;

	@Override
	public void onInitialize() {
		config = ServerWithGpuConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
		backend = Backends.select(config.preferGpu);

		LOGGER.info("Server With GPU: abilitato={}, backend di calcolo={} (gpu={})",
				config.enabled, backend.name(), backend.isGpu());

		ServerWithGpuCommands.register();
	}

	public static ServerWithGpuConfig config() {
		return config;
	}

	public static ComputeBackend backend() {
		return backend;
	}
}
