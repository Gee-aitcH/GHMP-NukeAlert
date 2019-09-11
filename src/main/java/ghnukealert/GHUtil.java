package ghnukealert;

import io.anuke.arc.collection.Array;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.BuildBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static io.anuke.mindustry.Vars.*;

public class GHUtil {

    //Get World Tiles
    public static Tile[] getTiles(){
        ArrayList<Tile> result = new ArrayList<>();
        for(Tile[] tiles : world.getTiles())
            result.addAll(Arrays.asList(tiles));
        return result.toArray(new Tile[0]);
    }

    public static Tile playerPosToTile(Player player){
        return world.tileWorld(player.x, player.y);
    }

    public static float[] vec2ToArr(Vector2 vec2){
        return new float[]{vec2.x, vec2.y};
    }

    public static String[] equalss(String str, String[] arr){
        return equalss(new String[]{str}, arr);
    }
    public static String[] equalss(String[] arr1, String[] arr2){
        ArrayList<String> result = new ArrayList<>();
        for(String b : arr2)
            for(String a : arr1)
                if(b.equals(a))
                    result.add(a);
        return result.toArray(new String[0]);
    }

    public static String[] containss(String str, String[] arr){
        return containss(new String[]{str}, arr);
    }
    public static String[] containss(String[] arr1, String[] arr2){
        ArrayList<String> result = new ArrayList<>();
        for(String b : arr2)
            for(String a : arr1)
                if(b.contains(a))
                    result.add(a);
        return result.toArray(new String[0]);
    }


    public static boolean parseBoolean(String str){
        return parseBoolean(str, false);
    }
    public static boolean parseBoolean(String str, boolean result){
        switch (str){
            case "true": case "1": case "yes":
                return true;
            case "false": case "0": case "no":
                return false;
            default:
                return result;
        }
    }
    public static int parseInt(String str){
        return parseInt(str, Integer.MAX_VALUE);
    }
    public static int parseInt(String str, int result){
        try{ return Integer.parseInt(str); }catch (Exception e) { return result; }
    }
    public static float parseFloat(String str){
        return parseFloat(str, Float.MAX_VALUE);
    }
    public static float parseFloat(String str, float result) {
        try{ return Float.parseFloat(str); }catch (Exception e) { return result; }
    }
    public static long parseLong(String str){
        return parseLong(str, Long.MAX_VALUE);
    }
    public static long parseLong(String str, long result) {
        try{ return Long.parseLong(str); }catch (Exception e) { return result; }
    }

    public static Item parseItem(String str){
        for(Item item : content.items()) if(str.equals(item.name)) return item;
        for(Item item : content.items()) if(str.contains(item.name)) return item;
        return null;
    }
    public static Item parseItem(String str, Item def){
        return parseItem(str) != null ? parseItem(str) : def;
    }
    public static Item[] parseItems(String[] strs){
        LinkedHashSet<Item> items = new LinkedHashSet<>();
        for(String str : strs) if(parseItem(str) != null) items.add(parseItem(str));
        return items.toArray(new Item[0]);
    }
    public static Item[] parseItems(String[] strs, Item[] def){
        return parseItems(strs).length > 0 ? parseItems(strs) : def;
    }

    public static String tileToString(Tile tile){
        return String.format("[[%s, %s]: b: %s f: %s \no: %s e: %s t: %s%s]",
                tile.x, tile.y, tile.block().name, tile.floor().name,
                tile.overlay().name, tile.entity == null ? "null" : (tile.entity.getClass().getSimpleName()), tile.getTeam(),
                !(tile.entity() instanceof BuildBlock.BuildEntity) ? "" : " r: " + ((BuildBlock.BuildEntity)tile.entity).cblock.name);
    }

    public static String tilesToString(Iterable<Tile> tiles){
        Array<String> result = new Array<>();
        for(Tile tile : tiles)
            result.add(tileToString(tile));
        return result.toString(",\n");
    }

    public static String tileToSimpleString(Tile tile){
        return String.format("[[%s, %s]: b: %s f: %s]",
                tile.x, tile.y, tile.block().name, tile.floor().name);
    }

    public static String tilesToSimpleString(Iterable<Tile> tiles){
        Array<String> result = new Array<>();
        for(Tile tile : tiles)
            result.add(tileToSimpleString(tile));
        return result.toString(",\n");
    }

    public static String playerToSimpleString(Player player){
        return String.format("[id: %s [%s, %s] n: %s[white]]",
                player.id, player.x, player.y, colorizeName(player));
    }

    public static String playerToSimpleString2(Player player){
        return String.format("[id: %s [%s, %s] n: %s[white]]",
                player.id, world.toTile(player.x), world.toTile(player.y), colorizeName(player));
    }

    public static String colorizeName(int id){
        return colorizeName(playerGroup.getByID(id));
    }

    public static String colorizeName(Player player){
        if(player == null || player.name == null) return null;
        return "[#" + player.color.toString().toUpperCase() + "]" + player.name;
    }

    public static String colorizeName(Player player, Color def){
        return colorizeName(player) + "[#" + def.toString().toUpperCase() + "]";
    }

    public static String fullPlayerName(Player player){
        return colorizeName(player, Color.white) + "(#" + player.id + ")";
    }
    public static String fullPlayerName(Player player, Color def){
        return fullPlayerName(player) + "[#" + def.toString().toUpperCase() + "]";
    }

    public static String onOffString(boolean b){
        return b ? "On" : "Off";
    }

    public static void print(String... args){
        StringBuilder sb = new StringBuilder();
        for (String str : args) {
            sb.append(str);
            sb.append("\n");
        }
        Call.sendMessage(sb.toString());
    }
}
