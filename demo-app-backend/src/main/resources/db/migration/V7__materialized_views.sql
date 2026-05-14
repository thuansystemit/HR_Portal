CREATE MATERIALIZED VIEW mv_upload_trend AS
SELECT
    dc.id                                AS category_id,
    dc.name                              AS category_name,
    date_trunc('month', d.uploaded_at)   AS month,
    count(*)                             AS upload_count
FROM documents d
JOIN document_categories dc ON dc.id = d.category_id
WHERE d.deleted_at    IS NULL
  AND d.upload_status = 'committed'
GROUP BY dc.id, dc.name, date_trunc('month', d.uploaded_at)
WITH DATA;

CREATE UNIQUE INDEX mv_upload_trend_uq
    ON mv_upload_trend (category_id, month);

CREATE MATERIALIZED VIEW mv_storage_by_category AS
SELECT
    dc.id                                AS category_id,
    dc.name                              AS category_name,
    count(d.id)                          AS document_count,
    coalesce(sum(d.size_bytes), 0)       AS total_bytes
FROM document_categories dc
LEFT JOIN documents d
       ON d.category_id   = dc.id
      AND d.deleted_at    IS NULL
      AND d.upload_status = 'committed'
WHERE dc.deleted_at IS NULL
GROUP BY dc.id, dc.name
WITH DATA;

CREATE UNIQUE INDEX mv_storage_by_category_uq
    ON mv_storage_by_category (category_id);
