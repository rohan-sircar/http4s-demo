docker run  \
-e POSTGRES_DB=test_db \
-e POSTGRES_USER=test_user \
-e POSTGRES_PASSWORD=password \
-p 5433:5432 \
postgres:12