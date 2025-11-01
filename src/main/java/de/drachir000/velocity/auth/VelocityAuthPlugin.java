package de.drachir000.velocity.auth;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.AccessLevel;
import lombok.Getter;
import org.bstats.velocity.Metrics;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Scanner;

@Getter
public class VelocityAuthPlugin {
	
	private final ProxyServer server;
	private final Logger logger;
	private final Path pluginDirectory;
	
	@Getter(AccessLevel.PRIVATE)
	private final Metrics.Factory metricsFactory;
	
	private Metrics metrics;
	
	@Inject
	public VelocityAuthPlugin(ProxyServer server, Logger logger, @DataDirectory Path pluginDirectory, Metrics.Factory metricsFactory) {
		this.server = server;
		this.logger = logger;
		this.pluginDirectory = pluginDirectory;
		this.metricsFactory = metricsFactory;
	}
	
	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
		
		logger.info("Initializing VelocityAuth...");
		
		logger.info("Registering bStats metrics...");
		this.metrics = registerBStats();
		
	}
	
	@Subscribe
	public void onProxyShutdown(ProxyShutdownEvent event) {
		
		logger.info("Shutting down VelocityAuth...");
		
		logger.info("Un-registering bStats metrics...");
		if (metrics != null) {
			metrics.shutdown();
			metrics = null;
		}
		
	}
	
	private Metrics registerBStats() {
		
		int pluginId = -1;
		
		try (
				InputStream inputStream = this.getClass().getResourceAsStream("bstats.json");
				Scanner scanner = new Scanner(inputStream).useDelimiter("\\A")
		) {
			
			String jsonString = scanner.hasNext() ? scanner.next() : "{}";
			JSONObject json = new JSONObject(jsonString);
			
			if (!json.has("plugin-id")) {
				throw new IllegalArgumentException("bstats.json is missing the 'plugin-id' field!");
			}
			
			pluginId = json.optInt("plugin-id", -1);
			
			if (pluginId == -1) {
				throw new IllegalArgumentException("bstats.json has an invalid 'plugin-id' field!");
			}
			
		} catch (IOException | NullPointerException | IllegalArgumentException | SecurityException e) {
			logger.warn("Failed to register bStats metrics: Failed to load bStats plugin Id!", e);
			return null;
		}
		
		Metrics metrics = getMetricsFactory().make(this, pluginId);
		
		// Custom Charts Here (if any in the future)
		
		return metrics;
		
	}
	
}