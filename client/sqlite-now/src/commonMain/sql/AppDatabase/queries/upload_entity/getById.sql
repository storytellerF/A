-- @@{ queryResult=UploadEntityRow }
SELECT
    collection,
    id,
    data,
    pathHash
FROM upload_entity
WHERE collection = :collection AND id = :id;
