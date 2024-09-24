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

-- THIS SCRIPT CONTAINS STUFF FOR LOCAL DATABASE TESTING ONLY; IT'S NOT TO BE DEPLOYED TO ANY AWS ENVIRONMENT

CREATE OR REPLACE FUNCTION public.jsonb_array_to_text_array(
    IN i_json_array JSONB
) RETURNS  text[]
-------------------------------------------------------------------------------
--
-- Function: public.jsonb_array_to_text_array(1)
--      Converts a JSONB array into a Postgres array
--
-- Parameters:
--      i_json_array         - JSON array
--
-- Returns:
--      Postgres array
--
-------------------------------------------------------------------------------
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS
    $$
        SELECT ARRAY(SELECT jsonb_array_elements_text(i_json_array));
    $$;

GRANT EXECUTE ON FUNCTION public.jsonb_array_to_text_array(JSONB) TO public;
