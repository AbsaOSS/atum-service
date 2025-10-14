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

ALTER SEQUENCE public.global_id_seq OWNER TO util_functions_owner;

CREATE OR REPLACE FUNCTION public.global_id() RETURNS BIGINT AS
$$
-------------------------------------------------------------------------------
--
-- Function: public.global_id(0)
--      Generates a unique ID
--
-- Returns:
--      - The next ID to use
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    RETURN nextval('global_id_seq');
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION public.global_id() OWNER TO util_functions_owner;
GRANT EXECUTE ON FUNCTION public.global_id() TO PUBLIC;
