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

package za.co.absa.atum.model

/**
 * Model defines the fields that will be used to retrieve the checkpoint from the database
 * @param partitioning name of the partition
 * @param user the user that the measures belongs to
 * @param parentPartitioning the name of the parent partition that the checkpoint belongs to
 */
case class CheckpointFilterCriteria (
                                      partitioning: String,
                                      byUser: String,
                                      parentPartitioning: String
                                    )
