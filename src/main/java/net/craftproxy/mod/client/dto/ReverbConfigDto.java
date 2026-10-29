package net.craftproxy.mod.client.dto;

/**
 * Public Reverb connection details handed to the mod by the CraftShot auth bridge.
 * The app secret stays on the server; clients only need host/port/scheme/key.
 */
public class ReverbConfigDto {
    public String host;
    public int port;
    public String scheme;
    public String key;

    public boolean isComplete() {
        return host != null && !host.isBlank()
                && key != null && !key.isBlank()
                && port > 0;
    }
}
