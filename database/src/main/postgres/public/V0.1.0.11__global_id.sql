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


--DROP SEQUENCE IF EXISTS public.global_id_seq

-- DB_ID should be a unique number within your deployment between 0 and 9222
CREATE SEQUENCE IF NOT EXISTS public.global_id_seq
    INCREMENT 1
    START 2000000000000001
    MINVALUE 2000000000000001
    MAXVALUE 3000000000000000
    CACHE 1;

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

GRANT EXECUTE ON FUNCTION public.global_id() TO PUBLIC;

