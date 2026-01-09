## Deployment

How to set up database for local testing

### Using Docker

```zsh
# start up postgres docker container (optional; instead you can create atum_db on your local postgres instance)
docker run --name=atum_db -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=atum_db -p 5432:5432 -d postgres:16

# migrate scripts
sbt flywayMigrate

# kill & remove docker container (optional; only if using dockerized postgres instance)
docker kill atum_db
docker rm atum_db
```

### Using local postgres instance
- create database `atum_db`
- migrate scripts
```zsh
sbt flywayMigrate
```

In case some structures are already present in the database, you can use
```zsh
sbt flywayClean 
```
to remove them or 
```zsh
sbt flywayBaseline 
```
to set the current state as the baseline.
