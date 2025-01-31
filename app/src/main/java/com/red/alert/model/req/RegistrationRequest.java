package com.red.alert.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RegistrationRequest
{
    @JsonProperty("token")
    public String token;

    @JsonProperty("platform")
    public String platform;

    public RegistrationRequest(String token, String platform)
    {
        // Set members
        this.token = token;
        this.platform = platform;
    }
}
