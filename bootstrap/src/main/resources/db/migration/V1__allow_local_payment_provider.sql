DO $$
DECLARE
    schema_name text := current_schema();
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = schema_name
          AND table_name = 'payments'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.payments DROP CONSTRAINT IF EXISTS payments_provider_check',
            schema_name
        );
        EXECUTE format(
            'ALTER TABLE %I.payments ADD CONSTRAINT payments_provider_check '
                'CHECK (provider IN (''LOCAL'', ''VNPAY'', ''MOMO'', ''STRIPE''))',
            schema_name
        );
    END IF;
END
$$;
