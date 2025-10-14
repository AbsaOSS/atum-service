------------------------------------------------------------------------------
--
-- Version v1.0
--
-- This script creates a user `util_functions_owner`.
-- This user is not intended to log into the database. It just owns utility
-- functions, tables and other objects
--
-------------------------------------------------------------------------------
DO
$do$
    BEGIN
        IF EXISTS (SELECT
                   FROM pg_catalog.pg_roles
                   WHERE rolname = 'util_functions_owner') THEN

            RAISE NOTICE 'Role "util_functions_owner" already exists. Skipping.';
        ELSE
            CREATE ROLE util_functions_owner WITH
                PASSWORD 'postgres'
                NOSUPERUSER
                INHERIT
                NOCREATEDB
                NOCREATEROLE
                NOREPLICATION;
        END IF;
    END;
$do$;
