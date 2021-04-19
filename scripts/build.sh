# export POSTGRES_DB=codegen_db
export CODEGEN_DB_HOST=localhost
export CODEGEN_DB_NAME=codegen_db
export CODEGEN_DB_USER=codegen_user
export CODEGEN_DB_PASSWORD=password
export CODEGEN_DB_PORT=5435

cid=$(docker run \
-e POSTGRES_DB=$CODEGEN_DB_NAME \
-e POSTGRES_USER=$CODEGEN_DB_USER \
-e POSTGRES_PASSWORD=$CODEGEN_DB_PASSWORD \
-p $CODEGEN_DB_PORT:5432 \
-d postgres:12)

echo "Container id is $cid"
sleep 5s
# ./wait-for-it.sh localhost:5434 -s -t 300 -- echo "db started"
sbtn flyway/flywayMigrate
# needs docker login
sbtn docker:publish
sbtn shutdown

docker stop $cid
docker rm $cid