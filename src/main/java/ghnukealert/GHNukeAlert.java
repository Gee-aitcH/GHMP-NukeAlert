package ghnukealert;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.files.FileHandle;
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
import io.anuke.mindustry.world.blocks.power.NuclearReactor;
import io.anuke.mindustry.world.blocks.storage.CoreBlock;

import java.io.*;
import java.util.*;

import static io.anuke.mindustry.Vars.*;

public class GHNukeAlert extends Plugin{

    /*  List of Settings Variable Used
    * nukealert
    * nukealertinterval
    * nukealertlevel
    * nukeprotection
    * nukelogging
    *
    */

    private static String LN = System.getProperty("line.separator");
    private static FileHandle ghnaDirectory;
    private ObjectMap<Tile, Player> prebuiltNukes = new ObjectMap<>();
    private Array<BuiltNuke> builtNukes = new Array<>();
    private Array<DestroyedNuke> destroyedNukes = new Array<>();
    private ObjectMap<Player, NukesBuilt> nukeBuilders = new ObjectMap<>();
    private long startTime, endTime, minInterval = 1000, maxInterval = 60000;
    private long[] lastAlert = new long[2];
    private float coreProtectionRange = 12 * tilesize;

    private String params = "[mode|bn|dn|rtrs|al|ac|help] [true|false]";
    private String clientdescription = LN +
            "Nuke Alert Made By GH" + LN +
            "bn - List the Nukes that was once Built on the map (could be a long list)" + LN +
            "dn - List the Nukes that was Destoryed (could be a long list)" + LN +
            "rtrs - List all the thorium reactors and their distance to the nearest ally core on the map" + LN +

            "mode - Display current mode" + LN +
            "al - Display current nuke announce level" + LN +
            "cnp - Display current Core Nuke Protection Status" + LN +
            "logging - Display current Logging mode" + LN +

            "help - Display this message again" + LN +
            "(Admins Only)";

    private String description = LN +
            "Nuke Alert Made By GH" + LN +
            "bn - List the Nukes that was once Built on the map (could be a long list)" + LN +
            "dn - List the Nukes that was Destoryed (could be a long list)" + LN +
            "rtrs - List all the thorium reactors and their distance to the nearest ally core on the map" + LN +

            "mode - Display current mode" + LN +
            "mode <true|false> - Change mode to" + LN +
            "al - Display current nuke announce level" + LN +
            "al <0|1|2> - Change who can hear the nuke alert (0 = No one, 1 = Admins only, 2 = Everyone)" + LN +
            "cnp - Display current Core Nuke Protection Status" + LN +
            "cnp <0|1|2> - Change Core Nuke Protection Status (0 = No Protection, 1 = Near Core Only, 2 = Everywhere on the map)" + LN +
            "logging - Display current Logging mode" + LN +
            "logging <true|false> - Change to Log or not to log the logs of nukes related to that game" + LN +

            "help - Display this message again";

    public GHNukeAlert(){
        Events.on(WorldLoadEvent.class, e -> worldLoadEvent());
        Events.on(BuildSelectEvent.class, this::buildSelectEvent);
        Events.on(BlockBuildEndEvent.class, this::blockBuildEndEvent);
        Events.on(BlockDestroyEvent.class, this::blockDestroyEvent);
        //Events.on(PlayerJoin.class, this::playerJoin());
        //Events.on(PlayerLeave.class, this::playerLeave());
        ghnaDirectory = dataDirectory.child("ghplugins/");
        ghnaDirectory = ghnaDirectory.child("ghna/");
        ghnaDirectory.mkdirs();
        startTime = endTime = lastAlert[0] = lastAlert[1] = -1;
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

                    case "interval":
                        if(arg.length == 1) info("Alert Interval is currently [" + nukealertinterval() + "]");
                        else {
                            long level = GHUtil.parseInt(arg[1]);
                            if (level < minInterval) info("[WARNING] arg[1](" + arg[1] + ") Is Below the Minimal Interval.");
                            else if(level > maxInterval) info("[WARNING] arg[1](" + arg[1] + ") Is Above the Maximum Interval.");
                            level = level < minInterval ? minInterval : level > maxInterval ? maxInterval : level;
                            nukealertinterval(level);
                            info("Alert Interval is set to [" + nukealertinterval() + "]");
                        }
                        break;

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

        handler.<Player>register("ghna", params, clientdescription, (arg, player) -> {
            if(!player.isAdmin) {
                print("[scarlet][ACCESS DENIED] This is an admin only command.", player);
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

                    case "help":
                        print(clientdescription, player);
                        break;

                    default:
                        print("You need some [yellow]help[white]?", player);
                        break;
                }
        });
    }

