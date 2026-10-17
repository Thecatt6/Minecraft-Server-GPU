package com.serverwithgpu.command;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import com.serverwithgpu.ServerWithGpu;
import com.serverwithgpu.compute.ComputeBackend;
import com.serverwithgpu.compute.HeightmapRequest;
import com.serverwithgpu.compute.SelfTest;
import com.serverwithgpu.terrain.HeightmapBenchmark;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ServerWithGpuCommands {
	private ServerWithGpuCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("serverwithgpu")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("status").executes(ServerWithGpuCommands::status))
				.then(literal("benchmark").executes(ServerWithGpuCommands::benchmark))
				.then(literal("selftest").executes(ServerWithGpuCommands::selftest)));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ComputeBackend backend = ServerWithGpu.backend();
		send(ctx, "Server With GPU: abilitato=" + ServerWithGpu.config().enabled
				+ ", backend=" + backend.name());
		return 1;
	}

	private static int benchmark(CommandContext<CommandSourceStack> ctx) {
		ComputeBackend backend = ServerWithGpu.backend();
		int size = ServerWithGpu.config().benchmarkSize;
		int octaves = ServerWithGpu.config().terrainOctaves;
		HeightmapRequest request = new HeightmapRequest(0L, 0, 0, size, octaves, 128.0);

		double ms = HeightmapBenchmark.averageMillis(backend, request, 5);
		send(ctx, String.format("Heightmap %dx%d (%d ottave) con %s: %.2f ms in media",
				size, size, octaves, backend.name(), ms));
		return 1;
	}

	private static int selftest(CommandContext<CommandSourceStack> ctx) {
		ComputeBackend backend = ServerWithGpu.backend();
		// Eseguito sul server thread: può richiedere qualche secondo.
		String report = SelfTest.run(backend);
		for (String line : report.split("\n")) {
			send(ctx, line);
		}
		return 1;
	}

	private static void send(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
