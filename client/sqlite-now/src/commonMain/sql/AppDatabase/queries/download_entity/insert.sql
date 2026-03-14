-- @@{ queryResult=DownloadEntityRow }
INSERT INTO download_entity (
    collection,
    id,
    fileId,
    data
)
VALUES (
    :collection,
    :id,
    :fileId,
    :data
)
ON CONFLICT(collection, id) DO UPDATE SET data = excluded.data, fileId = excluded.fileId
RETURNING
    collection,
    id,
    fileId,
    data;
