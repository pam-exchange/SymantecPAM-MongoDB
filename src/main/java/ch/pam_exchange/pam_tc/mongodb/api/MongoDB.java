package ch.pam_exchange.pam_tc.mongodb.api;

import com.ca.pam.extensions.core.api.exception.ExtensionException;
import com.ca.pam.extensions.core.model.LoggerWrapper;
import com.ca.pam.extensions.core.MasterAccount;
import com.ca.pam.extensions.core.TargetAccount;

import java.io.FileInputStream;
import java.util.Properties;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ConnectionString;
import com.mongodb.ServerAddress;

import org.bson.Document;


public class MongoDB {
	private static final Logger LOGGER = Logger.getLogger(MongoDB.class.getName());

	/**
	 * Constants
	 */
	private static final int DEFAULT_PORT = 27017;
	private static final long DEFAULT_CONNECT_TIMEOUT = 10000;
	private static final long DEFAULT_READ_TIMEOUT = 2000;
	private static final String CHANGE_OTHER = "other";

	/**
	 * Instance variables used in the processCredentialsVerify and
	 * processCredentialsUpdate
	 */
	private String hostname = "";
	private int port = DEFAULT_PORT;
	private long connectTimeout = DEFAULT_CONNECT_TIMEOUT;
	private long readTimeout = DEFAULT_READ_TIMEOUT;
	private String database = "";
	private String username = "";
	private String oldPassword = "";
	private String newPassword = "";
	private String changeProcess = CHANGE_OTHER;
	private MasterAccount masterAccount = null;
	private String masterUsername = "";
	private String masterPassword = "";
	private String masterDatabase = "";
	private boolean useMaster = true;
	private boolean useTLS = false;
	private boolean useClientTLS= false;
	private String clientKeystoreFile= null;
	private String clientKeystorePassword= null;
	
	/*
	 * Constructor
	 */
	public MongoDB(TargetAccount targetAccount) {

		/*
		 * Server attributes
		 */
		this.hostname = targetAccount.getTargetApplication().getTargetServer().getHostName();
		LOGGER.fine(LoggerWrapper.logMessage("hostname= " + this.hostname));

		/*
		 * Application attributes
		 */
		try {
			this.port = Integer.parseUnsignedInt(targetAccount.getTargetApplication().getExtendedAttribute("port"));
		}
		catch (Exception e) {
			LOGGER.warning(LoggerWrapper.logMessage("Using default port"));
			this.port = DEFAULT_PORT;
		}
		LOGGER.fine(LoggerWrapper.logMessage("port= " + this.port));

		try {
			this.connectTimeout = Long.parseUnsignedLong(targetAccount.getTargetApplication().getExtendedAttribute("connectionTimeout"));
		}
		catch (Exception e) {
			LOGGER.warning(LoggerWrapper.logMessage("Using default connectTimeout"));
			this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
		}
		LOGGER.fine(LoggerWrapper.logMessage("connectTimeout= " + this.connectTimeout));

		try {
			this.readTimeout = Long.parseUnsignedLong(targetAccount.getTargetApplication().getExtendedAttribute("readTimeout"));
		}
		catch (Exception e) {
			LOGGER.warning(LoggerWrapper.logMessage("Using default readTimeout"));
			this.readTimeout = DEFAULT_READ_TIMEOUT;
		}
		LOGGER.fine(LoggerWrapper.logMessage("readTimeout= " + this.readTimeout));

		this.database = targetAccount.getTargetApplication().getExtendedAttribute("database");
		LOGGER.fine(LoggerWrapper.logMessage("database= " + this.database));

		this.useTLS = "true".equals(targetAccount.getTargetApplication().getExtendedAttribute("useTLS"));
		LOGGER.fine(LoggerWrapper.logMessage("useTLS= " + this.useTLS));

		/*
		 * Account attributes
		 */
		this.username = targetAccount.getUserName();
		LOGGER.fine(LoggerWrapper.logMessage("username= " + this.username));

		this.newPassword = targetAccount.getPassword();
		//LOGGER.fine(LoggerWrapper.logMessage("newPassword= "+this.newPassword));
		LOGGER.fine(LoggerWrapper.logMessage("newPassword= <hidden>"));

		this.oldPassword = targetAccount.getOldPassword();
		// LOGGER.fine(LoggerWrapper.logMessage("oldPassword= "+this.oldPassword));
		LOGGER.fine(LoggerWrapper.logMessage("oldPassword= <hidden>"));

		if (this.oldPassword == null || this.oldPassword.isEmpty()) {
			LOGGER.fine(LoggerWrapper.logMessage("oldPassword is empty, set oldPassword to newPassword"));
			this.oldPassword = this.newPassword;
		}

		this.changeProcess = targetAccount.getExtendedAttribute("changeProcess");
		LOGGER.fine(LoggerWrapper.logMessage("changeProcess= " + this.changeProcess));

		this.useMaster = MongoDB.CHANGE_OTHER.equals(this.changeProcess);
		if (this.useMaster) {
			this.masterAccount = targetAccount.getMasterAccount("otherAccount");
			if (this.masterAccount == null) {
				LOGGER.fine(LoggerWrapper.logMessage("No master account"));
				this.useMaster = false;
			}
			else {
				// username
				this.masterUsername = this.masterAccount.getUserName();
				if (this.masterUsername == null || this.masterUsername.isEmpty()) {
					LOGGER.severe(LoggerWrapper.logMessage("masterUsername is empty"));
					this.useMaster = false;
				}
				else {
					LOGGER.fine(LoggerWrapper.logMessage("masterUsername= " + this.masterUsername));

					// password
					this.masterPassword = this.masterAccount.getPassword();
					if (this.masterPassword == null || this.masterPassword.isEmpty()) {
						LOGGER.severe(LoggerWrapper.logMessage("masterPassword is empty"));
						this.useMaster = false;
					}
					else {
						// LOGGER.fine(LoggerWrapper.logMessage("masterPassword= "+masterPassword));

						// database
						this.masterDatabase = this.masterAccount.getAsTargetAccount().getTargetApplication().getExtendedAttribute("database");
						if (this.masterDatabase == null || this.masterDatabase.isEmpty()) {
							LOGGER.severe(LoggerWrapper.logMessage("masterDatabase is empty"));
							this.useMaster= false;
						}
						else {
							LOGGER.fine(LoggerWrapper.logMessage("masterDatabase= " + masterDatabase));
						}
					}
				}
			}
		}
	}

