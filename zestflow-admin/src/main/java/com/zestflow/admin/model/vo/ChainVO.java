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
public class ChainVO {

    private Long id;
    private String code;
    private String name;
    private Long moduleId;
    private Integer status;
    private String description;
    private String designCode;
    private String designName;
    private String designDescription;
    private Long designId;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
