package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiConfigStatusVO {

    private boolean globallyEnabled;
    private boolean tenantEnabled;
    private boolean copilotAvailable;
    private String preset;
    private String model;
    private String presetDisplayName;
}
