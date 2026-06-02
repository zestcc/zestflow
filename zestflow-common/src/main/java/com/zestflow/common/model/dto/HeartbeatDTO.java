package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行器唯一标识 */
    private String executorId;

    /** 执行器状态：1-正常 0-过载 */
    @Builder.Default
    private int status = 1;
}
