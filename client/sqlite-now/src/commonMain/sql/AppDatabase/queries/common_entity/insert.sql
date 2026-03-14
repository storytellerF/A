-- @@{ queryResult=CommonEntityRow }
INSERT INTO common_entity (
    collection,
    id,
    data
)
VALUES (
    :collection,
    :id,
    :data
)
ON CONFLICT(collection, id) DO UPDATE SET data = excluded.data
RETURNING
    collection,
    id,
    data;
