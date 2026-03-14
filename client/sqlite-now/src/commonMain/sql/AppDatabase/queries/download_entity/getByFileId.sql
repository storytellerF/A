-- @@{ queryResult=DownloadEntityRow }
SELECT
    collection,
    id,
    fileId,
    data
FROM download_entity
WHERE collection = :collection AND fileId = :fileId;
