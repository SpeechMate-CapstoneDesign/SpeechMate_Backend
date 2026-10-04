-- 측정용 시드 (MySQL). 앱이 재시작할 때마다 다시 실행되므로 INSERT IGNORE.
INSERT IGNORE INTO user (id, provider, oid, created_at, modified_at) VALUES (1, 'KAKAO', 'measure-user-1', NOW(), NOW());
INSERT IGNORE INTO speech (id, user_id, file_url, title, non_verbal_status, created_at, modified_at)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n + 1 FROM seq WHERE n < 3000)
SELECT n, 1, CONCAT('measure/', n, '.mp4'), CONCAT('m-', n), 'NOT_STARTED', NOW(), NOW() FROM seq;
