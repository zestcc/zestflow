package com.zestflow.admin.ai.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiRagDocumentImportDTO {

    private List<AiRagDocumentSaveDTO> documents;
    /** 为 true 时先禁用同标题旧文档（软删）再导入 */
    private Boolean replaceByTitle;
}
