## Nuke Alert Made By GH

###### (updated to v96.0.5)
### Features

All commands are under the prefix "ghna", which stands for GH's Nuke Alert. An Example of a Working Command - "ghna help". There are a few configurable settings like "mode", "al", "cnp", "logging". In addition, there are "bn", dn" and "rtrs".

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

mode <true|false> - Change mode to

al - Display current nuke announce level

al <0|1|2> - Change who can hear the nuke alert (0 = No one, 1 = Admins only, 2 = Everyone)

aac - Display current Allow Admin Configure mode

aac <true|false> - Change to Admin can Configure the settings of this plugin or not

alim - Display current amount of max nuke alerts (/s)

alim <int> - Change the amount of max nuke alerts (/s) (-1 = No Limit)
  
cnp - Display current Core Nuke Protection Status
  
cnp <0|1|2> - Change Core Nuke Protection Status (0 = No Protection, 1 = Near Core Only, 2 = Everywhere on the map)
  
interval - Display current Nuke Alert Interval
  
interval <0|1|2> - Change Nuke Alert Interval (-1 = No Limit)
  
logging - Display current Logging mode
  
logging <true|false> - Change to Log or not to log the logs of nukes related to that game
  
clear - Clear the Alerting List of Nukes
  
help - Display this message again

Note: `allowadminconfig` is set to `true` by default, meaning admins can configure the settings. To switch off, do `ghna aac false` in server console.
  
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
