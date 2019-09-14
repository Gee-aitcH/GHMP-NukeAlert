## Nuke Alert Made By GH

###### (updated to v92)
### Features

All commands are under the prefix "ghna", which stands for GH's Nuke Alert. An Example of a Working Command - "ghna help". There are a few configurable settings, "mode", "al", "cnp", "logging". In addition, there are "bn", dn" and "rtrs".

mode: The Activeness of the plugin.

al: aka Alert Level, 0 = No one can hear the alert, 1 = only Admins can hear the alert, 2 = Everyone can hear the alert.

cnp: aka Core Nuke Protection, 0 = No Protection at all, 1 = Disable Explosions Near Core Only, 2 = Disable Explosions Everywhere on the map.

logging: The Activeness of the plugin's logging system, used for recording the nuke logs

bn: List the Nukes that was once Built on the map

dn: List the Nukes that was Destoryed

rtrs - List all the thorium reactors and their distance to the nearest ally core on the map

### Commands

  bn - List the Nukes that was once Built on the map (could be a long list)
  
  dn - List the Nukes that was Destoryed (could be a long list)
  
  rtrs - List all the thorium reactors and their distance to the nearest ally core on the map

  mode - Display current mode
  
  mode <true|false> - Change mode to active/deactive mode 
  
  al - Display current nuke announce level
  
  al <0|1|2> - Change who can hear the nuke alert (0 = No one, 1 = Admins only, 2 = Everyone)
  
  cnp - Display current Core Nuke Protection Status
  
  cnp <0|1|2> - Change Core Nuke Protection Status (0 = No Protection, 1 = Near Core Only, 2 = Everywhere on the map)
  
  logging - Display current Logging mode
  
  logging <true|false> - Change to Log or not to log the logs of nukes related to that game. 
  
  help - Display this message again

Note that clients cannot change any value of the settings, and only clients with admin status can see the currect status of the settings.

### Setup

Clone this repository first.
To edit the plugin display name and other data, take a look at `src/main.resources/plugin.json`.
Edit the name of the project itself by going into `settings.gradle`.

### Basic Usage

See `src/main/java/ghnukealert/ExamplePlugin.java` for some basic commands and event handlers.
Every main plugin class must extend `Plugin`. Make sure that `plugin.json` points to the correct main plugin class.

Please note that the plugin system is in **early alpha**, and is subject to major changes.

### Building a Jar

`gradlew jar` / `./gradlew jar`

Output jar should be in `build/libs`.


### Installing

Simply place the output jar from the step above in your server's `config/plugins` directory and restart the server.
List your currently installed plugins by running the `plugins` command.
