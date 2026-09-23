package com.demo.mcmhelper.modeling;

import dev.langchain4j.service.Result;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest

class ModelingServiceTest {
    @Resource
    private ModelingService modelingService;

    @Resource
    private ExpertChatRouter expertChatRouter;

    @Test
    void chat() {
        String result= modelingService.chat("你好，我是程序员leffisem");
        System.out.println(result);
    }
    @Test
    void chatWithMemory() {
        String result= modelingService.chat("你好，我是程序员leffisem");
        System.out.println(result);
        result=modelingService.chat("你好，我是谁来着");
        System.out.println(result);
    }

    @Test
    void chatforReport() {
        String userMessage="你好，我是程序员leffisem,请帮我指定学习报告";
        ModelingService.Report report= modelingService.chatforReport(userMessage);
        System.out.println(report);

    }
    @Test
    void chatWithRag() {
        Result<String> result= modelingService.chatWithRag("怎么学习java,有哪些常见面试题");
        System.out.println(result.sources());
        System.out.println(result.content());


    }

    @Test
    void chatWithTools() {
        String result= modelingService.chat("有哪些常见的计算机网络面试题");
        System.out.println(result);

    }

    @Test
    void chatWithMcp(){
        String result= modelingService.chat("什么是程序员鱼皮的编程导航");
        System.out.println(result);
    }

    @Test
    void chatWithGuardrail(){
        String result= modelingService.chat("kill the game");
        System.out.println(result);
    }

    // ========== 三段式输出策略测试 ==========

    @Test
    void testStrategyA_CompleteProblem() {
        String problem = """
                2023年全国大学生数学建模竞赛B题：
                基于多波束测深的海洋深度估算。
                已知多波束测深系统在海底某区域进行测量，换能器发射的声波束与海底法线方向的夹角为θ，
                海底坡度角为α，测量船在与海底法线方向夹角为θ的方向上发射声波束。
                请建立数学模型，给出多波束测深覆盖宽度的计算公式，并分析不同因素对覆盖宽度的影响。
                附件中给出了某海域的实测数据，请利用你的模型对该海域的深度进行估算。
                """;
        String result = String.join("", expertChatRouter.analyzeCompleteProblem(0, problem).collectList().block());
        System.out.println("【策略A】完整赛题分析:");
        System.out.println(result);
        assert result.contains("审题") || result.contains("分析") : "缺少审题分析部分";
        assert result.contains("模型") : "缺少模型构建部分";
        System.out.println("策略A验证通过");
    }

    @Test
    void testStrategyB_SpecificSection() {
        String result = String.join("", expertChatRouter.respondSpecificSection(0, "审题", "帮我审题，这道题有什么关键点？").collectList().block());
        System.out.println("【策略B】单一环节:");
        System.out.println(result);
        assert result.contains("审题") || result.contains("目标") : "缺少审题内容";
        System.out.println("策略B验证通过");
    }

    @Test
    void testStrategyC_VagueQuery() {
        String result = String.join("", expertChatRouter.chat(0, "你好，帮我建模").collectList().block());
        System.out.println("【策略C】模糊追问:");
        System.out.println(result);
        assert result.contains("补充") || result.contains("请问") || result.contains("?") : "缺少追问清单";
        System.out.println("策略C验证通过");
    }

    @Test
    void testIntentClassifier() {
        IntentClassifier classifier = new IntentClassifier();

        // 测试完整赛题（长文本 + 关键词）
        String longProblem = "请根据以下数据建立数学模型，目标是最小化成本，约束条件包括时间、资源等变量，预测未来趋势并评价方案优劣。";
        assert classifier.classify(longProblem, null) == IntentClassifier.IntentType.COMPLETE_PROBLEM :
                "长文本+关键词应识别为COMPLETE_PROBLEM";

        // 测试单一环节
        assert classifier.classify("帮我审题，看看这道题怎么做", null) == IntentClassifier.IntentType.SPECIFIC_SECTION :
                "含环节关键词应识别为SPECIFIC_SECTION";

        // 测试模糊追问
        assert classifier.classify("你好", null) == IntentClassifier.IntentType.VAGUE_QUERY :
                "短文本应识别为VAGUE_QUERY";

        // 测试extractSection
        String section = classifier.extractSection("帮我看看模型怎么建");
        assert "模型".equals(section) : "应提取到'模型'关键词";

        System.out.println("IntentClassifier 全部测试通过");
    }
}