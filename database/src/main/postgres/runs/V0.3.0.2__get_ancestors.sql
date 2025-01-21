/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE OR REPLACE FUNCTION runs.get_ancestors(
    IN i_id_partitioning    BIGINT,
    IN i_limit              INT DEFAULT 5,
    IN i_offset             BIGINT DEFAULT 0,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT ancestorid         BIGINT,
    OUT partitioning        JSONB,
    OUT author              TEXT,
    OUT has_more            BOOLEAN
)  RETURNS SETOF record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.get_ancestors(3)
--      Returns Ancestors' partition ID for the given id
--
-- Parameters:
--      i_id_partitioning   - id that we asking the Ancestors for
--      i_limit             - (optional) maximum number of partitionings to return, default is 5
--      i_offset            - (optional) offset to use for pagination, default is 0
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      ancestorid         - ID of Ancestor partition
--      partitioning        - partitioning data of ancestor
--      author              - author of the Ancestor partitioning
--      has_more            - Flag indicating if there are more partitionings available

-- Status codes:
--      11 - OK
--      41 - Partitioning not found
--      42 - Ancestor Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    partitionCreateAt TIMESTAMP;
    _has_more BOOLEAN;

BEGIN
    -- Check if the partitioning exists
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_id_partitioning;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    -- Get the creation timestamp of the partitioning
    SELECT created_at
    FROM runs.partitionings
    WHERE id_partitioning = i_id_partitioning
    INTO partitionCreateAt;

    -- Check if there are more partitionings than the limit
    SELECT count(*) > i_limit
    FROM flows.partitioning_to_flow PTF
    WHERE PTF.fk_flow IN (
        SELECT fk_flow
        FROM flows.partitioning_to_flow
        WHERE fk_partitioning = i_id_partitioning
    )
    LIMIT i_limit + 1 OFFSET i_offset
    INTO _has_more;

    -- Return the ancestors
    RETURN QUERY
        SELECT
            11 AS status,
            'OK' AS status_text,
            P.id_partitioning AS ancestorid,
            P.partitioning AS partitioning,
            P.created_by AS author,
            _has_more AS has_more
        FROM
            runs.partitionings P
                INNER JOIN flows.partitioning_to_flow PF ON PF.fk_partitioning = P.id_partitioning
                INNER JOIN flows.partitioning_to_flow PF2 ON PF2.fk_flow = PF.fk_flow
        WHERE
            PF2.fk_partitioning = i_id_partitioning
            AND
            P.created_at < partitionCreateAt
        GROUP BY P.id_partitioning
        ORDER BY P.id_partitioning, P.created_at DESC
        LIMIT i_limit
        OFFSET i_offset;

    IF FOUND THEN
        status := 11;
        status_text := 'OK';
    ELSE
        status := 42;
        status_text := 'Ancestor Partitioning not found';
    END IF;
    RETURN NEXT;
    RETURN;

END;
$$
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_ancestors(BIGINT, INT, BIGINT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_ancestors(BIGINT, INT, BIGINT) TO atum_user;
