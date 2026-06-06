package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiChainKeyHintsVO {

    /** Executor @ZestChain 声明的 chain_key */
    private List<String> declaredKeys;

    /** Admin 链列表中的 chain_key */
    private List<String> adminKeys;

    /** 已声明但未在 Admin 建链 */
    private List<String> declaredNotInAdmin;

    /** Admin 有链但 Executor 未声明 */
    private List<String> adminNotDeclared;
}
