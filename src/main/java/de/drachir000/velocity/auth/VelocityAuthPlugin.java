package de.drachir000.velocity.auth;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import org.slf4j.Logger;

import java.nio.file.Path;

@Getter
public class VelocityAuthPlugin {
	
	private final ProxyServer server;
	private final Logger logger;
	private final Path pluginDirectory;
	
	@Inject
	public VelocityAuthPlugin(ProxyServer server, Logger logger, @DataDirectory Path pluginDirectory) {
		this.server = server;
		this.logger = logger;
		this.pluginDirectory = pluginDirectory;
	}
	
	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
		logger.info("Initializing VelocityAuth...");
	}
	
	@Subscribe
	public void onProxyShutdown(ProxyShutdownEvent event) {
		logger.info("Shutting down VelocityAuth...");
	}
	
}