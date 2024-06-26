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

UPDATE flows.flows F
SET fk_primary_partitioning =
    (SELECT fk_partitioning
    FROM flows.partitioning_to_flow PTF
    WHERE PTF.fk_flow = F.id_flow
    ORDER BY fk_partitioning ASC
    LIMIT 1)
WHERE fk_primary_partitioning IS NULL;
