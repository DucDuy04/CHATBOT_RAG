package KLTN.RAG_CHATBOT_BE.domain.widget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WidgetConfigRepository extends JpaRepository<WidgetConfig, UUID> {
    Optional<WidgetConfig> findByApiKey(UUID apiKey);

    /**
     * Paginated search for admin chatbot list. Native query so JSON filters work on MySQL;
     * {@code deleted_at IS NULL} is explicit because some native paths bypass
     * {@link org.hibernate.annotations.SQLRestriction}.
     */
    @Query(
            value = """
                    SELECT w.* FROM widget_configs w
                    WHERE w.deleted_at IS NULL
                    AND (
                      :search = ''
                      OR LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%'))
                      OR LOWER(IFNULL(JSON_UNQUOTE(JSON_EXTRACT(w.ui_config, '$.description')), ''))
                         LIKE LOWER(CONCAT('%', :search, '%'))
                    )
                    AND (
                      :status = ''
                      OR (:status = 'ACTIVE' AND w.is_active = 1)
                      OR (:status = 'INACTIVE' AND w.is_active = 0)
                    )
                    AND (
                      :domain = ''
                      OR IFNULL(JSON_UNQUOTE(JSON_EXTRACT(w.ui_config, '$.domain')), '') = :domain
                    )
                    ORDER BY w.updated_at DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM widget_configs w
                    WHERE w.deleted_at IS NULL
                    AND (
                      :search = ''
                      OR LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%'))
                      OR LOWER(IFNULL(JSON_UNQUOTE(JSON_EXTRACT(w.ui_config, '$.description')), ''))
                         LIKE LOWER(CONCAT('%', :search, '%'))
                    )
                    AND (
                      :status = ''
                      OR (:status = 'ACTIVE' AND w.is_active = 1)
                      OR (:status = 'INACTIVE' AND w.is_active = 0)
                    )
                    AND (
                      :domain = ''
                      OR IFNULL(JSON_UNQUOTE(JSON_EXTRACT(w.ui_config, '$.domain')), '') = :domain
                    )
                    """,
            nativeQuery = true)
    Page<WidgetConfig> searchChatbots(
            @Param("search") String search,
            @Param("status") String status,
            @Param("domain") String domain,
            Pageable pageable);
}
