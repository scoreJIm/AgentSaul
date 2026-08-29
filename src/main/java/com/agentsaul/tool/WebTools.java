package com.agentsaul.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WebTools {

    private static final Logger log = LoggerFactory.getLogger(WebTools.class);

    @Tool(description = "Perform basic calculations. Supports +, -, *, /, ^ (power), sqrt, sin, cos, log.")
    public String calculate(@ToolParam(description = "Math expression, e.g. '2+2*3' or 'sqrt(144)'") String expression) {
        log.info("[Tool] calculate expr={}", expression);
        try {
            double result = eval(expression.trim());
            return String.format("%s = %s", expression, formatResult(result));
        } catch (Exception e) {
            return "Could not evaluate: " + expression + " (" + e.getMessage() + ")";
        }
    }

    @Tool(description = "Get current time in UTC, Beijing, New York, London, Tokyo")
    public String worldTime() {
        long ts = System.currentTimeMillis() / 1000;
        java.time.LocalDateTime utc = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        java.time.LocalDateTime bj = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.LocalDateTime ny = java.time.LocalDateTime.now(java.time.ZoneId.of("America/New_York"));
        java.time.LocalDateTime london = java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/London"));
        java.time.LocalDateTime tokyo = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Tokyo"));
        return String.format("UTC: %s | Beijing: %s | New York: %s | London: %s | Tokyo: %s",
                utc, bj, ny, london, tokyo);
    }

    // ── expression parser ──

    private double eval(String expr) {
        String e = expr.replaceAll("\\s+", "");
        if (e.isEmpty()) throw new IllegalArgumentException("empty expression");
        return parseExpression(e);
    }

    private int pos;
    private String input;

    private double parseExpression(String s) {
        pos = 0;
        input = s;
        double val = parseTerm();
        while (pos < input.length()) {
            char op = input.charAt(pos);
            if (op == '+') { pos++; val += parseTerm(); }
            else if (op == '-') { pos++; val -= parseTerm(); }
            else break;
        }
        return val;
    }

    private double parseTerm() {
        double val = parseUnary();
        while (pos < input.length()) {
            char op = input.charAt(pos);
            if (op == '*') { pos++; val *= parseUnary(); }
            else if (op == '/') { pos++; val /= parseUnary(); }
            else break;
        }
        return val;
    }

    private double parseUnary() {
        if (pos < input.length() && input.charAt(pos) == '-') { pos++; return -parsePower(); }
        return parsePower();
    }

    private double parsePower() {
        double val = parseFactor();
        if (pos < input.length() && input.charAt(pos) == '^') { pos++; val = Math.pow(val, parseUnary()); }
        return val;
    }

    private double parseFactor() {
        if (pos >= input.length()) throw new IllegalArgumentException("unexpected end of expression");

        char c = input.charAt(pos);

        // parentheses
        if (c == '(') {
            pos++;
            double val = parseExpression(input); // parses until matching ) or end
            if (pos < input.length() && input.charAt(pos) == ')') pos++;
            return val;
        }

        // function calls: sqrt, sin, cos, log
        if (Character.isLetter(c)) {
            int start = pos;
            while (pos < input.length() && Character.isLetter(input.charAt(pos))) pos++;
            String func = input.substring(start, pos);
            if (pos < input.length() && input.charAt(pos) == '(') {
                pos++;
                double arg = parseExpression(input);
                if (pos < input.length() && input.charAt(pos) == ')') pos++;
                return switch (func) {
                    case "sqrt" -> Math.sqrt(arg);
                    case "sin" -> Math.sin(Math.toRadians(arg));
                    case "cos" -> Math.cos(Math.toRadians(arg));
                    case "log" -> Math.log10(arg);
                    default -> throw new IllegalArgumentException("unknown function: " + func);
                };
            }
            throw new IllegalArgumentException("expected '(' after function: " + func);
        }

        // number
        int numStart = pos;
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) pos++;
        if (numStart == pos) throw new IllegalArgumentException("unexpected char: " + c);
        return Double.parseDouble(input.substring(numStart, pos));
    }

    private String formatResult(double r) {
        if (r == (long) r) return String.valueOf((long) r);
        return String.format("%.10f", r).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
