package com.demo.mcmhelper.modeling;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ModelingHelperTest {

    @Resource
    private ModelingHelper modelingHelper;

    @Test
    void chat() {
        modelingHelper.chat("你好，我是程序员leffisem");
    }

    @Test
    void chatWithMessage() {
        UserMessage userMessage= UserMessage.from(
                TextContent.from("描述图片"),
                ImageContent.from("https://www.codefather.cn/logo.png")
        );
        modelingHelper.chatWithMessage(userMessage);
    }
}