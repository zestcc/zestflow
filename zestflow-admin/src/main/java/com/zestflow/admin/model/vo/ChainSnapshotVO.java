package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainSnapshotVO {

    private Long id;
    private Long chainId;
    private Integer version;
    private String graphData;
    private String changeLog;
    private String publishedBy;
    private LocalDateTime createdAt;
}
