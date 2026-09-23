package com.demo.mcmhelper.modeling.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Set;

public class SafeInputGuardrail implements InputGuardrail {
    private static final Set<String> sensitiveWords =Set.of("kill","evil");

    @Override
    public InputGuardrailResult validate(UserMessage userMessage){
        //获取用户输入并且转换为小写以确保大小写不敏感
        String inputText = userMessage.singleText().toLowerCase();
        //使用正则表达式分割输入文本为单词
        String[] words = inputText.split("\\W+");
        //遍历所有单词是否存在敏感词
        for(String word:words){
            if(sensitiveWords.contains(word))
                return fatal("Sensitive word detected:"+word);
        }
        return success();
    }

}