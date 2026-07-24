package net.craftproxy.mod.client.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Standalone Rich Presence data holder (details, state, images, timestamps, party).
 * No longer depends on meteordevelopment:discord-ipc — used together with
 * CraftProxyDiscordIPC, which reimplements the actual pipe connection.
 */
public class PartyRichPresence implements CraftProxyDiscordIPC.RichPresenceJson {

    private String details;
    private String state;

    private String largeImageKey, largeImageText;
    private String smallImageKey, smallImageText;

    private Long startTimestamp;
    private Long endTimestamp;

    private String partyId;
    private int partySize;
    private int partyMax;

    public void setDetails(String details) {
        this.details = details;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setLargeImage(String key, String text) {
        this.largeImageKey = key;
        this.largeImageText = text;
    }

    public void setSmallImage(String key, String text) {
        this.smallImageKey = key;
        this.smallImageText = text;
    }

    public void setStart(long epochSeconds) {
        this.startTimestamp = epochSeconds;
    }

    public void setEnd(long epochSeconds) {
        this.endTimestamp = epochSeconds;
    }

    /**
     * @param partyId any stable identifier for the current session/server (e.g. server IP or a UUID)
     * @param size    current player count
     * @param max     max player count
     */
    public void setParty(String partyId, int size, int max) {
        this.partyId = partyId;
        this.partySize = size;
        this.partyMax = max;
    }

    public void clearParty() {
        this.partyId = null;
    }

    @Override
    public JsonObject toJson() {
        JsonObject o = new JsonObject();

        if (details != null) o.addProperty("details", details);
        if (state != null) o.addProperty("state", state);

        if (largeImageKey != null || smallImageKey != null) {
            JsonObject assets = new JsonObject();
            if (largeImageKey != null) assets.addProperty("large_image", largeImageKey);
            if (largeImageText != null) assets.addProperty("large_text", largeImageText);
            if (smallImageKey != null) assets.addProperty("small_image", smallImageKey);
            if (smallImageText != null) assets.addProperty("small_text", smallImageText);
            o.add("assets", assets);
        }

        if (startTimestamp != null || endTimestamp != null) {
            JsonObject timestamps = new JsonObject();
            if (startTimestamp != null) timestamps.addProperty("start", startTimestamp);
            if (endTimestamp != null) timestamps.addProperty("end", endTimestamp);
            o.add("timestamps", timestamps);
        }

        if (partyId != null) {
            JsonObject party = new JsonObject();
            party.addProperty("id", partyId);

            JsonArray size = new JsonArray();
            size.add(partySize);
            size.add(partyMax);
            party.add("size", size);

            o.add("party", party);
        }

        return o;
    }
}