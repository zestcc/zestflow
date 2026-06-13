package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SsoConfigVO {

    private boolean enabled;
    private String issuer;
    private String clientId;
    private String authorizationUrl;
}
