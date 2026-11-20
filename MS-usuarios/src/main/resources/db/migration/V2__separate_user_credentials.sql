DELETE FROM usuarios
WHERE email = 'admin@marketexpress.com'
  AND nombre = 'Admin Market';

ALTER TABLE usuarios
    DROP COLUMN password_hash;