    private void reset(){
        prebuiltNukes.clear();
        builtNukes.clear();
        destroyedNukes.clear();
        startTime = time();
    }

    private String plguinStatus(){
        return Log.format(LN +
                "Nuke Alert Status: {0}" + LN +
                "Alert Interval: {1}" + LN +
                "Alert Level: {2}" + LN +
                "Nuke Protection: {3}" + LN +
                "Do Logging: {4}",
                //"Log Visibility: {5}",
                nukealert() + "",
                nukealertinterval() + "",
                nukealertlevel() + "",
                nukeprotection() + "",
                nukelogging() + "");//,
        //nukelogvisibility() + "");
    }

    private void alert(Tile tile, Block block, Player builder, boolean building){
        //System.out.println("[Debug]: " + GHUtil.tileToSimpleString(tile));
        if(world.tile(tile.x, tile.y) == null || world.tile(tile.x, tile.y).block() != block)
            //System.out.println("[Alert Ended]: " + GHUtil.tileToSimpleString(tile));
            return;
        if(lastAlert[building ? 0 : 1] + nukealertinterval() < time()){
            printToEveryone("[Alert]: A NUKE IS " + (building ? "BEING " : "") + "BUILT AT " + smootherTilePosLog(tile.x, tile.y) + " BY PLAYER: [ " + GHUtil.fullPlayerName(builder) + " ]!", "[RED]");
            lastAlert[building ? 0 : 1] = time();
        }
        Timer.schedule(()-> alert(tile, block, builder, building), 0.1f);
    }

