package com.zestflow.admin.tenant.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicTenantProvisionDTO {

    @Size(max = 128)
    private String name;

    /** 可选；不传则服务端生成 trial-{uuid8} */
    @Size(max = 64)
    private String code;

    @Size(max = 500)
    private String description;
}
