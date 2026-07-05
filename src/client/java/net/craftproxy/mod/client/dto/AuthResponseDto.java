package net.craftproxy.mod.client.dto;

public class AuthResponseDto {

    public String token;
    public UserData user;

    public static class UserData {
        public String uuid;
        public String name;
    }

}