    private void worldLoadEvent(){
        if(nukealert() && nukelogging()) saveNukeInfos();
        reset();
    }
    private void buildSelectEvent(BuildSelectEvent e){
        if(!nukealert()) return;
        if(!(e.builder != null && e.builder.buildRequest() != null && e.builder instanceof Player && e.builder.buildRequest().block == Blocks.thoriumReactor)) return;
        Player player = (Player)e.builder;
        if(!e.breaking) {
            prebuiltNukes.put(e.tile, player);
            if(dstToClosestCore(e.tile) <= coreProtectionRange)
                alert(e.tile, e.tile.block(), player, true);
        }
    }
    private void blockBuildEndEvent(BlockBuildEndEvent e){
        if(!nukealert()) return;
        if(e.tile.block() == Blocks.thoriumReactor && !e.breaking) {
            for (Tile prebuiltNuke : prebuiltNukes.keys()) {
                Player builder = null;
                if (prebuiltNuke == e.tile)
                    builder = prebuiltNukes.get(prebuiltNuke);
                builtNukes.add(new BuiltNuke(time(), e.tile, builder));
                if(builder == null) continue;
                if(!nukeBuilders.containsKey(builder))
                    nukeBuilders.put(builder, new NukesBuilt());
                nukeBuilders.get(builder).add(dstToClosestCore(e.tile) <= coreProtectionRange);
                if(dstToClosestCore(e.tile) <= coreProtectionRange)
                    alert(e.tile, e.tile.block(), builder,false);
            }
        }else if(e.tile.block() == Blocks.air && e.breaking){
            for(BuiltNuke nuke : builtNukes)
                if(nuke.x == e.tile.x && nuke.y == e.tile.y) {
                    destroyedNukes.add(new DestroyedNuke(time(), e.tile, new ConditionWhenDestroyed(-1f, -1f, -1)));
                    break;
                }
        }
        prebuiltNukes.remove(e.tile);
    }
    private void blockDestroyEvent(BlockDestroyEvent e){
        if(!nukealert()) return;
        if(e.tile.block() == Blocks.thoriumReactor) {
            NuclearReactor.NuclearReactorEntity entity = (NuclearReactor.NuclearReactorEntity) e.tile.entity;
            destroyedNukes.add(new DestroyedNuke(time(), e.tile, new ConditionWhenDestroyed(entity.healthf(), entity.heat, entity.items.total())));
            switch(nukeprotection()) {
                case 1:
                    if (dstToClosestCore(e.tile) > coreProtectionRange) return;
                    entity.items.clear();
                    entity.heat = 0f;
                    break;
                case 2:
                    entity.items.clear();
                    entity.heat = 0f;
                    break;
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

    private Array<String> builtNukesToStringArr(boolean server){
        Array<String> result = new Array<>();
        StringBuilder sb = new StringBuilder();
        BuiltNuke nuke;
        for(int i = 0; i < builtNukes.size; i++){
            sb.delete(0, sb.length());
            nuke = builtNukes.get(i);
            sb.append("[").append(smootherIntLog(i, builtNukes.size, "0")).append("]: ");
            sb.append(msToDate(nuke.time)).append(": ");
            sb.append(smootherTilePosLog(nuke.x, nuke.y)).append(": ");
            if(server) sb.append("[").append(nuke.builder.id).append("]");
            else sb.append(GHUtil.fullPlayerName(nuke.builder));
            result.add(sb.toString());
        }
        return result;
    }
    private Array<String> destroyedNukesToStringArr(boolean server){
        Array<String> result = new Array<>();
        StringBuilder sb = new StringBuilder();
        DestroyedNuke nuke;
        for(int i = 0; i < destroyedNukes.size; i++){
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
    private Array<String> nukeBuildersToStringArr(){
        Array<String> result = new Array<>();
        for(Player player : nukeBuilders.keys())
            result.add("[" + smootherIntLog(result.size, nukeBuilders.size, "0") + ": " + nukeBuilders.get(player).toString(player));
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
            Tile closestCore = closestCore(reactor);
            if(closestCore == null) continue;
            result.add("Team: [" + reactor.getTeam() + "]: Nuke: " + smootherTilePosLog(reactor.x, reactor.y) + ", Core: " + smootherTilePosLog(closestCore.x, closestCore.y) + ", " +
                    "Dst: [" + smootherFloatLog(reactor.dst(closestCore), 5) + "]");
        }
        return result;
    }
    private Tile closestCore(Tile tile){
        if(state.teams.get(tile.getTeam()).cores.size <= 0) return null;
        return Geometry.findClosest(tile.x, tile.y, state.teams.get(tile.getTeam()).cores);
    }
    private float dstToClosestCore(Tile tile){
        if (state.teams.get(tile.getTeam()).cores.size <= 0) return -1f;
        Tile core = Geometry.findClosest(tile.x, tile.y, state.teams.get(tile.getTeam()).cores);
        return tile.dst(core) - (core.block().size * tilesize / 2f + 1);
    }

//Save
    private void saveNukeInfos(){
        if(startTime == endTime) return;
        System.out.println(Core.settings.getDataDirectory());
        endTime = time();
        File file = new File(ghnaDirectory.path() + "\\" + msToFileName(time()) + ".txt");
        System.out.println(file.getAbsolutePath());

        //create new txt file or sth
        try(BufferedWriter br = new BufferedWriter(new FileWriter(file))){
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
            br.write(nukeBuildersToStringArr().toString(LN) + " }");

            Log.info("[GHNA]: New Log is Created [" + file.getAbsolutePath() + "]");
        }catch (IOException e){
            e.printStackTrace();
        }
    }
//  Save

//Utils
    private String smootherFloatLog(float f, int to){
        return smootherFloatLog(f, to, " ");
    }
    private String smootherFloatLog(float f, int to, String fill){
        StringBuilder sb = new StringBuilder();
        int sf = String.valueOf(f).length();
        if(sf > to) return String.valueOf(f).substring(0, to);
        for(int j = to - sf; j > 0; j--) sb.append(fill);
        return sb.append(f).toString();
    }
    private String smootherIntLog(int i, int to) {
        return smootherIntLog(i, to, " ");
    }
    private String smootherIntLog(int i, int to, String fill){
        StringBuilder sb = new StringBuilder();
        for(int j = String.valueOf(to).length() - String.valueOf(i).length(); j > 0; j--)
            sb.append(fill);
        return sb.append(i).toString();
    }

    private String smootherTilePosLog(short x, short y){
        return "[" + smootherIntLog(x, world.width()) + ", " + smootherIntLog(y, world.height()) + "]";
    }

    private void printToEveryone(String str, String textcolor){
        int alertLevel = nukealertlevel();
        for(Player player : playerGroup.all())
            if(alertLevel == 2 || (alertLevel == 1 && player.isAdmin))
            player.sendMessage(textcolor + str);

        //Let's not do this, Logging Alerts into Console is Pretty Spammy.
        //Log.info(str);
    }
    private void print(String str, Player player){
        player.sendMessage(str);
    }
    private void print(Array<String> arr, Player player){
        StringBuilder msg = new StringBuilder();
        for(int i = 0, j = 0; i < arr.size; i++, j++) {
            if (msg.length() + arr.get(i).length() >= 1024 || j > 8) {
                if(player != null) print(msg.toString(), player);
                else info(msg.toString());
                msg.delete(0, msg.length()-1);
                j = 0;
            }
            msg.append(LN).append(arr.get(i));
            if(i + 1 < arr.size)
                msg.append(", ");
        }
        if(player != null) print(msg.toString() + LN + "Total: " + arr.size, player);
        else info(msg.toString() + LN + "Total: " + arr.size);
    }

    private void info(String str){
        Log.info(str);
    }
    private void info(Array<String> arr){
        print(arr, null);
    }

    private long time(){
        return System.currentTimeMillis();
    }

    private String msToDate(long ms) {
        return msToString(ms, true, false, false, ":", " | ", ".");
    }

    private String msToFileName(long ms){
        return msToString(ms, false, true, true, "-", "-", "-");
    }

    private String msToString(long ms, boolean outer, boolean date, boolean timezone, String sep1, String sep2, String sep3){
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

    private String getTimeZone(){
        return new GregorianCalendar().getTimeZone().getDisplayName(false, TimeZone.SHORT);
    }

    private boolean nukealert(){
        return Core.settings.getBool("nukealert", true);
    }
    private void nukealert(boolean b){
        Core.settings.put("nukealert", b);
        Core.settings.save();
    }
    private int nukealertinterval(){
        return Core.settings.getInt("nukealertinterval", (int)minInterval);
    }
    private void nukealertinterval(long i){
        i = i < minInterval ? minInterval : i > maxInterval ? maxInterval : i;
        Core.settings.put("nukealertinterval", i);
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
//  Utils

    private class BuiltNuke{
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
    private class DestroyedNuke{
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
    private class ConditionWhenDestroyed{
        float healthf;
        float heat;
        int items;

        private ConditionWhenDestroyed(float healthf, float heat, int items) {
            this.healthf = healthf;
            this.heat = heat;
            this.items = items;
        }

        private String toString(boolean colorized){
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

        private boolean deconstructed(){
            return healthf == heat && heat == items && items == -1;
        }
        private boolean hasItem(){
            return items > 0;
        }
        private boolean overheat(){
            return heat >= 0.999f;
        }
    }
    private class NukesBuilt{
        private int nukes, lethalNukes;

        private NukesBuilt() {
            nukes = lethalNukes = 0;
        }

        private void add(boolean lethal) {
            if(lethal)
                nukes++;
            lethalNukes++;
        }

        private String toString(Player player){
            return "Nuke: [" + nukes + ", " + lethalNukes + "], ID: [" + player.id + "], Player: [ " + player.name + " ], UUID: [" + player.uuid + "], IP: [" + player.con.address + "]";
        }
    }

//Vote


        /*handler.<Player>register("votetodisablethatcoolandusefulcorenukeprotectionforoneminutebecauseiamstupidandundeterminedthaticanneverrecoverfromthateasilyfixablesituation",
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
        });*/
    /*private int voteTime = 60 * 10;
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
    }*/
//  Vote

    /*
    private void adminChatSendMessage(String[] dirty, Player player) {
        Array<String> arr = new Array<>(dirty);
        arr.remove(0);
        StringBuilder msg = new StringBuilder();
        msg.append("[GOLD][AC][").append(player == null ? "[SCARLET]Server[GOLD]" : GHUtil.fullPlayerName(player, Color.GOLD)).append("]: ");
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

    private Array<Player> adminChat = new Array<>();

                    case "ac": adminChatSendMessage(arg, null); break;

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
    */
}