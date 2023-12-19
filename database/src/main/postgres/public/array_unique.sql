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

CREATE OR REPLACE FUNCTION public.array_unique (
    IN i_text_array TEXT[]
) RETURNS TEXT[]
-------------------------------------------------------------------------------
--
-- Function: public.array_unique(1)
--      Returns a Postgres Array with unique elements with no NULLs
--      Based on the answer in https://dba.stackexchange.com/questions/226456/how-can-i-get-a-unique-array-in-postgresql
--
--      Note: the original order of the elements is not preserved; in fact, no kind of order is guaranteed
--
-- Parameters:
--      i_text_array    - Postgres array
--
-- Returns:
--      Postgres array with unique, non-null elements
--
-------------------------------------------------------------------------------
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
BEGIN ATOMIC
SELECT ARRAY (
           SELECT DISTINCT elem
           FROM UNNEST(array_remove(i_text_array, NULL)) AS arrayWithoutNulls(elem)
       );
END;

GRANT EXECUTE ON FUNCTION public.array_unique(TEXT[]) TO public;

