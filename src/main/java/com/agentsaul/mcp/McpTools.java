package com.agentsaul.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Tools exposed via MCP (Model Context Protocol).
 * <p>
 * These are annotated with Spring AI's {@code @Tool} (same as direct tools),
 * and MCP auto-configuration will additionally expose them through the MCP
 * protocol when the MCP server starter is on the classpath.
 * <p>
 * For pure MCP-specific tools, use {@code @McpTool} from the MCP module.
 * Here we use {@code @Tool} so they work both as direct tools AND MCP tools
 * — demonstrating the dual-exposure capability.
 */
@Component
public class McpTools {

    private static final Logger log = LoggerFactory.getLogger(McpTools.class);

    @Tool(name = "legalStatuteLookup", description = "通过 MCP 协议查询法律条文概要。输入法律主题关键词，返回相关法律条文摘要")
    public String legalStatuteLookup(
            @ToolParam(description = "法律主题关键词，如: 合同违约、离婚财产分割、劳动仲裁") String keyword) {
        log.info("[MCP Tool] legalStatuteLookup keyword={}", keyword);

        Map<String, String> statutes = Map.of(
                "合同违约", "合同法第107条：当事人一方不履行合同义务或者履行合同义务不符合约定的，应当承担继续履行、采取补救措施或者赔偿损失等违约责任。赔偿额应相当于因违约造成的损失，包括合同履行后可获得的利益。",
                "离婚财产分割", "民法典第1087条：离婚时，夫妻的共同财产由双方协议处理；协议不成的，由人民法院根据财产的具体情况，按照照顾子女、女方和无过错方权益的原则判决。",
                "劳动仲裁", "劳动争议调解仲裁法第27条：劳动争议申请仲裁的时效期间为一年。仲裁时效期间从当事人知道或者应当知道其权利被侵害之日起计算。劳动关系存续期间因拖欠劳动报酬发生争议的，不受一年时效限制。",
                "试用期", "劳动合同法第19条：劳动合同期限三个月以上不满一年的，试用期不得超过一个月；一年以上不满三年的，试用期不得超过二个月；三年以上固定期限和无固定期限的劳动合同，试用期不得超过六个月。",
                "工伤", "工伤保险条例第14条：在工作时间和工作场所内，因工作原因受到事故伤害的，应当认定为工伤。在工作时间和工作岗位，突发疾病死亡或者在48小时之内经抢救无效死亡的，视同工伤。",
                "正当防卫", "刑法第20条：为了使国家、公共利益、本人或者他人的人身、财产和其他权利免受正在进行的不法侵害，而采取的制止不法侵害的行为，对不法侵害人造成损害的，属于正当防卫，不负刑事责任。"
        );

        return statutes.entrySet().stream()
                .filter(e -> e.getKey().contains(keyword) || keyword.contains(e.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse("未找到与\"" + keyword + "\"直接相关的法律条文。建议尝试: 合同违约、离婚财产分割、劳动仲裁、试用期、工伤、正当防卫");
    }

    @Tool(name = "mcpServerTime", description = "通过 MCP 协议获取服务器当前时间")
    public String mcpServerTime() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("[MCP Tool] mcpServerTime = {}", now);
        return "MCP 服务器时间: " + now + " (Asia/Shanghai)";
    }

    @Tool(name = "mcpCaseAnalyzer", description = "通过 MCP 协议分析案件情况，给出简要法律建议")
    public String mcpCaseAnalyzer(
            @ToolParam(description = "案件类型，如: 合同纠纷、劳动争议、婚姻家庭、刑事辩护") String caseType,
            @ToolParam(description = "简要案情描述") String description) {
        log.info("[MCP Tool] mcpCaseAnalyzer type={}", caseType);

        return String.format("""
                === MCP 案件分析报告 ===
                案件类型: %s
                案情简述: %s
                初步分析:
                - 建议收集并保存所有相关证据材料
                - 确认诉讼时效是否在有效期内
                - 建议先通过调解/协商方式解决
                - 如协商无果，可通过诉讼途径维权
                提示: 以上为初步分析，具体法律意见请咨询执业律师。
                """, caseType, description);
    }
}
