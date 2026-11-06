package com.sportbooking.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,        // số giây access token còn hiệu lực
        UserResponse user) {
}