	/**
	 * Verifies credentials against target device. Stub method should be implemented
	 * by Target Connector Developer.
	 *
	 * @param targetAccount object that contains details for the account for
	 *                      verification Refer to TargetAccount java docs for more
	 *                      details.
	 * @throws ExtensionException if there is any problem while verifying the
	 *                            credential
	 *
	 */
	public void mongodbCredentialVerify() throws ExtensionException {

		MongoClient mongoClient = null;
		MongoCredential cred = null;

		try {
			cred = MongoCredential.createCredential(this.username, this.database, this.newPassword.toCharArray());
			//LOGGER.fine(LoggerWrapper.logMessage("cred= " + cred.toString()));

			mongoClient = createMongoClient(cred);

			MongoDatabase db = mongoClient.getDatabase(this.database);

			Document cmd = new Document("usersInfo", this.username);
			LOGGER.fine(LoggerWrapper.logMessage("cmd= " + cmd.toJson()));

			Document res = db.runCommand(cmd);
			LOGGER.fine(LoggerWrapper.logMessage("res= " + res.toJson()));
		}
		catch (MongoSecurityException e) {
			LOGGER.severe(LoggerWrapper.logMessage("SecurityException: Cannot authenticate user -- " + e.getMessage()));
			throw new ExtensionException(MongoDBMessageConstants.ERR_AUTHENTICATION, false);
		}
		catch (MongoTimeoutException | MongoSocketException e) {
			LOGGER.severe(LoggerWrapper.logMessage("TimeoutException: Cannot connect to host -- " + e.getMessage()));
			throw new ExtensionException(MongoDBMessageConstants.ERR_CONNECTION, false);
		}
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, LoggerWrapper.logMessage("Extension Exception"), e);
			throw new ExtensionException(MongoDBMessageConstants.ERR_EXCEPTION, false);
		}
		finally {
			cred = null;
			try { mongoClient.close(); } catch (Exception e) {}
		}

		// --- the end of verify ---
		LOGGER.info(LoggerWrapper.logMessage("MongoDB user '" + this.username + "' password verified - OK"));
	}

	/**
	 * Updates credentials against target device. Stub method should be implemented
	 * by Target Connector Developer.
	 *
	 * @param targetAccount object that contains details for the account for
	 *                      verification Refer to TargetAccount java docs for more
	 *                      details.
	 * @throws ExtensionException if there is any problem while update the
	 *                            credential
	 */
	public void mongodbCredentialUpdate() throws ExtensionException {

		MongoClient mongoClient = null;
		MongoCredential cred = null;

		try {
			if (this.useMaster) {
				cred = MongoCredential.createCredential(this.masterUsername, this.masterDatabase, this.masterPassword.toCharArray());
			}
			else {
				cred = MongoCredential.createCredential(this.username, this.database, this.oldPassword.toCharArray());
			}
			//LOGGER.fine(LoggerWrapper.logMessage("cred= " + cred.toString()));

			mongoClient = createMongoClient(cred);
			MongoDatabase db = mongoClient.getDatabase(this.database);

			Document cmd = new Document("updateUser", this.username).append("pwd", this.newPassword);
			//String str= cmd.toJson().replace(newPassword, "<hidden>");
			//String str = cmd.toJson().replaceAll("^(.*\"pwd\":\\W*\").*(\"})$", "$1<hidden>$2");
			//LOGGER.fine(LoggerWrapper.logMessage("cmd= " + str));

			Document res = db.runCommand(cmd);
			LOGGER.fine(LoggerWrapper.logMessage("runCommand res= " + res.toJson()));

		}
		catch (MongoSecurityException e) {
			LOGGER.severe(LoggerWrapper.logMessage("SecurityException: Cannot authenticate user -- " + e.getMessage()));
			throw new ExtensionException(MongoDBMessageConstants.ERR_AUTHENTICATION, false);
		}
		catch (MongoTimeoutException | MongoSocketException e) {
			LOGGER.severe(LoggerWrapper.logMessage("TimeoutException: Cannot connect to host -- " + e.getMessage()));
			throw new ExtensionException(MongoDBMessageConstants.ERR_CONNECTION, false);
		}
		catch (MongoCommandException e) {
			if (e.getMessage().contains("(Unauthorized)")) {
				LOGGER.severe(
						LoggerWrapper.logMessage("User is not authorized to update password -- " + e.getMessage()));
				throw new ExtensionException(MongoDBMessageConstants.ERR_AUTHORIZED, false);
			}
			else if (e.getMessage().contains("(UserNotFound)")) {
				LOGGER.severe(LoggerWrapper.logMessage("User is not found in database -- " + e.getMessage()));
				throw new ExtensionException(MongoDBMessageConstants.ERR_USERNOTFOUND, false);
			}
			else {
				LOGGER.log(Level.SEVERE, LoggerWrapper.logMessage("Extension Exception"), e);
				throw new ExtensionException(MongoDBMessageConstants.ERR_EXCEPTION, false);
			}
		}
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, LoggerWrapper.logMessage("Extension Exception"), e);
			throw new ExtensionException(MongoDBMessageConstants.ERR_EXCEPTION, false);
		}
		finally {
			cred = null;
			try {mongoClient.close();} catch (Exception e) {}
		}

		// --- the end of update ---
		LOGGER.info(LoggerWrapper.logMessage("MongoDB user '" + this.username + "' password updated - OK"));
	}

	/*
	 * createMongoClient
	 * 
	 */
	private MongoClient createMongoClient(MongoCredential cred) {

 		String uri= "mongodb://"+this.hostname+":"+this.port+"/";
 
		uri+= "?connectTimeoutMS=" + this.connectTimeout;
		uri+= "&socketTimeoutMS=" + this.readTimeout;
		if (this.useTLS) {
			uri+= "&tls=true";
			uri+= "&tlsAllowInvalidHostnames=true";
		}
		String str = uri.replaceAll("(&tlsCertificateKeyFilePassword)=(.*)$", "$1=<hidden>");
		LOGGER.fine(LoggerWrapper.logMessage("connectString= " + str));
		
		MongoClientSettings clientSettings = MongoClientSettings.builder()
				.applyConnectionString(new ConnectionString(uri))
				.credential(cred).build();

		return MongoClients.create(clientSettings);
	}
}
