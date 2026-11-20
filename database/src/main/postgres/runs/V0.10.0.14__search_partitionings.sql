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
    IN i_keys JSONB DEFAULT NULL,
    IN i_exact_keys BOOLEAN DEFAULT FALSE,
    IN i_author TEXT DEFAULT NULL,
    IN i_search_text TEXT DEFAULT NULL,
    IN i_from_time TIMESTAMPTZ DEFAULT NULL,
    IN i_to_time TIMESTAMPTZ DEFAULT NULL,
    IN i_latest_first BOOLEAN DEFAULT FALSE,
    IN i_limit INT DEFAULT 10,
    IN i_offset BIGINT DEFAULT 0,
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
    _page_ids BIGINT[];
BEGIN
    -- 1. Validate inputs and reject missing/incompatible filter combinations with status 51.
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

    -- 2. Normalize key filters (_kv_keys for @>, _all_keys for ?&) and escape substring ILIKE pattern.
    IF _keys IS NOT NULL THEN
        _kv_keys := nullif(jsonb_strip_nulls(_keys), '{}'::JSONB);
        _all_keys := ARRAY(SELECT jsonb_object_keys(_keys));
        _key_count := coalesce(cardinality(_all_keys), 0);
    END IF;
    IF i_search_text IS NOT NULL THEN
        _search_pattern := '%' || replace(replace(replace(i_search_text, '\', '\\'), '%', '\%'), '_', '\_') || '%';
    END IF;

    -- 3. Select up to i_limit + 1 ordered IDs using a static branch per index access path
    --    (avoids generic-plan OR traps and empty @> '{}' GIN scans), then hydrate rows once below.
    IF _keys IS NULL AND _has_time THEN
        -- No keys + time window: drive from checkpoints time range, then filter author/search_text.
        WITH active_ids AS MATERIALIZED (
            SELECT DISTINCT C.fk_partitioning
            FROM runs.checkpoints C
            WHERE C.process_start_time >= _from
              AND C.process_start_time < _to
        )
        SELECT ARRAY(
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
        )
        INTO _page_ids;
    ELSIF _keys IS NULL AND i_author IS NOT NULL THEN
        -- No keys, no time + author: drive from partitionings_created_by_idx equality predicate.
        _page_ids := ARRAY(
            SELECT P.id_partitioning
            FROM runs.partitionings P
            WHERE P.created_by = i_author
              AND (
                  _search_pattern IS NULL
                  OR runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
              )
            ORDER BY CASE WHEN _latest_first THEN P.id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN P.id_partitioning END ASC
            LIMIT i_limit + 1 OFFSET i_offset
        );
    ELSIF _keys IS NULL THEN
        -- Standalone search_text only (no keys, time, or author): scan and filter via helper.
        _page_ids := ARRAY(
            SELECT P.id_partitioning
            FROM runs.partitionings P
            WHERE runs._partitioning_matches_search_text(P.partitioning -> 'keysToValuesMap', _search_pattern)
            ORDER BY CASE WHEN _latest_first THEN P.id_partitioning END DESC,
                     CASE WHEN NOT _latest_first THEN P.id_partitioning END ASC
            LIMIT i_limit + 1 OFFSET i_offset
        );
    ELSIF _kv_keys IS NOT NULL AND NOT _has_time THEN
        -- Key/value (or mixed) without time: GIN @> _kv_keys + ?& _all_keys (+ optional exact/author/text).
        _page_ids := ARRAY(
            SELECT P.id_partitioning
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
        );
    ELSIF _kv_keys IS NOT NULL THEN
        -- Key/value (or mixed) with time: GIN @> / ?& filters plus checkpoint time window EXISTS.
        _page_ids := ARRAY(
            SELECT P.id_partitioning
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
        );
    ELSIF NOT _has_time THEN
        -- Key-existence only (all i_keys values are null) without time: GIN ?& _all_keys without empty @>.
        _page_ids := ARRAY(
            SELECT P.id_partitioning
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
        );
    ELSE
        -- Key-existence only with time: GIN ?& _all_keys plus checkpoint time window EXISTS.
        _page_ids := ARRAY(
            SELECT P.id_partitioning
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
        );
    END IF;

    -- 4. Fetch full partitioning rows for up to i_limit IDs, preserving branch sort order and computing has_more.
    RETURN QUERY
        SELECT 11, 'OK'::TEXT, P.id_partitioning, P.partitioning, P.created_by,
               (coalesce(cardinality(_page_ids), 0) > i_limit)
        FROM unnest(_page_ids) WITH ORDINALITY AS U(id_partitioning, ord)
        JOIN runs.partitionings P ON P.id_partitioning = U.id_partitioning
        WHERE U.ord <= i_limit
        ORDER BY U.ord;
END;
$$
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp;

ALTER FUNCTION runs.search_partitionings(JSONB, BOOLEAN, TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, BOOLEAN, INT, BIGINT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs.search_partitionings(JSONB, BOOLEAN, TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, BOOLEAN, INT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION runs.search_partitionings(JSONB, BOOLEAN, TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, BOOLEAN, INT, BIGINT) TO atum_user;
