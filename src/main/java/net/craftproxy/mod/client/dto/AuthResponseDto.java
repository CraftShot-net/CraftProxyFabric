package net.craftproxy.mod.client.dto;

public class AuthResponseDto {

    public String token;
    public UserData user;

    /**
     * Per-user CraftShot token for the social core (friends, DMs, presence).
     * {@code null} when the CraftShot backend was unreachable; old mod versions
     * simply ignore these fields.
     */
    public String craftshot_token;
    public Integer craftshot_user_id;

    /** Public Reverb connection details for instant message delivery. */
    public ReverbConfigDto craftshot_reverb;

    public static class UserData {
        public String uuid;
        public String name;
    }

}
