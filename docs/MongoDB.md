# MongoDB Setup
Configuring MongoDB (on prem) can be a bit tricky if you want to use TLS for clients connecting to the database.
Here are some of the details encounted with the setup.

The environment used is as follows:

- CentOS 9 (with SELINUX)
- MongoDB, version 8.0.0-1
- MongoDB Client for Java, version 5.1.4

Download and install the MongoDB using **yum** or other tools available.
After installation it is required to edit the `/etc/mongod.conf` file.
Basically the changes needed is to permit remote connections and to specify
the details for TLS service side connections.

It is recommended to enable TLS for server side authentication. Alas, to specify
a certificate and private key for the MongoDB software.

First you will need to obtain a certificate from a trusted Certificate Authority.
Previous versions of MongoDB can use a selfsigned certificate, but it must now
be from a CA. The CA can be self hosted, but there are a few tricks to deal with.

See also [change in TLS requirements](https://jira.mongodb.org/browse/SERVER-72839).

## Certificates and keys

From your favorite CA obtain the root CA and signing CA certificates (not the private keys).
The file format should be **.pem** with base64 encoded certificates. They can and should
be bundled into a single file (here `mongodb-ca-bundle.pem`).

You will also need a **.pem** file with the MongoDB server certificate and private key.
The example here uses a **.pem** file with the privitate key in plain text (not encrypted).
The private key can be encrypted. If so, also specify the `certificateKeyFilePassword`
option in the TLS settings in the MongoDB configuration file.

## File permissions

Store the files (`mongodb-ca-bundle.pem` and `mongodb-server.pem`) with the other
MongoDB files. Here the directory `/var/lib/mongo` is used.

The Linux used is configured with SELINUX enabled. This will require additional
filesystem permissions for the two files. Run the following commands.

```
chown mongod:mongod /var/lib/mongo/mongodb*.pem
chmod 600 /var/lib/mongo/mongodb*.pem
chcon system_u:object_r:mongod_var_lib_t:s0 /var/lib/mongo/mongodb*.pem
ls -lahZ /var/lib/mongo
```

## mongod.conf

Edit the MongoDB configuration file `/etc/mongod.conf` to enable TLS.
Add/edit the net section.

```
Net:
  port: 27017
  bindIp: 0.0.0.0
  tls:
    mode: preferTLS
    certificateKeyFile: /var/lib/mongo/mongodb-server.pem
    # certificateKeyFilePassword: <not used here>
	CAFile: /var/lib/mongo/mongodb-ca-bundle.pem
	allowConnectionsWithoutCertificates: true
```

It is recommended to set `allowConnectionsWithoutCertificates: true`. This means
that a MongoDB client does not require to present a TLS client side certificate.
If this is set to false or omitted, then a client must present a TLS Client certificate.
The MongoDB client here is the MongoDB client for Java used in the PAM Target Connector 
for MongoDB. It runs in Apache Tomcat and uses Java. It is possible to specify 
the TLS client side certificate. However, this is done at the JVM level and will 
be applicable for all connectors and not only the MongoDB connector.
It is recommended to use `allowConnectionsWithoutCertificates: true`

If you absolutely must use TLS client side certificate, you must store the
client certificate and private key in a Java keystore. Add the parameters 
`-Djavax.net.ssl.KeyStore` and `-Djavax.net.ssl.keyStorePassword` when starting
Tomcat. This can be done with the JSSE_OPTS environment variable.

## Firewall rules

It is recommended to setup a firewall on the Linux server used. The FW rules must
permit inbound connections for teh port used for MongoDB.
The bindIp configuration is set to allow all clients to connect. To limit
the scope where permitted clients can originate from, setup a FW rule accordingly.

