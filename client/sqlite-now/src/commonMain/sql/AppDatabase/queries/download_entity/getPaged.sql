-- @@{ queryResult=DownloadEntityRow }
SELECT
    collection,
    id,
    fileId,
    data
FROM download_entity
WHERE collection = :collection
ORDER BY id DESC
LIMIT :limit OFFSET :offset;
