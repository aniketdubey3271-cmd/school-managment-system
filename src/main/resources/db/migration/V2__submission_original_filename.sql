-- Keep the student's original filename for downloads; file_url holds the stored (random) name.
ALTER TABLE submissions ADD COLUMN original_filename VARCHAR(255) NOT NULL DEFAULT '';
