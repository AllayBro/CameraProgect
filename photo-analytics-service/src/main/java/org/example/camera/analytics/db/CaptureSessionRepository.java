package org.example.camera.analytics.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptureSessionRepository extends JpaRepository<CaptureSessionEntity, String> {}
