package com.example.rag.security;

import com.example.rag.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 输入安全校验（基础版，适合原型演示）：
 * 1. 非空与长度限制，防止超长请求挤占上下文与 token；
 * 2. 提示词注入基础检测：识别“覆盖系统指令 / 越权索取信息 / 越狱扮演”等常见攻击模式。
 *
 * 说明：关键词规则只能拦截已知模式，生产环境应叠加语义分类模型、输入清洗与输出审计。
 */
@Component
public class PromptGuard {

    /** 注入/越狱敏感特征词（大小写不敏感匹配） */
    private static final List<String> INJECTION_PATTERNS = Arrays.asList(
            "忽略前面", "忽略以上", "忽略所有指令", "忽略之前", "忘掉你的角色", "忘记你的角色",
            "你现在是", "现在你是", "system prompt", "systemprompt", "开发者指令",
            "泄露", "输出你的", "把你的密钥", "打印密钥", "跳过安全", "解除限制",
            "越狱", "jailbreak", "扮演黑客", "假装没有规则", "bypass", "ignore previous",
            "ignore all", "you are now", "reveal your"
    );

    @Value("${rag.security.max-question-length:1000}")
    private int maxQuestionLength;

    /**
     * 校验用户问题；不合法直接抛出 ApiException。
     *
     * @param question 用户原始输入
     * @return 清洗后的输入（去除首尾空白）
     */
    public String check(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw ApiException.badRequest("问题不能为空");
        }
        String normalized = question.trim();

        // 长度限制：防止超长请求
        if (normalized.length() > maxQuestionLength) {
            throw ApiException.badRequest("问题长度不能超过 " + maxQuestionLength + " 个字符");
        }

        // 提示词注入检测（大小写不敏感）
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String pattern : INJECTION_PATTERNS) {
            if (lower.contains(pattern.toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("输入包含不安全指令，已被安全策略拦截");
            }
        }
        return normalized;
    }
}
