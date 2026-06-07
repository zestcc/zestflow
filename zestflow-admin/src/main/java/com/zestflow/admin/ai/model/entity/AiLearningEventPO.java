package com.zestflow.admin.ai.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("zf_ai_learning_event")
public class AiLearningEventPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String appCode;
    private Long sessionId;
    private String intent;
    private String feature;
    private String chainCode;
    private Integer httpMode;
    private String payloadJson;
    private Integer validatePassed;
    private Integer validateRounds;
    private Integer adopted;
    private Integer playgroundSuccess;
    private BigDecimal promotionScore;
    private Integer promotionEligible;
    private String userCorrection;
    private Integer promotedToRag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
