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
