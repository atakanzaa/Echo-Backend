-- Coach oturumlarına journal entry bağlantısı
-- Kullanıcı bir journal hakkında coach ile konuşmak istediğinde kullanılır
ALTER TABLE coach_sessions ADD COLUMN journal_entry_id UUID REFERENCES journal_entries(id);
