package com.example.aiagent.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 删除请求体
 */
@Data
@Schema(description = "删除请求体")
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    @Schema(description = "id")
    private Long id;

    private static final long serialVersionUID = 1L;
}
