package com.agentsaul.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lightweight intent classifier — extracts user intent and entities
 * without an extra LLM call. Falls back to general_chat for unknown.
 */
@Component
public class IntentParser {

    private static final Logger log = LoggerFactory.getLogger(IntentParser.class);

    public record IntentResult(String intent, Map<String, String> entities, String language) {}

    private static final Pattern ZH_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");

    public IntentResult parse(String text) {
        String lang = ZH_PATTERN.matcher(text).find() ? "zh" : "en";
        String intent = classifyIntent(text, lang);
        Map<String, String> entities = extractEntities(text, intent);
        log.info("[Intent] lang={} intent={} entities={}", lang, intent, entities);
        return new IntentResult(intent, entities, lang);
    }

    private String classifyIntent(String text, String lang) {
        String lower = text.toLowerCase();

        if (matches(lower, "weather|天气|气温|下雨|下雪|温度|刮风|雾霾")) return "weather";
        if (matches(lower, "where am i|where is|location|where|在哪里|位置|定位|ip|在哪|什么地方")) return "location";
        if (matches(lower, "what time|what's the time|现在几点|时间|日期|今天几号|星期")) return "time";
        if (matches(lower, "translate|翻译|翻成|译成|用.*说|怎么说")) return "translation";
        if (matches(lower, "calculate|compute|算|计算|等于|多少.*等于|求解|eval")) return "calculation";
        if (matches(lower, "search|搜索|查一下|查找|百度|google")) return "search";
        if (matches(lower, "legal|law|lawyer|attorney|court|sue|sue|contract|法律|律师|起诉|合同|赔偿|诉讼|权利|违法")) return "legal";
        if (matches(lower, "deadline|期限|截止|多少天|工作日|到期|deadline")) return "deadline";
        if (matches(lower, "settlement|settle|赔偿|赔多少|受伤|医疗费|误工费")) return "settlement";

        return "general_chat";
    }

    private Map<String, String> extractEntities(String text, String intent) {
        Map<String, String> e = new java.util.HashMap<>();

        // extract city names from weather/location queries
        var cityMatcher = Pattern.compile("(Beijing|Shanghai|Shenzhen|Guangzhou|Hangzhou|Chengdu|Wuhan|Nanjing|Tokyo|London|New York|Paris|北京|上海|深圳|广州|杭州|成都|武汉|南京|东京|伦敦|纽约|巴黎)").matcher(text);
        if (cityMatcher.find()) e.put("city", cityMatcher.group(1));

        // extract IP
        var ipMatcher = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b").matcher(text);
        if (ipMatcher.find()) e.put("ip", ipMatcher.group(1));

        // extract numbers for calculation/settlement
        var numMatcher = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*(万|k|million|billion|亿)?").matcher(text);
        while (numMatcher.find() && e.size() < 4) {
            String key = "num" + (e.size() + 1);
            String val = numMatcher.group(1);
            if (numMatcher.group(2) != null) val += numMatcher.group(2);
            if (!e.containsKey(key)) e.put(key, val);
        }

        // extract language codes for translation
        var langMatcher = Pattern.compile("(Chinese|English|Japanese|Korean|French|German|Spanish|中文|英文|日语|韩语|法语|德语|西班牙语|zh|en|ja|ko|fr|de|es)").matcher(text);
        while (langMatcher.find()) {
            String l = langMatcher.group(1).toLowerCase();
            String code = switch (l) {
                case "chinese","中文","zh" -> "zh";
                case "english","英文","en" -> "en";
                case "japanese","日语","ja" -> "ja";
                case "korean","韩语","ko" -> "ko";
                case "french","法语","fr" -> "fr";
                case "german","德语","de" -> "de";
                case "spanish","西班牙语","es" -> "es";
                default -> l;
            };
            e.put("language", code);
        }

        return e;
    }

    private boolean matches(String text, String regexPattern) {
        return Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
}
