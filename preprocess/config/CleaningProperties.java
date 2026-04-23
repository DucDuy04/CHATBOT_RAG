package KLTN.RAG_CHATBOT_BE.preprocess.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.preprocessing.cleaner")
public class CleaningProperties {

    private boolean enableNoiseRemoval = true;
    private boolean enableLineUnbreak = true;
    private boolean enableWhitespaceNormalization = true;

    // Regex patterns to drop known noise lines such as page numbers and watermarks.
    private List<String> noiseRegex = new ArrayList<>(List.of(
            "(?i)^page\\s+\\d+(\\s+of\\s+\\d+)?$",
            "(?i)^confidential$",
            "(?i)^draft$"
    ));

    // If two lines form a likely broken sentence, they will be merged with this separator.
    private String lineJoinSeparator = " ";
}
