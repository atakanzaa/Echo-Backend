-- ALTER COLUMN TYPE enum→varchar PostgreSQL'de çalışmıyor (internal <> operatörü yok).
-- Çözüm: yeni VARCHAR kolon ekle → veriyi kopyala → eski kolonu sil → yeniden adlandır.

ALTER TABLE journal_entries ADD COLUMN status_new VARCHAR(20) NOT NULL DEFAULT 'uploading';
UPDATE journal_entries SET status_new = status::text;
ALTER TABLE journal_entries DROP COLUMN status;
ALTER TABLE journal_entries RENAME COLUMN status_new TO status;
DROP TYPE entry_status;
