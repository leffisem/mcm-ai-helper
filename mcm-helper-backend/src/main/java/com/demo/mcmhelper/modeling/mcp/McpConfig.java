package com.demo.mcmhelper.modeling.mcp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Value("${modelScope.mcp.url}")
    private String mcpUrl;


    @Bean
    public McpToolProvider mcpToolProvider() {
        // 1. 创建 SSE 传输层（和 MCP 服务通讯）
        HttpMcpTransport transport =new  HttpMcpTransport.Builder()
                .sseUrl(mcpUrl)  // 你从魔搭获取的 SSE URL
                // 如果魔搭服务需要鉴权，可以添加 headers
                // .headers(headers -> headers.set("Authorization", "Bearer " + apiKey))
                .logRequests(true)
                .logResponses(true)
                .build();

        // 2. 创建 MCP 客户端
        McpClient mcpClient =new  DefaultMcpClient.Builder()
                .key("leffisem")
                .transport(transport)
                .build();

        // 3. 从 MCP 客户端获取工具提供者
        return McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
    }
}