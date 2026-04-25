CREATE TABLE document_sections (
  id BINARY(16) PRIMARY KEY,
  document_id BINARY(16) NOT NULL,
  widget_config_id BINARY(16) NOT NULL,

  section_key VARCHAR(64) NOT NULL,
  parent_section_key VARCHAR(64) NULL,

  title VARCHAR(500) NOT NULL,
  heading_path_text VARCHAR(1000) NULL,

  page_start INT NULL,
  page_end INT NULL,
  order_index INT NOT NULL,

  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) NULL,

  CONSTRAINT fk_document_sections_document
    FOREIGN KEY (document_id) REFERENCES documents(id),

  CONSTRAINT fk_document_sections_widget
    FOREIGN KEY (widget_config_id) REFERENCES widget_configs(id),

  CONSTRAINT uq_document_sections_doc_section_key
    UNIQUE (document_id, section_key)
);

CREATE INDEX idx_document_sections_widget
  ON document_sections(widget_config_id);

CREATE INDEX idx_document_sections_document_order
  ON document_sections(document_id, order_index);

CREATE INDEX idx_document_sections_widget_section_key
  ON document_sections(widget_config_id, section_key);

CREATE TABLE document_tables (
  id BINARY(16) PRIMARY KEY,
  document_id BINARY(16) NOT NULL,
  widget_config_id BINARY(16) NOT NULL,
  section_id BINARY(16) NULL,

  table_key VARCHAR(64) NOT NULL,
  section_key VARCHAR(64) NULL,

  title VARCHAR(500) NULL,
  page_start INT NULL,
  page_end INT NULL,
  order_index INT NOT NULL,

  markdown_content LONGTEXT NOT NULL,
  json_content JSON NULL,

  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) NULL,

  CONSTRAINT fk_document_tables_document
    FOREIGN KEY (document_id) REFERENCES documents(id),

  CONSTRAINT fk_document_tables_widget
    FOREIGN KEY (widget_config_id) REFERENCES widget_configs(id),

  CONSTRAINT fk_document_tables_section
    FOREIGN KEY (section_id) REFERENCES document_sections(id),

  CONSTRAINT uq_document_tables_doc_table_key
    UNIQUE (document_id, table_key)
);

CREATE INDEX idx_document_tables_widget
  ON document_tables(widget_config_id);

CREATE INDEX idx_document_tables_document_order
  ON document_tables(document_id, order_index);

CREATE INDEX idx_document_tables_section_key
  ON document_tables(widget_config_id, section_key);

CREATE INDEX idx_document_tables_table_key
  ON document_tables(widget_config_id, table_key);

ALTER TABLE document_chunks
  ADD COLUMN widget_config_id BINARY(16) NULL,
  ADD COLUMN section_db_id BINARY(16) NULL,
  ADD COLUMN table_db_id BINARY(16) NULL,

  ADD COLUMN section_id VARCHAR(64) NULL,
  ADD COLUMN parent_id VARCHAR(64) NULL,
  ADD COLUMN table_id VARCHAR(64) NULL,

  ADD COLUMN chunk_type VARCHAR(50) NOT NULL DEFAULT 'text',
  ADD COLUMN section_title VARCHAR(500) NULL,
  ADD COLUMN heading_path_text VARCHAR(1000) NULL,

  ADD COLUMN page_start INT NULL,
  ADD COLUMN page_end INT NULL,
  ADD COLUMN order_index INT NULL,

  ADD COLUMN prev_chunk_id BINARY(16) NULL,
  ADD COLUMN next_chunk_id BINARY(16) NULL,

  ADD COLUMN token_count INT NULL,
  ADD COLUMN source_file VARCHAR(255) NULL;

UPDATE document_chunks dc
JOIN documents d ON dc.document_id = d.id
SET dc.widget_config_id = d.widget_config_id
WHERE dc.widget_config_id IS NULL;

ALTER TABLE document_chunks
  ADD CONSTRAINT fk_document_chunks_widget_config
  FOREIGN KEY (widget_config_id) REFERENCES widget_configs(id);

ALTER TABLE document_chunks
  ADD CONSTRAINT fk_document_chunks_section_db
  FOREIGN KEY (section_db_id) REFERENCES document_sections(id);

ALTER TABLE document_chunks
  ADD CONSTRAINT fk_document_chunks_table_db
  FOREIGN KEY (table_db_id) REFERENCES document_tables(id);

ALTER TABLE document_chunks
  ADD CONSTRAINT fk_document_chunks_prev
  FOREIGN KEY (prev_chunk_id) REFERENCES document_chunks(id);

ALTER TABLE document_chunks
  ADD CONSTRAINT fk_document_chunks_next
  FOREIGN KEY (next_chunk_id) REFERENCES document_chunks(id);

CREATE INDEX idx_document_chunks_widget_section
  ON document_chunks(widget_config_id, section_id);

CREATE INDEX idx_document_chunks_widget_table
  ON document_chunks(widget_config_id, table_id);

CREATE INDEX idx_document_chunks_document_order
  ON document_chunks(document_id, order_index);

CREATE INDEX idx_document_chunks_parent
  ON document_chunks(parent_id);

CREATE INDEX idx_document_chunks_widget_chunk_type
  ON document_chunks(widget_config_id, chunk_type);

CREATE INDEX idx_document_chunks_section_db
  ON document_chunks(section_db_id);

CREATE INDEX idx_document_chunks_table_db
  ON document_chunks(table_db_id);
