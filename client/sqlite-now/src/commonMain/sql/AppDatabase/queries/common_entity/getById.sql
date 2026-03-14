-- @@{ queryResult=CommonEntityRow }
SELECT
    collection,
    id,
    data
FROM common_entity
WHERE collection = :collection AND id = :id;
