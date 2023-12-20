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
) RETURNS SETOF TEXT AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._validate_partitioning(2)
--      Validates the input partitioning and returns a set of individual error messages, one per an issue.
--      The validation performs:
--          1) Correct structure of the input JSONB object
--          2) The list of keys in 'keys' is unique and doesn't have NULLs
--          3) The keys in 'keys' and in 'keysToValuesMap' correspond to each other
--          4) (if i_is_pattern = false) The values in 'keysToValuesMap' are non-empty/non-null
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
    _mandatory_fields_in_input CONSTANT TEXT[] := ARRAY['keys', 'version', 'keysToValuesMap'];
    _all_fields_in_input TEXT[];

    _is_input_properly_structured BOOL;

    _partitioning_keys_all_cnt INTEGER;
    _partitioning_keys_uniq_and_not_null TEXT[];
    _partitioning_keys_uniq_and_not_null_cnt INTEGER;

    _partitioning_keys_from_values_map TEXT[];

    _partitioning_null_values_cnt INTEGER;

BEGIN
    -- Checking whether the input has correct structure.
    SELECT ARRAY(SELECT jsonb_object_keys(i_partitioning))
    INTO _all_fields_in_input;

    SELECT i_partitioning ?& _mandatory_fields_in_input
    INTO _is_input_properly_structured;
    IF NOT _is_input_properly_structured THEN
        RETURN NEXT
            'The input partitioning is not properly structured, it should have this structure: '
                || _mandatory_fields_in_input::TEXT
                || ' but contains: '
                || _all_fields_in_input::TEXT;
    END IF;

    -- Checking whether the array 'keys' is valid, i.e. has unique, non-null elements.
    SELECT jsonb_array_length(i_partitioning->'keys')
    INTO _partitioning_keys_all_cnt;

    SELECT array_unique(x.keys)
    FROM jsonb_to_record(i_partitioning) AS x(keys text[])
    INTO _partitioning_keys_uniq_and_not_null;

    _partitioning_keys_uniq_and_not_null_cnt := array_length(_partitioning_keys_uniq_and_not_null, 1);

    IF _partitioning_keys_all_cnt != _partitioning_keys_uniq_and_not_null_cnt THEN
        RETURN NEXT 'The input partitioning is invalid, the keys must be unique and can not contain NULLs: '
            || (i_partitioning->>'keys');
    END IF;

    -- Checking whether the map 'keysToValuesMap' has the same keys as the 'keys' attribute.
    SELECT ARRAY(SELECT jsonb_object_keys(i_partitioning->'keysToValuesMap'))
    INTO _partitioning_keys_from_values_map;

    IF NOT (
            (_partitioning_keys_from_values_map @> _partitioning_keys_uniq_and_not_null)
            AND (_partitioning_keys_from_values_map <@ _partitioning_keys_uniq_and_not_null)
        ) THEN
        RETURN NEXT
            'The input partitioning is invalid, the keys in ''keys'' and ''keysToValuesMap'' do not correspond. '
                || 'Given in ''keysToValuesMap'': '
                || _partitioning_keys_from_values_map::TEXT
                || ' vs (probably expected from ''keys''): '
                || _partitioning_keys_uniq_and_not_null::TEXT;
    END IF;

    -- Checking the validity of values in the map 'keysToValuesMap',
    -- non-pattern-like partitioning can't have null values there.
    IF NOT i_is_pattern THEN
        SELECT COUNT(*)
        FROM jsonb_each_text(i_partitioning->'keysToValuesMap') AS elem
        WHERE COALESCE(TRIM(elem.value), '') = ''
        INTO _partitioning_null_values_cnt;

        IF _partitioning_null_values_cnt > 0 THEN
            RETURN NEXT 'The input partitioning is invalid, some values in ''keysToValuesMap'' are NULLs or empty: '
                || (i_partitioning->>'keysToValuesMap');
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql IMMUTABLE SECURITY DEFINER;

ALTER FUNCTION runs._validate_partitioning(JSONB, BOOL) OWNER TO atum_owner;