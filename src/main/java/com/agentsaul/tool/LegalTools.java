package com.agentsaul.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class LegalTools {

    private static final Logger log = LoggerFactory.getLogger(LegalTools.class);

    @Tool(description = "Calculate a legal deadline. Given a start date (yyyy-MM-dd) and number of days, returns the deadline date, excluding weekends.")
    public String calculateDeadline(
            @ToolParam(description = "Start date in yyyy-MM-dd format") String startDate,
            @ToolParam(description = "Number of calendar days to add") int days) {
        log.info("[Tool] calculateDeadline start={} days={}", startDate, days);
        try {
            LocalDate date = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate deadline = date;
            int added = 0;
            while (added < days) {
                deadline = deadline.plusDays(1);
                if (deadline.getDayOfWeek().getValue() < 6) added++;
            }
            return String.format("Deadline: %s (%d business days from %s, excluding weekends)",
                    deadline.format(DateTimeFormatter.ISO_LOCAL_DATE), days, startDate);
        } catch (Exception e) {
            return "Invalid date format. Use yyyy-MM-dd.";
        }
    }

    @Tool(description = "Estimate settlement amount for a personal injury case based on medical bills, lost wages, and pain multiplier (1-5).")
    public String estimateSettlement(
            @ToolParam(description = "Total medical bills in dollars") double medicalBills,
            @ToolParam(description = "Total lost wages in dollars") double lostWages,
            @ToolParam(description = "Pain and suffering multiplier, typically 1-5") double multiplier) {
        log.info("[Tool] estimateSettlement medical={} lostWages={} multiplier={}", medicalBills, lostWages, multiplier);
        double painAndSuffering = medicalBills * multiplier;
        double total = medicalBills + lostWages + painAndSuffering;
        double low = total * 0.7;
        double high = total * 1.3;
        return String.format("Estimated Settlement Range: $%.2f - $%.2f (Base: $%.2f medical + $%.2f lost wages + $%.2f pain/suffering at %.1fx)",
                low, high, medicalBills, lostWages, painAndSuffering, multiplier);
    }

    @Tool(description = "Get information about a common legal topic. Covers: statute_of_limitations, miranda_rights, search_and_seizure, self_defense, contract_basics.")
    public String legalInfo(@ToolParam(description = "Legal topic key") String topic) {
        log.info("[Tool] legalInfo topic={}", topic);
        return switch (topic.toLowerCase()) {
            case "statute_of_limitations" -> "Statute of limitations varies by jurisdiction and crime type. Generally: personal injury = 2-3 years, breach of contract = 4-6 years, felony crimes = 3-7 years. Always check your local jurisdiction.";
            case "miranda_rights" -> "Miranda Rights: You have the right to remain silent. Anything you say can be used against you in court. You have the right to an attorney. If you cannot afford one, one will be appointed.";
            case "search_and_seizure" -> "Fourth Amendment protects against unreasonable searches. Police generally need a warrant. Exceptions: plain view, exigent circumstances, consent, search incident to arrest, automobile exception.";
            case "self_defense" -> "Self-defense requires: imminent threat, proportional force, reasonable belief of danger. Stand Your Ground laws vary by state. Duty to retreat exists in some jurisdictions.";
            case "contract_basics" -> "Valid contract requires: offer, acceptance, consideration, capacity, legality. Oral contracts can be enforceable but harder to prove. Statute of Frauds requires written contracts for certain types.";
            default -> "Available topics: statute_of_limitations, miranda_rights, search_and_seizure, self_defense, contract_basics. Which would you like to know about?";
        };
    }
}
