-- Fresh-install compatibility shim for the 2023-06 schema-cleanup migrations.
--
-- Those migrations DROP columns/tables/FKs that older versions of the JPA
-- entities used to create. The entities no longer declare them, so on a NEW
-- database (schema built by Hibernate ddl-auto) these objects never exist and
-- the historical DROP statements fail ("check that column/key exists").
--
-- This migration is versioned to run immediately BEFORE that cleanup block and
-- re-creates exactly those objects (types are irrelevant - they are dropped
-- moments later). Every statement is guarded with an existence check so it is
-- a safe no-op on databases where the objects are already present.

-- ---- Re-create obsolete columns -------------------------------------------
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND COLUMN_NAME='ingredients'), 'SELECT 1', 'ALTER TABLE Product ADD COLUMN ingredients TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND COLUMN_NAME='nutritionalValue'), 'SELECT 1', 'ALTER TABLE Product ADD COLUMN nutritionalValue TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND COLUMN_NAME='howToUse'), 'SELECT 1', 'ALTER TABLE Product ADD COLUMN howToUse TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND COLUMN_NAME='specialityDescription'), 'SELECT 1', 'ALTER TABLE Product ADD COLUMN specialityDescription TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND COLUMN_NAME='knowledgeBlog'), 'SELECT 1', 'ALTER TABLE Product ADD COLUMN knowledgeBlog TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND COLUMN_NAME='specialityDocument_id'), 'SELECT 1', 'ALTER TABLE Product ADD COLUMN specialityDocument_id BIGINT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductLabelContent' AND COLUMN_NAME='ingredients'), 'SELECT 1', 'ALTER TABLE ProductLabelContent ADD COLUMN ingredients TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductLabelContent' AND COLUMN_NAME='nutritionalValue'), 'SELECT 1', 'ALTER TABLE ProductLabelContent ADD COLUMN nutritionalValue TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductLabelContent' AND COLUMN_NAME='howToUse'), 'SELECT 1', 'ALTER TABLE ProductLabelContent ADD COLUMN howToUse TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductLabelContent' AND COLUMN_NAME='specialityDescription'), 'SELECT 1', 'ALTER TABLE ProductLabelContent ADD COLUMN specialityDescription TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductLabelContent' AND COLUMN_NAME='knowledgeBlog'), 'SELECT 1', 'ALTER TABLE ProductLabelContent ADD COLUMN knowledgeBlog TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductLabelContent' AND COLUMN_NAME='specialityDocument_id'), 'SELECT 1', 'ALTER TABLE ProductLabelContent ADD COLUMN specialityDocument_id BIGINT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Process' AND COLUMN_NAME='storage'), 'SELECT 1', 'ALTER TABLE Process ADD COLUMN storage TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Process' AND COLUMN_NAME='codesOfConduct'), 'SELECT 1', 'ALTER TABLE Process ADD COLUMN codesOfConduct TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Responsibility' AND COLUMN_NAME='relationship'), 'SELECT 1', 'ALTER TABLE Responsibility ADD COLUMN relationship TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Responsibility' AND COLUMN_NAME='farmer'), 'SELECT 1', 'ALTER TABLE Responsibility ADD COLUMN farmer TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Responsibility' AND COLUMN_NAME='story'), 'SELECT 1', 'ALTER TABLE Responsibility ADD COLUMN story TEXT NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductSettings' AND COLUMN_NAME='checkAuthenticity'), 'SELECT 1', 'ALTER TABLE ProductSettings ADD COLUMN checkAuthenticity TINYINT(1) NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductSettings' AND COLUMN_NAME='traceOrigin'), 'SELECT 1', 'ALTER TABLE ProductSettings ADD COLUMN traceOrigin TINYINT(1) NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ProductSettings' AND COLUMN_NAME='giveFeedback'), 'SELECT 1', 'ALTER TABLE ProductSettings ADD COLUMN giveFeedback TINYINT(1) NULL'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---- Re-create obsolete tables --------------------------------------------
CREATE TABLE IF NOT EXISTS Product_keyMarketsShare (id BIGINT NULL);
CREATE TABLE IF NOT EXISTS ProductLabelContent_keyMarketsShare (id BIGINT NULL);
CREATE TABLE IF NOT EXISTS ProcessStandard (id BIGINT NULL);
CREATE TABLE IF NOT EXISTS ProcessDocument (id BIGINT NULL);
CREATE TABLE IF NOT EXISTS ResponsibilityFarmerPicture (id BIGINT NULL);

-- ---- Re-create obsolete foreign keys --------------------------------------
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='Product' AND CONSTRAINT_NAME='FK5414co2yomrmr1wbiahodoi6j' AND CONSTRAINT_TYPE='FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE Product ADD CONSTRAINT FK5414co2yomrmr1wbiahodoi6j FOREIGN KEY (specialityDocument_id) REFERENCES Product(id)'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @s := (SELECT IF(EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='ProductLabelContent' AND CONSTRAINT_NAME='FKo78331b0piyfvhsjhibs5ckmo' AND CONSTRAINT_TYPE='FOREIGN KEY'), 'SELECT 1', 'ALTER TABLE ProductLabelContent ADD CONSTRAINT FKo78331b0piyfvhsjhibs5ckmo FOREIGN KEY (specialityDocument_id) REFERENCES ProductLabelContent(id)'));
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
