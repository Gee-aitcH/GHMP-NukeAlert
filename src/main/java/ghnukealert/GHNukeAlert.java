package ghnukealert;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.function.Supplier;
import io.anuke.arc.math.geom.Geometry;
import io.anuke.arc.util.*;
import io.anuke.arc.util.Timer;
import io.anuke.mindustry.content.*;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.plugin.Plugin;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.BuildBlock;
import io.anuke.mindustry.world.blocks.power.NuclearReactor;

import java.io.*;
import java.util.*;

import static ghnukealert.GHUtil.LN;
import static ghnukealert.GHUtil.getTiles;
import static io.anuke.mindustry.Vars.*;
import static io.anuke.mindustry.core.GameState.State.playing;
import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class GHNukeAlert extends Plugin {

    /*  List of Settings Variable Used
     * nukealert
     * nukealertinterval
     * nukealertlevel
     * nukeprotection
     * nukelogging
     * allowadminconfig
     */

    //TODO:
    //

    private static FileHandle ghnaDirectory;
    private static FileHandle ghnaLogsDirectory;
    private static FileHandle ghnaSettingsDirectory;
    private ObjectMap<Tile, Player> prebuiltNukes = new ObjectMap<>();
    private Array<BuiltNuke> builtNukes = new Array<>();
    private Array<DestroyedNuke> destroyedNukes = new Array<>();
    private ObjectMap<Player, NukesBuilt> nukeBuilders = new ObjectMap<>();
    private HashMap<Tile, Boolean> alertingNukes = new HashMap<>();

    private ObjectMap<Player, Array<String>> pendingMessages = new ObjectMap<>();
    private Array<String> pendingInfo = new Array<>();

    private long startTime, endTime;
    private int minInterval = 1000, maxInterval = 60000;
    private long lastAlert, lastSent = -1;
    private float coreProtectionRange = 12 * tilesize;
    private String textColor = "[lightgray]";

    private boolean toAlert = true;
    private ObjectMap<Player, HashSet<Tile>> builtNukesToAlert = new ObjectMap<>();
    private ObjectMap<Player, HashSet<Tile>> buildingNukesToAlert = new ObjectMap<>();
    private Player nullBuilder = new Player() {{
        con = null;
    }};
    private int index = 0, sentThisSec;

    private boolean sendingMsg = false;

    private String params = "[mode|bn|dn|rtrs|al|ac|help] [true|false]";
    private String clientdescription = LN +
            "[accent]Nuke Alert[] Made By [sky]GH[]" + LN +
            "[orange]bn [amount][] - List the Nukes that was once Built on the map (could be a long list)" + LN +
            "[orange]dn [amount][] - List the Nukes that was Destoryed (could be a long list)" + LN +
            "[orange]rtrs [amount][] - List all the thorium reactors and their distance to the nearest ally core on the map" + LN +

            "[orange]mode[] - Display current mode" + LN +
            "[orange]mode <true|false>[] - Change mode to" + LN +
            "[orange]al[] - Display current nuke announce level" + LN +
            "[orange]al <0|1|2>[] - Change who can hear the nuke alert (0 = No one, 1 = Admins only, 2 = Everyone)" + LN +
            "[orange]aac[] - Display current Allow Admin Configure mode" + LN +
            "[orange]aac <true|false>[] - Change to Admin can Configure the settings of this plugin or not" + LN +
            "[orange]alim[] - Display current amount of max nuke alerts (/s)" + LN +
            "[orange]alim <int>[] - Change the amount of max nuke alerts (/s) (-1 = No Limit)" + LN +
            "[orange]cnp[] - Display current Core Nuke Protection Status" + LN +
            "[orange]cnp <0|1|2>[] - Change Core Nuke Protection Status (0 = No Protection, 1 = Near Core Only, 2 = Everywhere on the map)" + LN +
            "[orange]interval[] - Display current Nuke Alert Interval" + LN +
            "[orange]interval <0|1|2>[] - Change Nuke Alert Interval (-1 = No Limit)" + LN +
            "[orange]logging[] - Display current Logging mode" + LN +
            "[orange]logging <true|false>[] - Change to Log or not to log the logs of nukes related to that game" + LN +

            "[orange]clear[] - Clear the Alerting List of Nukes" + LN +
            "[orange]help[] - Display this message again" + LN +
            "(Admin Only)";


    private String description = LN +
            "Nuke Alert Made By GH" + LN +
            "bn - List the Nukes that was once Built on the map (could be a long list)" + LN +
            "dn - List the Nukes that was Destoryed (could be a long list)" + LN +
            "rtrs - List all the thorium reactors and their distance to the nearest ally core on the map" + LN +

            "mode - Display current mode" + LN +
            "mode <true|false> - Change mode to" + LN +
            "al - Display current nuke announce level" + LN +
            "al <0|1|2> - Change who can hear the nuke alert (0 = No one, 1 = Admins only, 2 = Everyone)" + LN +
            "aac - Display current Allow Admin Configure mode" + LN +
            "aac <true|false> - Change to Admin can Configure the settings of this plugin or not" + LN +
            "alim - Display current amount of max nuke alerts (/s)" + LN +
            "alim <int> - Change the amount of max nuke alerts (/s) (-1 = No Limit)" + LN +
            "cnp - Display current Core Nuke Protection Status" + LN +
            "cnp <0|1|2> - Change Core Nuke Protection Status (0 = No Protection, 1 = Near Core Only, 2 = Everywhere on the map)" + LN +
            "interval - Display current Nuke Alert Interval" + LN +
            "interval <0|1|2> - Change Nuke Alert Interval (-1 = No Limit)" + LN +
            "logging - Display current Logging mode" + LN +
            "logging <true|false> - Change to Log or not to log the logs of nukes related to that game" + LN +

            "clear - Clear the Alerting List of Nukes" + LN +
            "help - Display this message again";

    public GHNukeAlert() {
        Events.on(WorldLoadEvent.class, e -> worldLoadEvent());
        Events.on(BuildSelectEvent.class, this::buildSelectEvent);
        Events.on(BlockBuildEndEvent.class, this::blockBuildEndEvent);
        Events.on(BlockDestroyEvent.class, this::blockDestroyEvent);
        ghnaDirectory = dataDirectory.child("ghplugins/");
        ghnaDirectory = ghnaDirectory.child("ghna/");
        ghnaDirectory.mkdirs();
        ghnaLogsDirectory = ghnaDirectory.child("logs/");
        ghnaSettingsDirectory = ghnaDirectory.child("settings/");
        ghnaLogsDirectory.mkdirs();
        ghnaSettingsDirectory.mkdirs();
        startTime = endTime = lastAlert = -1;

        //Welp, this is the update function now. Lmao
        Core.app.addListener(new ApplicationListener() {
            public void update() {
                //printToEveryone("Update: state: " + state.getState() + ": " + (state.getState() != playing) + ", index: " + index + ": " + (index % 6 != 0));
                if (state.getState() != playing) return;
                if (playerGroup.all().size < 1){
                    index = 0;
                    return;
                }
                if (index % 30 * 60 == 0){
                    for(HashSet<Tile> tiles : builtNukesToAlert.values())
                        for(Tile tile : tiles)
                            if(nukeIndex(tile) == -1)
                                tiles.remove(tile);

                    for(HashSet<Tile> tiles : buildingNukesToAlert.values())
                        for(Tile tile : tiles)
                            if(nukeIndex(tile) == -1)
                                tiles.remove(tile);
                }
                //info("update(): builtNukesToAlert: " + builtNukesToAlert.size + ", buildingNukesToAlert: " + buildingNukesToAlert.size + LN +
                //        (toAlert + ", last: " + lastAlert + ", inter: " + nukealertinterval() + ", t: " + time()));
                if (toAlert && lastAlert + nukealertinterval() < time() && (builtNukesToAlert.size >= 1 || buildingNukesToAlert.size >= 1)) {
                    String strToAlert = "[scarlet]";
                    if (builtNukesToAlert.size > 0) {
                        strToAlert += "[Alert]: BUILT NUKES ALERT!" + LN;

                        strToAlert += "NUKES: <[gold][";
                        HashSet<Tile> set = new HashSet<>();
                        outer:
                        for (HashSet<Tile> tiles : builtNukesToAlert.values())
                            for(Tile tile : tiles)
                                if (set.size() < 10) set.add(tile);
                                else break outer;
                        Array<String> arr = new Array<>();
                        set.forEach(t -> arr.add("[" + t.x + ", " + t.y + "]"));
                        strToAlert += arr.toString(", ");
                        strToAlert += "][]>." + LN;

                        strToAlert += "BUILT BY PLAYERS: [< ";
                        Array<String> playerNames = new Array<>();
                        for (Player player : builtNukesToAlert.keys())
                            if(playerNames.size < 10) playerNames.add(GHUtil.colorizeName(player));
                            else break;
                        strToAlert += playerNames.toString(" [scarlet]>, < ");
                        strToAlert += " [scarlet]>].";

                        //info("update(): builtNukesToAlert.strToAlert: " + strToAlert.toString());
                    }
                    if (buildingNukesToAlert.size > 0) {
                        if (strToAlert.length() > 0)
                            strToAlert += (LN + LN);
                        strToAlert += "[Alert]: BUILDING NUKES ALERT!" + LN;

                        strToAlert += "NUKES: <[gold][";
                        HashSet<Tile> set = new HashSet<>();
                        outer:
                        for (HashSet<Tile> tiles : buildingNukesToAlert.values())
                            for(Tile tile : tiles)
                                if (set.size() < 10) set.add(tile);
                                else break outer;
                        Array<String> arr = new Array<>();
                        set.forEach(t -> arr.add("[" + t.x + ", " + t.y + "]"));
                        strToAlert += arr.toString(", ");
                        strToAlert += "][]>. " + LN;

                        strToAlert += "BUILDING BY PLAYERS: [< ";
                        Array<String> playerNames = new Array<>();
                        for (Player player : buildingNukesToAlert.keys())
                            if(playerNames.size < 10) playerNames.add(GHUtil.colorizeName(player));
                            else break;
                        strToAlert += playerNames.toString(" [scarlet]>, < ");
                        strToAlert += " [scarlet]>].";
                        //info("update(): buildingNukesToAlert.strToAlert: " + strToAlert.toString());
                    }

                    pendingMessages.keys().forEach(p1 -> {
                        boolean has = false;
                        for(Player p2 : playerGroup.all())
                            if(p1 == p2) has = true;
                        if(!has) pendingMessages.remove(p1);
                    });

                    playerGroup.all().forEach(p -> {
                        if(!pendingMessages.containsKey(p)) pendingMessages.put(p, new Array<>());
                    });
                    String o = strToAlert;
                    pendingMessages.keys().forEach(p -> print(o, p, true, false));
                    lastAlert = time();
                }


                //if(index % 4 == 0){
                    if(lastSent + 1000 < time()){
                        sentThisSec = 0;
                        lastSent = time();
                    }
                    if(pendingMessages.size > 0)
                        for (; sentThisSec < nukealertlimit(); sentThisSec += pendingMessages.size) {
                            for(Player p : pendingMessages.keys())
                                if (pendingMessages.containsKey(p) && pendingMessages.get(p).size >= 1)
                                    p.sendMessage(pendingMessages.get(p).remove(0));
                        }
                //}

                index++;
            }
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("ghna", params, "Type 'ghna help' for more information", arg -> {
            if (arg.length == 0)
                info(plguinStatus());
            else
                switch (arg[0]) {
                    //case "d":
                    //    info("Ing: " + buildingNukesToAlert + "\nEd: " + builtNukesToAlert + "\nSize: Ing: " + buildingNukesToAlert.size + ", Ed: " + builtNukesToAlert.size + ", index: " + index);
                    //    break;
                    case "bn":
                        if (arg.length > 1 && GHUtil.parseInt(arg[1]) != Integer.MAX_VALUE)
                            info(builtNukesToStringArr(false, GHUtil.parseInt(arg[1])));
                        else
                            info(builtNukesToStringArr(false));
                        break;
                    case "dn":
                        if (arg.length > 1 && GHUtil.parseInt(arg[1]) != Integer.MAX_VALUE)
                            info(destroyedNukesToStringArr(false, GHUtil.parseInt(arg[1])));
                        else
                            info(destroyedNukesToStringArr(false));
                        break;
                    case "rtrs":
                        if (arg.length > 1 && GHUtil.parseInt(arg[1]) != Integer.MAX_VALUE)
                            info(reactorsToStringArr(false, GHUtil.parseInt(arg[1])));
                        else
                            info(reactorsToStringArr(false));
                        break;

                    case "help":
                        info(description);
                        break;

                    case "clear":
                        builtNukesToAlert.clear();
                        buildingNukesToAlert.clear();
                        info("Alerting List of Nukes has been Cleared");
                        break;

                    case "mode":
                        if (arg.length == 1) info("Alert is currently " + GHUtil.onOffString(nukealert()));
                        else {
                            switch (arg[1]) {
                                case "true":
                                    nukealert(true);
                                    break;
                                case "false":
                                    nukealert(false);
                                    break;
                                default:
                                    info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.");
                                    break;
                            }
                            info("Alert is set to " + GHUtil.onOffString(nukealert()));
                        }
                        break;
                    case "aac":
                        if (arg.length == 1)
                            info("Allow Admin to Config is currently " + GHUtil.onOffString(allowadminconfig()));
                        else {
                            switch (arg[1]) {
                                case "true":
                                    allowadminconfig(true);
                                    break;
                                case "false":
                                    allowadminconfig(false);
                                    break;
                                default:
                                    info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.");
                                    break;
                            }
                            info("Allow Admin to Config is set to " + GHUtil.onOffString(allowadminconfig()));
                        }
                        break;

                    case "al":
                        if (arg.length == 1) info("Alert Level is currently [" + nukealertlevel() + "]");
                        else {
                            int level = GHUtil.parseInt(arg[1]);
                            if (level < 0 || level > 2) {
                                info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.");
                                break;
                            }
                            nukealertlevel(level);
                            info("Alert Level is set to [" + nukealertlevel() + "]");
                        }
                        break;

                    case "alim":
                        if (arg.length == 1) info("Current Maximum Nuke Alert Rate is [" + nukealertlimit() + "/s]");
                        else {
                            int interval = GHUtil.parseInt(arg[1]);
                            if (interval == Integer.MAX_VALUE) {
                                info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.");
                                break;
                            }
                            if (interval < 0) interval = Integer.MAX_VALUE;
                            nukealertlimit(interval);
                            info("Maximum Nuke Alert Rate is set to [" + nukealertlimit() + "/s]");
                        }
                        break;


                    case "cnp":
                        if (arg.length == 1) info("Core Nuke Protection is currently [" + nukeprotection() + "]");
                        else {
                            int level = GHUtil.parseInt(arg[1]);
                            if (level < 0 || level > 2) {
                                info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.");
                                break;
                            }
                            nukeprotection(level);
                            info("Core Nuke Protection is set to [" + nukeprotection() + "]");
                        }
                        break;

                    case "interval":
                        if (arg.length == 1) info("Alert Interval is currently [" + nukealertinterval() + "]");
                        else {
                            int level = GHUtil.parseInt(arg[1]);
                            if (level < minInterval)
                                info("[WARNING] arg[1](" + arg[1] + ") Is Below the Minimal Interval.");
                            else if (level > maxInterval)
                                info("[WARNING] arg[1](" + arg[1] + ") Is Above the Maximum Interval.");
                            level = level < minInterval ? minInterval : level > maxInterval ? maxInterval : level;
                            nukealertinterval(level);
                            info("Alert Interval is set to [" + nukealertinterval() + "]");
                        }
                        break;

                    case "logging":
                        if (arg.length == 1) info("Logging is currently " + GHUtil.onOffString(nukelogging()));
                        else {
                            switch (arg[1]) {
                                case "true":
                                    nukelogging(true);
                                    break;
                                case "false":
                                    nukelogging(false);
                                    break;
                                default:
                                    info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.");
                                    break;
                            }
                            info("Logging is set to " + GHUtil.onOffString(nukelogging()));
                        }
                        break;

                    default:
                        info("You need some help? Do 'ghna help'");
                        break;
                }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("ghna", params, "Type '/ghna help' for more information", (arg, player) -> {
            if (!player.isAdmin) {
                print("[scarlet][ACCESS DENIED] This is an admin only command.", player);
                return;
            }
            if (arg.length == 0)
                print(plguinStatus(), player);
            else
                switch (arg[0]) {
                    case "bn":
                        if (arg.length > 1 && GHUtil.parseInt(arg[1]) != Integer.MAX_VALUE)
                            print(builtNukesToStringArr(false, GHUtil.parseInt(arg[1])), player);
                        else
                            print(builtNukesToStringArr(false), player);
                        break;

                    case "dn":
                        if (arg.length > 1 && GHUtil.parseInt(arg[1]) != Integer.MAX_VALUE)
                            print(destroyedNukesToStringArr(false, GHUtil.parseInt(arg[1])), player);
                        else
                            print(destroyedNukesToStringArr(false), player);
                        break;

                    case "rtrs":
                        if (arg.length > 1 && GHUtil.parseInt(arg[1]) != Integer.MAX_VALUE)
                            print(reactorsToStringArr(false, GHUtil.parseInt(arg[1])), player);
                        else
                            print(reactorsToStringArr(false), player);
                        break;

                    case "help":
                        print(clientdescription, player);
                        break;

                    case "clear":
                        builtNukesToAlert.clear();
                        buildingNukesToAlert.clear();
                        print("Alerting List of Nukes has been Cleared", player);
                        break;

                    case "aac":
                        info("Allow Admin to Config is currently " + GHUtil.onOffString(allowadminconfig()));
                        break;

                    case "al":
                        if (!allowadminconfig() || arg.length == 1)
                            print("Alert Level is currently [" + nukealertlevel() + "]", player);
                        else {
                            int level = GHUtil.parseInt(arg[1]);
                            if (level < 0 || level > 2) {
                                print("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.", player);
                                break;
                            }
                            nukealertlevel(level);
                            print("Alert Level is set to [" + nukealertlevel() + "]", player);
                        }
                        break;

                    case "alim":
                        if (!allowadminconfig() || arg.length == 1)
                            print("Current Maximum Nuke Alert Rate is [" + nukealertlimit() + "/s]", player);
                        else {
                            int interval = GHUtil.parseInt(arg[1]);
                            if (interval == Integer.MAX_VALUE) {
                                print("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.", player);
                                break;
                            }
                            if (interval < 0) interval = Integer.MAX_VALUE;
                            nukealertlimit(interval);
                            print("Maximum Nuke Alert Rate is set to [" + nukealertlimit() + "]", player);
                        }
                        break;

                    case "cnp":
                        if (!allowadminconfig() || arg.length == 1)
                            print("Core Nuke Protection is currently [" + nukeprotection() + "]", player);
                        else {
                            int level = GHUtil.parseInt(arg[1]);
                            if (level < 0 || level > 2) {
                                print("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.", player);
                                break;
                            }
                            nukeprotection(level);
                            print("Core Nuke Protection is set to [" + nukeprotection() + "]", player);
                        }
                        break;

                    case "interval":
                        if (!allowadminconfig() || arg.length == 1)
                            print("Alert Interval is currently [" + nukealertinterval() + "]", player);
                        else {
                            int level = GHUtil.parseInt(arg[1]);
                            if (level < minInterval)
                                print("[WARNING] arg[1](" + arg[1] + ") Is Below the Minimal Interval.", player);
                            else if (level > maxInterval)
                                print("[WARNING] arg[1](" + arg[1] + ") Is Above the Maximum Interval.", player);
                            level = level < minInterval ? minInterval : level > maxInterval ? maxInterval : level;
                            nukealertinterval(level);
                            print("Alert Interval is set to [" + nukealertinterval() + "]", player);
                        }
                        break;

                    case "logging":
                        if (!allowadminconfig() || arg.length == 1)
                            print("Logging is currently " + GHUtil.onOffString(nukelogging()), player);
                        else {
                            switch (arg[1]) {
                                case "true":
                                    nukelogging(true);
                                    break;
                                case "false":
                                    nukelogging(false);
                                    break;
                                default:
                                    print("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.", player);
                                    break;
                            }
                            print("Logging is set to " + GHUtil.onOffString(nukelogging()), player);
                        }
                        break;

                    case "mode":
                        if (!allowadminconfig() || arg.length == 1)
                            print("Alert is currently " + GHUtil.onOffString(nukealert()), player);
                        else {
                            switch (arg[1]) {
                                case "true":
                                    nukealert(true);
                                    break;
                                case "false":
                                    nukealert(false);
                                    break;
                                default:
                                    print("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.", player);
                                    break;
                            }
                            print("Alert is set to " + GHUtil.onOffString(nukealert()), player);
                        }
                        break;
                }
        });
    }

    private void reset() {
        prebuiltNukes.clear();
        builtNukes.clear();
        destroyedNukes.clear();
        nukeBuilders.clear();
        pendingMessages.clear();
        pendingInfo.clear();
        builtNukesToAlert.clear();
        buildingNukesToAlert.clear();
        alertingNukes.clear();
        startTime = time();
    }

    private String plguinStatus() {
        return Log.format(LN +
                        "Nuke Alert Status: {0}" + LN +
                        "Alert Interval: {1}" + LN +
                        "Alert Level: {2}" + LN +
                        "Nuke Protection: {3}" + LN +
                        "Do Logging: {4}" + LN +
                        "Allow Admin Configure: {5}",
                        //"Log Visibility: {5}",
                nukealert() + "",
                nukealertinterval() + "",
                nukealertlevel() + "",
                nukeprotection() + "",
                nukelogging() + "",
                allowadminconfig());//,
                //nukelogvisibility() + "");
    }

    private void alert(Tile tile, Player builder, boolean building) {
        if (builder == null) builder = nullBuilder;
        //info("alert(): t: [" + tile.x + ", " + tile.y + "], p: " + GHUtil.colorizeName(builder) + ", b: " + building);
        if (!nukealert()) {
            if (builtNukesToAlert.get(builder).size() > 0) builtNukesToAlert.clear();
            if (buildingNukesToAlert.get(builder).size() > 0) buildingNukesToAlert.clear();
            return;
        }
        Tile t = world.tile(tile.x, tile.y);
        Block block = t.block();
        boolean isNuke = true;
        if (block == Blocks.thoriumReactor && !building) {
            if (!builtNukesToAlert.containsKey(builder))
                builtNukesToAlert.put(builder, new HashSet<>());
            builtNukesToAlert.get(builder).add(tile);
        } else if ((block instanceof BuildBlock && ((BuildBlock.BuildEntity) t.entity).cblock == Blocks.thoriumReactor) && building) {
            if (!buildingNukesToAlert.containsKey(builder))
                buildingNukesToAlert.put(builder, new HashSet<>());
            buildingNukesToAlert.get(builder).add(tile);
        } else isNuke = false;

        if ((!isNuke || !building) && buildingNukesToAlert.containsKey(builder)) {
            buildingNukesToAlert.get(builder).remove(tile);
            if (buildingNukesToAlert.get(builder).size() == 0)
                buildingNukesToAlert.remove(builder);
        }
        if ((!isNuke || building) && builtNukesToAlert.containsKey(builder)) {
            builtNukesToAlert.get(builder).remove(tile);
            if (builtNukesToAlert.get(builder).size() == 0)
                builtNukesToAlert.remove(builder);
        }
        if(!isNuke) alertingNukes.remove(tile);
    }

    //Event Listeners
    private void worldLoadEvent() {
        if (nukealert() && nukelogging()) saveNukeInfos();
        reset();
        for (Tile t : GHUtil.getTiles())
            if ((t.block() == Blocks.thoriumReactor || (t.block() instanceof BuildBlock && ((BuildBlock.BuildEntity) t.entity).cblock == Blocks.thoriumReactor)) &&
                    dstToClosestCore(t) <= coreProtectionRange && (!alertingNukes.containsKey(t) || alertingNukes.get(t))) {
                alertingNukes.put(t, false);
                alert(t, nullBuilder, t.block() == Blocks.thoriumReactor);
                info("[Alert]: A NUKE IS BUILT AT " + smootherTilePosLog(t.x, t.y) + "!");
            }
    }

    private void buildSelectEvent(BuildSelectEvent e) {
        if (!nukealert()) return;
        if (!(e.builder != null && e.builder.buildRequest() != null && e.builder instanceof Player && e.builder.buildRequest().block == Blocks.thoriumReactor))
            return;
        Player player = (Player) e.builder;
        if (!e.breaking) {
            prebuiltNukes.put(e.tile, player);
            if (dstToClosestCore(e.tile) <= coreProtectionRange && (!alertingNukes.containsKey(e.tile) || !alertingNukes.get(e.tile))) {
                alertingNukes.put(e.tile, true);
                info("[Alert]: A NUKE IS BEING BUILT AT " + smootherTilePosLog(e.tile.x, e.tile.y) +
                        " BY PLAYER: [ " + GHUtil.fullPlayerName(player, textColor) + " ]" + "!");
            }
        }
        if (dstToClosestCore(e.tile) <= coreProtectionRange)
            alert(e.tile, player, true);
    }

    private void blockBuildEndEvent(BlockBuildEndEvent e) {
        if (!nukealert()) return;
        Player builder = e.player;
        if (e.tile.block() == Blocks.thoriumReactor && !e.breaking) {
            for (Tile prebuiltNuke : prebuiltNukes.keys()) {
                if (prebuiltNuke == e.tile)
                    builder = prebuiltNukes.get(prebuiltNuke);
                builtNukes.add(new BuiltNuke(time(), e.tile, builder));
                if (builder == null) continue;
                if (!nukeBuilders.containsKey(builder))
                    nukeBuilders.put(builder, new NukesBuilt());
                nukeBuilders.get(builder).add(dstToClosestCore(e.tile) <= coreProtectionRange);
                if (dstToClosestCore(e.tile) <= coreProtectionRange && (!alertingNukes.containsKey(e.tile) || alertingNukes.get(e.tile))) {
                    alertingNukes.put(e.tile, false);
                    info("[Alert]: A NUKE IS BUILT AT " + smootherTilePosLog(e.tile.x, e.tile.y) +
                            " BY PLAYER: [ " + GHUtil.fullPlayerName(builder, textColor) + " ]" + "!");
                }
            }
        } else if (e.tile.block() == Blocks.air && e.breaking) {
            for (BuiltNuke nuke : builtNukes)
                if (nuke.x == e.tile.x && nuke.y == e.tile.y) {
                    destroyedNukes.add(new DestroyedNuke(time(), e.tile, new ConditionWhenDestroyed(-1f, -1f, -1)));
                    break;
                }
        }
        if (dstToClosestCore(e.tile) <= coreProtectionRange)
            alert(e.tile, builder, false);
        prebuiltNukes.remove(e.tile);
    }

    private void blockDestroyEvent(BlockDestroyEvent e) {
        if (!nukealert()) return;
        if (dstToClosestCore(e.tile) <= coreProtectionRange)
            alert(e.tile, nullBuilder, false);
        if (e.tile.block() == Blocks.thoriumReactor) {
            NuclearReactor.NuclearReactorEntity entity = (NuclearReactor.NuclearReactorEntity) e.tile.entity;
            destroyedNukes.add(new DestroyedNuke(time(), e.tile, new ConditionWhenDestroyed(entity.healthf(), entity.heat, entity.items.total())));
            switch (nukeprotection()) {
                case 1:
                    if (dstToClosestCore(e.tile) > coreProtectionRange) return;
                    entity.items.set(Items.thorium, Integer.MIN_VALUE);
                    entity.heat = Float.MIN_VALUE;
                    break;
                case 2:
                    entity.items.set(Items.thorium, Integer.MIN_VALUE);
                    entity.heat = Float.MIN_VALUE;
                    break;
            }
        } else if (e.tile.block().name.equals("build3")) {
            prebuiltNukes.remove(e.tile);
        }
    }
//  Event Listeners

    //To String
    private Array<String> builtNukesToStringArr(boolean server) {
        return builtNukesToStringArr(server, -1);
    }
    private Array<String> builtNukesToStringArr(boolean server, int amount) {
        Array<String> result = new Array<>();
        StringBuilder sb = new StringBuilder();
        BuiltNuke nuke;
        int i = amount < 0 ? 0 : builtNukes.size - amount;
        for (; i < builtNukes.size; i++) {
            sb.delete(0, sb.length());
            nuke = builtNukes.get(i);
            sb.append("[").append(smootherIntLog(i, builtNukes.size, "0")).append("]: ");
            sb.append(msToDate(nuke.time)).append(": ");
            sb.append(smootherTilePosLog(nuke.x, nuke.y)).append(": ");
            if (server) sb.append("[").append(nuke.builder == null ? "null" : nuke.builder.id).append("]");
            else sb.append(GHUtil.fullPlayerName(nuke.builder, textColor));
            result.add(sb.toString());
        }
        return result;
    }

    private Array<String> destroyedNukesToStringArr(boolean server) {
        return destroyedNukesToStringArr(server, -1);
    }
    private Array<String> destroyedNukesToStringArr(boolean server, int amount) {
        Array<String> result = new Array<>();
        StringBuilder sb = new StringBuilder();
        DestroyedNuke nuke;
        int i = amount < 0 ? 0 : destroyedNukes.size - amount;
        for (; i < destroyedNukes.size; i++) {
            sb.delete(0, sb.length());
            nuke = destroyedNukes.get(i);
            sb.append("[").append(smootherIntLog(i, destroyedNukes.size, "0")).append("]: ");
            sb.append(msToDate(nuke.time)).append(": ");
            sb.append(smootherTilePosLog(nuke.x, nuke.y)).append(": ");
            if (server) sb.append(nuke.cwd.toString(false));
            else sb.append(nuke.cwd.toString(true));
            sb.append("]");
            result.add(sb.toString());
        }
        return result;
    }

    private Array<String> reactorsToStringArr(boolean server) {
        return reactorsToStringArr(server, -1);
    }
    private Array<String> reactorsToStringArr(boolean server, int amount) {
        HashMap<Team, HashMap<Tile, Float>> reactors = new HashMap<>();
        Array<String> result = new Array<>();

        for(Team team : Team.values()) reactors.put(team, new HashMap<>());

        for(Tile tile : getTiles())
            if (tile.block() == Blocks.thoriumReactor && closestCore(tile) != null)
                reactors.get(tile.getTeam()).put(tile, tile.dst(Objects.requireNonNull(closestCore(tile))));

        int i = amount < 0 ? Integer.MAX_VALUE : amount;
        for (Team team : Team.values()) {
            if(i < 0) break;
            HashMap<Tile, Float> teamReactors = reactors.get(team), sorted =
                    teamReactors.entrySet().stream().sorted(comparingByValue()).collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

            reactors.put(team, new HashMap<>(sorted));

            if (teamReactors.size() < 1) continue;
            Tile closest = teamReactors.keySet().toArray(new Tile[0])[0];
            float dst = teamReactors.get(closest);
            result.add("Team: [[[#" + closest.getTeam().color + "]" + closest.getTeam() + "[]]: Nukes: \n");
            teamReactors.keySet().forEach(t -> result.add(GHUtil.tileToSimpleString(t)));
            result.add("\nShortest Dst: [" + smootherFloatLog(dst, 5) + "]");
            i--;
        }
        return result;
    }

    private Array<String> nukeBuildersToStringArr() {
        Array<String> result = new Array<>();
        for (Player player : nukeBuilders.keys())
            result.add("[" + smootherIntLog(result.size, nukeBuilders.size, "0") + ": " + nukeBuilders.get(player).toString(player));
        return result;
    }

    private Array<String> oStrArrToStr(Array<String> arr) {
        return oStrArrToStr(arr, -1);
    }

    private Array<String> oStrArrToStr(Array<String> arr, int total) {
        Array<String> result = new Array<>();
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < arr.size; i++) {
            if (msg.length() + arr.get(i).length() >= 1024 || (i % 8 == 0 && i != 0) && msg.length() != 0) {
                result.add(msg.toString());
                if (msg.length() > 0) msg.delete(0, msg.length() - 1);
            }
            msg.append(LN).append(arr.get(i));
            if (i + 1 < arr.size) msg.append(", ");
        }
        if(total != -1)
            msg.append(LN).append("Total: ").append(arr.size - total);
        result.add(msg.toString());
        return result;
    }
//  To String

    //Save
    private void saveNukeInfos() {
        if (startTime == endTime) return;
        System.out.println(Core.settings.getDataDirectory());
        endTime = time();
        File file = new File(ghnaDirectory.path() + "\\" + msToFileName(time()) + ".txt");
        System.out.println(file.getAbsolutePath());

        //create new txt file or sth
        try (BufferedWriter br = new BufferedWriter(new FileWriter(file))) {
            //Yes. I am using BufferedWriter here. GH is Here.
            br.write("[Nuke Info]:{" + LN + LN);
            br.write("Time: From: " + msToDate(startTime) + "    To: " + msToDate(endTime) + "    Time Zone: " + getTimeZone() + LN);
            br.write("Map: [" + world.getMap().name() + "]" + LN);
            br.write("Nukes Built: [" + builtNukes.size + "]    Nukes Destroyed: [" + destroyedNukes.size + "]" + LN + LN + LN);
            br.write("Built Nukes: {" + LN + LN);
            br.write(builtNukesToStringArr(true).toString(LN) + " }" + LN + LN + LN);
            br.write("Destroyed Nukes: {" + LN + LN);
            br.write(destroyedNukesToStringArr(true).toString(LN) + " }" + LN + LN + LN);
            br.write("Player Who Built Nuke: {" + LN + LN);
            br.write(nukeBuildersToStringArr().toString(LN) + " }" + LN + LN + LN);

            info("[GHNA]: New Log is Created [" + file.getAbsolutePath() + "]");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
//  Save

    //Log & Send Message
    private void info(String str) {
        pendingInfo.add(str);
        infoPrint();
    }

    private void info(Array<String> arr) {
        for(String str : oStrArrToStr(arr)){
            info(str);
        }
    }

    private void infoPrint() {
        if (pendingInfo.size > 0) Log.info(pendingInfo.remove(0));
        if (pendingInfo.size > 0) Timer.schedule(this::infoPrint, Time.delta());
    }


    private void print(String str, Player player) {
        print(str, player, false, true);
    }
    private void print(String str, Player player, boolean urgent) {
        print(str, player, urgent, true);
    }
    private void print(String str, Player player, boolean urgent, boolean defColor) {
        if (!pendingMessages.containsKey(player))
            pendingMessages.put(player, new Array<>());

        String[] lines = str.split(LN);
        StringBuilder current = new StringBuilder();
        int maxLines = 8;
        for(int i = 0, j = 0; i <= lines.length; i++, j++) {
            if (i < lines.length) {
                if (j != 0) current.append(LN);
                    current.append(lines[i]);
                if (j < maxLines) continue;
            }
            String o = (defColor ? textColor : "") + current.toString();
            if (urgent) pendingMessages.get(player).insert(0, o);
            else pendingMessages.get(player).add(o);
            current.delete(0, current.length());
            j = 0;
        }
    }


    private void print(Array<String> arr, Player player) {
        print(arr, player, false, true);
    }
    private void print(Array<String> arr, Player player, boolean urgent, boolean defColor) {
        for(String str : oStrArrToStr(arr)) {
            print(str, player, false, true);
        }
    }

    private void printToEveryone(String str) {
        printToEveryone(str, false);
    }
    private void printToEveryone(String str, boolean urgent) {
        int alertLevel = nukealertlevel();
        for (Player player : playerGroup.all())
            if (alertLevel == 2 || (alertLevel == 1 && player.isAdmin))
                print(str, player, urgent);

        //Let's not do this, Logging Alerts into Console is Pretty Spammy.
        //info(str);
    }
//  Log & Send Message

    //Utils
    private int nukeIndex(Tile t){
        return t.block() == Blocks.thoriumReactor ? 1 : t.block() instanceof BuildBlock && ((BuildBlock.BuildEntity) t.entity).cblock == Blocks.thoriumReactor ? 0 : -1;
    }

    private Tile closestCore(Tile tile) {
        if (state.teams.get(tile.getTeam()).cores.size <= 0) return null;
        return Geometry.findClosest(tile.x, tile.y, state.teams.get(tile.getTeam()).cores);
    }

    private float dstToClosestCore(Tile tile) {
        if (state.teams.get(tile.getTeam()).cores.size <= 0) return -1f;
        Tile core = Geometry.findClosest(tile.x, tile.y, state.teams.get(tile.getTeam()).cores);
        return tile.dst(core) - (core.block().size * tilesize / 2f + 1);
    }

    private String smootherFloatLog(float f, int to) {
        return smootherFloatLog(f, to, " ");
    }

    private String smootherFloatLog(float f, int to, String fill) {
        StringBuilder sb = new StringBuilder();
        int sf = String.valueOf(f).length();
        if (sf > to) return String.valueOf(f).substring(0, to);
        for (int j = to - sf; j > 0; j--) sb.append(fill);
        return sb.append(f).toString();
    }

    private String smootherIntLog(int i, int to) {
        return smootherIntLog(i, to, " ");
    }

    private String smootherIntLog(int i, int to, String fill) {
        StringBuilder sb = new StringBuilder();
        for (int j = String.valueOf(to).length() - String.valueOf(i).length(); j > 0; j--)
            sb.append(fill);
        return sb.append(i).toString();
    }

    private String smootherTilePosLog(short x, short y) {
        return "[" + smootherIntLog(x, world.width()) + ", " + smootherIntLog(y, world.height()) + "]";
    }

    private <T> T getSetting(String name, Class<T> type, Supplier<T> def){
        return Core.settings.getObject(name, type, def);
    }
    private void setSetting(String name, Object value, Class<?> type){
        Core.settings.putObject(name, value, type);
    }

    private boolean nukealert() {
        return Core.settings.getBool("nukealert", true);
    }

    private void nukealert(boolean b) {
        Core.settings.put("nukealert", b);
        Core.settings.save();
    }

    private int nukealertinterval() {
        return Core.settings.getInt("nukealertinterval", (int) minInterval);
    }

    private void nukealertinterval(int i) {
        i = i < minInterval ? minInterval : i > maxInterval ? maxInterval : i;
        Core.settings.put("nukealertinterval", i);
        Core.settings.save();
    }

    private int nukealertlevel() {
        return Core.settings.getInt("nukealertlevel", 2);
    }

    private void nukealertlevel(int i) {
        Core.settings.put("nukealertlevel", i);
        Core.settings.save();
    }

    private int nukeprotection() {
        return Core.settings.getInt("nukeprotection", 1);
    }

    private void nukeprotection(int i) {
        Core.settings.put("nukeprotection", i);
        Core.settings.save();
    }

    private boolean nukelogging() {
        return Core.settings.getBool("nukelogging", true);
    }

    private void nukelogging(boolean b) {
        Core.settings.put("nukelogging", b);
        Core.settings.save();
    }

    private int nukealertlimit() {
        return Core.settings.getInt("nukealertlimit", 200);
    }

    private void nukealertlimit(int i) {
        Core.settings.put("nukealertlimit", i);
        Core.settings.save();
    }

    private boolean allowadminconfig(){
        return Core.settings.getBool("allowadminconfig", true);
    }

    private void allowadminconfig(boolean b){
        Core.settings.put("allowadminconfig", b);
        Core.settings.save();
    }
//  Utils

    //Time
    private long time() {
        return System.currentTimeMillis();
    }

    private String getTimeZone() {
        return new GregorianCalendar().getTimeZone().getDisplayName(false, TimeZone.SHORT);
    }

    private String periodToTimeStr(long period) {
        int ms = (int) period % 1000,
                sec = (int) (period / 1000 % 60),
                min = (int) (period / (1000 * 60) % 60),
                hr = (int) (period / (1000 * 60 * 60) % 24),
                days = (int) (period / (1000 * 60 * 60 * 24));
        return "[" + (days == 0 ? "" : days + ":") + smootherIntLog(hr, 23, "0") + ":" + smootherIntLog(min, 59, "0") + ":" + smootherIntLog(sec, 59, "0") + "." + smootherIntLog(ms, 999, "0") + "]";
    }

    private String msToDate(long ms) {
        return msToString(ms, true, false, false, ":", " | ", ".");
    }

    private String msToFileName(long ms) {
        return msToString(ms, false, true, true, "-", "-", "-");
    }

    private String msToString(long ms, boolean outer, boolean date, boolean timezone, String sep1, String sep2, String sep3) {
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(ms);
        String yr = String.valueOf(cal.get(Calendar.YEAR));
        String mon = smootherIntLog(cal.get(Calendar.MONTH), 10, "0");
        String day = smootherIntLog(cal.get(Calendar.DATE), 10, "0");
        String hr = smootherIntLog(cal.get(Calendar.HOUR), 10, "0");
        String min = smootherIntLog(cal.get(Calendar.MINUTE), 10, "0");
        String sec = smootherIntLog(cal.get(Calendar.SECOND), 10, "0");
        String millis = smootherIntLog(cal.get(Calendar.MILLISECOND), 100, "0");

        return (outer ? "[" : "") + (date ? mon + sep1 + day + sep1 + yr + sep2 : "") + hr + sep1 + min + sep1 + sec + sep3 + millis + (timezone ? "(" + cal.getTimeZone().getDisplayName(false, TimeZone.SHORT) + ")" : "") + (outer ? "]" : "");
    }
//  Time

    //Classes
    private class BuiltNuke {
        Long time;
        short x, y;
        Player builder;

        private BuiltNuke(Long time, Tile tile, Player builder) {
            this.time = time;
            this.x = tile.x;
            this.y = tile.y;
            this.builder = builder;
        }
    }

    private class DestroyedNuke {
        Long time;
        short x, y;
        ConditionWhenDestroyed cwd;

        private DestroyedNuke(Long time, Tile tile, ConditionWhenDestroyed cwd) {
            this.time = time;
            this.x = tile.x;
            this.y = tile.y;
            this.cwd = cwd;
        }
    }

    private class ConditionWhenDestroyed {
        float healthf;
        float heat;
        int items;

        private ConditionWhenDestroyed(float healthf, float heat, int items) {
            this.healthf = healthf;
            this.heat = heat;
            this.items = items;
        }

        private String toString(boolean colorized) {
            //D.I.E.  Deconstructed, has Items, Exploded. :D

            //L.M.A.O Long & Messy, Actual Outrage.
            String result = "";
            result += deconstructed() || hasItem() || overheat() ? "[" : " ";
            result += deconstructed() ? (colorized ? "[[accent]" : "") + "D[]" : " ";
            result += hasItem() ? (colorized ? "[#f9a3c7]" : "") + "I[]" : " ";
            result += overheat() ? (colorized ? "[RED]" : "") + "E[]" : " ";
            result += deconstructed() || hasItem() || overheat() ? "]" : " ";

            result += "Healthf: " + smootherFloatLog(healthf, 5) + ", Heat: " + smootherFloatLog(heat, 5) + ", Items: " + smootherIntLog(items, 2);
            return result;
        }

        private boolean deconstructed() {
            return healthf == heat && heat == items && items == -1;
        }

        private boolean hasItem() {
            return items > 0;
        }

        private boolean overheat() {
            return heat >= 0.999f;
        }
    }

    private class NukesBuilt {
        private int nukes, lethalNukes;

        private NukesBuilt() {
            nukes = lethalNukes = 0;
        }

        private void add(boolean lethal) {
            if (lethal)
                nukes++;
            lethalNukes++;
        }

        private String toString(Player player) {
            return "Nuke: [" + nukes + ", " + lethalNukes + "], " + (player.con == null ? "[Anonymous]" : "ID: [" + player.id + "], Player: [ " + player.name + " ], UUID: [" + player.uuid + "], IP: [" + player.con.address + "]");
        }
    }
}