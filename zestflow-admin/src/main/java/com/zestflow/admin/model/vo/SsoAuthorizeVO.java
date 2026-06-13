package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SsoAuthorizeVO {

    private String authorizationUrl;
    private String state;
}
