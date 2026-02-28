package com.example.aiagent.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页请求
 */
@Data
@Schema(description = "分页请求")
public class PageRequest {

    /**
     * 当前页号
     */
    @Schema(description = "当前页号")
    private int current = 1;

    /**
     * 页面大小
     */
    @Schema(description = "页面大小")
    private int pageSize = 10;

    /**
     * 排序字段
     */
    @Schema(description = "排序字段")
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    @Schema(description = "排序顺序（默认降序）")
    private String sortOrder = "descend";
}
