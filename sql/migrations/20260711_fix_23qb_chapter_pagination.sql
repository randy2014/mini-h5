UPDATE crawler_source_config
SET config_json = JSON_SET(
    config_json,
    '$.chapterRules.maxPages',
    8
  ),
  updated_at = NOW()
WHERE source_code = '23qb_public';
