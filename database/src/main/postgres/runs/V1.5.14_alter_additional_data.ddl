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

ALTER TABLE runs.additional_data DROP COLUMN updated_by;
ALTER TABLE runs.additional_data DROP COLUMN updated_at;

ALTER TABLE runs.additional_data DROP CONSTRAINT flows_unq;
ALTER TABLE runs.additional_data ADD CONSTRAINT ad_unq UNIQUE (fk_partitioning, ad_name);
