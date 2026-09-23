package com.demo.mcmhelper.modeling.model;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.community.dashscope.chat-model")
@Data
public class QwenModelingConfig {

    private String modelName;
    private String apiKey;  // 注意：字段名要和配置文件中的一致

    @Resource
    private ChatModelListener modelingListener;

    @Bean
    @Primary
    public ChatModel myQwenModeling() {
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .listeners(List.of(modelingListener))
                .build();
    }

    // 流式聊天模型 Bean
    @Bean
    public StreamingChatModel myQwenStreamingModeling() {
        return QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .listeners(List.of(modelingListener))
                .build();
    }
}