DROP FUNCTION IF EXISTS flows.get_flow_checkpoints(BIGINT, INT, BIGINT, TEXT, HSTORE, BOOLEAN);

CREATE OR REPLACE FUNCTION flows.get_flow_checkpoints(
    IN i_flow_id BIGINT,
    IN i_checkpoints_limit INT DEFAULT NULL,
    IN i_offset BIGINT DEFAULT 0,
    IN i_checkpoint_name TEXT DEFAULT NULL,
    IN i_checkpoint_properties JSONB DEFAULT NULL,
    IN i_latest_first BOOLEAN DEFAULT TRUE,
    OUT status INTEGER,
    OUT status_text TEXT,
    OUT id_checkpoint UUID,
    OUT checkpoint_name TEXT,
    OUT checkpoint_author TEXT,
    OUT measured_by_atum_agent BOOLEAN,
    OUT measure_name TEXT,
    OUT measured_columns TEXT[],
    OUT measurement_value JSONB,
    OUT checkpoint_start_time TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_end_time TIMESTAMP WITH TIME ZONE,
    OUT id_partitioning BIGINT,
    OUT partitioning JSONB,
    OUT partitioning_author TEXT,
    OUT has_more BOOLEAN
)
    RETURNS SETOF record AS
$$
DECLARE
    _has_more     BOOLEAN;
    _latest_first BOOLEAN := coalesce(i_latest_first, TRUE);
BEGIN
    -- Check if the flow exists by querying the partitioning_to_flow table.
    -- Rationale:
    -- This table is preferred over the flows table because:
    -- 1. Every flow has at least one record in partitioning_to_flow.
    -- 2. This table is used in subsequent queries, providing a caching advantage.
    -- 3. Improves performance by reducing the need to query the flows table directly.
    PERFORM 1 FROM flows.partitioning_to_flow WHERE fk_flow = i_flow_id;
    IF NOT FOUND THEN
        status := 42;
        status_text := 'Flow not found';
        RETURN NEXT;
        RETURN;
    END IF;

    -- Determine if there are more checkpoints than the limit
    IF i_checkpoints_limit IS NOT NULL THEN
        SELECT count(*) > i_checkpoints_limit
        FROM (SELECT 1
              FROM runs.checkpoints C
                       JOIN flows.partitioning_to_flow PF ON C.fk_partitioning = PF.fk_partitioning
              WHERE PF.fk_flow = i_flow_id
                AND (i_checkpoint_name IS NULL OR C.checkpoint_name = i_checkpoint_name)
                AND (
                    i_checkpoint_properties IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM jsonb_each(i_checkpoint_properties) AS flt(k, v)
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM runs.checkpoint_properties CP
                            WHERE CP.fk_checkpoint = C.id_checkpoint
                              AND CP.property_name = flt.k
                              AND (v ? CP.property_value)
                        )
                    )
                )
              ORDER BY CASE
                          WHEN _latest_first THEN C.process_start_time
                          END DESC,
                      CASE
                          WHEN NOT _latest_first THEN C.process_start_time
                          END ASC,
                      C.id_checkpoint ASC
              LIMIT i_checkpoints_limit + 1 OFFSET i_offset) s
        INTO _has_more;
    ELSE
        _has_more := false;
    END IF;

    -- Retrieve the checkpoints and their associated measurements
    RETURN QUERY
        WITH limited_checkpoints AS (SELECT C.id_checkpoint,
                                            C.fk_partitioning,
                                            C.checkpoint_name,
                                            C.created_by,
                                            C.measured_by_atum_agent,
                                            C.process_start_time,
                                            C.process_end_time
                                     FROM runs.checkpoints C
                                              JOIN flows.partitioning_to_flow PF ON C.fk_partitioning = PF.fk_partitioning
                                     WHERE PF.fk_flow = i_flow_id
                                       AND (i_checkpoint_name IS NULL OR C.checkpoint_name = i_checkpoint_name)
                                       AND (
                                           i_checkpoint_properties IS NULL
                                           OR NOT EXISTS (
                                               SELECT 1
                                               FROM jsonb_each(i_checkpoint_properties) AS flt(k, v)
                                               WHERE NOT EXISTS (
                                                   SELECT 1
                                                   FROM runs.checkpoint_properties CP
                                                   WHERE CP.fk_checkpoint = C.id_checkpoint
                                                     AND CP.property_name = flt.k
                                                     AND (v ? CP.property_value)
                                               )
                                           )
                                       )
                                     ORDER BY CASE
                                                  WHEN _latest_first THEN C.process_start_time
                                                  END DESC,
                                              CASE
                                                  WHEN NOT _latest_first THEN C.process_start_time
                                                  END ASC,
                                              C.id_checkpoint ASC
                                     LIMIT i_checkpoints_limit OFFSET i_offset)
        SELECT 11                    AS status,
               'OK'                  AS status_text,
               LC.id_checkpoint,
               LC.checkpoint_name,
               LC.created_by         AS checkpoint_author,
               LC.measured_by_atum_agent,
               MD.measure_name,
               MD.measured_columns,
               M.measurement_value,
               LC.process_start_time AS checkpoint_start_time,
               LC.process_end_time   AS checkpoint_end_time,
               LC.fk_partitioning    AS id_partitioning,
               P.partitioning        AS partitioning,
               P.created_by          AS partitioning_author,
               _has_more             AS has_more
        FROM limited_checkpoints LC
                 INNER JOIN
             runs.measurements M ON LC.id_checkpoint = M.fk_checkpoint
                 INNER JOIN
             runs.measure_definitions MD ON M.fk_measure_definition = MD.id_measure_definition
                 INNER JOIN
             runs.partitionings P ON LC.fk_partitioning = P.id_partitioning
        ORDER BY CASE
                     WHEN _latest_first THEN LC.process_start_time
                     END DESC,
                 CASE
                     WHEN NOT _latest_first THEN LC.process_start_time
                     END ASC,
                 LC.id_checkpoint ASC;
END;
$$ LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION flows.get_flow_checkpoints(BIGINT, INT, BIGINT, TEXT, JSONB, BOOLEAN) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION flows.get_flow_checkpoints(BIGINT, INT, BIGINT, TEXT, JSONB, BOOLEAN) TO atum_owner;
