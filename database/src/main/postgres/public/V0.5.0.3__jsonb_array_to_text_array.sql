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

DROP FUNCTION jsonb_array_to_text_array(jsonb);

CREATE OR REPLACE FUNCTION public.jsonb_array_to_text_array(
    IN i_jsonb_array JSONB
) RETURNS  text[]
-------------------------------------------------------------------------------
--
-- Function: public.jsonb_array_to_text_array(1)
--      Converts a JSONB array into a Postgres array
--
-- Parameters:
--      i_jsonb_array         - JSONB array
--
-- Returns:
--      Postgres array
--
-------------------------------------------------------------------------------
AS
$$
    SELECT ARRAY(SELECT jsonb_array_elements_text(i_jsonb_array));
$$
LANGUAGE sql IMMUTABLE SECURITY INVOKER;

ALTER FUNCTION public.jsonb_array_to_text_array(JSONB) OWNER TO util_functions_owner;
GRANT EXECUTE ON FUNCTION public.jsonb_array_to_text_array(JSONB) TO public;
