package com.project.ecommerce_api.models.authDto.response;

import com.project.ecommerce_api.models.JwtAuthenticationResponse;
import com.project.ecommerce_api.models.UserInfo;
import lombok.Data;

@Data
public class VerificationResponse {
    private JwtAuthenticationResponse authInfo;
    private UserInfo user;
}
