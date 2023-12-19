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

CREATE OR REPLACE FUNCTION runs._validate_partitioning(
    IN i_partitioning JSONB,
    IN i_is_pattern BOOL = false
)
    RETURNS SETOF TEXT AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._validate_partitioning(2)
--      Validates the input partitioning and returns a set of individual error messages, one per an issue.
--
-- Parameters:
--      i_partitioning      - partitioning to validate, a valid example:
-- 	                          {
--                              "keys": ["one", "two", "three"],
--                              "version": 1,
-- 	                            "keysToValuesMap": {
--                                  "one": "DatasetA",
--                                  "two": "Version1",
-- 	                          	    "three": "2022-12-20"
--                               }
--                            }
--      i_is_pattern        - flag signaling whether the partitioning is not a real one, but just a pattern.
--                            If the input partitioning is just a pattern, then values can have NULL values and
--                            such check would be skipped.
--
-- Returns:
--      Set of issues described as individual textual sentences detailing each problem.
--
-------------------------------------------------------------------------------
DECLARE
    _mandatoryFieldsInInput CONSTANT TEXT[] := ARRAY['keys', 'version', 'keysToValuesMap'];
    _allFieldsInInput TEXT[];

    _isPartitionProperlyStructured BOOL;

    _partitionKeysAllCnt INTEGER;
    _partitionKeysUniqAndNotNull TEXT[];
    _partitionKeysUniqAndNotNullCnt INTEGER;

    _partitioningKeysAll TEXT[];
    _partitioningKeysFromValuesMap TEXT[];

    _partitionNullValuesCnt INTEGER;

BEGIN
    -- Checking whether the input has correct structure.
    SELECT ARRAY(SELECT jsonb_object_keys(i_partitioning))
    INTO _allFieldsInInput;

    SELECT i_partitioning ?& _mandatoryFieldsInInput
    INTO _isPartitionProperlyStructured;

    IF NOT _isPartitionProperlyStructured THEN
        RETURN NEXT
            'The input partitioning is not properly structured, it should have this structure: '
                || _mandatoryFieldsInInput::TEXT
                || ' but contains: '
                || _allFieldsInInput::TEXT;
    END IF;

    -- Checking whether the array 'keys' is valid, i.e. has unique, non-null elements.
    SELECT ARRAY(SELECT i_partitioning->'keys')
    INTO _partitioningKeysAll;

    _partitionKeysAllCnt := array_length(_partitioningKeysAll, 1);

    WITH nonNullKeys AS (
        SELECT UNNEST(array_remove(x.keys, NULL))
        FROM jsonb_to_record(i_partitioning) AS x(keys text[])
    )
    SELECT ARRAY(SELECT DISTINCT * FROM nonNullKeys)
    INTO _partitionKeysUniqAndNotNull;

    _partitionKeysUniqAndNotNullCnt := array_length(_partitionKeysUniqAndNotNull, 1);

    IF _partitionKeysAllCnt != _partitionKeysUniqAndNotNullCnt THEN
        RETURN NEXT 'The input partitioning is invalid, the keys are not unique: '
            || array_to_string(_partitioningKeysAll, ', ');
    END IF;

    -- Checking whether the map 'keysToValuesMap' has the same keys as the 'keys' attribute.
    SELECT ARRAY(SELECT jsonb_object_keys(i_partitioning->'keysToValuesMap'))
    INTO _partitioningKeysFromValuesMap;

    IF NOT (
            (_partitioningKeysFromValuesMap @> _partitionKeysUniqAndNotNull)
            AND (_partitioningKeysFromValuesMap <@ _partitionKeysUniqAndNotNull)
        ) THEN
        RETURN NEXT
            'The keys in keysToValuesMap are not as it should: '
                || _partitioningKeysFromValuesMap::TEXT
                || ' vs (what it should be): '
                || _partitionKeysUniqAndNotNull::TEXT;
    END IF;

    -- Checking the validity of values in the map 'keysToValuesMap',
    -- non-pattern-like partitioning can't have null values there.
    IF NOT i_is_pattern THEN
        SELECT COUNT(*)
        FROM jsonb_each_text(i_partitioning->'keysToValuesMap') AS elem
        WHERE (elem.value) IS NULL
        INTO _partitionNullValuesCnt;

        IF _partitionNullValuesCnt > 0 THEN
            RETURN NEXT 'The values in keysToValuesMap have some NULL values present: '
                || (i_partitioning->>'keysToValuesMap');
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql IMMUTABLE SECURITY DEFINER;

ALTER FUNCTION runs._validate_partitioning(JSONB, BOOL) OWNER TO atum_owner;
