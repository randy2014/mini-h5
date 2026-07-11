USE mini_novel_crawler;

UPDATE crawl_source
SET rule_config_json = JSON_SET(
    COALESCE(rule_config_json, JSON_OBJECT()),
    '$.poc.metadataOnly',
    true
  ),
  updated_at = NOW()
WHERE source_code = 'xbookcn_authorized';
