package com.leaveflow;
import java.math.*; import java.time.*;
public class BalanceCalculator { public BigDecimal entitlement(LocalDate joined, int year, BigDecimal annual){ LocalDate start=LocalDate.of(year,1,1); if(joined.isAfter(LocalDate.of(year,12,31)))return BigDecimal.ZERO; int months=joined.isBefore(start)||joined.equals(start)?12:13-joined.getMonthValue(); return annual.multiply(BigDecimal.valueOf(months)).divide(BigDecimal.valueOf(12),1,RoundingMode.HALF_UP); } }
