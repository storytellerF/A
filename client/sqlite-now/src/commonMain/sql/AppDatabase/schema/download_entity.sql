CREATE TABLE download_entity (
    collection TEXT NOT NULL,
    id TEXT NOT NULL,
    fileId INTEGER NOT NULL,
    data TEXT NOT NULL,
    PRIMARY KEY (collection, id),
    UNIQUE (collection, fileId)
);
