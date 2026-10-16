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

CREATE OR REPLACE FUNCTION runs.search_partitionings(
    IN i_limit INT DEFAULT 10,
    IN i_offset BIGINT DEFAULT 0,
    IN i_keys JSONB DEFAULT NULL,
    IN i_from_time TIMESTAMPTZ DEFAULT NULL,
    IN i_to_time TIMESTAMPTZ DEFAULT NULL,
    OUT status INTEGER,
    OUT status_text TEXT,
    OUT id BIGINT,
    OUT partitioning JSONB,
    OUT author TEXT,
    OUT has_more BOOLEAN
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
-- Searches partitionings by exact key/value containment and/or the existence of
-- any checkpoint in [i_from_time, i_to_time). Missing time bounds are unbounded.
-- Keys-only searches include partitionings without checkpoints. An empty keys
-- object is unconstrained, so at least one time bound is then required.
--
-- Returns original partitioning envelopes in ID ascending order, status 11/OK,
-- and paging metadata. No matches (including offsets past the end) return no rows.
-- Invalid direct-call arguments raise invalid_parameter_value (SQLSTATE 22023).
--
-- Additive migration: rollback can drop this function after callers are removed;
-- the supporting indexes can be dropped concurrently without changing data.
-------------------------------------------------------------------------------
DECLARE
    _keys JSONB := nullif(i_keys, '{}'::JSONB);
    _predicates TEXT := '';
BEGIN
    IF i_limit IS NULL OR i_limit < 1 OR i_limit > 1000 THEN
        RAISE EXCEPTION 'i_limit must be between 1 and 1000 and must not be NULL'
            USING ERRCODE = '22023';
    END IF;
    IF i_offset IS NULL OR i_offset < 0 THEN
        RAISE EXCEPTION 'i_offset must be nonnegative and must not be NULL'
            USING ERRCODE = '22023';
    END IF;
    IF i_keys IS NOT NULL THEN
        IF jsonb_typeof(i_keys) <> 'object' THEN
            RAISE EXCEPTION 'i_keys must be a JSON object of string values'
                USING ERRCODE = '22023';
        END IF;
        IF EXISTS (SELECT 1 FROM jsonb_each(i_keys) K WHERE jsonb_typeof(K.value) <> 'string') THEN
            RAISE EXCEPTION 'i_keys must be a JSON object of string values'
                USING ERRCODE = '22023';
        END IF;
    END IF;
    IF i_from_time IS NOT NULL AND i_to_time IS NOT NULL AND i_from_time >= i_to_time THEN
        RAISE EXCEPTION 'i_from_time must be earlier than i_to_time'
            USING ERRCODE = '22023';
    END IF;
    IF _keys IS NULL AND i_from_time IS NULL AND i_to_time IS NULL THEN
        RAISE EXCEPTION 'At least one key/value or checkpoint time bound is required'
            USING ERRCODE = '22023';
    END IF;

    -- Only fixed predicate fragments are concatenated; all input values stay bound.
    -- Replanning avoids a generic optional-filter plan that cannot use selective key/time indexes.
    IF _keys IS NOT NULL THEN
        _predicates := _predicates || ' AND (P.partitioning -> ''keysToValuesMap'') @> $3';
    END IF;
    IF i_from_time IS NOT NULL OR i_to_time IS NOT NULL THEN
        _predicates := _predicates ||
            ' AND EXISTS (SELECT 1 FROM runs.checkpoints C WHERE C.fk_partitioning = P.id_partitioning';
        IF i_from_time IS NOT NULL THEN
            _predicates := _predicates || ' AND C.process_start_time >= $4';
        END IF;
        IF i_to_time IS NOT NULL THEN
            _predicates := _predicates || ' AND C.process_start_time < $5';
        END IF;
        _predicates := _predicates || ')';
    END IF;

    RETURN QUERY EXECUTE
        $query$
        WITH candidates AS MATERIALIZED (
            SELECT P.id_partitioning, P.partitioning, P.created_by
            FROM runs.partitionings P
            WHERE TRUE
        $query$ || _predicates || $query$
            ORDER BY P.id_partitioning
            LIMIT $1 + 1 OFFSET $2
        )
        SELECT 11, 'OK'::TEXT, C.id_partitioning, C.partitioning, C.created_by,
               count(*) OVER () > $1
        FROM candidates C
        ORDER BY C.id_partitioning
        LIMIT $1
        $query$
        USING i_limit, i_offset, _keys, i_from_time, i_to_time;
END;
$$
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp;

ALTER FUNCTION runs.search_partitionings(INT, BIGINT, JSONB, TIMESTAMPTZ, TIMESTAMPTZ) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.search_partitionings(INT, BIGINT, JSONB, TIMESTAMPTZ, TIMESTAMPTZ) TO atum_user;
