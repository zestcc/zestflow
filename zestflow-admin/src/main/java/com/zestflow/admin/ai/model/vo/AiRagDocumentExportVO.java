package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiRagDocumentExportVO {

    private int count;
    private List<AiRagDocumentVO> documents;
}
