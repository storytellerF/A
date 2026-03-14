-- @@{ queryResult=UploadEntityRow }
SELECT
    collection,
    id,
    data,
    pathHash
FROM upload_entity
WHERE collection = :collection
ORDER BY id DESC
LIMIT :limit OFFSET :offset;
