package KLTN.RAG_CHATBOT_BE.rag.budget;

import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves context character budget for final prompt assembly (task 23I).
 */
@Slf4j
@Component
public class PromptBudgetResolver {

    @Value("${rag.retrieval.prompt-budget.enabled:true}")
    private boolean promptBudgetEnabled;

    @Value("${rag.retrieval.prompt-budget.fact-context-char-budget:10000}")
    private int factContextCharBudget;

    @Value("${rag.retrieval.prompt-budget.table-context-char-budget:14000}")
    private int tableContextCharBudget;

    @Value("${rag.retrieval.prompt-budget.list-context-char-budget:16000}")
    private int listContextCharBudget;

    @Value("${rag.retrieval.prompt-budget.hard-max-context-char-budget:18000}")
    private int hardMaxContextCharBudget;

    public boolean isEnabled() {
        return promptBudgetEnabled;
    }

    public int resolveCharBudget(QueryAnalyzerService.QueryType queryType, boolean isLockedScope) {
        if (!promptBudgetEnabled) {
            return -1;
        }
        if (isLockedScope) {
            return hardMaxContextCharBudget;
        }
        int budget = switch (queryType) {
            case TABLE_LOOKUP, COUNT_QUERY -> tableContextCharBudget;
            case LIST_ALL, SECTION_SUMMARY -> listContextCharBudget;
            default -> factContextCharBudget;
        };
        return Math.min(budget, hardMaxContextCharBudget);
    }

    public int hardMax() {
        return hardMaxContextCharBudget;
    }

    public void logBudget(int requestedTopN, int selectedContexts, int contextChars, boolean budgetLimited) {
        log.info("[RAG][budget] requestedTopN={} selectedContexts={} contextChars={} budgetLimited={}",
                requestedTopN, selectedContexts, contextChars, budgetLimited);
    }
}
