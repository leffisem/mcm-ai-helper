package com.demo.mcmhelper.modeling.dto;

import lombok.Data;

/**
 * 对话请求体（POST JSON）
 */
@Data
public class ChatRequest {

    /** 用户消息内容（支持长文本） */
    private String message;

    /** 会话记忆 ID，默认 1 */
    private Integer memoryId = 1;
}
