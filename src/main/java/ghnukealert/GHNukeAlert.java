package ghnukealert;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.math.geom.Geometry;
import io.anuke.arc.util.*;
import io.anuke.arc.util.io.FastDeflaterOutputStream;
import io.anuke.mindustry.content.*;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.gen.*;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.io.SaveVersion;
import io.anuke.mindustry.plugin.Plugin;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.power.NuclearReactor;
import io.anuke.mindustry.world.blocks.storage.CoreBlock;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static io.anuke.mindustry.Vars.*;

public class GHNukeAlert extends Plugin{

    /*  List of Settings Variable Used
    * nukealert
    * nukealertlevel
    * nukeprotection
    * nukelogging
    * nukesaveindex
    *
    */

    //TODO configurable core nuke protection

    private static FileHandle ghnaDirectory;
    private Array<Player> adminChat = new Array<>();
    private ObjectMap<Tile, Player> prebuiltNukes = new ObjectMap<>();
    private LongMap<ObjectMap<Tile, Player>> builtNukes = new LongMap<>();
    private LongMap<ObjectMap<Tile, ConditionWhenDestroyed>> destroyedNukes = new LongMap<>();

    private String params = "[mode|bn|dn|rtrs|al|ac|help] [true|false]";
    private String clientdescription =
            "\nCore Nuker Detector From GHNA. By GH\n" +
                    "mode = Display current mode\n" +
                    "bn = List the builtNukes\n" +
                    "dn = List the destroyedNukes\n" +
                    "rtrs = List all the thorium reactors and their distance to the nearest ally core on the map\n" +
                    "al = Display current nuke announce level\n" +
                    "cnp = Display current Core Nuke Protection Status\n" +
                    "help = Display this message again\n" +
                    "(Admins Only)";
    private String description =
            "\nCore Nuker Detector From GHNA. By GH\n" +
                    "mode = Display current mode\n" +
                    "mode <true|false> = Change mode to\n" +
                    "bn = List the builtNukes\n" +
                    "dn = List the destroyedNukes\n" +
                    "rtrs = List all the thorium reactors and their distance to the nearest ally core on the map\n" +
                    "al = Display current nuke announce level\n" +
                    "al <0|1|2> = Change who can hear the nuke alert (0 = No one, 1 = Admins only, 2 = Everyone)\n" +
                    "cnp = Display current Core Nuke Protection Status\n" +
                    "cnp <0|1>= Change Core Nuke Protection Status\n" +
                    "help = Display this message again";

