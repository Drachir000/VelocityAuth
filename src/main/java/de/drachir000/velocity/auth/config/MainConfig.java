package de.drachir000.velocity.auth.config;

import de.drachir000.velocity.auth.VelocityAuthPlugin;
import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents the structure of the config.yml file.
 * SnakeYAML will automatically populate this object.
 * Note: Field names MUST match the YAML keys.
 */
@Setter
@Getter
@SuppressWarnings("unused") // Fields are populated by SnakeYAML
public class MainConfig {
	
	private static MainConfig instance;
	
	/**
	 * Constructs an instance of the {@link MainConfig} class.
	 * This constructor enforces the singleton pattern by ensuring that only one
	 * instance of this class is created during the application's lifecycle.
	 * If an instance already exists, an {@link IllegalStateException} is thrown.
	 * The instance is assigned to the static {@code instance} field.
	 * </br></br>
	 * Note: This is a private constructor and cannot be accessed externally.
	 */
	private MainConfig() {
		
		if (instance != null) throw new IllegalStateException("Already initialized! (this should never happen)");
		
		instance = this;
		
	}
	
	/**
	 * Retrieves the singleton instance of the {@linkplain MainConfig} class.
	 * If the instance is null, this method initializes it by loading the configuration
	 * through the {@linkplain #load()} method. The configuration file is parsed and loaded
	 * as a {@linkplain MainConfig} instance from the application's configuration file.
	 *
	 * @return the singleton instance of the {@linkplain MainConfig} class
	 * @throws IOException if an error occurs while loading the configuration file
	 */
	public static MainConfig getInstance() throws IOException {
		
		if (instance == null) {
			return load();
		}
		
		return instance;
		
	}
	
	/**
	 * Loads the main configuration file (config.yml) for the plugin.
	 * If the file does not exist, a default configuration is created from the embedded resource within the JAR file.
	 * The method uses SnakeYAML to parse the YAML configuration into an instance of {@linkplain MainConfig}.
	 *
	 * @return the parsed {@linkplain MainConfig} instance representing the configuration loaded from the file
	 * @throws IOException if an error occurs while creating or loading the configuration file
	 */
	private static MainConfig load() throws IOException {
		
		VelocityAuthPlugin plugin = VelocityAuthPlugin.getInstance();
		Path configPath = plugin.getPluginDirectory().resolve("config.yml");
		
		if (Files.notExists(configPath)) {
			
			plugin.getLogger().info("Creating default config.yml...");
			
			Files.createDirectories(configPath.getParent());
			
			try (InputStream in = MainConfig.class.getResourceAsStream("/config.yml");
			     OutputStream out = Files.newOutputStream(configPath)) {
				
				if (in == null) {
					throw new IOException("Could not find default config.yml in JAR!");
				}
				
				in.transferTo(out);
				
			}
			
		}
		
		// using "safe" constructor for SnakeYAML
		LoaderOptions options = new LoaderOptions();
		Yaml yaml = new Yaml(new Constructor(MainConfig.class, options));
		
		try (InputStream in = Files.newInputStream(configPath)) {
			return yaml.load(in);
		}
		
	}
	
	private List<String> public_servers;
	private @Nullable String auth_server;
	
	/**
	 * Determines whether the given server name is included in the list of public servers.
	 * The method performs a case-insensitive match or, if applicable, checks
	 * for a match using regular expressions defined in the list of public servers.
	 *
	 * @param serverName the name of the server to be checked; can be a plain name or a name that matches a regex pattern
	 * @return true if the serverName is explicitly listed or matches a regex pattern in the public servers, false otherwise
	 */
	public boolean isServerPublic(String serverName) {
		
		if (serverName == null || getPublic_servers() == null) return false;
		
		for (String publicServer : getPublic_servers()) {
			
			if (publicServer == null) continue;
			
			if (serverName.equalsIgnoreCase(publicServer)) return true;
			
			try {
				if (Pattern.matches(publicServer, serverName)) return true;
			} catch (PatternSyntaxException ignored) {
			}
			
		}
		
		return false;
		
	}
	
}
