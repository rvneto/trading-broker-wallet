-- Idempotency constraint: prevents duplicate processing of the same Kafka event
-- If the same order_id + transaction_type is received twice (e.g. Kafka reprocessing),
-- the insert will fail and the consumer will not commit the offset silently.
ALTER TABLE wallet_transactions
    ADD CONSTRAINT uq_order_transaction UNIQUE (order_id, transaction_type);