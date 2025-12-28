package org.example.camera.catalog.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogRecordRepository extends JpaRepository<CatalogRecordEntity, String> {
    List<CatalogRecordEntity> findBySessionId(String sessionId);
}
