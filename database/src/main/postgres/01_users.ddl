/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



DO
$do$
    BEGIN
        IF EXISTS (
                SELECT FROM pg_catalog.pg_roles
                WHERE  rolname = 'atum_owner') THEN

            RAISE NOTICE 'Role "atum_owner" already exists. Skipping.';
        ELSE
            CREATE ROLE atum_owner WITH
                LOGIN
                NOSUPERUSER
                INHERIT
                NOCREATEDB
                NOCREATEROLE
                NOREPLICATION
                PASSWORD 'changeme';
        END IF;
    END;
$do$;


DO
$do$
    BEGIN
        IF EXISTS (
                SELECT FROM pg_catalog.pg_roles
                WHERE  rolname = 'atum_user') THEN

            RAISE NOTICE 'Role "atum_user" already exists. Skipping.';
        ELSE
            CREATE ROLE atum_user WITH
                LOGIN
                NOSUPERUSER
                INHERIT
                NOCREATEDB
                NOCREATEROLE
                NOREPLICATION
                PASSWORD 'changeme';
        END IF;
    END
$do$;


DO
$do$
    BEGIN
        IF EXISTS (
                SELECT FROM pg_catalog.pg_roles
                WHERE  rolname = 'atum_configurator') THEN

            RAISE NOTICE 'Role "atum_configurator" already exists. Skipping.';
        ELSE
            CREATE ROLE atum_configurator WITH
                LOGIN
                NOSUPERUSER
                INHERIT
                NOCREATEDB
                NOCREATEROLE
                NOREPLICATION
                PASSWORD 'changeme';
        END IF;
    END
$do$;
