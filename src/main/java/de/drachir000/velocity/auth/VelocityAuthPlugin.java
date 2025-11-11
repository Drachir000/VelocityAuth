package de.drachir000.velocity.auth;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import de.drachir000.velocity.auth.config.MainConfig;
import de.drachir000.velocity.auth.data.DatabaseManager;
import lombok.AccessLevel;
import lombok.Getter;
import org.bstats.velocity.Metrics;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Getter
public class VelocityAuthPlugin {
	
	@Getter
	private static VelocityAuthPlugin instance = null;
	
	private final ProxyServer server;
	private final Logger logger;
	private final Path pluginDirectory;
	
	@Getter(AccessLevel.PRIVATE)
	private final Metrics.Factory metricsFactory;
	
	private Metrics metrics;
	private MainConfig mainConfig;
	private DatabaseManager databaseManager;
	
	@Inject
	public VelocityAuthPlugin(ProxyServer server, Logger logger, @DataDirectory Path pluginDirectory, Metrics.Factory metricsFactory) {
		
		if (instance != null) throw new IllegalStateException("Already initialized!");
		instance = this;
		
		this.server = server;
		this.logger = logger;
		this.pluginDirectory = pluginDirectory;
 		this.metricsFactory = metricsFactory;
		
	}
	
	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
		
		logger.info("Initializing VelocityAuth...");
		
		logger.info("Loading main configuration...");
		try {
			mainConfig = MainConfig.getInstance();
			if (mainConfig == null) throw new NullPointerException("MainConfig is null!");
		} catch (IOException | NullPointerException e) {
			logger.error("Failed to load main configuration!", e);
			throw new RuntimeException("Failed to load main configuration!", e);
		}
		
		if (mainConfig.getAuth_server() != null && !mainConfig.isServerPublic(mainConfig.getAuth_server())) {
			mainConfig.setAuth_server(null);
			logger.warn("auth_server is not public! Continuing without auth_server...");
		}
		
		logger.info("Connecting to database...");
		try {
			
			this.databaseManager = new DatabaseManager(this);
			this.databaseManager.connect();
			
			logger.info("Successfully connected to database!");
			
		} catch (Exception e) {
			logger.error("Failed to connect to database!", e);
			throw new RuntimeException("Database connection failed", e);
		}
		
		logger.info("Registering bStats metrics...");
		this.metrics = registerBStats();
		
		logger.info("VelocityAuth initialized.");
		
	}
	
	@Subscribe
	public void onProxyShutdown(ProxyShutdownEvent event) {
		
		logger.info("Shutting down VelocityAuth...");
		
		logger.info("Un-registering bStats metrics...");
		if (metrics != null) {
			metrics.shutdown();
			metrics = null;
		}
		
		logger.info("Closing database connection...");
		if (this.databaseManager != null) {
			this.databaseManager.close();
			logger.info("Database connection closed.");
		}
		
		logger.info("VelocityAuth shutdown.");
		
	}
	
	private Metrics registerBStats() {
		
		int pluginId = -1;
		
		try (
				InputStream inputStream = this.getClass().getResourceAsStream("/bstats.json");
		) {
			
			String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			JSONObject json = new JSONObject(jsonString);
			
			if (!json.has("plugin-id")) {
				throw new IllegalArgumentException("bstats.json is missing the 'plugin-id' field!");
			}
			
			pluginId = json.optInt("plugin-id", -1);
			
			if (pluginId == -1) {
				throw new IllegalArgumentException("bstats.json has an invalid 'plugin-id' field!");
			}
			
		} catch (IOException | NullPointerException | IllegalArgumentException | JSONException | SecurityException e) {
			logger.warn("Failed to register bStats metrics: Failed to load bStats plugin Id!", e);
			return null;
		}
		
		Metrics metrics = getMetricsFactory().make(this, pluginId);
		
		// Custom Charts Here (if any in the future)
		
		return metrics;
		
	}
	
}