    public GHNukeAlert(){
        Events.on(WorldLoadEvent.class, e -> worldLoadEvent());
        Events.on(BuildSelectEvent.class, this::buildSelectEvent);
        Events.on(BlockBuildEndEvent.class, this::blockBuildEndEvent);
        Events.on(BlockDestroyEvent.class, this::blockDestroyEvent);
        //Events.on(PlayerJoin.class, this::playerJoin());
        //Events.on(PlayerLeave.class, this::playerLeave());
        ghnaDirectory = dataDirectory.child("ghplugins/ghna/");
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("ghna", params, description, arg -> {
            if(arg.length == 0)
                info(plguinStatus());
            else
                switch (arg[0]) {
                    case "bn": info(builtNukesToStringArr(true)); break;
                    case "dn": info(destroyedNukesToStringArr(true)); break;
                    case "rtrs": info(reactorsToStringArr()); break;

                    case "help": info(description); break;
                    case "ac": adminChatSendMessage(arg, null); break;

                    case "al":
                        if(arg.length == 1) info("Alert Level is currently [" + nukealertlevel() + "]");
                        else {
                            int level = GHUtil.parseInt(arg[1]);
                            if (level < 0 || level > 2) {
                                info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.");
                                return;
                            }
                            nukealertlevel(level);
                            info("Alert Level is set to [" + nukealertlevel() + "]");
                        }
                        break;

                    case "cnp":
                        if(arg.length == 1) info("Core Nuke Protection is currently [" + nukeprotection() + "]");
                        else {
                            int level = GHUtil.parseInt(arg[1]);
                            if (level < 0 || level > 2) {
                                info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format.");
                                return;
                            }
                            nukeprotection(level);
                            info("Core Nuke Protection is set to [" + nukeprotection() + "]");
                        }
                        break;

                    case "mode":
                        if(arg.length == 1) info("Alert is currently " + GHUtil.onOffString(nukealert()));
                        else switch (arg[1]) {
                                case "true": nukealert(true); break;
                                case "false": nukealert(false); break;
                                default: info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format."); return;
                            }
                        info("Alert is set to " + GHUtil.onOffString(nukealert()));
                        break;

                        //TODO
                    case "logging":
                        if(arg.length == 1) info("Logging is currently " + GHUtil.onOffString(nukelogging()));
                        else switch (arg[1]) {
                            case "true": nukelogging(true); break;
                            case "false": nukelogging(false); break;
                            default: info("[ERROR] arg[1](" + arg[1] + ") Does Not Match the Format."); return;
                        }
                        info("Logging is set to " + GHUtil.onOffString(nukelogging()));
                        break;

                    default: info("You need some help? Do 'ghna help'"); break;
                }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler){

        handler.<Player>register("ghna",params, clientdescription, (arg, player) -> {
            if(!player.isAdmin) {
                print("[scarlet][ACCESS DENIED] This is an admin only command", player);
                return;
            }
            if(arg.length == 0)
                print(plguinStatus(), player);
            else
                switch (arg[0]) {
                    case "bn":
                        print(builtNukesToStringArr(false), player);
                        return;

                    case "dn":
                        print(destroyedNukesToStringArr(false), player);
                        return;

                    case "rtrs":
                        print(reactorsToStringArr(), player);
                        return;

                    case "al":
                        print("Alert Level is currently [" + nukealertlevel() + "]", player);
                        return;

                    case "cnp":
                        print("Core Nuke Protection is currently [" + nukealertlevel() + "]", player);
                        return;

                    case "help":
                        print(clientdescription, player);
                        break;

                    default:
                        print("You need some [yellow]help[white]?", player);
                        break;
                }
        });

        handler.<Player>register("votetodisablethatcoolandusefulcorenukeprotectionforoneminutebecauseiamstupidandundeterminedthaticanneverrecoverfromthateasilyfixablesituation",
                "[1|0]", "Vote for Core Nuke Protection to Disable for 1 minute", (args, player) -> {
            if(args.length == 0) {
                if(session.active + voteDuration < System.currentTimeMillis()) {
                    Call.sendMessage(Strings.format("{0}[lightgray] has started a vote to Disable Core Nuke Protection for 1 minute \n[lightgray]Type[orange]'/votetodisablethatcoolandusefulcorenukeprotectionforoneminutebecauseiamstupidandundeterminedthaticanneverrecoverfromthateasilyfixablesituation 1' to agree."));
                    session = new VoteSession();
                }else
                    print("[scarlet][ERROR] There is a vote going on right now, 1 vote at a time.", player);
            }else if(args.length == 1){
                if(session.active + voteDuration < System.currentTimeMillis()){
                    print("[scarlet][ERROR] There is no vote going on right now.", player);
                    return;
                }
                if(args[0].equals("1") || args[0].equals("0")) {
                    Call.sendMessage(Strings.format("{0}[lightgray] has voted to Disable Core Nuke Protection for 1 minute [accent] ({1}/{2})\n[lightgray]Type[orange]'/votetodisablethatcoolandusefulcorenukeprotectionforoneminutebecauseiamstupidandundeterminedthaticanneverrecoverfromthateasilyfixablesituation 1' to agree.",
                            GHUtil.fullPlayerName(player), session.votes, session.votesRequired()));
                    session.vote(player, args[0].equals("1"));
                    session.checkPass();
                    vtime.reset();
                }else{
                    print("[scarlet][ERROR] Invalid 1st Argument. Please Try Again.", player);
                }
            }else{
                print("[scarlet][ERROR] There're too many Arguments. Only 1 is needed. Please Try Again.", player);
            }
        });

        handler.<Player>register("ac [msg|command]", "Admin Chat. 'join|leave' 'kick'(admin only)", (arg, player) -> {
            if(player.isAdmin)
                adminChatSendMessage(arg, player);
            else
                if(arg.length >= 2)
                    switch(arg[1]){
                        case "join":
                            if (!adminChat.contains(player)) {
                                adminChat.add(player);
                                adminChatSendMessage(GHUtil.fullPlayerName(player) + "[accent]has joined the Admin Chat.");
                            } else print("[ERROR] You are already in the Admin Chat.", player);
                            break;

                        case "leave":
                            if (adminChat.remove(player)) adminChatSendMessage(GHUtil.fullPlayerName(player) + "[accent]has left the Admin Chat.");
                            else print("[ERROR] You are not in the Admin Chat.", player);
                            break;
                        default:
                            if (adminChat.contains(player)) adminChatSendMessage(arg, player);
                            else print("[ERROR] You are not in the Admin Chat.", player);
                            break;
                }
        });
    }

    private void reset(){
        prebuiltNukes.clear();
        builtNukes.clear();
        destroyedNukes.clear();
    }

    private void worldLoadEvent(){
        //if() saveNukeInfos();
        reset();
    }
    private void buildSelectEvent(BuildSelectEvent e){
        if(!nukealert()) return;
        if(!(e.builder != null && e.builder.buildRequest() != null && e.builder instanceof Player && e.builder.buildRequest().block == Blocks.thoriumReactor)) return;
        Player player = (Player)e.builder;
        if(!e.breaking)
            prebuiltNukes.put(e.tile, player);
    }
    private void blockBuildEndEvent(BlockBuildEndEvent e){
        if(!nukealert()) return;
        if(e.tile.block() == Blocks.thoriumReactor && !e.breaking) {
            for (Tile prebuiltNuke : prebuiltNukes.keys()) {
                Player builder = null;
                if (prebuiltNuke == e.tile)
                    builder = prebuiltNukes.get(prebuiltNuke);
                builtNukes.put(time(), ObjectMap.of(e.tile, builder));
            }
        }else if(e.tile.block() == Blocks.air && e.breaking){
            outer:
            for(ObjectMap<Tile, Player> map : builtNukes.values())
                for(Tile builtNuke : map.keys())
                    if(builtNuke == e.tile) {
                        destroyedNukes.put(time(), ObjectMap.of(e.tile, new ConditionWhenDestroyed(-1f, -1f, -1)));
                        break outer;
                    }
        }
        prebuiltNukes.remove(e.tile);
    }
    private void blockDestroyEvent(BlockDestroyEvent e){
        if(!nukealert()) return;
        if(e.tile.block() == Blocks.thoriumReactor) {
            NuclearReactor.NuclearReactorEntity entity = (NuclearReactor.NuclearReactorEntity) e.tile.entity;
            destroyedNukes.put(time(), ObjectMap.of(e.tile, new ConditionWhenDestroyed(entity.healthf(), entity.heat, entity.items.total())));

            if(nukeprotection() == 0) return;
            Tile core = Geometry.findClosest(e.tile.x, e.tile.y, state.teams.get(e.tile.getTeam()).cores);
            int dist = (int)e.tile.dst(core)/tilesize - (core.block().size/2 + 1);
            if(dist <= 9) {
                entity.items.clear();
                entity.heat = 0f;
            }
        }else if(e.tile.block().name.equals("build3")){
            prebuiltNukes.remove(e.tile);
        }
    }
    /*private void playerJoin(PlayerJoin e){
        if(e.player.isAdmin)
            adminChat.add(e.player);
    }
    private void playerLeave(PlayerJoin e){
        adminChat.remove(e.player);
    }*/

    private String plguinStatus(){
        return Log.format("\n" +
                        "Nuke Alert Status: {0}\n" +
                        "Alert Level: {1}\n" +
                        "Nuke Protection: {2}\n" +
                        "Do Logging: {3}",
                //"Log Visibility: {4}",
                nukealert() + "",
                nukealertlevel() + "",
                nukeprotection() + "",
                nukelogging() + "");//,
        //nukelogvisibility() + "");
    }

//Save
    public static void saveToSlot(int slot){
        FileHandle file = fileFor(slot);
        boolean exists = file.exists();
        if(exists) file.moveTo(backupFileFor(file));
        try{
            write(fileFor(slot));
        }catch(Exception e){
            if(exists) backupFileFor(file).moveTo(file);
            throw new RuntimeException(e);
        }
    }

    private static FileHandle fileFor(int slot){
        return ghnaDirectory.child(slot + "." + saveExtension);
    }

    private static FileHandle backupFileFor(FileHandle file){
        return file.sibling(file.name() + "-backup." + file.extension());
    }

    private static void write(FileHandle file, StringMap tags){
        write(new FastDeflaterOutputStream(file.write(false, bufferSize)), tags);
    }

    private static void write(FileHandle file){
        write(file, null);
    }

    private static void write(OutputStream os, StringMap tags){
        try(DataOutputStream stream = new DataOutputStream(os)){
            stream.write(SaveIO.header);
            stream.writeInt(getVersion().version);
            if(tags == null){
                getVersion().write(stream);
            }else{
                getVersion().write(stream, tags);
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private static SaveVersion getVersion(){
        return SaveIO.versionArray.peek();
    }
//  Save

    private void adminChatSendMessage(String[] dirty, Player player) {
        Array<String> arr = new Array<>(dirty);
        arr.remove(0);
        StringBuilder msg = new StringBuilder();
        msg.append("[GOLD][AC][").append(player == null ? "[SCARLET]Server[GOLD]" : GHUtil.fullPlayerName(player, Color.gold)).append("]: ");
        for(int i = 0; i < arr.size; i++){
            msg.append(arr.get(i));
            if(i + 1 < arr.size)
                msg.append(" ");
        }
        adminChatSendMessage(msg.toString());
    }
    private void adminChatSendMessage(String msg){
        for(Player player : playerGroup.all())
            if (player.isAdmin)
                adminChat.add(player);
        for(Player player : adminChat)
            player.sendMessage(msg);
        info(msg);
    }

    private Array<String> builtNukesToStringArr(boolean server){
        Array<String> result = new Array<>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for(long key : builtNukes.keys().toArray().items) {
            sb.append("[").append(i).append(": ").append(msToDate(key)).append(": ");
            for (Tile tile : builtNukes.get(key).keys()) {
                sb.append("[").append(tile.x).append(", ").append(tile.y).append("]: ");
                if(server) sb.append("<").append(GHUtil.fullPlayerName(builtNukes.get(key).get(tile))).append(">");
                else sb.append(GHUtil.fullPlayerName(builtNukes.get(key).get(tile), Color.white));
            }
            sb.append("]");
            result.add(sb.toString());
            sb.delete(0, sb.length());
            i++;
        }
        return result;
    }
    private Array<String> destroyedNukesToStringArr(boolean server){
        Array<String> result = new Array<>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for(long key : destroyedNukes.keys().toArray().items){
            sb.append("[").append(i).append(": ").append(msToDate(key)).append(": ");
            for (Tile tile : destroyedNukes.get(key).keys()) {
                sb.append("[").append(tile.x).append(", ").append(tile.y).append("]: ");
                if(server) sb.append(destroyedNukes.get(key).get(tile).toString());
                else sb.append("[#").append(destroyedNukes.get(key).get(tile).overheat() ? Color.scarlet : Color.green).append("]").append(destroyedNukes.get(key).get(tile).toString()).append("[WHITE]");
            }
            sb.append("]");
            result.add(sb.toString());
            sb.delete(0, sb.length());
            i++;
        }
        return result;
    }
    private Array<String> reactorsToStringArr(){
        ObjectMap<Tile, Team> cores = new ObjectMap<>(), reactors = new ObjectMap<>();
        Array<String> result = new Array<>();
        for (int x = 0; x < world.width(); x++)
            for (int y = 0; y < world.height(); y++) {
                if (world.tile(x, y).block() instanceof CoreBlock) {
                    cores.put(world.tile(x, y), world.tile(x, y).getTeam());
                    continue;
                }
                if (world.tile(x, y).block() == Blocks.thoriumReactor)
                    reactors.put(world.tile(x, y), world.tile(x, y).getTeam());
            }

        for (Tile reactor : reactors.keys()) {
            float min = Float.MAX_VALUE;
            Tile closestCore = null;
            for (Tile core : cores.keys()) {
                if(reactor.getTeam() != core.getTeam() || reactor.dst(core) > min) continue;
                min = reactor.dst(core);
                closestCore = core;
            }

            if(min == Float.MAX_VALUE) continue;
            result.add("Team [" + reactor.getTeam() + "]: Reactor at [" + reactor.x + ", " + reactor.y + "] is Closest to the Core [" + closestCore.x + ", " + closestCore.y + "] " +
                    "with [" + min + "] units distance.");
        }
        return result;
    }

//Utils

    private void print(String str, Player player){
        player.sendMessage(str);
    }
    private void print(Array<String> arr, Player player){
        StringBuilder msg = new StringBuilder();
        msg.append("\n");
        boolean sent = false;
        for(int i = 0, j = 0; i < arr.size; i++, j++) {
            if (msg.length() + arr.get(i).length() >= 1024 || j > 8) {
                print(msg.toString(), player);
                msg.delete(0, msg.length()-1);
                j = 0;
                sent = true;
            }
            msg.append(arr.get(i));
            if(i + 1 < arr.size) {
                msg.append(",");
                if(!sent)
                    msg.append("\n");
            }
        }
        print(msg.toString() + "\nTotal: " + arr.size, player);
    }

    private void info(String str){
        Log.info(str);
    }
    private void info(Array<String> arr){
        StringBuilder msg = new StringBuilder();
        msg.append("\n");
        boolean sent = false;
        for(int i = 0, j = 0; i < arr.size; i++, j++) {
            if (msg.length() + arr.get(i).length() >= 1024 || j > 8) {
                info(msg.toString());
                msg.delete(0, msg.length()-1);
                j = 0;
                sent = true;
            }
            msg.append(arr.get(i));
            if(i + 1 < arr.size) {
                msg.append(",");
                if(!sent)
                    msg.append("\n");
            }
        }
        info(msg.toString() + "\nTotal: " + arr.size);
    }

    private long time(){
        return System.currentTimeMillis();
    }
    private String msToDate(long ms){
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(ms);
        String yr = "" + cal.get(Calendar.YEAR);
        String mon = cal.get(Calendar.MONTH) < 10 ? "0" + cal.get(Calendar.MONTH) : "" + cal.get(Calendar.MONTH);
        String day = cal.get(Calendar.DATE) < 10 ? "0" + cal.get(Calendar.DATE) : "" + cal.get(Calendar.DATE);
        String hr = cal.get(Calendar.HOUR) < 10 ? "0" + cal.get(Calendar.HOUR) : "" + cal.get(Calendar.HOUR);
        String min = cal.get(Calendar.MINUTE) < 10 ? "0" + cal.get(Calendar.MINUTE) : "" + cal.get(Calendar.MINUTE);
        String sec = cal.get(Calendar.SECOND) < 10 ? "0" + cal.get(Calendar.SECOND) : "" + cal.get(Calendar.SECOND);
        String millis = cal.get(Calendar.MILLISECOND) < 100 ? cal.get(Calendar.MILLISECOND) < 10 ? "00" + cal.get(Calendar.MILLISECOND) : "0" + cal.get(Calendar.MILLISECOND) : "" + cal.get(Calendar.MILLISECOND);

        return "[" + mon + ":" + day + ":" + yr + " | " + hr + ":" + min + ":" + sec + "." + millis + " (" + cal.getTimeZone().getDisplayName(false, TimeZone.SHORT) + ")" + "]";
    }

    private boolean nukealert(){
        return Core.settings.getBool("nukealert", true);
    }
    private void nukealert(boolean b){
        Core.settings.put("nukealert", b);
        Core.settings.save();
    }
    private int nukealertlevel(){
        return Core.settings.getInt("nukealertlevel", 2);
    }
    private void nukealertlevel(int i){
        Core.settings.put("nukealertlevel", i);
        Core.settings.save();
    }
    private int nukeprotection(){
        return Core.settings.getInt("nukeprotection", 1);
    }
    private void nukeprotection(int i){
        Core.settings.put("nukeprotection", i);
        Core.settings.save();
    }
    private boolean nukelogging(){
        return Core.settings.getBool("nukelogging", true);
    }
    private void nukelogging(boolean b){
        Core.settings.put("nukelogging", b);
        Core.settings.save();
    }
    private int nukesaveindex(){
        return Core.settings.getInt("nukesaveindex", 0);
    }
    private void nukesaveindex(int i){
        Core.settings.put("nukesaveindex", i);
        Core.settings.save();
    }
//  Utils

//Vote
    private int voteTime = 60 * 10;
    private Timekeeper vtime = new Timekeeper(voteTime);
    private int voteDuration = 2 * 60 * 1000;
    //current kick sessions
    private VoteSession session = new VoteSession(-1);

    class VoteSession{
        long active;
        ObjectSet<String> voted = new ObjectSet<>();
        Timer.Task task;
        int yes = 0, no = 0, adminYes = 0, adminNo = 0;
        int votes;

        private VoteSession(){
            this(time());
        }

        private VoteSession(long time){
            this.active = time;
            this.task = Timer.schedule(() -> {
                if(!checkPass())
                    Call.sendMessage("[lightgray]Vote Failed. Not Enough Votes to Disable Core Nuke Protection.");
                task.cancel();
            }, voteDuration);
        }

        boolean checkPass(){
            if(votes >= votesRequired() && votePassed()){
                int current = nukeprotection();
                nukeprotection(0);
                Call.sendMessage("[green]Vote Passed. Nuke Protection is Disabled for 1 minute.");
                Timer.schedule(() -> {
                            nukeprotection(current);
                            Call.sendMessage("[grey]Nuke Protection is On again.");
                        }, 60f);
                task.cancel();
                return true;
            }
            return false;
        }

        public void vote(Player player, boolean yes){
            session.votes++;
            //session.voted.addAll(player.uuid, netServer.admins.getInfo(player.uuid).lastIP);
            if(player.isAdmin)
                if(yes) session.adminYes++; else session.adminNo++;
            else
                if(yes) session.yes++; else session.no++;
        }

        public int votesRequired(){
            return 5;
        }

        public boolean votePassed(){
            return yes + adminYes * 3 > no * 2 + adminNo * 4;
        }
    }
//  Vote

    private class ConditionWhenDestroyed{
        float health;
        float heat;
        int items;

        private ConditionWhenDestroyed(float health, float heat, int items) {
            this.health = health;
            this.heat = heat;
            this.items = items;
        }

        public String toString(){
            if(health == heat && heat == items && items == -1)
                return "Deconstructed";
            return "Health: " + health + ", Heat: " + heat + ", Items: " + items;
        }

        private boolean overheat(){
            return heat >= 0.999f;
        }
    }
}