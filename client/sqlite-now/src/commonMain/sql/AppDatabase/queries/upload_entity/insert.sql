-- @@{ queryResult=UploadEntityRow }
INSERT INTO upload_entity (
    collection,
    id,
    data,
    pathHash
)
VALUES (
    :collection,
    :id,
    :data,
    :pathHash
)
ON CONFLICT(collection, id) DO UPDATE SET data = excluded.data, pathHash = excluded.pathHash
RETURNING
    collection,
    id,
    data,
    pathHash;
