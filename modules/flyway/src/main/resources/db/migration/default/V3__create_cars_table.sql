create table "cars" (
  "id" VARCHAR(255) PRIMARY KEY NOT NULL,
  "model" VARCHAR(1024) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NULL
);
