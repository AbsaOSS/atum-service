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

CREATE OR REPLACE FUNCTION runs._partitioning_matches_search_text(
    IN i_keys_to_values JSONB,
    IN i_search_pattern TEXT
) RETURNS BOOLEAN AS
$$
-------------------------------------------------------------------------------
-- Internal helper for runs.search_partitionings: returns TRUE when any key name or value
-- in i_keys_to_values matches the pre-escaped ILIKE pattern i_search_pattern.
-------------------------------------------------------------------------------
    SELECT EXISTS (
        SELECT 1
        FROM pg_catalog.jsonb_each_text(i_keys_to_values) KV
        WHERE KV.key ILIKE i_search_pattern ESCAPE '\'
           OR KV.value ILIKE i_search_pattern ESCAPE '\'
    );
$$
LANGUAGE sql STABLE
SET search_path = pg_catalog, pg_temp;

ALTER FUNCTION runs._partitioning_matches_search_text(JSONB, TEXT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs._partitioning_matches_search_text(JSONB, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION runs._partitioning_matches_search_text(JSONB, TEXT) TO atum_owner;

CREATE OR REPLACE FUNCTION runs.search_partitionings(
    IN i_limit INT DEFAULT 10,
    IN i_offset BIGINT DEFAULT 0,
    IN i_keys JSONB DEFAULT NULL,
    IN i_from_time TIMESTAMPTZ DEFAULT NULL,
    IN i_to_time TIMESTAMPTZ DEFAULT NULL,
    IN i_latest_first BOOLEAN DEFAULT FALSE,
    IN i_exact_keys BOOLEAN DEFAULT FALSE,
    IN i_author TEXT DEFAULT NULL,
    IN i_search_text TEXT DEFAULT NULL,
    OUT status INTEGER,
    OUT status_text TEXT,
    OUT id BIGINT,
    OUT partitioning JSONB,
    OUT author TEXT,
    OUT has_more BOOLEAN
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
-- Searches partitionings by key/value containment, key existence (JSON null values in i_keys),
-- optional exact key-set matching (i_exact_keys = TRUE), registration author (i_author),
-- case-insensitive substring match on any key name or value (i_search_text), and/or the existence
-- of any checkpoint in [i_from_time, i_to_time). Matching is always independent of stored key order.
--
-- Missing time bounds are unbounded. Searches without time bounds include partitionings
-- without checkpoints. An empty keys object is unconstrained, so at least one non-empty filter
-- (key, author, search text, or time bound) is required; i_author must not be blank, i_search_text
-- requires at least 2 non-whitespace characters, and i_exact_keys cannot be TRUE when no keys are supplied.
--
-- Returns original partitioning envelopes ordered by ID (ascending by default, or descending when i_latest_first is
-- true), status 11/OK, and paging metadata. No matches (including offsets past the end) return no rows.
-- Invalid direct-call arguments return status 51 with a descriptive status_text.
--
-- Additive migration: rollback can drop this function after callers are removed;
-- the supporting indexes can be dropped concurrently without changing data.
-------------------------------------------------------------------------------
DECLARE
    _keys JSONB := nullif(i_keys, '{}'::JSONB);
    _kv_keys JSONB := NULL;
    _all_keys TEXT[] := NULL;
    _key_count INT := 0;
    _latest_first BOOLEAN := coalesce(i_latest_first, FALSE);
    _exact_keys BOOLEAN := coalesce(i_exact_keys, FALSE);
    _has_time BOOLEAN := (i_from_time IS NOT NULL OR i_to_time IS NOT NULL);
    _search_pattern TEXT := NULL;
    -- Sentinel bounds turn optional [from, to) filters into pure range predicates (no OR), keeping index scans
    -- viable under cached generic plans (assumes checkpoints.process_start_time never stores +/-infinity).
    _from TIMESTAMPTZ := coalesce(i_from_time, '-infinity'::TIMESTAMPTZ);
    _to TIMESTAMPTZ := coalesce(i_to_time, 'infinity'::TIMESTAMPTZ);
BEGIN
    IF i_limit IS NULL OR i_limit < 1 OR i_limit > 1000 THEN
        status := 51;
        status_text := 'i_limit must be between 1 and 1000 and must not be NULL';
        RETURN NEXT;
        RETURN;
    END IF;
    IF i_offset IS NULL OR i_offset < 0 THEN
        status := 51;
        status_text := 'i_offset must be nonnegative and must not be NULL';
        RETURN NEXT;
        RETURN;
    END IF;
    IF i_keys IS NOT NULL THEN
        IF jsonb_typeof(i_keys) <> 'object' THEN
            status := 51;
            status_text := 'i_keys must be a JSON object of string or null values';
            RETURN NEXT;
            RETURN;
        END IF;
        IF EXISTS (SELECT 1 FROM jsonb_each(i_keys) K WHERE jsonb_typeof(K.value) NOT IN ('string', 'null')) THEN
            status := 51;
            status_text := 'i_keys must be a JSON object of string or null values';
            RETURN NEXT;
            RETURN;
        END IF;
    END IF;
    IF i_author IS NOT NULL AND btrim(i_author, E' \t\n\r\f' || chr(11)) = '' THEN
        status := 51;
        status_text := 'i_author must not be blank';
        RETURN NEXT;
        RETURN;
    END IF;
    IF i_search_text IS NOT NULL AND character_length(btrim(i_search_text, E' \t\n\r\f' || chr(11))) < 2 THEN
        status := 51;
        status_text := 'i_search_text must contain at least 2 non-whitespace characters';
        RETURN NEXT;
        RETURN;
    END IF;
    IF i_from_time IS NOT NULL AND i_to_time IS NOT NULL AND i_from_time >= i_to_time THEN
        status := 51;
        status_text := 'i_from_time must be earlier than i_to_time';
        RETURN NEXT;
        RETURN;
    END IF;
    IF _keys IS NULL AND i_from_time IS NULL AND i_to_time IS NULL AND i_author IS NULL AND i_search_text IS NULL THEN
        status := 51;
        status_text := 'At least one key/value, author, search text or checkpoint time bound is required';
        RETURN NEXT;
        RETURN;
    END IF;
    IF _exact_keys AND _keys IS NULL THEN
        status := 51;
        status_text := 'i_exact_keys requires at least one key in i_keys';
        RETURN NEXT;
        RETURN;
    END IF;

    IF _keys IS NOT NULL THEN
        _kv_keys := nullif(jsonb_strip_nulls(_keys), '{}'::JSONB);
        _all_keys := ARRAY(SELECT jsonb_object_keys(_keys));
        _key_count := coalesce(cardinality(_all_keys), 0);
    END IF;
    IF i_search_text IS NOT NULL THEN
        _search_pattern := '%' || replace(replace(replace(i_search_text, '\', '\\'), '%', '\%'), '_', '\_') || '%';
    END IF;

    -- Static IF/ELSIF branches (instead of dynamic RETURN QUERY EXECUTE/$query$ or a single IS NULL OR query)
    -- keep SQL compile-time checked and readable while giving each access path (time-based without keys,
    -- author without keys or time, standalone search_text, key-value/mixed, key-only, with or without time)
    -- its own plan without empty @> '{}' GIN full-index scans or OR-blocked generic index plans.
    -- Trade-off: CASE-based ORDER BY (matching runs.get_partitioning_checkpoints) avoids duplicating
    -- ASC/DESC queries, at the cost of sorting all key-matching rows before LIMIT on very broad key filters.
    IF _keys IS NULL AND _has_time THEN
        -- Scalar primary-key lookups prevent an ID-ordered merge join from scanning unrelated history.
        -- Exclude orphan checkpoints and apply optional author/search_text filters before paging;
        -- load full partitioning rows only for the selected IDs.
        RETURN QUERY
            WITH active_ids AS MATERIALIZED (
                SELECT DISTINCT C.fk_partitioning
                FROM runs.checkpoints C
                WHERE C.process_start_time >= _from
                  AND C.process_start_time < _to
            ),
            page_ids AS MATERIALIZED (
                SELECT A.fk_partitioning
                FROM active_ids A
                WHERE EXISTS (
                    SELECT 1
                    FROM runs.partitionings P
                    WHERE P.id_partitioning = A.fk_partitioning
                      AND (i_author IS NULL OR P.created_by = i_author)
                      AND (
                          _search_pattern IS NULL
                          OR runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
                      )
                )
                ORDER BY CASE WHEN _latest_first THEN A.fk_partitioning END DESC,
                         CASE WHEN NOT _latest_first THEN A.fk_partitioning END ASC
                LIMIT i_limit + 1 OFFSET i_offset
            ),
            page_partitionings AS MATERIALIZED (
                SELECT (SELECT P FROM runs.partitionings P
                        WHERE P.id_partitioning = I.fk_partitioning) AS partitioning_row
                FROM page_ids I
            )
            SELECT 11, 'OK'::TEXT, (P.partitioning_row).id_partitioning,
                   (P.partitioning_row).partitioning, (P.partitioning_row).created_by,
                   count(*) OVER () > i_limit
            FROM page_partitionings P
            ORDER BY CASE WHEN _latest_first THEN (P.partitioning_row).id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN (P.partitioning_row).id_partitioning END ASC
            LIMIT i_limit;
    ELSIF _keys IS NULL AND i_author IS NOT NULL THEN
        -- Direct equality predicate on created_by (no OR) keeps partitionings_created_by_idx usable
        -- even under cached generic plans.
        RETURN QUERY
            WITH candidates AS MATERIALIZED (
                SELECT P.id_partitioning, P.partitioning, P.created_by
                FROM runs.partitionings P
                WHERE P.created_by = i_author
                  AND (
                      _search_pattern IS NULL
                      OR runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
                  )
                ORDER BY CASE WHEN _latest_first THEN P.id_partitioning END DESC,
                         CASE WHEN NOT _latest_first THEN P.id_partitioning END ASC
                LIMIT i_limit + 1 OFFSET i_offset
            )
            SELECT 11, 'OK'::TEXT, C.id_partitioning, C.partitioning, C.created_by,
                   count(*) OVER () > i_limit
            FROM candidates C
            ORDER BY CASE WHEN _latest_first THEN C.id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN C.id_partitioning END ASC
            LIMIT i_limit;
    ELSIF _keys IS NULL THEN
        RETURN QUERY
            WITH candidates AS MATERIALIZED (
                SELECT P.id_partitioning, P.partitioning, P.created_by
                FROM runs.partitionings P
                WHERE runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
                ORDER BY CASE WHEN _latest_first THEN P.id_partitioning END DESC,
                         CASE WHEN NOT _latest_first THEN P.id_partitioning END ASC
                LIMIT i_limit + 1 OFFSET i_offset
            )
            SELECT 11, 'OK'::TEXT, C.id_partitioning, C.partitioning, C.created_by,
                   count(*) OVER () > i_limit
            FROM candidates C
            ORDER BY CASE WHEN _latest_first THEN C.id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN C.id_partitioning END ASC
            LIMIT i_limit;
    ELSIF _kv_keys IS NOT NULL AND NOT _has_time THEN
        RETURN QUERY
            WITH candidates AS MATERIALIZED (
                SELECT P.id_partitioning, P.partitioning, P.created_by
                FROM runs.partitionings P
                WHERE (P.partitioning -> 'keysToValuesMap') @> _kv_keys
                  AND (P.partitioning -> 'keysToValuesMap') ?& _all_keys
                  AND (NOT _exact_keys OR jsonb_array_length(P.partitioning -> 'keys') = _key_count)
                  AND (i_author IS NULL OR P.created_by = i_author)
                  AND (
                      _search_pattern IS NULL
                      OR runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
                  )
                ORDER BY CASE WHEN _latest_first THEN P.id_partitioning END DESC,
                         CASE WHEN NOT _latest_first THEN P.id_partitioning END ASC
                LIMIT i_limit + 1 OFFSET i_offset
            )
            SELECT 11, 'OK'::TEXT, C.id_partitioning, C.partitioning, C.created_by,
                   count(*) OVER () > i_limit
            FROM candidates C
            ORDER BY CASE WHEN _latest_first THEN C.id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN C.id_partitioning END ASC
            LIMIT i_limit;
    ELSIF _kv_keys IS NOT NULL THEN
        RETURN QUERY
            WITH candidates AS MATERIALIZED (
                SELECT P.id_partitioning, P.partitioning, P.created_by
                FROM runs.partitionings P
                WHERE (P.partitioning -> 'keysToValuesMap') @> _kv_keys
                  AND (P.partitioning -> 'keysToValuesMap') ?& _all_keys
                  AND (NOT _exact_keys OR jsonb_array_length(P.partitioning -> 'keys') = _key_count)
                  AND (i_author IS NULL OR P.created_by = i_author)
                  AND (
                      _search_pattern IS NULL
                      OR runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM runs.checkpoints C
                      WHERE C.fk_partitioning = P.id_partitioning
                        AND C.process_start_time >= _from
                        AND C.process_start_time < _to
                  )
                ORDER BY CASE WHEN _latest_first THEN P.id_partitioning END DESC,
                         CASE WHEN NOT _latest_first THEN P.id_partitioning END ASC
                LIMIT i_limit + 1 OFFSET i_offset
            )
            SELECT 11, 'OK'::TEXT, C.id_partitioning, C.partitioning, C.created_by,
                   count(*) OVER () > i_limit
            FROM candidates C
            ORDER BY CASE WHEN _latest_first THEN C.id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN C.id_partitioning END ASC
            LIMIT i_limit;
    ELSIF NOT _has_time THEN
        RETURN QUERY
            WITH candidates AS MATERIALIZED (
                SELECT P.id_partitioning, P.partitioning, P.created_by
                FROM runs.partitionings P
                WHERE (P.partitioning -> 'keysToValuesMap') ?& _all_keys
                  AND (NOT _exact_keys OR jsonb_array_length(P.partitioning -> 'keys') = _key_count)
                  AND (i_author IS NULL OR P.created_by = i_author)
                  AND (
                      _search_pattern IS NULL
                      OR runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
                  )
                ORDER BY CASE WHEN _latest_first THEN P.id_partitioning END DESC,
                         CASE WHEN NOT _latest_first THEN P.id_partitioning END ASC
                LIMIT i_limit + 1 OFFSET i_offset
            )
            SELECT 11, 'OK'::TEXT, C.id_partitioning, C.partitioning, C.created_by,
                   count(*) OVER () > i_limit
            FROM candidates C
            ORDER BY CASE WHEN _latest_first THEN C.id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN C.id_partitioning END ASC
            LIMIT i_limit;
    ELSE
        RETURN QUERY
            WITH candidates AS MATERIALIZED (
                SELECT P.id_partitioning, P.partitioning, P.created_by
                FROM runs.partitionings P
                WHERE (P.partitioning -> 'keysToValuesMap') ?& _all_keys
                  AND (NOT _exact_keys OR jsonb_array_length(P.partitioning -> 'keys') = _key_count)
                  AND (i_author IS NULL OR P.created_by = i_author)
                  AND (
                      _search_pattern IS NULL
                      OR runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM runs.checkpoints C
                      WHERE C.fk_partitioning = P.id_partitioning
                        AND C.process_start_time >= _from
                        AND C.process_start_time < _to
                  )
                ORDER BY CASE WHEN _latest_first THEN P.id_partitioning END DESC,
                         CASE WHEN NOT _latest_first THEN P.id_partitioning END ASC
                LIMIT i_limit + 1 OFFSET i_offset
            )
            SELECT 11, 'OK'::TEXT, C.id_partitioning, C.partitioning, C.created_by,
                   count(*) OVER () > i_limit
            FROM candidates C
            ORDER BY CASE WHEN _latest_first THEN C.id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN C.id_partitioning END ASC
            LIMIT i_limit;
    END IF;
END;
$$
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp;

ALTER FUNCTION runs.search_partitionings(INT, BIGINT, JSONB, TIMESTAMPTZ, TIMESTAMPTZ, BOOLEAN, BOOLEAN, TEXT, TEXT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs.search_partitionings(INT, BIGINT, JSONB, TIMESTAMPTZ, TIMESTAMPTZ, BOOLEAN, BOOLEAN, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION runs.search_partitionings(INT, BIGINT, JSONB, TIMESTAMPTZ, TIMESTAMPTZ, BOOLEAN, BOOLEAN, TEXT, TEXT) TO atum_user;
