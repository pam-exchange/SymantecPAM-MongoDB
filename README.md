# SymantecPAM-MongoDB
Symantec PAM Target Connector for MongoDB

The connector will allow password verify and update for users defined in a MongoDB database.
The eovironment used is as follows:

- CentOS 9 (with SELINUX)
- Java JDK, version 17.0.12
- Apache Tomcat, version 10.1.30
- MongoDB, version 8.0.0-1
- Symantec PAM, version 4.2.0.826
- capamextensioncore, version 4.21.0.82

## Installation

1) Download the sources from GitHub
2) Add the capamextensioncore.jar from Symantec PAM as part of local Maven repository.
3) Edit the files `mongodb_messages.properties` and `MongoDBMessageConstants.java` and adjust the message numbers to to match your environment. It is important that the numbers does not conflict with any other numbers from other connectors.
4) Run the command `mvnw package` to compile the connector.
5) Copy the target connector `mongodb.war` to the Tomcat `webapps_targetconnector` directory.
6) To enable logging of the connector add the following to the Tomcat `logging.properties` file

```
#
# Target Connectors
#
ch.pam_exchange.pam_tc.mongodb.api.level = FINE
ch.pam_exchange.pam_tc.mongodb.api.handlers= java.util.logging.ConsoleHandler
```

It is recommended to use a TLS connection from the Tomcat TCF to the MongoDB. For this to work there are a few steps to be completet too.

7) It is required to issue a server certificate (and private key) to the MongoDB. Obtain this from your favorite Certificate Authority.
8) For MongoDB usage obtain the certificates in a ca-bundle.pem file with the Root CA and any other Signing CA certificates (not private keys). The file is used by MongoDB. More about this later.
9) Create a Java truststore with the same two certificates. This is used when starting Tomcat. When starting Tomcat ensure that the truststore file and truststore password are used. The options when starting Tomcat are:
- -Djavax-net.ssl.trustStore=/opt/tomcat/conf/ca-bundle.truststore
- -Djavax.net.ssl.trustStorePassword=<password>

Filename and location of the ca-bundle.truststore can be different.
Keep in mind that this is a JVM wide setting and will be used by any connector using TLS.

10) For MongoDB edit the configuration file `/etc/mongod.conf` to enable TLS.
Add/edit the section # Network Interfaces to look like this:

```
Net:
  port: 27017
  bindIp: 0.0.0.0
  tls:
    mode: preferTLS
    certificateKeyFile: /var/lib/mongo/mongodb-server.pem
	CAFile: /var/lib/mongo/mongodb-ca-bundle.pem
	allowConnectionsWithoutCertificates: true
```

This will allow TLS server side authentication. Alas, the connection is secured but without TLS client side authentication. Filename and location of the `mongodb-server.pem` and `mongodb-ca-bundle.pem` can be different. When using SELINUX the two files must have the correct permissions set.

Run the commands
```
chown mongod:mongod /var/lib/mongo/mongodb*.pem
chmod 600 /var/lib/mongo/mongodb*.pem
chcon system_u:object_r:mongod_var_lib_t:s0 /var/lib/mongo/mongodb*.pem
```

It is possible also to use TLS client side authentication. This is done by having a client side keystore with the client certificate and private key, and adding the `-Djavax.net.ssl.keyStore` and `-Djavax.net.ssl.keyStorePassword` parameters when starting Tomcat. Keep in mind that this is a JVM wide setting and it may influence all connectors and not only the MongoDB connector.

11) Start Tomcat

That's it for installation of the connector and setup of MongoDB.

## Test users

To create some users for testing of the connector, you can run the following commands from a MongoDB shell.

### Create pamMaster account
This account is used as a Master account in PAM. The user is permitted to change passwords for other users.
```
use admin
db.createUser({user: "super",pwd: "Admin4cspm!",roles:[{role: "userAdminAnyDatabase",db: "admin"}]})
db.auth("super", "Admin4cspm!")
db.createUser({user: "pamMaster",pwd: "Admin4cspm!",roles:[{role: "userAdminAnyDatabase",db: "admin"}]})
db.logout()
```

In the example both users `super` and `pamMaster` are created. From within PAM, only the user `pamMaster` is required.

### Create some dependent accounts

Create a bunch of other users, which will be managed in PAM.

```
use admin
db.logout()
db.auth("super","Admin4cspm!");
use test
db.dropUser("adm1")
db.dropUser("adm2")
db.dropUser("adm3")
db.dropRole("testChangeOwnPasswordRole")
db.createRole({ role: "testChangeOwnPasswordRole",privileges: [{resource: { db: "test", collection: ""},actions: [ "changeOwnPassword" ]}],roles: []})

db.createUser({user: "adm1",pwd: "Admin4cspm!",roles:["readWrite", { role:"testChangeOwnPasswordRole", db:"test" }]})
db.createUser({user: "adm2",pwd: "Admin4cspm!",roles:["readWrite", { role:"testChangeOwnPasswordRole", db:"test" }]})
db.createUser({user: "adm3",pwd: "Admin4cspm!",roles:["readWrite", { role:"testChangeOwnPasswordRole", db:"test" }]})

use admin
db.logout()
```

This is what's needed to test the connector from within PAM. There is a master account and a few dependent accounts. The `pamMaster` account is defined in the `admin` database and the dependent users are defined in the `test` database.

## Add pamMaster to PAM

In the PAM GUI add an application for MongoDB using the `admin` database.

![MongoDB Application for admin database](/docs/MongoDB-Application-admin.png)

Add the account for the `pamMaster` user. The current password must be known for this account.

![MongoDB Account for pamMaster](/docs/MongoDB-Account-pamMaster-1.png)
![MongoDB Account for pamMaster](/docs/MongoDB-Account-pamMaster-2.png)


## Add dependent accounts to PAM

In the PAM GUI add an application for MongoDB using the `test` database.

![MongoDB Application for test database](/docs/MongoDB-Application-test.png)

Add the account for the `adm1` user. Generate a new password for this account.

![MongoDB Account for pamMaster](/docs/MongoDB-Account-adm1-1.png)
![MongoDB Account for pamMaster](/docs/MongoDB-Account-adm1-2.png)

If everything goes as planned, there are now two accounts defined and synchronized.

![MongoDB Account for pamMaster](/docs/MongoDB-Accounts.png)


