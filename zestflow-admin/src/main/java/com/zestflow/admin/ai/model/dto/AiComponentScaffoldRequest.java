package com.zestflow.admin.ai.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiComponentScaffoldRequest {

    private String appCode;
    private String componentId;
    private String componentType;
    private String groupName;
    private String description;
    private List<ParamItem> inputParams;
    private List<ParamItem> outputParams;

    @Data
    public static class ParamItem {
        private String name;
        private String type;
        private boolean required;
        private String description;
    }
}
