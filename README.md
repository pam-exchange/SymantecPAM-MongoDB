# SymantecPAM-MongoDB
Symantec PAM Target Connector for MongoDB

The connector will allow password verify and update for users defined in a MongoDB database.
The eovironment used is as follows:

- CentOS 9 (with SELINUX)
- Java JDK, version 17.0.12
- Apache Tomcat, version 10.1.30
- MongoDB, version 8.0.0-1
- MongoDB Client for Java, version 5.1.4
- Symantec PAM, version 4.2.0.826
- capamextensioncore, version 4.21.0.82

## Installation

Installation and setup for MongoDB is found [here](/docs/MongoDB.md).

- Download the sources from GitHub
- Add the `capamextensioncore.jar` from Symantec PAM as part of local Maven repository.
- Edit the files `mongodb_messages.properties` and `MongoDBMessageConstants.java`
and adjust the message numbers to to match your environment.
It is important that the numbers does not conflict with any other numbers from other connectors.
- Run the command `mvnw package` to compile the connector.
- Copy the target connector `mongodb.war` to the Tomcat `webapps_targetconnector` directory.
- It is recommended to enable logging from the connector by adding the following to the
Tomcat `logging.properties` file.

```
#
# Target Connectors
#
ch.pam_exchange.pam_tc.mongodb.api.level = FINE
ch.pam_exchange.pam_tc.mongodb.api.handlers= java.util.logging.ConsoleHandler
```

It is recommended to use a TLS connection from the Tomcat TCF to the MongoDB server.
For this to work there are a few steps to be completet too.

- Create a Java truststore with the RootCA and SigningCA certificates used when the
certificate for the MongoDB server is signed. These are the same as used when
configuring MongoDB. However, they must be stored in a Java TrustStore file.
When starting Tomcat ensure that the truststore file and truststore password are
used by the JVM. This can be done by defining the JSSE_OPTS environment variable or
by editing the startup command directly. The options required when starting Tomcat are:

- -Djavax-net.ssl.trustStore=/opt/tomcat/conf/ca-bundle.truststore
- -Djavax.net.ssl.trustStorePassword=_password-for-trustStore_

Filename and location of the ca-bundle.truststore can be different.
Keep in mind that this is a JVM wide setting and will be used by any connector using TLS.

- Finally start/restart Tomcat


## Setup test users in MongoDB

To create some users for testing of the connector, you can run the following
commands from a MongoDB shell.

### Create pamMaster account
This account is used as a Master account in PAM. The user is permitted
to change passwords for other users.
```
use admin
db.createUser({user: "super",pwd: "Admin4cspm!",roles:[{role: "userAdminAnyDatabase",db: "admin"}]})
db.auth("super", "Admin4cspm!")
db.createUser({user: "pamMaster",pwd: "Admin4cspm!",roles:[{role: "userAdminAnyDatabase",db: "admin"}]})
db.logout()
```

In the example both users `super` and `pamMaster` are created in the database `admin`. 
From within PAM, only the user `pamMaster` is required and will change its password.

### Create some dependent accounts

Create a bunch of other users, which will be managed in PAM. They will be
created in the `test` database.

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

The role for the dependent users is set to allow change own password. This is not strictly required as the setup 
is envisioned to use a master account to change passwords.

This is what's needed to test the connector from within PAM.
There is a master account and a few dependent accounts. The `pamMaster` account is defined in the `admin` database and the dependent users are defined in the `test` database.

## Setup test users in PAM
### Add pamMaster

In the PAM GUI add an application for MongoDB using the `admin` database. This can also be done
using the API/CLI for PAM. 
The password composition policy used is defined without special characters.

![MongoDB Application for admin database](/docs/MongoDB-Application-admin.png)

Add the account for the `pamMaster` user. The current password must be known for this account.

![MongoDB Account for pamMaster](/docs/MongoDB-Account-pamMaster-1.png)
![MongoDB Account for pamMaster](/docs/MongoDB-Account-pamMaster-2.png)

Remember to set the account to update both PAM and target in the Password tab.

After adding the `pamMaster` account, revisit the account and change the password
to something random decided by PAM.

### Add dependent account

In the PAM GUI add an application for MongoDB using the `test` database.

![MongoDB Application for test database](/docs/MongoDB-Application-test.png)

Add the account for the `adm1` user. Generate a new password for this account.

![MongoDB Account for pamMaster](/docs/MongoDB-Account-adm1-1.png)
![MongoDB Account for pamMaster](/docs/MongoDB-Account-adm1-2.png)

Remember to set the account to update both PAM and target in the Password tab. 

In the GUI you will see the account name for the master account. Be sure to
select the correct master account created for the correct MongoDB instance.

If everything goes as planned, there are now two accounts defined, synchronized and both having a random password.

![MongoDB Account for pamMaster](/docs/MongoDB-Accounts.png)

