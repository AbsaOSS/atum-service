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

DROP FUNCTION IF EXISTS validation.is_partitioning_valid(JSONB, BOOL);
DROP FUNCTION IF EXISTS validation.validate_partitioning(JSONB, BOOL);

DROP FUNCTION IF EXISTS runs._get_id_partitioning(jsonb, boolean);
DROP FUNCTION IF EXISTS runs._write_measurement(uuid, bigint, text, text[], jsonb, text);
DROP FUNCTION IF EXISTS runs.create_partitioning_if_not_exists(jsonb, text, jsonb);
DROP FUNCTION IF EXISTS runs.write_checkpoint(jsonb, uuid, text, timestamp with time zone, timestamp with time zone, jsonb[], boolean, text);

DROP FUNCTION IF EXISTS flows._add_to_parent_flows(bigint, bigint, text);
DROP FUNCTION IF EXISTS flows._create_flow(bigint, text);
