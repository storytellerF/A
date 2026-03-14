CREATE TABLE upload_entity (
    collection TEXT NOT NULL,
    id TEXT NOT NULL,
    data TEXT NOT NULL,
    pathHash TEXT NOT NULL,
    PRIMARY KEY (collection, id),
    UNIQUE (collection, pathHash)
);
