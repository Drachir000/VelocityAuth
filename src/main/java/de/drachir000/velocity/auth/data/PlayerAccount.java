package de.drachir000.velocity.auth.data;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.*;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode(of = {"uuid", "mcName", "discordId", "email", "dateAccepted", "lastOnline", "bannedUntil"})
@DatabaseTable(tableName = "velocityauth_accounts") // Default name overwritten with config value by DatabaseManager
public class PlayerAccount {
	
	@Setter(AccessLevel.PACKAGE)
	private boolean modified = true;
	
	/**
	 * The player's unique Minecraft UUID. This is the Primary Key.
	 */
	@DatabaseField(id = true, canBeNull = false, dataType = DataType.UUID)
	private UUID uuid;
	
	/**
	 * The player's unique Minecraft UUID. This is the Primary Key.
	 */
	@DatabaseField(index = true, canBeNull = false, unique = true)
	private String mcName;
	
	/**
	 * The player's unique Discord User ID.
	 * Indexed for fast lookups.
	 */
	@DatabaseField(index = true, canBeNull = false, unique = true)
	private String discordId;
	
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
	@DatabaseField(canBeNull = false, dataType = DataType.DATE_LONG)
	private Date bannedUntil;
	
	/**
	 * No-argument constructor required by OrmLite.
	 */
	public PlayerAccount() {
	}
	
	/**
	 * Convenience constructor for creating a new player.
	 */
	public PlayerAccount(@Nonnull UUID uuid, @Nonnull String mcName, @Nonnull String discordId, @Nonnull String email) {
		this.uuid = uuid;
		this.mcName = mcName;
		this.discordId = discordId;
		this.email = email;
		this.dateAccepted = new Date();
		this.lastOnline = new Date();
		this.bannedUntil = new Date(0);
	}
	
	/**
	 * Full constructor for creating a player.
	 */
	public PlayerAccount(@Nonnull UUID uuid, @Nonnull String mcName, @Nonnull String discordId, @Nonnull String email, @Nonnull Date dateAccepted, @Nonnull Date lastOnline, @Nonnull Date bannedUntil) {
		this.uuid = uuid;
		this.mcName = mcName;
		this.discordId = discordId;
		this.email = email;
		this.dateAccepted = dateAccepted;
		this.lastOnline = lastOnline;
		this.bannedUntil = bannedUntil;
	}
	
	// Special Getter
	
	/**
	 * Checks if the player account is currently banned.
	 *
	 * @return true if the current time is earlier than the timestamp specified
	 * in the bannedUntil field, indicating the player is banned;
	 * otherwise, false.
	 */
	public boolean isBanned() {
		return bannedUntil.getTime() > System.currentTimeMillis();
	}
	
	// Setter
	
	/**
	 * Sets the UUID for the player account and marks the account as modified.
	 * Be careful when changing the primary key of an account.
	 *
	 * @param uuid the unique identifier to be set for the player account. Must not be null.
	 */
	public void setUuid(@Nonnull UUID uuid) {
		this.uuid = uuid;
		this.modified = true;
	}
	
	/**
	 * Updates the Minecraft username associated with the player account and marks the account
	 * as modified.
	 *
	 * @param mcName the new Minecraft username to be set for the player account. Must not be null.
	 */
	public void setMcName(@Nonnull String mcName) {
		this.mcName = mcName;
		this.modified = true;
	}
	
	/**
	 * Updates the Discord ID associated with the player account and marks the account as modified.
	 *
	 * @param discordId the new Discord ID to be set for the player account. Must not be null.
	 */
	public void setDiscordId(@Nonnull String discordId) {
		this.discordId = discordId;
		this.modified = true;
	}
	
	/**
	 * Updates the email address associated with the player account and marks the account as modified.
	 *
	 * @param email the new email address to be set for the player account. Must not be null.
	 */
	public void setEmail(@Nonnull String email) {
		this.email = email;
		this.modified = true;
	}
	
	/**
	 * Sets the date when the player's account was accepted and marks the account as modified.
	 * Should only be set once.
	 *
	 * @param dateAccepted the date to be set as the acceptance date for the player account. Must not be null.
	 */
	public void setDateAccepted(@Nonnull Date dateAccepted) {
		this.dateAccepted = dateAccepted;
		this.modified = true;
	}
	
	/**
	 * Updates the last online date for the player account and marks the account as modified.
	 *
	 * @param lastOnline the date and time to be set as the player's last online timestamp. Must not be null.
	 */
	public void setLastOnline(@Nonnull Date lastOnline) {
		this.lastOnline = lastOnline;
		this.modified = true;
	}
	
	/**
	 * Updates the timestamp that specifies until when the player account is banned
	 * and marks the account as modified.
	 *
	 * @param bannedUntil the date and time until which the player account is banned. Must not be null.
	 */
	public void setBannedUntil(@Nonnull Date bannedUntil) {
		this.bannedUntil = bannedUntil;
		this.modified = true;
	}
	
}
