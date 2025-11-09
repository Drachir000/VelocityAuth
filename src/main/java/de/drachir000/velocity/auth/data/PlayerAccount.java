package de.drachir000.velocity.auth.data;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@DatabaseTable(tableName = "velocityauth_accounts") // Default name TODO overwrite with config value
public class PlayerAccount {
	
	/**
	 * The player's unique Minecraft UUID. This is the Primary Key.
	 */
	@DatabaseField(id = true, canBeNull = false, dataType = DataType.UUID)
	private UUID uuid;
	
	/**
	 * The player's unique Discord User ID.
	 * Indexed for fast lookups.
	 */
	@DatabaseField(index = true, unique = true)
	private long discordId;
	
	/**
	 * The player's unique email address.
	 * Indexed for fast lookups.
	 */
	@DatabaseField(index = true, canBeNull = false, unique = true)
	private String email;
	
	/**
	 * The exact date and time this account was created/accepted.
	 */
	@DatabaseField(canBeNull = false, dataType = DataType.DATE_LONG)
	private Date dateAccepted;
	
	/**
	 * The exact date and time this player was last online.
	 */
	@DatabaseField(canBeNull = false, dataType = DataType.DATE_LONG)
	private Date lastOnline;
	
	/**
	 * Timestamp (in millis) of when the player's ban expires.
	 * 0 by default, meaning not banned.
	 */
	@DatabaseField(defaultValue = "0")
	private long bannedUntil;
	
	/**
	 * No-argument constructor required by OrmLite.
	 */
	public PlayerAccount() {
	}
	
	/**
	 * Convenience constructor for creating a new player.
	 */
	public PlayerAccount(@Nonnull UUID uuid, long discordId, @Nonnull String email) {
		this.uuid = uuid;
		this.discordId = discordId;
		this.email = email;
		this.dateAccepted = new Date();
		this.lastOnline = new Date();
		this.bannedUntil = 0;
	}
	
	/**
	 * Full constructor for creating a player.
	 */
	public PlayerAccount(@Nonnull UUID uuid, long discordId, @Nonnull String email, @Nonnull Date dateAccepted, @Nonnull Date lastOnline, long bannedUntil) {
		this.uuid = uuid;
		this.discordId = discordId;
		this.email = email;
		this.dateAccepted = dateAccepted;
		this.lastOnline = lastOnline;
		this.bannedUntil = bannedUntil;
	}
	
}
