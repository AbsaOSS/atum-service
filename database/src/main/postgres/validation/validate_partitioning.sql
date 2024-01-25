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

CREATE OR REPLACE FUNCTION validation.validate_partitioning(
    IN i_partitioning JSONB,
    IN i_strict_check BOOLEAN = true
) RETURNS SETOF TEXT AS
$$
-------------------------------------------------------------------------------
--
-- Function: validation.validate_partitioning(2)
--      Validates the input partitioning and returns a set of individual error messages, one per an issue.
--      The validation performs:
--          1) Correct structure of the input JSONB object
--          2) The list of keys in 'keys' is unique and doesn't have NULLs
--          3) (if i_strict_check = true) The keys in 'keys' and in 'keysToValues' correspond to each other
--          4) (if i_strict_check = true) The values in 'keysToValues' are non-null
--
-- Parameters:
--      i_partitioning      - partitioning to validate, a valid example:
--                            {
--                              "keys": ["one", "two", "three"],
--                              "version": 1,
--                              "keysToValues": {
--                                  "one": "DatasetA",
--                                  "two": "Version1",
--                                  "three": "2022-12-20"
--                               }
--                            }
--      i_strict_check      - flag signaling whether the partitioning check should be strict or no.
--                            E.g., if an input partitioning is just a pattern, then values can have NULLs and
--                            such check would be skipped.
--
-- Returns:
--      Set of issues described as individual textual sentences detailing each problem.
--
-------------------------------------------------------------------------------
DECLARE
    _mandatory_fields_in_input CONSTANT TEXT[] := ARRAY['keys', 'version', 'keysToValues'];
    _all_fields_in_input TEXT[];

    _is_input_properly_structured BOOL;

    _version INTEGER;

    _partitioning_keys_all_cnt INTEGER;
    _partitioning_keys_uniq_and_not_null TEXT[];
    _partitioning_keys_uniq_and_not_null_cnt INTEGER;

    _partitioning_keys_from_values_map TEXT[];

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

    SELECT CAST(i_partitioning->>'version' AS INTEGER)
    INTO _version;

    IF _version != 1 THEN
        RETURN NEXT 'The input partitioning is not of the supported version. Should be 1, but it is: ' || _version;
    END IF;

    -- Checking whether the array 'keys' is valid, i.e. has unique, non-null elements.
    SELECT jsonb_array_length(i_partitioning->'keys')
    INTO _partitioning_keys_all_cnt;

    SELECT array_agg(X.keys), count(1)
    FROM (
         SELECT DISTINCT(JAE.value) AS keys
         FROM jsonb_array_elements_text(i_partitioning->'keys') AS JAE
         WHERE JAE.value IS NOT NULL
    ) AS X
    INTO _partitioning_keys_uniq_and_not_null, _partitioning_keys_uniq_and_not_null_cnt;

    IF _partitioning_keys_all_cnt != _partitioning_keys_uniq_and_not_null_cnt THEN
        RETURN NEXT 'The input partitioning is invalid, the keys must be unique and can not contain NULLs: '
            || (i_partitioning->>'keys');
    END IF;

    -- Checking whether the map 'keysToValues' has the same keys as the 'keys' attribute.
    IF i_strict_check THEN
        SELECT ARRAY(SELECT jsonb_object_keys(i_partitioning->'keysToValues'))
        INTO _partitioning_keys_from_values_map;

        IF NOT (
            (_partitioning_keys_from_values_map @> _partitioning_keys_uniq_and_not_null)
            AND (_partitioning_keys_from_values_map <@ _partitioning_keys_uniq_and_not_null)
        ) THEN

            RETURN NEXT
                'The input partitioning is invalid, the keys in ''keys'' and ''keysToValues'' do not correspond. '
                    || 'Given in ''keysToValues'': '
                    || _partitioning_keys_from_values_map::TEXT
                    || ' vs (probably expected from ''keys''): '
                    || _partitioning_keys_uniq_and_not_null::TEXT;
        END IF;
    END IF;

    -- Checking the validity of values in the map 'keysToValues',
    -- non-pattern-like partitioning can't have null values there.
    IF i_strict_check THEN
        PERFORM 1
        FROM jsonb_each_text(i_partitioning->'keysToValues') AS elem
        WHERE elem.value IS NULL;

        IF found THEN
            RETURN NEXT 'The input partitioning is invalid, some values in ''keysToValues'' are NULLs: '
                || (i_partitioning->>'keysToValues');
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql IMMUTABLE SECURITY DEFINER;

ALTER FUNCTION validation.validate_partitioning(JSONB, BOOL) OWNER TO atum_owner;
