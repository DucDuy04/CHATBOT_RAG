package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class PlaygroundCompareResult {
    String answer;
    List<ChatResponse.SourceDto> sources;
    Long latency;
    Map<String, Object> config;
    TokenUsageDto tokenUsage;
}
