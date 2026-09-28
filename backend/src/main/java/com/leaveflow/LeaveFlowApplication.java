package com.leaveflow;
import org.springframework.boot.*; import org.springframework.boot.autoconfigure.*; import org.springframework.scheduling.annotation.*;
@SpringBootApplication @EnableScheduling public class LeaveFlowApplication { public static void main(String[] a){SpringApplication.run(LeaveFlowApplication.class,a);} }
