package ru.local.spbumailpilot;
public class AiPolicyTest {
 public static void main(String[] args){if(!AiPolicy.enabled("{}")||!AiPolicy.enabled("{\"settings\":{\"neuralAnalysis\":true}}")||AiPolicy.enabled("{\"settings\":{\"neuralAnalysis\":false}}")||AiPolicy.enabled("broken"))throw new AssertionError();System.out.println("PASS: legacy AI preference defaults, explicit disable/enable and corrupted storage blocks analysis.");}
}
