package com.demo.mcmhelper.modeling;

/**
 * 意图识别器：根据用户输入判断三段式输出策略
 */
public class IntentClassifier {

    public enum IntentType {
        /** 完整赛题分析 */
        COMPLETE_PROBLEM,
        /** 单一环节求助 */
        SPECIFIC_SECTION,
        /** 模糊追问 */
        VAGUE_QUERY
    }

    /** 完整赛题关键词（篇幅长 + 含数据/目标/约束类词汇） */
    private static final String[] COMPLETE_KEYWORDS = {
            "数据", "目标", "约束", "变量", "指标", "预测", "评价", "优化",
            "附件", "表格", "数值", "参数", "条件", "假设", "公式"
    };

    /** 单一环节关键词 */
    private static final String[] SECTION_KEYWORDS = {
            "审题", "方法", "推荐", "模型", "代码", "检验", "验证",
            "写作", "论文", "摘要", "结果", "灵敏度", "稳健性", "求解"
    };

    /**
     * 判断用户意图类型
     *
     * @param userMessage 用户输入
     * @param context     上下文（当前未使用，预留扩展）
     * @return 意图类型
     */
    public IntentType classify(String userMessage, String context) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentType.VAGUE_QUERY;
        }

        String trimmed = userMessage.trim();

        // 规则1：长度 > 50 字 + 含完整赛题关键词 → COMPLETE_PROBLEM
        if (trimmed.length() > 50 && containsAnyKeyword(trimmed, COMPLETE_KEYWORDS)) {
            return IntentType.COMPLETE_PROBLEM;
        }

        // 规则2：含环节关键词 → SPECIFIC_SECTION
        if (containsAnyKeyword(trimmed, SECTION_KEYWORDS)) {
            return IntentType.SPECIFIC_SECTION;
        }

        // 规则3：其他 → VAGUE_QUERY
        return IntentType.VAGUE_QUERY;
    }

    /**
     * 从消息中提取环节关键词
     *
     * @param userMessage 用户输入
     * @return 匹配到的第一个环节关键词，未匹配则返回 null
     */
    public String extractSection(String userMessage) {
        if (userMessage == null) {
            return null;
        }
        String lower = userMessage.toLowerCase();
        for (String keyword : SECTION_KEYWORDS) {
            if (lower.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private boolean containsAnyKeyword(String text, String[] keywords) {
        String lower = text.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}