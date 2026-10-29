package net.craftproxy.mod.client.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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

    private String button1Label, button1Url;
    private String button2Label, button2Url;

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

    /**
     * Sets up to two buttons shown on the Rich Presence card. Discord allows a maximum
     * of two; call with only the first pair to show just one, or with none to clear.
     *
     * Note: Discord does not show buttons on your OWN client's presence preview in the
     * Discord app — you have to check from a friend's account/another client to see them.
     *
     * @param label1 text on the first button, or null for none
     * @param url1   target URL for the first button (must be http/https), or null
     * @param label2 text on the second button, or null for none
     * @param url2   target URL for the second button, or null
     */
    public void setButtons(String label1, String url1, String label2, String url2) {
        this.button1Label = label1;
        this.button1Url = url1;
        this.button2Label = label2;
        this.button2Url = url2;
    }

    public void clearButtons() {
        setButtons(null, null, null, null);
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

        JsonArray buttons = new JsonArray();
        if (button1Label != null && button1Url != null) {
            JsonObject b1 = new JsonObject();
            b1.addProperty("label", button1Label);
            b1.addProperty("url", button1Url);
            buttons.add(b1);
        }
        if (button2Label != null && button2Url != null) {
            JsonObject b2 = new JsonObject();
            b2.addProperty("label", button2Label);
            b2.addProperty("url", button2Url);
            buttons.add(b2);
        }
        if (!buttons.isEmpty()) {
            o.add("buttons", buttons);
        }

        return o;
    }
}