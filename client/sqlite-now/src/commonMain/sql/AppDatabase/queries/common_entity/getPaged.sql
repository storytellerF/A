-- @@{ queryResult=CommonEntityRow }
SELECT
    collection,
    id,
    data
FROM common_entity
WHERE collection = :collection
ORDER BY id ASC
LIMIT :limit OFFSET :offset;
