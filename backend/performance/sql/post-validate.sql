-- =====================================================================
--  POST-VALIDACIÓN T14 JMeter — después de ejecutar la carga 1000 A↔B
--  Target: 0 doble gasto, suma algebraica balances = suma inicial,
--          ledger = 2 * N * TRANSFERS (double entry)
--  Conexión: docker exec micropay-postgres psql -U app_user -d micropay -f backend/performance/sql/post-validate.sql
-- =====================================================================

-- [1] Doble gasto (same wallet id + pair user-currency >1 wallet) nunca pasa por UNIQUE ux_wallets_user_currency.
--      Validamos: para los users de carga creados con prefijo jmeter-*, ningún wallet quedó en NEGATIVE
--      y el número de movimientos por transacción P2P es exactamente 2.
WITH load_users AS (
    SELECT u.id, u.email, u.referral_code
    FROM users u
    WHERE u.email LIKE 'jmeter-%@example.com'
),
load_wallets AS (
    SELECT w.id, w.user_id, w.currency, w.balance, w.reserved, w.status, w.version
    FROM wallets w JOIN load_users lu ON w.user_id = lu.id
),
double_entry_check AS (
    SELECT t.id AS tx_id,
           COUNT(m.id) AS moves_count,
           SUM(m.amount) AS net_amount
    FROM transactions t
    JOIN load_wallets lw ON (lw.id = t.wallet_from_id OR lw.id = t.wallet_to_id)
    LEFT JOIN account_moves m ON m.transaction_id = t.id
    WHERE t.type = 'TRANSFER_P2P' AND t.status = 'COMPLETED'
    GROUP BY t.id
)
SELECT
    -- [1.1] 0 transacciones con != 2 movimientos (double entry broken)
    (SELECT COUNT(*) FROM double_entry_check WHERE moves_count <> 2) AS tx_moves_neq_2,
    -- [1.2] 0 transacciones con suma neta de moves != 0 (conservación)
    (SELECT COUNT(*) FROM double_entry_check WHERE ABS(net_amount) > 0.00000001) AS tx_net_nonzero,
    -- [1.3] 0 balances negativos (double spend directo)
    (SELECT COUNT(*) FROM wallets w JOIN load_users lu ON w.user_id = lu.id WHERE w.balance < 0) AS wallet_balances_negative,
    -- [1.4] Wallet status no degradados por lock/conflict
    (SELECT COUNT(*) FROM load_wallets WHERE status <> 'ACTIVE') AS wallets_not_active;

-- [2] Conservación moneda global para los wallets de JMeter:
--     Suma inicial (seeds) = Alice + Bob = seedA + seedB
--     Cualquier transfer A↔B deja total = seedA + seedB invariable.
WITH load_users AS (
    SELECT u.id, u.email,
           CASE WHEN email LIKE 'jmeter-alice%@example.com' THEN 'ALICE' ELSE 'BOB' END AS role
    FROM users u WHERE u.email LIKE 'jmeter-%@example.com'
),
load_wallets AS (
    SELECT lu.role, w.balance, w.currency
    FROM wallets w JOIN load_users lu ON w.user_id = lu.id
)
SELECT role, currency,
       SUM(balance) AS current_sum
FROM load_wallets
GROUP BY ROLLUP (role, currency)
ORDER BY role, currency;

-- [3] Consistencia wallet.balance vs ledger snapshots acumulados.
--     Para cada wallet de JMeter: último balanceAfter de movimientos = wallet.balance
WITH load_users AS (
    SELECT u.id FROM users u WHERE u.email LIKE 'jmeter-%@example.com'
),
load_wallets AS (
    SELECT w.id AS wallet_id, w.balance AS current_balance
    FROM wallets w JOIN load_users lu ON w.user_id = lu.id
),
last_move_per_wallet AS (
    SELECT m.wallet_id, m.balance_after,
           ROW_NUMBER() OVER (PARTITION BY m.wallet_id ORDER BY m.created_at DESC) AS rn
    FROM account_moves m
    JOIN load_wallets lw ON lw.wallet_id = m.wallet_id
)
SELECT
    COUNT(*) FILTER (WHERE lw.current_balance <> lmpw.balance_after) AS wallets_balance_mismatch_ledger
FROM load_wallets lw
LEFT JOIN last_move_per_wallet lmpw ON lmpw.wallet_id = lw.wallet_id AND lmpw.rn = 1;

-- [4] Métricas rough: throughput (tx/min) y número errores 409/422 en tabla failures (si existe;
--     para métricas detalladas mirar AggregateReport del .jtl)
SELECT status,
       type,
       COUNT(*) AS count
FROM transactions t
JOIN (SELECT w.id FROM wallets w
      JOIN users u ON w.user_id = u.id
      WHERE u.email LIKE 'jmeter-%@example.com') lw ON (lw.id = t.wallet_from_id OR lw.id = t.wallet_to_id)
GROUP BY status, type
ORDER BY count DESC;
