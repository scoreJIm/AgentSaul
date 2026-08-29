package com.agentsaul.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("McpTools")
class McpToolsTest {

    private final McpTools tools = new McpTools();

    @Nested
    @DisplayName("legalStatuteLookup")
    class LegalStatuteLookup {

        @Test
        @DisplayName("should return statute for known keyword")
        void shouldReturnStatuteForKnownKeyword() {
            String result = tools.legalStatuteLookup("合同违约");
            assertThat(result).contains("合同法第107条");
            assertThat(result).contains("违约责任");
        }

        @Test
        @DisplayName("should return statute for labor arbitration")
        void shouldReturnLaborArbitration() {
            String result = tools.legalStatuteLookup("劳动仲裁");
            assertThat(result).contains("时效期间为一年");
        }

        @Test
        @DisplayName("should return not-found message for unknown keyword")
        void shouldReturnNotFoundForUnknownKeyword() {
            String result = tools.legalStatuteLookup("不存在的法律问题");
            assertThat(result).contains("未找到");
            assertThat(result).contains("建议尝试");
        }
    }

    @Nested
    @DisplayName("mcpServerTime")
    class ServerTime {

        @Test
        @DisplayName("should return server time string")
        void shouldReturnServerTime() {
            String result = tools.mcpServerTime();
            assertThat(result).contains("MCP 服务器时间");
            assertThat(result).contains("Asia/Shanghai");
        }
    }

    @Nested
    @DisplayName("mcpCaseAnalyzer")
    class CaseAnalyzer {

        @Test
        @DisplayName("should return analysis report with case details")
        void shouldReturnAnalysisReport() {
            String result = tools.mcpCaseAnalyzer("合同纠纷", "甲方未按约定支付货款");
            assertThat(result).contains("MCP 案件分析报告");
            assertThat(result).contains("合同纠纷");
            assertThat(result).contains("甲方未按约定支付货款");
            assertThat(result).contains("收集并保存所有相关证据材料");
            assertThat(result).contains("咨询执业律师");
        }
    }
}
