# BFTS

BFTS is a personal backup and synchronization program. It allows to backup multiple machines (PCs and Android phones) and to optionally synchronize backed up folders between different machines.

## Project status

The project is not ready to be used by general public. At the moment, it's more of a tech preview for developers, although I'm using it successfully at home. If you are interested to join me as a developer, contact me via the email I use in commits.

## Description

Each installation can act as a client, a server, or both. This allows for a great flexibility of backup options: backup on a local drive (USB, LAN etc.), backup on a different PC in local network, backup on a remote PC, or any combination of these. Both sources and destinations can be unavailable at any time. Android installations are client only and need a computer as backup destination.

A recap of BFTS main features:

- client is as light and dumb as possible to avoid high resource usage;
- bandwidth friendly (for example, there's no need to backup files already uploaded from another machine);
- backups are fully deduplicated (even within different computers), compressed and optionally encrypted;
- servers can be at home or anywhere else, e.g. on a storage VPS;
- restores happen by synchronizing to an empty folder or by browsing the backup as an ftp server.

BFTS is released under the [Affero GPL license](http://www.gnu.org/licenses/agpl.html). I could switch to another open source license in future. The software itself might undergo significant changes, **including the backup format**.

## How to try

BFTS requires a Java 8 JDK to compile and a Java 8 JRE to run. If using Oracle Java SE, version 8u151 or higher is recommended, otherwise the JRE must be patched using the [Java Cryptography Extension (JCE) Unlimited Strength](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html) patch from Oracle. OpenJDK does not need this patch.

Clone and compile with Maven (or Gradle for Android). You get a Jar file named bfts.jar which includes all dependencies. Run it with `java -jar bfts.jar`: you'll get a shell prompt. The shell is based on [Cliche](https://code.google.com/p/cliche/). Type `?list` to get a list of commands; type `?help <command name>` to get a little more information about that command.

First of all, use the `name` command to give a name to your client. Two clients with the same name must not connect to the same backup destination, so please keep names unique.

Then initialize a backup destination using the `init` command. BFTS uses SQLite to store file information, while file contents are stored as compressed files. (A BFTS backup can contain a lot of files, but this eases its replication using tools like rsync.) The SQLite database can run in memory or on disk. Running it in memory might make it faster, although you'll need more RAM and you'll have to wait for it to load at startup and to be stored at shutdown. It's fine to keep it on disk and let the OS cache it in RAM.

Use the `connect` command to connect to the destination you just created. Connection is not automatic as you might want to connect from another client. (In that case, you must also use the `publish` command to choose an HTTP port.) When connecting, you also choose whether to use encryption or not. Encryption is a property of the pair source+destination, which means for example that you can do an unencrypted backup to a USB drive, and an encrypted backup to a remote server.

Then use the `add` command to add directories to your backup. You can add as many directories as you want.

Then use the `start` command to start the backup and the `stop` command to stop it. Here is a example of command sequence.

```
name mylaptop
init usbdrive F:\bfts_backup false
connect usb F:\bfts_backup false
add usb documents C:\Users\John\Documents
add usb pictures C:\Users\John\Pictures
start
```

You could initialize a backup on your home server:

```
name myserver
init local /home/john/bfts_backup false
publish local 8715
start
```

Then go back to your laptop to add a new backup:

```
stop
connect server myserver_host_name_or_ip:8715 false
add server documents C:\Users\John\Documents
start
```

You can synchronize your laptop with your desktop by backing up sources with the same name using the same destination and encryption choice:

```
name mydesktop
connect server myserver_host_name_or_ip:8715 false
add server documents C:\Users\JohnDoe\Documents
start
```

_Note: synchronization is currently disabled by default. It can be enabled by modifying the database manually: `syncSource` and `syncTarget` must be equal to `1` for all involved sources._

Needless to say, all this will be greatly simplified when the GUI will be available.

# Intended BFTS Features

Note: even fully developed features are still under extensive testing and might change.

Local drive or directory means every directory that is accessible from the computer: internal hard drives, USB drives, network drives and so on. On OSes that allow mounting remote filesystems (e.g. sshfs on Linux), they can be used too.

Feature  | Development Status
------------- | -------------
Backup local directories on local drives | Done
Backup local directories on other instances of BFTS, running on remote computers (using HTTP) | Done
Optional client side encryption using a password | Done
HTTP data exchange encryption using a password | Done
Data deduplication, without sending data on the network twice | Done
Backup compression | Done
File and subdirectory exclusion | Done
Synchronization | Done, although it must be improved, for example conflicts are left untouched at the moment
Backup browsing via FTP | Done
Command line client | Done
GUI | Not done
Android client | Done (really basic, requires Android Oreo)
