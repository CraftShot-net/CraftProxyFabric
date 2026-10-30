package net.craftproxy.mod.client.dto;

/**
 * Answer of {@code POST /api/redeem}. On success {@code error} is null; otherwise it holds a code such as
 * {@code invalid_code}, {@code code_expired}, {@code code_used_up}, {@code already_redeemed} or {@code already_owned}.
 */
public record RedeemCodeResponseDto(
        String message,
        String error,
        String cosmeticId,
        String type,
        boolean equipped,
        String textureUrl
) {
    public boolean isSuccess() {
        return error == null && cosmeticId != null;
    }
}
