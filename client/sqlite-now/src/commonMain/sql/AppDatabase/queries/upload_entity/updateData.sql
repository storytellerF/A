UPDATE upload_entity
SET data = :data
WHERE collection = :collection AND id = :id;
