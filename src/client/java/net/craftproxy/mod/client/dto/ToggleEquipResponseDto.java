package net.craftproxy.mod.client.dto;

public record ToggleEquipResponseDto(
        String message,
        String cosmeticId,
        boolean equipped
) {}
